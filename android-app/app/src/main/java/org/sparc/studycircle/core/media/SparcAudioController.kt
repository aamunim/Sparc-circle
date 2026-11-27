package org.sparc.studycircle.core.media

import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import org.sparc.studycircle.core.model.WordTimestampCue
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

class SparcAudioController(private val context: Context) {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    var isPlaying by mutableStateOf(false)
        private set
    var isTtsSpeaking by mutableStateOf(false)
        private set
    var currentPositionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(18_000L)
        private set
    var speed by mutableFloatStateOf(1.0f)
        private set
    var isStreamingCloudAudio by mutableStateOf(false)
        private set

    /** Direct active word index emitted by Android TextToSpeech `onRangeStart` when in TTS mode. */
    var ttsActiveWordIndex by mutableIntStateOf(-1)
        private set

    private var ttsSpokenText: String = ""
    private var ttsCharOffsetToWordIdx: IntArray = IntArray(0)
    private var ttsBaseStartWordIdx: Int = 0
    private var ttsClockAnchorElapsedMs: Long = 0L
    private var ttsClockAnchorPositionMs: Long = 0L

    private val fallbackFile: File by lazy {
        ensureLocalPreviewWav(context)
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (!isTtsSpeaking) {
                    isPlaying = playing
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isTtsSpeaking && playbackState == Player.STATE_READY && player.duration > 0) {
                    durationMs = player.duration
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                loadLocalFallback()
            }
        })
        loadLocalFallback()

        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = (status == TextToSpeech.SUCCESS)
                if (ttsReady) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            isTtsSpeaking = true
                            isPlaying = true
                        }

                        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                            val map = ttsCharOffsetToWordIdx
                            if (start in map.indices) {
                                ttsActiveWordIndex = ttsBaseStartWordIdx + map[start]
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            isTtsSpeaking = false
                            isPlaying = false
                            ttsActiveWordIndex = -1
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            isTtsSpeaking = false
                            isPlaying = false
                        }
                    })
                }
            }
        }
    }

    fun loadUrlOrFallback(remoteUrl: String?) {
        stopTts()
        val validCloud = !remoteUrl.isNullOrBlank() &&
            remoteUrl.startsWith("http") &&
            !remoteUrl.contains("storage.sparc.org.bd")
        if (validCloud) {
            isStreamingCloudAudio = true
            player.setMediaItem(MediaItem.fromUri(Uri.parse(remoteUrl)))
            player.prepare()
        } else {
            loadLocalFallback()
        }
    }

    private fun loadLocalFallback() {
        isStreamingCloudAudio = false
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(fallbackFile)))
        player.prepare()
    }

    fun togglePlayPause() {
        if (isTtsSpeaking) {
            stopTts()
            return
        }
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        }
    }

    /**
     * Plays either the cloud MP3 (if uploaded) OR speaks [cues] aloud via Android's built-in
     * Bengali/English TextToSpeech engine with synchronized word-by-word timestamps!
     */
    fun toggleReadAlong(cues: List<WordTimestampCue>, preferTtsWhenNoMp3: Boolean = true) {
        if (isStreamingCloudAudio || !preferTtsWhenNoMp3 || cues.isEmpty()) {
            togglePlayPause()
            return
        }
        if (isTtsSpeaking) {
            stopTts()
        } else {
            val resumeIdx = cues.indexOfFirst { it.e > currentPositionMs }.takeIf { it >= 0 } ?: 0
            speakCuesFromIndex(cues, resumeIdx)
        }
    }

    fun seekReadAlongToCue(cueIndex: Int, cues: List<WordTimestampCue>) {
        if (cueIndex !in cues.indices) return
        val targetMs = cues[cueIndex].s
        if (isStreamingCloudAudio) {
            seekTo(targetMs)
            if (!player.isPlaying) player.play()
        } else {
            currentPositionMs = targetMs
            durationMs = cues.last().e.coerceAtLeast(1_000L)
            if (isTtsSpeaking || ttsReady) {
                speakCuesFromIndex(cues, cueIndex)
            }
        }
    }

    private fun speakCuesFromIndex(cues: List<WordTimestampCue>, startIdx: Int) {
        val engine = tts
        player.pause()
        val slice = cues.subList(startIdx.coerceIn(0, cues.lastIndex), cues.size)
        val totalEndMs = cues.last().e.coerceAtLeast(1_000L)
        durationMs = totalEndMs
        currentPositionMs = slice.first().s
        ttsBaseStartWordIdx = startIdx
        ttsActiveWordIndex = startIdx
        ttsClockAnchorElapsedMs = android.os.SystemClock.elapsedRealtime()
        ttsClockAnchorPositionMs = currentPositionMs

        // Build exact character-offset -> local word index map for UtteranceProgressListener.onRangeStart
        val sb = StringBuilder()
        val indexList = ArrayList<Int>(slice.sumOf { it.w.length + 1 })
        slice.forEachIndexed { localIdx, c ->
            for (ch in c.w) {
                sb.append(ch)
                indexList.add(localIdx)
            }
            sb.append(' ')
            indexList.add(localIdx)
        }
        ttsSpokenText = sb.toString()
        ttsCharOffsetToWordIdx = indexList.toIntArray()

        isTtsSpeaking = true
        isPlaying = true

        if (engine != null && ttsReady) {
            val hasBengali = ttsSpokenText.any { it.code in 0x0980..0x09FF }
            val targetLocale = if (hasBengali) Locale.forLanguageTag("bn-BD") else Locale.US
            val res = engine.setLanguage(targetLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (hasBengali) engine.setLanguage(Locale.forLanguageTag("bn"))
            }
            engine.setSpeechRate(speed)
            // Speak up to 3900 chars per TTS limit
            engine.speak(ttsSpokenText.take(3900), TextToSpeech.QUEUE_FLUSH, null, "sparc_read_along")
        }
    }

    fun stopTts() {
        runCatching { tts?.stop() }
        isTtsSpeaking = false
        ttsActiveWordIndex = -1
        if (!player.isPlaying) {
            isPlaying = false
        }
    }

    fun seekTo(positionMs: Long) {
        if (isTtsSpeaking) {
            ttsClockAnchorElapsedMs = android.os.SystemClock.elapsedRealtime()
            ttsClockAnchorPositionMs = positionMs.coerceAtLeast(0L)
            currentPositionMs = ttsClockAnchorPositionMs
            return
        }
        val target = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
        player.seekTo(target)
        currentPositionMs = target
    }

    fun skipSeconds(deltaSeconds: Int) {
        seekTo(currentPositionMs + (deltaSeconds * 1000L))
    }

    fun cyclePlaybackSpeed() {
        val next = when (speed) {
            0.75f -> 1.0f
            1.0f -> 1.25f
            1.25f -> 1.5f
            else -> 0.75f
        }
        speed = next
        player.playbackParameters = PlaybackParameters(next)
        tts?.setSpeechRate(next)
        if (isTtsSpeaking) {
            ttsClockAnchorElapsedMs = android.os.SystemClock.elapsedRealtime()
            ttsClockAnchorPositionMs = currentPositionMs
        }
    }

    fun syncPosition() {
        if (isTtsSpeaking) {
            val elapsed = ((android.os.SystemClock.elapsedRealtime() - ttsClockAnchorElapsedMs) * speed).toLong()
            val nextPos = ttsClockAnchorPositionMs + elapsed
            if (nextPos >= durationMs) {
                currentPositionMs = durationMs
                stopTts()
            } else {
                currentPositionMs = nextPos
            }
            return
        }
        currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        if (player.duration > 0) {
            durationMs = player.duration
        }
    }

    fun release() {
        stopTts()
        runCatching { tts?.shutdown() }
        player.release()
    }

    companion object {
        private fun ensureLocalPreviewWav(context: Context): File {
            val file = File(context.cacheDir, "sparc_audio_summary_preview.wav")
            if (file.exists() && file.length() > 1024) return file

            val sampleRate = 16_000
            val seconds = 18
            val numSamples = sampleRate * seconds
            val dataSize = numSamples * 2
            val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(36 + dataSize)
            buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
            buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16)
            buffer.putShort(1)
            buffer.putShort(1)
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * 2)
            buffer.putShort(2)
            buffer.putShort(16)
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(dataSize)

            val chordFreqs = doubleArrayOf(261.63, 329.63, 392.00, 523.25)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate.toDouble()
                val noteFreq = chordFreqs[((t / 2.25).toInt()) % chordFreqs.size]
                val envelope = 0.22 * (0.6 + 0.4 * sin(2.0 * PI * 0.5 * t))
                val wave = (sin(2.0 * PI * noteFreq * t) + 0.4 * sin(2.0 * PI * (noteFreq * 1.5) * t)) * envelope
                val pcm = (wave * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                buffer.putShort(pcm)
            }

            FileOutputStream(file).use { it.write(buffer.array()) }
            return file
        }
    }
}

@Composable
fun rememberSparcAudioController(remoteUrl: String? = null): SparcAudioController {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = remember { SparcAudioController(context) }

    LaunchedEffect(remoteUrl) {
        controller.loadUrlOrFallback(remoteUrl)
    }

    // 60ms high-precision ticker for silky word-by-word karaoke highlighting
    LaunchedEffect(controller.isPlaying, controller.isTtsSpeaking) {
        while (controller.isPlaying || controller.isTtsSpeaking) {
            controller.syncPosition()
            delay(60L)
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    return controller
}
