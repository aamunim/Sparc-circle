package org.sparc.studycircle.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.BookItem
import org.sparc.studycircle.core.model.HighlightVisibility

@Composable
fun LibraryAndReaderScreen(
    state: SparcAppState,
    repository: SparcRepository
) {
    var selectedBookId by remember { mutableStateOf(state.books.first().id) }
    val selectedBook = state.books.find { it.id == selectedBookId } ?: state.books.first()
    var selectedChapterNo by remember(selectedBookId) { mutableIntStateOf(1) }
    val currentChapter = selectedBook.chapters.find { it.chapterNo == selectedChapterNo }
        ?: selectedBook.chapters.first()

    // Reading Speed & Daily Page Target Estimator state (Section 3.3)
    var targetDays by remember { mutableIntStateOf(8) }
    var dailyMinutes by remember { mutableIntStateOf(30) }
    val dailyPagesByDays = SparcDomainEngine.calculateDailyPageTarget(currentChapter.totalPages, targetDays)
    val (estDaysByMinutes, estPagesByMinutes) = SparcDomainEngine.estimateDaysFromDailyMinutes(
        totalPages = currentChapter.totalPages,
        defaultReadingMinutes = currentChapter.defaultReadingMinutes,
        dailyAllocatedMinutes = dailyMinutes
    )

    // Audio summary player state (Section 3.4)
    var isAudioPlaying by remember { mutableStateOf(false) }

    // Highlight & Physical Order dialogs
    var highlightDraftText by remember { mutableStateOf<String?>(null) }
    var orderDialogBook by remember { mutableStateOf<BookItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Book Selector & First Chapter Free Trial Badge (Section 3.1)
        item {
            Text("ডিজিটাল ও ফিজিক্যাল লাইব্রেরি", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            state.books.forEach { book ->
                val isSelected = book.id == selectedBook.id
                OutlinedCard(
                    onClick = { selectedBookId = book.id },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                book.title,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (book.isFirstChapterFree) {
                                AssistChip(onClick = {}, label = { Text("১ম অধ্যায় ফ্রি") })
                            }
                        }
                        Text("${book.author} • ${book.category}", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isSelected) {
                                Badge { Text("নির্বাচিত বই") }
                            }
                            OutlinedButton(onClick = { orderDialogBook = book }) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("হার্ডকপি অর্ডার (৳${book.physicalPriceBdt.toInt()})")
                            }
                        }
                    }
                }
            }
        }

        // Chapter Selector enforcing Individual Unlocking Rule (Section 3.1 & 5.4)
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedBook.chapters.forEach { ch ->
                    val prevProg = state.myChapterProgress[ch.chapterNo - 1]
                    val unlocked = SparcDomainEngine.isChapterUnlockedForUser(selectedBook, ch.chapterNo, prevProg)
                    FilterChip(
                        selected = selectedChapterNo == ch.chapterNo,
                        onClick = { if (unlocked) selectedChapterNo = ch.chapterNo },
                        enabled = unlocked,
                        leadingIcon = if (!unlocked) {
                            { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        label = { Text("অধ্যায় ${ch.chapterNo}") }
                    )
                }
            }
        }

        // Smart Page & Reading Speed Estimator (Section 3.3)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "স্মার্ট পেজ ও টাইম এস্টিমেটর (${currentChapter.totalPages} পৃষ্ঠা • ডিফল্ট ${currentChapter.defaultReadingMinutes} মিনিট)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("লক্ষ্যমাত্রার দিনসংখ্যা: $targetDays দিন")
                    Slider(
                        value = targetDays.toFloat(),
                        onValueChange = { targetDays = it.toInt().coerceAtLeast(1) },
                        valueRange = 2f..15f,
                        steps = 12
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "📖 প্রতিদিন গড়ে $dailyPagesByDays পৃষ্ঠা করে পড়লে $targetDays দিনে অধ্যায়টি শেষ হবে।",
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "অথবা দৈনিক $dailyMinutes মিনিট সময় দিলে আনুমানিক $estDaysByMinutes দিনে (দৈনিক $estPagesByMinutes পৃষ্ঠা) শেষ হবে।",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(20, 30, 45).forEach { mins ->
                            SuggestionChip(
                                onClick = { dailyMinutes = mins },
                                label = { Text("দৈনিক $mins মিনিট") }
                            )
                        }
                    }
                }
            }
        }

        // Audio Summary & Bite-Sized Flash Notes (Section 3.4)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            currentChapter.audioSummaryTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(onClick = { isAudioPlaying = !isAudioPlaying }) {
                            Icon(
                                if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play Audio Summary"
                            )
                        }
                    }
                    if (isAudioPlaying) {
                        LinearProgressIndicator(progress = { 0.42f }, modifier = Modifier.fillMaxWidth())
                    }
                    HorizontalDivider()
                    Text("বাইট-সাইজড ফ্ল্যাশ-নোটস:", style = MaterialTheme.typography.labelMedium)
                    currentChapter.flashNotes.forEach { note ->
                        Text("• $note", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Interactive In-App Reader with Highlighter & Privacy Control (Section 3.2)
        item {
            Text(
                "ইন্টারেক্টিভ রিডার — যেকোনো বাক্যে ট্যাপ করে হাইলাইট/আন্ডারলাইন ও প্রাইভেসি সেট করুন:",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(currentChapter.sections, key = { it.id }) { sec ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${sec.title} (${sec.pageRange})", fontWeight = FontWeight.Bold)
                    sec.paragraphPreview.forEach { para ->
                        Surface(
                            onClick = { highlightDraftText = para },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(para, modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.Default.Highlight,
                                    contentDescription = "Highlight",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Saved Highlights with Privacy Badges
        if (state.highlights.isNotEmpty()) {
            item {
                Text("সংরক্ষিত হাইলাইট ও রিডিং নোটস", style = MaterialTheme.typography.titleMedium)
            }
            items(state.highlights, key = { it.id }) { hl ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "\"${hl.highlightedText}\"",
                            textDecoration = if (hl.isUnderline) TextDecoration.Underline else TextDecoration.None,
                            modifier = Modifier
                                .background(
                                    if (!hl.isUnderline) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(4.dp)
                        )
                        if (hl.note.isNotBlank()) {
                            Text("নোট: ${hl.note}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "দৃশ্যমানতা: ${hl.visibility.labelBn} • পৃষ্ঠা ${hl.pageNo}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    highlightDraftText?.let { text ->
        HighlightPrivacyDialog(
            selectedText = text,
            onDismiss = { highlightDraftText = null },
            onSave = { note, isUnderline, visibility ->
                repository.addHighlightAndLogReading(
                    bookId = selectedBook.id,
                    chapterNo = currentChapter.chapterNo,
                    pageNo = 12,
                    selectedText = text,
                    note = note,
                    isUnderline = isUnderline,
                    visibility = visibility
                )
                highlightDraftText = null
            }
        )
    }

    orderDialogBook?.let { book ->
        PhysicalBookOrderDialog(
            book = book,
            onDismiss = { orderDialogBook = null },
            onOrder = { mode, addr, phone ->
                repository.placePhysicalBookOrder(book, mode, addr, phone)
                orderDialogBook = null
            }
        )
    }
}

@Composable
private fun HighlightPrivacyDialog(
    selectedText: String,
    onDismiss: () -> Unit,
    onSave: (String, Boolean, HighlightVisibility) -> Unit
) {
    var note by remember { mutableStateOf("") }
    var isUnderline by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf(HighlightVisibility.PRIVATE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("হাইলাইট ও প্রাইভেসি কন্ট্রোল") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\"$selectedText\"", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isUnderline,
                        onClick = { isUnderline = false },
                        label = { Text("হাইলাইট") }
                    )
                    FilterChip(
                        selected = isUnderline,
                        onClick = { isUnderline = true },
                        label = { Text("আন্ডারলাইন") }
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ব্যক্তিগত বা গ্রুপ নোট") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("কারা দেখতে পাবেন?", style = MaterialTheme.typography.labelMedium)
                HighlightVisibility.entries.forEach { vis ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = visibility == vis, onClick = { visibility = vis })
                        Text(vis.labelBn)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(note, isUnderline, visibility) }) { Text("সংরক্ষণ করুন") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
private fun PhysicalBookOrderDialog(
    book: BookItem,
    onDismiss: () -> Unit,
    onOrder: (String, String, String) -> Unit
) {
    var mode by remember { mutableStateOf("Purchase") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ফিজিক্যাল বই অর্ডার / সংগ্রহ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "নিরাপত্তা নোট: এই অর্ডারের তথ্য কেবল অ্যাডমিন, শিক্ষক বা SPARC-এর অনুমোদিত প্রতিনিধিই দেখতে পাবেন।",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "Purchase",
                        onClick = { mode = "Purchase" },
                        label = { Text("ক্রয় (৳${book.physicalPriceBdt.toInt()})") }
                    )
                    FilterChip(
                        selected = mode == "Borrow",
                        onClick = { mode = "Borrow" },
                        label = { Text("পড়ার জন্য সংগ্রহ") }
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("ডেলিভারি ঠিকানা / বিভাগ") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("যোগাযোগের মোবাইল নম্বর") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onOrder(mode, address, phone) }) { Text("অর্ডার নিশ্চিত করুন") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
