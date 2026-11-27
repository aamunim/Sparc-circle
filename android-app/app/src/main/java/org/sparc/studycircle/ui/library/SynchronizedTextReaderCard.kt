package org.sparc.studycircle.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sparc.studycircle.core.domain.WordSyncEngine
import org.sparc.studycircle.core.media.SparcAudioController
import org.sparc.studycircle.core.model.ChapterItem
import org.sparc.studycircle.core.model.ReaderPaperTheme
import org.sparc.studycircle.core.model.WordTimestampCue
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.tr
import org.sparc.studycircle.ui.components.SparcPrimaryButton
import org.sparc.studycircle.ui.components.SparcScholarlyCard

private data class IndexedCue(
    val globalIndex: Int,
    val cue: WordTimestampCue
)

@Composable
fun SynchronizedTextReaderCard(
    bookTitle: String,
    chapter: ChapterItem,
    audioController: SparcAudioController,
    paperTheme: ReaderPaperTheme,
    fontSizeSp: Int,
    lineSpacingMultiplier: Float,
    onSaveHighlight: (paragraphNo: Int, snippet: String) -> Unit,
    onSaveEditedText: (chapterTitle: String, contentMode: String, fullText: String, subtitleRaw: String) -> Unit
) {
    val p = LocalSparcPalette.current
    var autoFollow by remember { mutableStateOf(true) }
    var showEditSheet by remember { mutableStateOf(false) }

    val effectiveText = remember(chapter.fullTextContent, chapter.flashNotes) {
        chapter.fullTextContent.trim().ifBlank {
            chapter.flashNotes.joinToString("\n\n")
        }
    }

    val effectiveDurationMs = remember(
        audioController.isStreamingCloudAudio,
        audioController.durationMs,
        chapter.audioDurationMinutes,
        effectiveText
    ) {
        when {
            audioController.isStreamingCloudAudio && audioController.durationMs > 2_000L ->
                audioController.durationMs
            chapter.wordTimestamps.isNotEmpty() ->
                chapter.wordTimestamps.last().e.coerceAtLeast(5_000L)
            else -> {
                val wordCount = effectiveText.split(Regex("\\s+")).count { it.isNotBlank() }
                (wordCount * 410L).coerceAtLeast(10_000L)
            }
        }
    }

    val cues = remember(effectiveText, chapter.wordTimestamps, effectiveDurationMs) {
        WordSyncEngine.resolvePlaybackCues(
            fullText = effectiveText,
            savedCues = chapter.wordTimestamps,
            actualDurationMs = effectiveDurationMs
        )
    }

    val activeWordIdx = remember(
        cues,
        audioController.currentPositionMs,
        audioController.isTtsSpeaking,
        audioController.ttsActiveWordIndex
    ) {
        if (audioController.isTtsSpeaking && audioController.ttsActiveWordIndex in cues.indices) {
            audioController.ttsActiveWordIndex
        } else {
            WordSyncEngine.findActiveWordIndex(cues, audioController.currentPositionMs)
        }
    }

    val paragraphs = remember(cues) {
        cues.mapIndexed { idx, c -> IndexedCue(idx, c) }
            .groupBy { it.cue.p }
            .toSortedMap()
            .values
            .toList()
    }

    val activeParaIdx = remember(activeWordIdx, cues, paragraphs) {
        val paraId = cues.getOrNull(activeWordIdx)?.p ?: -1
        paragraphs.indexOfFirst { it.firstOrNull()?.cue?.p == paraId }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(activeParaIdx, autoFollow, audioController.isPlaying) {
        if (autoFollow && audioController.isPlaying && activeParaIdx in paragraphs.indices) {
            listState.animateScrollToItem(activeParaIdx)
        }
    }

    val paperBg = Color(paperTheme.bgHex)
    val paperText = Color(paperTheme.textHex)
    val paperMuted = Color(paperTheme.mutedHex)
    val totalEndMs = cues.lastOrNull()?.e ?: effectiveDurationMs

    SparcScholarlyCard {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr("🎧 Read-Along টেক্সট ও শব্দে-শব্দে টাইমস্ট্যাম্প", "🎧 Read-Along Text & Word Timestamps"),
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text
                )
                Text(
                    text = if (audioController.isStreamingCloudAudio) {
                        tr(
                            "☁️ ক্লাউড অডিও সিঙ্ক সক্রিয় • যেকোনো শব্দে ট্যাপ করলে অডিও সেখানে যাবে",
                            "☁️ Cloud MP3 synced • Tap any word to jump audio there"
                        )
                    } else {
                        tr(
                            "🗣️ বিল্ট-ইন বাংলা/ইংরেজি অডিও রিডার প্রস্তুত • যেকোনো শব্দে ট্যাপ করে শুনুন",
                            "🗣️ Built-in Voice Reader ready • Tap Play or tap any word"
                        )
                    },
                    fontSize = 11.5.sp,
                    color = p.muted
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = p.cardAlt,
                border = BorderStroke(1.dp, p.border),
                modifier = Modifier.clickable { showEditSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = p.primary, modifier = Modifier.size(15.dp))
                    Text(tr("টেক্সট এডিট", "Edit Text"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.primary)
                }
            }
        }

        // Sticky Read-Along Playback & Timestamp Bar
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = p.primarySoft,
            border = BorderStroke(1.dp, p.primary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(p.primary)
                            .clickable { audioController.toggleReadAlong(cues) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (audioController.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play Read-Along",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "${WordSyncEngine.formatTimestampMs(audioController.currentPositionMs)} / ${WordSyncEngine.formatTimestampMs(totalEndMs)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            color = p.primarySoftText
                        )
                        val activeWordLabel = cues.getOrNull(activeWordIdx)?.let {
                            "⏱️ [${WordSyncEngine.formatTimestampMs(it.s)}] \"${it.w}\""
                        } ?: tr("শব্দে ট্যাপ করে শুরু করুন", "Tap Play or any word")
                        Text(
                            text = activeWordLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = p.primarySoftText.copy(alpha = 0.88f),
                            maxLines = 1
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = p.card,
                        modifier = Modifier.clickable { audioController.cyclePlaybackSpeed() }
                    ) {
                        Text(
                            text = "${audioController.speed}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = p.text,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (autoFollow) p.primary else p.card,
                        modifier = Modifier.clickable { autoFollow = !autoFollow }
                    ) {
                        Text(
                            text = tr("🎯 অটো-স্ক্রল", "🎯 Follow"),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (autoFollow) Color.White else p.muted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Paper-Themed Word-by-Word Synchronized Reader Viewport
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = paperBg,
            border = BorderStroke(1.dp, p.border),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 460.dp)
        ) {
            if (paragraphs.isEmpty()) {
                Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        tr("এই অধ্যায়ে এখনো টেক্সট যুক্ত হয়নি। উপরের 'টেক্সট এডিট' বাটনে ট্যাপ করে টেক্সট যোগ করুন।", "No direct text yet. Tap 'Edit Text' above to paste or upload chapter text."),
                        color = paperMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(paragraphs) { pIdx, paraCues ->
                        val firstCue = paraCues.first().cue
                        val isCurrentPara = pIdx == activeParaIdx && audioController.isPlaying

                        // Build character-range -> globalIndex lookup + AnnotatedString for silky 60fps rendering
                        val wordRanges = remember(paraCues) {
                            val list = ArrayList<Triple<Int, Int, Int>>(paraCues.size)
                            var cursor = 0
                            paraCues.forEachIndexed { i, ic ->
                                val len = ic.cue.w.length
                                list.add(Triple(cursor, cursor + len, ic.globalIndex))
                                cursor += len + if (i < paraCues.lastIndex) 1 else 0
                            }
                            list
                        }

                        val annotated = remember(paraCues, activeWordIdx, paperText, paperMuted) {
                            buildAnnotatedString {
                                paraCues.forEachIndexed { i, ic ->
                                    val start = length
                                    append(ic.cue.w)
                                    val end = length
                                    when {
                                        ic.globalIndex == activeWordIdx -> {
                                            addStyle(
                                                SpanStyle(
                                                    background = Color(0xFFFACC15),
                                                    color = Color(0xFF0F172A),
                                                    fontWeight = FontWeight.ExtraBold
                                                ),
                                                start,
                                                end
                                            )
                                        }
                                        activeWordIdx >= 0 && ic.globalIndex < activeWordIdx && isCurrentPara -> {
                                            addStyle(
                                                SpanStyle(color = paperText.copy(alpha = 0.72f)),
                                                start,
                                                end
                                            )
                                        }
                                        else -> {
                                            addStyle(SpanStyle(color = paperText), start, end)
                                        }
                                    }
                                    if (i < paraCues.lastIndex) append(" ")
                                }
                            }
                        }

                        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrentPara) Color(0xFF2563EB).copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF2563EB).copy(alpha = 0.14f),
                                    modifier = Modifier.clickable {
                                        audioController.seekReadAlongToCue(paraCues.first().globalIndex, cues)
                                    }
                                ) {
                                    Text(
                                        text = "▶ ${WordSyncEngine.formatTimestampMs(firstCue.s)}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            val snippet = paraCues.joinToString(" ") { it.cue.w }.take(220)
                                            onSaveHighlight(pIdx + 1, snippet)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.BookmarkAdd,
                                        contentDescription = null,
                                        tint = paperMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        tr("হাইলাইট", "Save"),
                                        fontSize = 10.sp,
                                        color = paperMuted,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Text(
                                text = annotated,
                                style = TextStyle(
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * lineSpacingMultiplier).sp,
                                    color = paperText
                                ),
                                onTextLayout = { layoutResult = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(wordRanges, cues) {
                                        detectTapGestures { tapOffset ->
                                            val res = layoutResult ?: return@detectTapGestures
                                            val charOffset = res.getOffsetForPosition(tapOffset)
                                            val hit = wordRanges.firstOrNull { (s, e, _) ->
                                                charOffset in s..e
                                            } ?: wordRanges.minByOrNull { (s, _, _) ->
                                                kotlin.math.abs(charOffset - s)
                                            }
                                            if (hit != null) {
                                                audioController.seekReadAlongToCue(hit.third, cues)
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet) {
        DirectChapterTextEditorSheet(
            bookTitle = bookTitle,
            chapter = chapter,
            onDismiss = { showEditSheet = false },
            onSave = { title, mode, txt, sub ->
                onSaveEditedText(title, mode, txt, sub)
                showEditSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectChapterTextEditorSheet(
    bookTitle: String,
    chapter: ChapterItem,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    val p = LocalSparcPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(chapter.title) }
    var contentMode by remember {
        mutableStateOf(chapter.contentMode.ifBlank { "text" })
    }
    var fullText by remember { mutableStateOf(chapter.fullTextContent) }
    var subtitleRaw by remember { mutableStateOf("") }

    val openTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText()
                    }.getOrNull()
                }
                if (!loaded.isNullOrBlank()) {
                    if (loaded.contains("-->") || loaded.trimStart().startsWith("[")) {
                        subtitleRaw = loaded
                    } else {
                        fullText = loaded
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        contentColor = p.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                tr("📝 সরাসরি টেক্সট ও টাইমস্ট্যাম্প আপলোড", "📝 Direct Chapter Text & Timestamp Upload"),
                style = MaterialTheme.typography.titleLarge,
                color = p.text
            )
            Text("$bookTitle • Ch ${chapter.chapterNo}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.primary)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "text" to tr("📝 শুধু টেক্সট", "📝 Text Only"),
                    "hybrid" to tr("📚 টেক্সট + PDF", "📚 Hybrid"),
                    "pdf" to tr("📄 শুধু PDF", "📄 PDF Only")
                ).forEach { (m, label) ->
                    val active = contentMode == m
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) p.primary else p.cardAlt,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { contentMode = m }
                    ) {
                        Text(
                            label,
                            color = if (active) Color.White else p.text,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(tr("অধ্যায়ের শিরোনাম", "Chapter Title")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = p.primarySoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openTextLauncher.launch(arrayOf("text/*", "application/json", "*/*")) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = p.primarySoftText)
                    Text(
                        tr("📂 ফোন থেকে .txt / .md / .srt / .vtt ফাইল নিন", "📂 Import .txt / .md / .srt / .vtt File"),
                        color = p.primarySoftText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedTextField(
                value = fullText,
                onValueChange = { fullText = it },
                label = { Text(tr("অধ্যায়ের সম্পূর্ণ পাঠ্য (বাংলা/ইংরেজি)", "Full Chapter Text")) },
                minLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            if (subtitleRaw.isNotBlank()) {
                Text(
                    tr("✓ সাবটাইটেল টাইমস্ট্যাম্প ফাইল লোড হয়েছে!", "✓ Subtitle timestamp file loaded!"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.primary
                )
            }

            SparcPrimaryButton(
                text = tr("💾 টেক্সট ও শব্দে-শব্দে টাইমস্ট্যাম্প সংরক্ষণ করুন", "💾 Save Text & Word Timestamps"),
                onClick = { onSave(title, contentMode, fullText, subtitleRaw) }
            )
        }
    }
}
