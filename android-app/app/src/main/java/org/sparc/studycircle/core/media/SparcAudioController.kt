package org.sparc.studycircle.core.media

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class SparcAudioController(private val context: Context) {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    var isPlaying by mutableStateOf(false)
        private set
    var currentPositionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(18_000L)
        private set
    var speed by mutableFloatStateOf(1.0f)
        private set

    private val fallbackFile: File by lazy {
        ensureLocalPreviewWav(context)
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.duration > 0) {
                    durationMs = player.duration
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Gracefully fall back to local generated audio summary preview if remote URL is unreachable
                loadLocalFallback()
            }
        })
        loadLocalFallback()
    }

    fun loadUrlOrFallback(remoteUrl: String?) {
        if (!remoteUrl.isNullOrBlank() && remoteUrl.startsWith("http")) {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(remoteUrl)))
            player.prepare()
        } else {
            loadLocalFallback()
        }
    }

    private fun loadLocalFallback() {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(fallbackFile)))
        player.prepare()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
        player.seekTo(target)
        currentPositionMs = target
    }

    fun skipSeconds(deltaSeconds: Int) {
        seekTo(player.currentPosition + (deltaSeconds * 1000L))
    }

    fun cyclePlaybackSpeed() {
        val next = when (speed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        speed = next
        player.playbackParameters = PlaybackParameters(next)
    }

    fun syncPosition() {
        currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        if (player.duration > 0) {
            durationMs = player.duration
        }
    }

    fun release() {
        player.release()
    }

    companion object {
        /**
         * Generates a pleasant 18-second harmonic ambient study audio track (16-bit PCM WAV)
         * so ExoPlayer works offline out-of-the-box before real MP3 summaries are uploaded.
         */
        private fun ensureLocalPreviewWav(context: Context): File {
            val file = File(context.cacheDir, "sparc_audio_summary_preview.wav")
            if (file.exists() && file.length() > 1024) return file

            val sampleRate = 16_000
            val seconds = 18
            val numSamples = sampleRate * seconds
            val dataSize = numSamples * 2
            val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF WAV header
            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(36 + dataSize)
            buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
            buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16)
            buffer.putShort(1) // PCM
            buffer.putShort(1) // Mono
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * 2)
            buffer.putShort(2)
            buffer.putShort(16)
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(dataSize)

            val chordFreqs = doubleArrayOf(261.63, 329.63, 392.00, 523.25) // C-major calm study chord
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

    LaunchedEffect(controller.isPlaying) {
        while (controller.isPlaying) {
            controller.syncPosition()
            delay(250L)
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    return controller
}
