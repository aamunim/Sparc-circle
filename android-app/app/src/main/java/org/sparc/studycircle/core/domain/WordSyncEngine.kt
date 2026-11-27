package org.sparc.studycircle.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.sparc.studycircle.core.model.WordTimestampCue
import kotlin.math.max
import kotlin.math.roundToLong

object WordSyncEngine {

    private val json = Json { ignoreUnknownKeys = true }

    fun splitTextIntoParagraphs(rawText: String): List<String> =
        rawText.replace("\r\n", "\n")
            .split(Regex("\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun estimateWordWeight(word: String): Double {
        val alphaNumCount = word.count { it.isLetterOrDigit() }.coerceAtLeast(2)
        val pauseBonus = when {
            word.endsWith("।") || word.endsWith(".") || word.endsWith("?") || word.endsWith("!") -> 4.5
            word.endsWith(",") || word.endsWith(";") || word.endsWith(":") || word.endsWith("—") || word.endsWith("-") -> 2.2
            else -> 0.0
        }
        return alphaNumCount.toDouble() + pauseBonus
    }

    /**
     * Generates smooth word-by-word millisecond timestamps across all paragraphs of [rawText]
     * scaled to [totalDurationMs]. Used both when publishing direct text and as a zero-setup fallback.
     */
    fun buildAutoWordTimeline(rawText: String, totalDurationMs: Long): List<WordTimestampCue> {
        val paragraphs = splitTextIntoParagraphs(rawText)
        if (paragraphs.isEmpty()) return emptyList()

        val paraWords = paragraphs.map { p -> p.split(Regex("\\s+")).filter { it.isNotBlank() } }
        val totalWords = paraWords.sumOf { it.size }
        if (totalWords == 0) return emptyList()

        val safeDuration = max(totalDurationMs, (totalWords * 380L).coerceAtLeast(5_000L))
        val paraWeights = paraWords.map { words ->
            words.sumOf { estimateWordWeight(it) }.coerceAtLeast(1.0)
        }
        val totalWeight = paraWeights.sum().coerceAtLeast(1.0)

        val cues = ArrayList<WordTimestampCue>(totalWords)
        var paraCursor = 0.0
        for (pIdx in paragraphs.indices) {
            val pSpan = (paraWeights[pIdx] / totalWeight) * safeDuration.toDouble()
            val pStart = paraCursor
            val pEnd = if (pIdx == paragraphs.lastIndex) safeDuration.toDouble() else (paraCursor + pSpan)
            val words = paraWords[pIdx]
            val pWeight = paraWeights[pIdx]
            var wCursor = pStart
            for (wIdx in words.indices) {
                val w = words[wIdx]
                val wSpan = (estimateWordWeight(w) / pWeight) * (pEnd - pStart)
                val s = wCursor.roundToLong()
                val e = if (wIdx == words.lastIndex) {
                    pEnd.roundToLong().coerceAtLeast(s + 40L)
                } else {
                    (wCursor + wSpan).roundToLong().coerceAtLeast(s + 40L)
                }
                cues.add(WordTimestampCue(w = w, s = s, e = e, p = pIdx))
                wCursor += wSpan
            }
            paraCursor += pSpan
        }
        return cues
    }

    /**
     * Resolves cues for playback: if saved [savedCues] exist and match the text, scales them
     * gently if actual audio [actualDurationMs] differs materially; otherwise builds an auto timeline.
     */
    fun resolvePlaybackCues(
        fullText: String,
        savedCues: List<WordTimestampCue>,
        actualDurationMs: Long
    ): List<WordTimestampCue> {
        if (savedCues.isNotEmpty()) {
            val lastEnd = savedCues.last().e.coerceAtLeast(1L)
            if (actualDurationMs > 2_000L && kotlin.math.abs(actualDurationMs - lastEnd) > 3_000L) {
                val ratio = actualDurationMs.toDouble() / lastEnd.toDouble()
                return savedCues.map { c ->
                    c.copy(
                        s = (c.s * ratio).roundToLong(),
                        e = (c.e * ratio).roundToLong().coerceAtLeast((c.s * ratio).roundToLong() + 30L)
                    )
                }
            }
            return savedCues
        }
        return buildAutoWordTimeline(fullText, actualDurationMs)
    }

    /** Binary search in O(log N) for the active word cue at [positionMs]. */
    fun findActiveWordIndex(cues: List<WordTimestampCue>, positionMs: Long): Int {
        if (cues.isEmpty() || positionMs < 0L) return -1
        var lo = 0
        var hi = cues.lastIndex
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = cues[mid]
            if (positionMs in c.s..c.e) return mid
            if (positionMs > c.e) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }

    fun parseSubtitleToWordTimestamps(rawSubtitle: String): Pair<String, List<WordTimestampCue>> {
        val trimmed = rawSubtitle.trim()
        require(trimmed.isNotEmpty()) { "Subtitle input is empty" }

        if (trimmed.startsWith("[")) {
            val arr = json.parseToJsonElement(trimmed).jsonArray
            val parsed = arr.mapNotNull { el ->
                val o = el.jsonObject
                val w = o["w"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val s = o["s"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val e = o["e"]?.jsonPrimitive?.longOrNull ?: (s + 250L)
                val p = o["p"]?.jsonPrimitive?.intOrNull ?: 0
                WordTimestampCue(w = w, s = s, e = e, p = p)
            }
            val text = parsed.groupBy { it.p }
                .toSortedMap()
                .values
                .joinToString("\n\n") { list -> list.joinToString(" ") { it.w } }
            return text to parsed
        }

        val blocks = trimmed
            .replace("\r\n", "\n")
            .replace(Regex("^WEBVTT[^\n]*\n+", RegexOption.IGNORE_CASE), "")
            .split(Regex("\n{2,}"))

        val cues = mutableListOf<WordTimestampCue>()
        val paragraphs = mutableListOf<String>()
        val timeRegex = Regex("""(?:(\d{1,2}):)?(\d{1,2}):(\d{1,2})[.,](\d{1,3})""")

        fun parseMs(part: String): Long? {
            val m = timeRegex.find(part.trim()) ?: return null
            val hrs = m.groupValues[1].toLongOrNull() ?: 0L
            val mins = m.groupValues[2].toLongOrNull() ?: 0L
            val secs = m.groupValues[3].toLongOrNull() ?: 0L
            val ms = m.groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            return ((hrs * 3600L + mins * 60L + secs) * 1000L) + ms
        }

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val arrowIdx = lines.indexOfFirst { it.contains("-->") }
            if (arrowIdx == -1) continue
            val parts = lines[arrowIdx].split("-->")
            val startMs = parseMs(parts.getOrNull(0).orEmpty().trim()) ?: continue
            val endMs = parseMs(parts.getOrNull(1).orEmpty().trim().substringBefore(" ")) ?: continue
            if (endMs <= startMs) continue

            val textLine = lines.drop(arrowIdx + 1)
                .joinToString(" ")
                .replace(Regex("<[^>]+>"), "")
                .trim()
            if (textLine.isEmpty()) continue

            val pIdx = paragraphs.size
            paragraphs.add(textLine)
            val words = textLine.split(Regex("\\s+")).filter { it.isNotBlank() }
            val weightSum = words.sumOf { estimateWordWeight(it) }.coerceAtLeast(1.0)
            var cursor = startMs.toDouble()
            words.forEachIndexed { idx, w ->
                val span = (estimateWordWeight(w) / weightSum) * (endMs - startMs).toDouble()
                val s = cursor.roundToLong()
                val e = if (idx == words.lastIndex) endMs else (cursor + span).roundToLong().coerceAtLeast(s + 30L)
                cues.add(WordTimestampCue(w = w, s = s, e = e, p = pIdx))
                cursor += span
            }
        }
        require(cues.isNotEmpty()) { "No valid .srt / .vtt / JSON cues found" }
        return paragraphs.joinToString("\n\n") to cues
    }

    fun formatTimestampMs(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }
}
