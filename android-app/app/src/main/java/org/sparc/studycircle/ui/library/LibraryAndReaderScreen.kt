package org.sparc.studycircle.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.ui.components.*

@Composable
fun LibraryAndReaderScreen(
    state: SparcAppState,
    repository: SparcRepository,
    initialSubTab: Int = 0
) {
    var subTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }
    var selectedBookId by remember { mutableStateOf(state.books.first().id) }
    val selectedBook = state.books.find { it.id == selectedBookId } ?: state.books.first()
    var orderDialogBook by remember { mutableStateOf<BookItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf("ডিজিটাল লাইব্রেরি", "স্মার্ট রিডার ও অডিও"),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        if (subTab == 0) {
            AcademicLibraryCatalogView(
                books = state.books,
                onReadFreeChapter = { book ->
                    selectedBookId = book.id
                    subTab = 1
                },
                onOrderHardcopy = { book -> orderDialogBook = book }
            )
        } else {
            SmartPaperReaderView(book = selectedBook, repository = repository)
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
    val p = LocalSparcPalette.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(books, key = { it.id }) { book ->
            SparcScholarlyCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .height(118.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.primaryGradient)
                            .padding(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("SPARC CORE", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = book.title.take(26),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (book.isFirstChapterFree) {
                            Surface(shape = RoundedCornerShape(8.dp), color = p.accentSoft) {
                                Text(
                                    text = "১ম অধ্যায় ও কুইজ ফ্রি",
                                    color = p.accentText,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(book.title, style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            "${book.author} • ${book.totalChapters} অধ্যায় • ৳${book.physicalPriceBdt.toInt()}",
                            fontSize = 12.sp,
                            color = p.muted
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onReadFreeChapter(book) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ফ্রি অধ্যায় পড়ুন", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.cardAlt,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOrderHardcopy(book) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 11.dp)) {
                            Text("হার্ডকপি অর্ডার", color = p.text, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartPaperReaderView(
    book: BookItem,
    repository: SparcRepository
) {
    val p = LocalSparcPalette.current
    val chapter = book.chapters.first()
    var targetDays by remember { mutableIntStateOf(8) }
    val dailyPages = SparcDomainEngine.calculateDailyPageTarget(chapter.totalPages, targetDays)

    var selectedPrivacy by remember { mutableStateOf(HighlightVisibility.GROUP) }
    val audioController = org.sparc.studycircle.core.media.rememberSparcAudioController()
    val activeQuote = "Sayings, doings and relatings hang together in the project of a practice."

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Real Native Android PdfRenderer Viewer + Open Any Device PDF!
        item {
            NativePdfBookViewerCard(
                bookTitle = book.title,
                onQuickHighlightPage = { pageNo ->
                    repository.addHighlightAndLogReading(
                        bookId = book.id,
                        chapterNo = chapter.chapterNo,
                        pageNo = pageNo,
                        selectedText = "PDF পৃষ্ঠা $pageNo হাইলাইট — $activeQuote",
                        note = "PDF রিডার থেকে সংরক্ষিত",
                        isUnderline = false,
                        visibility = selectedPrivacy
                    )
                }
            )
        }

        // 2. Real Media3 ExoPlayer Audio Summary Player
        item {
            SparcAudioWaveformBar(
                title = "বাংলা অডিও সামারি (ExoPlayer)",
                currentMs = audioController.currentPositionMs,
                durationMs = audioController.durationMs,
                speedLabel = "${audioController.speed}x",
                isPlaying = audioController.isPlaying,
                onTogglePlay = { audioController.togglePlayPause() },
                onSeekTo = { ms -> audioController.seekTo(ms) },
                onSkipSeconds = { sec -> audioController.skipSeconds(sec) },
                onCycleSpeed = { audioController.cyclePlaybackSpeed() }
            )
        }

        // 3. Smart Daily Page Estimator
        item {
            SparcScholarlyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("পেজ এস্টিমেটর ($targetDays দিনে শেষ)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = p.text)
                    Surface(shape = RoundedCornerShape(8.dp), color = p.primarySoft) {
                        Text(
                            "দৈনিক $dailyPages পৃষ্ঠা",
                            color = p.primarySoftText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Slider(
                    value = targetDays.toFloat(),
                    onValueChange = { targetDays = it.toInt().coerceAtLeast(1) },
                    valueRange = 3f..14f,
                    steps = 10
                )
            }
        }

        // 4. Highlight Privacy Selector & Bite-Sized Flash Notes
        item {
            SparcScholarlyCard(containerColor = p.readerPaper) {
                Text("হাইলাইট প্রাইভেসি কন্ট্রোল ও ফ্ল্যাশ-নোটস", style = MaterialTheme.typography.titleMedium, color = p.text)

                Surface(shape = RoundedCornerShape(12.dp), color = p.cardAlt) {
                    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            HighlightVisibility.PRIVATE to "Private",
                            HighlightVisibility.SUPERVISOR to "Supervisor",
                            HighlightVisibility.GROUP to "Group"
                        ).forEach { (vis, label) ->
                            val active = selectedPrivacy == vis
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (active) p.primary else Color.Transparent)
                                    .clickable {
                                        selectedPrivacy = vis
                                        repository.addHighlightAndLogReading(
                                            bookId = book.id,
                                            chapterNo = chapter.chapterNo,
                                            pageNo = 4,
                                            selectedText = activeQuote,
                                            note = "প্রপোজালে উদ্ধৃত করব",
                                            isUnderline = false,
                                            visibility = vis
                                        )
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (active) Color.White else p.muted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                chapter.flashNotes.forEach { fn ->
                    Text("• $fn", style = MaterialTheme.typography.bodyMedium, color = p.muted)
                }
            }
        }
    }
}

@Composable
private fun HardcopyOrderDialog(
    book: BookItem,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    val p = LocalSparcPalette.current
    var mode by remember { mutableStateOf("Purchase") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        titleContentColor = p.text,
        textContentColor = p.text,
        title = { Text("হার্ডকপি বই অর্ডার / সংগ্রহ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "গোপনীয়তা: শুধুমাত্র SPARC অ্যাডমিন, শিক্ষক বা প্রতিনিধি দেখতে পাবেন।",
                    fontSize = 12.sp,
                    color = p.primary
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
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("ঠিকানা") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("মোবাইল নম্বর") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(mode, address, phone) },
                colors = ButtonDefaults.buttonColors(containerColor = p.primary)
            ) {
                Text("নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল", color = p.muted) }
        }
    )
}
