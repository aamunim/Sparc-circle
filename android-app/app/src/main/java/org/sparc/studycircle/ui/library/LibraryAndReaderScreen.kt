package org.sparc.studycircle.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.BookItem
import org.sparc.studycircle.core.model.HighlightVisibility
import org.sparc.studycircle.core.theme.*
import org.sparc.studycircle.ui.components.*

@Composable
fun LibraryAndReaderScreen(
    state: SparcAppState,
    repository: SparcRepository,
    initialSubTab: Int = 0
) {
    // Sub-tabs: 0 = Screen 3 (Academic Library), 1 = Screen 4 (Smart Paper Reader & Audio)
    var subTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }
    var selectedBookId by remember { mutableStateOf(state.books.first().id) }
    val selectedBook = state.books.find { it.id == selectedBookId } ?: state.books.first()
    val chapter = selectedBook.chapters.first()

    var orderDialogBook by remember { mutableStateOf<BookItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf("একাডেমিক লাইব্রেরি", "স্মার্ট বুক রিডার ও অডিও"),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        if (subTab == 0) {
            // SCREEN 3: SPARC Academic Library & Confidential Hardcopy Order
            AcademicLibraryCatalogView(
                books = state.books,
                onReadFreeChapter = { book ->
                    selectedBookId = book.id
                    subTab = 1
                },
                onOrderHardcopy = { book -> orderDialogBook = book }
            )
        } else {
            // SCREEN 4: Smart Paper Reader, Floating Privacy Popover & Mini Audio Waveform Player
            SmartPaperReaderView(
                book = selectedBook,
                state = state,
                repository = repository
            )
        }
    }

    orderDialogBook?.let { book ->
        HardcopyOrderDialog(
            book = book,
            onDismiss = { orderDialogBook = null },
            onConfirm = { mode, addr, phone ->
                repository.placePhysicalBookOrder(book, mode, addr, phone)
                orderDialogBook = null
            }
        )
    }
}

@Composable
private fun AcademicLibraryCatalogView(
    books: List<BookItem>,
    onReadFreeChapter: (BookItem) -> Unit,
    onOrderHardcopy: (BookItem) -> Unit
) {
    var selectedCat by remember { mutableStateOf("All Books") }
    val categories = listOf("All Books", "Action Research", "Methodology", "Qualitative")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("SPARC Academic Library", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    val active = selectedCat == cat
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (active) Color(0xFFE7E5DC) else SparcSurface,
                        border = BorderStroke(1.dp, SparcCardBorder),
                        modifier = Modifier.clickable { selectedCat = cat }
                    ) {
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        items(books, key = { it.id }) { book ->
            SparcScholarlyCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Editorial Book Cover Illustration
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(136.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (book.id.contains("cpar")) SparcEmerald else Color(0xFF155E75)
                            )
                            .padding(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(4.dp)
                                    .background(SparcAmber)
                            )
                            Text(
                                text = book.title.take(28),
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 14.sp
                            )
                            Text(
                                text = book.author.take(18),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 9.sp
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (book.isFirstChapterFree) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SparcOliveBadge
                            ) {
                                Text(
                                    text = "1st Chapter & Quiz FREE",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(book.title, style = MaterialTheme.typography.titleMedium)
                        Text(book.author, style = MaterialTheme.typography.bodyMedium, color = SparcMutedInk)
                        Text(
                            "${book.totalChapters} Chapters • Min ${book.minMembersRequired} Members",
                            style = MaterialTheme.typography.labelMedium,
                            color = SparcInk
                        )
                    }
                }

                SparcPrimaryButton(
                    text = "Read Chapter 1 Free (১ম অধ্যায় ফ্রি পড়ুন)",
                    onClick = { onReadFreeChapter(book) }
                )

                OutlinedButton(
                    onClick = { onOrderHardcopy(book) },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.2.dp, SparcInk),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Order Hardcopy (BDT ${book.physicalPriceBdt.toInt()})",
                        color = SparcInk,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SparcSurface,
                border = BorderStroke(1.dp, SparcCardBorder),
                shadowElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Confidential Hardcopy Request: Purchase or Borrow", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "• শুধুমাত্র SPARC অ্যাডমিন, শিক্ষক ও অনুমোদিত প্রতিনিধির কাছে দৃশ্যমান।",
                        fontSize = 12.sp,
                        color = SparcMutedInk
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartPaperReaderView(
    book: BookItem,
    state: SparcAppState,
    repository: SparcRepository
) {
    val chapter = book.chapters.first()
    var targetDays by remember { mutableIntStateOf(8) }
    val dailyPages = SparcDomainEngine.calculateDailyPageTarget(chapter.totalPages, targetDays)

    var selectedPrivacy by remember { mutableStateOf(HighlightVisibility.GROUP) }
    var isAudioPlaying by remember { mutableStateOf(true) }
    var speedIdx by remember { mutableIntStateOf(1) }
    val speeds = listOf("1.0x", "1.25x", "1.5x")
    var showFlashNotes by remember { mutableStateOf(false) }

    val activeHighlightQuote = "Sayings, doings and relatings hang together in the project of a practice."

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Reading Progress & Estimator Strip
        LinearProgressIndicator(
            progress = { 18f / chapter.totalPages.toFloat() },
            color = SparcEmerald,
            trackColor = Color(0xFFE2E8F0),
            modifier = Modifier.fillMaxWidth()
        )
        Surface(color = SparcSurface) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Page 18 of ${chapter.totalPages} • ~$dailyPages pages/day target ($targetDays days)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "টার্গেট দিন বদলান",
                        fontSize = 12.sp,
                        color = SparcEmerald,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            targetDays = if (targetDays >= 12) 4 else targetDays + 2
                        }
                    )
                }
            }
        }

        // Paper-toned Editorial Reader Body
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .background(SparcReaderPaper),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = SparcInk
                )
            }

            item {
                Text(
                    text = chapter.sections.first().paragraphPreview.first(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = SparcInk
                )
            }

            // Floating Contextual Highlight Privacy Popover + Highlighted Quote (Matching Screen 4 Mockup!)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SparcSurface,
                    border = BorderStroke(1.dp, SparcCardBorder),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                HighlightVisibility.PRIVATE to "Private",
                                HighlightVisibility.SUPERVISOR to "Supervisor",
                                HighlightVisibility.GROUP to "Group"
                            ).forEach { (vis, label) ->
                                val active = selectedPrivacy == vis
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (active) SparcEmerald else SparcSubtleBg)
                                        .clickable {
                                            selectedPrivacy = vis
                                            repository.addHighlightAndLogReading(
                                                bookId = book.id,
                                                chapterNo = chapter.chapterNo,
                                                pageNo = 18,
                                                selectedText = activeHighlightQuote,
                                                note = "প্রপোজালের তাত্ত্বিক ফ্রেমওয়ার্কে উদ্ধৃত করব",
                                                isUnderline = false,
                                                visibility = vis
                                            )
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (active) Color.White else SparcInk,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SparcSubtleBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💬 নোট: প্রপোজালের তাত্ত্বিক ফ্রেমওয়ার্কে এটি উদ্ধৃত করব",
                                fontSize = 12.sp,
                                color = SparcMutedInk,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = activeHighlightQuote,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SparcInk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SparcHighlightYellow)
                        .padding(10.dp)
                )
            }

            items(chapter.sections.drop(1)) { sec ->
                Text(sec.title, style = MaterialTheme.typography.titleMedium)
                sec.paragraphPreview.forEach { p ->
                    Text(p, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (showFlashNotes) {
                item {
                    SparcScholarlyCard {
                        Text("⚡ বাইট-সাইজড ফ্ল্যাশ-নোটস", style = MaterialTheme.typography.titleMedium)
                        chapter.flashNotes.forEach { n ->
                            Text("• $n", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Docked Flash Notes Pill + Frosted Mini Audio Waveform Player
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SparcReaderPaper)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFE7E5DC),
                border = BorderStroke(1.dp, SparcCardBorder),
                modifier = Modifier.clickable { showFlashNotes = !showFlashNotes }
            ) {
                Text(
                    text = "Flash Notes (${chapter.flashNotes.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            SparcAudioWaveformBar(
                title = "Bangla Audio Summary",
                timeLabel = "02:45 / 06:30",
                speedLabel = speeds[speedIdx],
                isPlaying = isAudioPlaying,
                onTogglePlay = { isAudioPlaying = !isAudioPlaying },
                onCycleSpeed = { speedIdx = (speedIdx + 1) % speeds.size }
            )
        }
    }
}

@Composable
private fun HardcopyOrderDialog(
    book: BookItem,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var mode by remember { mutableStateOf("Purchase") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("হার্ডকপি বই অর্ডার / সংগ্রহ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "গোপনীয়তা: এই অর্ডারের তথ্য কেবল SPARC অ্যাডমিন, শিক্ষক বা অনুমোদিত প্রতিনিধি দেখতে পাবেন।",
                    fontSize = 12.sp,
                    color = SparcEmerald
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
                        label = { Text("সংগ্রহ (Borrow)") }
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("বিভাগ / ডেলিভারি ঠিকানা") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("মোবাইল নম্বর") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(mode, address, phone) },
                colors = ButtonDefaults.buttonColors(containerColor = SparcEmerald)
            ) {
                Text("অর্ডার নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
