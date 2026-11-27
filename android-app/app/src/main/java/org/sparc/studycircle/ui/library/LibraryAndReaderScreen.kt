package org.sparc.studycircle.ui.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.ExerciseTemplateParser
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.BookItem
import org.sparc.studycircle.core.model.ChapterType
import org.sparc.studycircle.core.model.HighlightVisibility
import org.sparc.studycircle.core.model.ReaderPaperTheme
import org.sparc.studycircle.core.theme.LocalAppLanguage
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.tr
import org.sparc.studycircle.core.theme.trContent
import org.sparc.studycircle.ui.components.*

@Composable
fun LibraryAndReaderScreen(
    state: SparcAppState,
    repository: SparcRepository,
    initialSubTab: Int = 0,
    onOpenCircleHub: () -> Unit = {}
) {
    var subTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }
    var selectedBookId by remember { mutableStateOf(state.books.firstOrNull()?.id.orEmpty()) }
    val selectedBook = state.books.find { it.id == selectedBookId } ?: state.books.firstOrNull()
    var orderDialogBook by remember { mutableStateOf<BookItem?>(null) }
    var uploadSheetInitialBook by remember { mutableStateOf<BookItem?>(null) }
    var showUploadSheet by remember { mutableStateOf(false) }
    var curriculumSheetBook by remember { mutableStateOf<BookItem?>(null) }
    var curriculumInitialChapterNo by remember { mutableIntStateOf(1) }
    var templateImportBook by remember { mutableStateOf<BookItem?>(null) }
    var templateInitialChapterNo by remember { mutableIntStateOf(1) }
    var circleInviteBook by remember { mutableStateOf<BookItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf(
                    tr("ডিজিটাল লাইব্রেরি", "Digital Library"),
                    tr("স্মার্ট রিডার ও কমফোর্ট", "Smart Reader & Comfort")
                ),
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
                onOpenCircleInvite = { book -> circleInviteBook = book },
                onOrderHardcopy = { book -> orderDialogBook = book },
                onOpenCloudUpload = { targetBook ->
                    uploadSheetInitialBook = targetBook
                    showUploadSheet = true
                },
                onOpenCurriculumBuilder = { targetBook ->
                    curriculumSheetBook = targetBook
                    curriculumInitialChapterNo = 1
                },
                onOpenTemplateImport = { targetBook ->
                    templateImportBook = targetBook
                    templateInitialChapterNo = 1
                }
            )
        } else if (selectedBook != null && selectedBook.chapters.isNotEmpty()) {
            SmartPaperReaderView(
                state = state,
                book = selectedBook,
                repository = repository,
                onOpenCircleInvite = { circleInviteBook = selectedBook },
                onOpenCloudUpload = {
                    uploadSheetInitialBook = selectedBook
                    showUploadSheet = true
                },
                onOpenCurriculumBuilder = { chNo ->
                    curriculumSheetBook = selectedBook
                    curriculumInitialChapterNo = chNo
                },
                onOpenTemplateImport = { chNo ->
                    templateImportBook = selectedBook
                    templateInitialChapterNo = chNo
                }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                SparcScholarlyCard {
                    Text(
                        tr("📖 এখনো কোনো বই বা অধ্যায় যুক্ত হয়নি", "📖 No Book or Chapter Available Yet"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        tr(
                            "পড়া শুরু করতে আগে ডিজিটাল লাইব্রেরি থেকে নতুন বই/PDF ও অনুশীলনী টেমপ্লেট আপলোড করুন।",
                            "Upload a book/PDF and exercise template from the Digital Library to begin reading."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalSparcPalette.current.muted
                    )
                    SparcPrimaryButton(
                        text = tr("☁️ নতুন বই / PDF আপলোড করুন", "☁️ Upload New Book / PDF"),
                        onClick = {
                            uploadSheetInitialBook = null
                            showUploadSheet = true
                        }
                    )
                }
            }
        }
    }

    orderDialogBook?.let { book ->
        HardcopyOrderDialog(
            book = book,
            walletBalanceBdt = state.currentUser.walletBalanceBdt,
            bkashNumber = state.appCustomization.bkashMerchantNumber,
            nagadNumber = state.appCustomization.nagadMerchantNumber,
            borrowDepositBdt = state.appCustomization.borrowDepositBdt,
            onDismiss = { orderDialogBook = null },
            onConfirm = { mode, addr, phone, payOption, directTrx ->
                repository.placePhysicalBookOrder(book, mode, addr, phone, payOption, directTrx)
                orderDialogBook = null
            }
        )
    }

    if (showUploadSheet) {
        CloudMediaUploadBottomSheet(
            books = state.books,
            initialBook = uploadSheetInitialBook,
            repository = repository,
            onDismiss = { showUploadSheet = false }
        )
    }

    curriculumSheetBook?.let { targetBook ->
        ChapterCurriculumBuilderSheet(
            book = targetBook,
            initialChapterNo = curriculumInitialChapterNo,
            repository = repository,
            onDismiss = { curriculumSheetBook = null }
        )
    }

    templateImportBook?.let { targetBook ->
        ExerciseTemplateImporterSheet(
            book = targetBook,
            initialChapterNo = templateInitialChapterNo,
            repository = repository,
            onDismiss = { templateImportBook = null }
        )
    }

    circleInviteBook?.let { targetBook ->
        BookCircleAndInviteBottomSheet(
            state = state,
            book = targetBook,
            repository = repository,
            onNavigateToCircle = {
                circleInviteBook = null
                onOpenCircleHub()
            },
            onDismiss = { circleInviteBook = null }
        )
    }
}

@Composable
private fun AcademicLibraryCatalogView(
    books: List<BookItem>,
    onReadFreeChapter: (BookItem) -> Unit,
    onOpenCircleInvite: (BookItem) -> Unit,
    onOrderHardcopy: (BookItem) -> Unit,
    onOpenCloudUpload: (BookItem?) -> Unit,
    onOpenCurriculumBuilder: (BookItem) -> Unit,
    onOpenTemplateImport: (BookItem) -> Unit
) {
    val p = LocalSparcPalette.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cloud Supabase Storage Upload & Guest PDF + Template Studio Banner Card
        item {
            SparcGradientHeroCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = tr("☁️ বই/গবেষণা PDF ও টেমপ্লেট স্টুডিও", "☁️ PDF, Audio & Exercise Template Studio"),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp
                        )
                        Text(
                            text = tr(
                                "যেকোনো লেখক/গবেষকের PDF আপলোড করুন এবং নির্ধারিত টেমপ্লেটে অনুশীলনী যুক্ত করুন",
                                "Upload author/researcher PDFs & attach exercises via standard template"
                            ),
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        modifier = Modifier.clickable { onOpenCloudUpload(null) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(17.dp))
                            Text(
                                text = tr("আপলোড", "Upload"),
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            }
        }

        if (books.isEmpty()) {
            item {
                SparcScholarlyCard {
                    Text(
                        tr("📚 লাইব্রেরিতে এখনো কোনো বই নেই", "📚 Digital Library Is Currently Empty"),
                        style = MaterialTheme.typography.titleMedium,
                        color = p.text
                    )
                    Text(
                        tr(
                            "উপরের 'আপলোড' বাটন বা ওয়েব অ্যাডমিন পোর্টাল থেকে প্রথম বই/প্রবন্ধের PDF ও অনুশীলনী টেমপ্লেট যুক্ত করুন।",
                            "Use the Upload button above or the Web Admin Portal to publish your first book/PDF and exercise template."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.muted
                    )
                }
            }
        }

        items(books, key = { it.id }) { book ->
            val context = LocalContext.current
            val hasCloudPdf = book.pdfUrl.startsWith("http") && !book.pdfUrl.contains("storage.sparc.org.bd")
            val isOfflineCached = remember(book.pdfUrl) {
                hasCloudPdf && java.io.File(
                    context.cacheDir,
                    "cloud_pdfs/sparc_${book.pdfUrl.hashCode().toUInt()}.pdf"
                ).let { it.exists() && it.length() > 100L }
            }
            val hasCloudAudio = book.chapters.any {
                it.audioSummaryUrl.startsWith("http") && !it.audioSummaryUrl.contains("storage.sparc.org.bd")
            }

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
                            Text("SPARC READ", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (book.isFirstChapterFree) {
                                Surface(shape = RoundedCornerShape(8.dp), color = p.accentSoft) {
                                    Text(
                                        text = tr("১ম অধ্যায় ফ্রি", "Ch 1 Free"),
                                        color = p.accentText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (isOfflineCached) {
                                Surface(shape = RoundedCornerShape(8.dp), color = p.accentSoft) {
                                    Text(
                                        text = tr("📥 অফলাইন প্রস্তুত ✓", "📥 Offline Ready ✓"),
                                        color = p.accentText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            } else if (hasCloudPdf) {
                                Surface(shape = RoundedCornerShape(8.dp), color = p.primarySoft) {
                                    Text(
                                        text = "☁️ PDF",
                                        color = p.primarySoftText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (hasCloudAudio) {
                                Surface(shape = RoundedCornerShape(8.dp), color = p.primarySoft) {
                                    Text(
                                        text = "🎧 MP3",
                                        color = p.primarySoftText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (book.chapters.any { it.fullTextContent.isNotBlank() || it.contentMode == "text" || it.contentMode == "hybrid" }) {
                                Surface(shape = RoundedCornerShape(8.dp), color = p.primarySoft) {
                                    Text(
                                        text = tr("📝 টেক্সট সিঙ্ক", "📝 Read-Along"),
                                        color = p.primarySoftText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Text(book.title, style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            tr(
                                "${book.author} • ${book.totalChapters} অধ্যায়" + (if (book.physicalPriceBdt > 0) " • ৳${book.physicalPriceBdt.toInt()}" else " • Open Access"),
                                "${book.author} • ${book.totalChapters} Ch" + (if (book.physicalPriceBdt > 0) " • ৳${book.physicalPriceBdt.toInt()}" else " • Open Access")
                            ),
                            fontSize = 12.sp,
                            color = p.muted
                        )
                        if (book.sourceAttribution.isNotBlank()) {
                            Text(
                                "📖 ${book.sourceAttribution}",
                                fontSize = 11.sp,
                                color = p.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Primary Row: Read & Listen + Study Circle & Invite Connection!
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onReadFreeChapter(book) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Text(tr("পড়ুন ও শুনুন", "Read & Listen"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.primarySoft,
                        border = BorderStroke(1.dp, p.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1.1f)
                            .clickable { onOpenCircleInvite(book) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 11.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, tint = p.primarySoftText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(tr("সার্কেল ও ইনভাইট", "Circle & Invite"), color = p.primarySoftText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Secondary Tools Row: Hardcopy, Template Exercise Upload, Chapter Edit, Cloud PDF/MP3
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = p.cardAlt,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOrderHardcopy(book) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(tr("হার্ডকপি", "Hardcopy"), color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = p.cardAlt,
                        modifier = Modifier
                            .weight(1.15f)
                            .clickable { onOpenTemplateImport(book) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(tr("📋 টেমপ্লেট অনুশীলনী", "📋 Template"), color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = p.cardAlt,
                        modifier = Modifier.clickable { onOpenCurriculumBuilder(book) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Icon(Icons.Default.EditNote, contentDescription = "Customize Chapters", tint = p.text, modifier = Modifier.size(16.dp))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = p.cardAlt,
                        modifier = Modifier.clickable { onOpenCloudUpload(book) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Upload PDF/Audio", tint = p.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartPaperReaderView(
    state: SparcAppState,
    book: BookItem,
    repository: SparcRepository,
    onOpenCircleInvite: () -> Unit,
    onOpenCloudUpload: () -> Unit,
    onOpenCurriculumBuilder: (Int) -> Unit,
    onOpenTemplateImport: (Int) -> Unit
) {
    val p = LocalSparcPalette.current
    val lang = LocalAppLanguage.current
    val ui = state.uiCustomization

    var selectedChapterIdx by remember(book.id) { mutableIntStateOf(0) }
    val safeIdx = selectedChapterIdx.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
    val chapter = book.chapters.getOrElse(safeIdx) { book.chapters.first() }

    var targetDays by remember { mutableIntStateOf(8) }
    val dailyPages = SparcDomainEngine.calculateDailyPageTarget(chapter.totalPages, targetDays)

    var selectedPrivacy by remember { mutableStateOf(HighlightVisibility.GROUP) }
    val audioController = org.sparc.studycircle.core.media.rememberSparcAudioController(
        remoteUrl = chapter.audioSummaryUrl
    )
    val activeQuote = "Sayings, doings and relatings hang together in the project of a practice."

    // Pack 2 Built-in Pomodoro Focus Study Timer
    var pomoPresetMin by remember { mutableIntStateOf(25) }
    var pomoSecondsLeft by remember { mutableIntStateOf(25 * 60) }
    var pomoRunning by remember { mutableStateOf(false) }

    LaunchedEffect(pomoRunning) {
        while (pomoRunning && pomoSecondsLeft > 0) {
            delay(1000L)
            pomoSecondsLeft -= 1
            if (pomoSecondsLeft <= 0) {
                pomoRunning = false
            }
        }
    }

    val paperBg = Color(ui.readerPaperTheme.bgHex)
    val paperText = Color(ui.readerPaperTheme.textHex)
    val paperMuted = Color(ui.readerPaperTheme.mutedHex)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Chapter Selector + Study Circle Invite + Template & Upload Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(book.chapters.size) { idx ->
                            val ch = book.chapters[idx]
                            val active = idx == safeIdx
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) p.primary else p.card,
                                border = BorderStroke(1.dp, if (active) p.primary else p.border),
                                modifier = Modifier.clickable { selectedChapterIdx = idx }
                            ) {
                                Text(
                                    text = tr("অধ্যায় ${ch.chapterNo}", "Ch ${ch.chapterNo}"),
                                    color = if (active) Color.White else p.text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = p.primarySoft,
                        modifier = Modifier.clickable { onOpenCircleInvite() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, tint = p.primarySoftText, modifier = Modifier.size(15.dp))
                            Text(tr("সার্কেল ইনভাইট", "Circle Invite"), color = p.primarySoftText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = p.cardAlt,
                        border = BorderStroke(1.dp, p.border),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenTemplateImport(chapter.chapterNo) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                            Text(tr("📋 টেমপ্লেট ইমপোর্ট", "📋 Import Template"), color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = p.cardAlt,
                        border = BorderStroke(1.dp, p.border),
                        modifier = Modifier
                            .weight(0.85f)
                            .clickable { onOpenCurriculumBuilder(chapter.chapterNo) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                            Text(tr("✏️ অধ্যায় এডিট", "✏️ Edit Ch"), color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = p.cardAlt,
                        border = BorderStroke(1.dp, p.border),
                        modifier = Modifier
                            .weight(0.85f)
                            .clickable { onOpenCloudUpload() }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                            Text("☁️ PDF/MP3", color = p.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 1. Read-Along Synchronized Text Reader + Optional PDF Switcher
        item {
            val prefersTextDefault = chapter.fullTextContent.isNotBlank() || chapter.contentMode == "text" || chapter.contentMode == "hybrid"
            var readerModeIdx by remember(chapter.id, prefersTextDefault) {
                mutableIntStateOf(if (prefersTextDefault) 0 else 1)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SparcSegmentedPills(
                    options = listOf(
                        tr("🎧 Read-Along টেক্সট ও টাইমস্ট্যাম্প", "🎧 Read-Along Text"),
                        tr("📄 মূল PDF ভিউয়ার", "📄 PDF Viewer")
                    ),
                    selectedIndex = readerModeIdx,
                    onSelect = { readerModeIdx = it }
                )

                if (readerModeIdx == 0) {
                    SynchronizedTextReaderCard(
                        bookTitle = book.title,
                        chapter = chapter,
                        audioController = audioController,
                        paperTheme = ui.readerPaperTheme,
                        fontSizeSp = ui.readerFontSizeSp,
                        lineSpacingMultiplier = ui.readerLineSpacingMultiplier,
                        onSaveHighlight = { paraNo, snippet ->
                            repository.addHighlightAndLogReading(
                                bookId = book.id,
                                chapterNo = chapter.chapterNo,
                                pageNo = paraNo,
                                selectedText = snippet,
                                note = "Read-Along Paragraph $paraNo",
                                isUnderline = false,
                                visibility = selectedPrivacy
                            )
                        },
                        onSaveEditedText = { chTitle, cMode, fullTxt, subRaw ->
                            repository.saveChapterDirectText(
                                bookId = book.id,
                                chapterNo = chapter.chapterNo,
                                chapterTitle = chTitle,
                                contentMode = cMode,
                                fullTextContent = fullTxt,
                                subtitleRaw = subRaw
                            )
                        }
                    )
                } else {
                    val savedLastPage = ui.lastPdfPageByBookId[book.id] ?: 1
                    val bookHighlights = remember(state.highlights, book.id) {
                        state.highlights.filter { it.bookId == book.id }
                    }
                    NativePdfBookViewerCard(
                        bookId = book.id,
                        bookTitle = book.title,
                        bookAuthor = book.author,
                        chapters = book.chapters,
                        cloudPdfUrl = book.pdfUrl,
                        paperTheme = ui.readerPaperTheme,
                        trimMargins = ui.pdfTrimMargins,
                        scrollVertical = ui.pdfScrollVertical,
                        initialSavedPage = savedLastPage,
                        bookmarks = ui.pdfBookmarks,
                        bookHighlights = bookHighlights,
                        audioController = audioController,
                        onChangePaperTheme = { chosen ->
                            repository.updateUiCustomization(ui.copy(readerPaperTheme = chosen), null, null)
                        },
                        onToggleTrimMargins = { enabled ->
                            repository.updateUiCustomization(
                                ui.copy(pdfTrimMargins = enabled),
                                if (enabled) "✂️ অটো মার্জিন ট্রিম চালু (+৩৫% বড় লেখা)" else "মার্জিন ট্রিম বন্ধ করা হয়েছে",
                                if (enabled) "✂️ Smart Margin Trim enabled (+35% larger text)" else "Margin trim disabled"
                            )
                        },
                        onToggleScrollVertical = { vertical ->
                            repository.updateUiCustomization(
                                ui.copy(pdfScrollVertical = vertical),
                                if (vertical) "↕ উপর-নিচ একটানা স্ক্রল মোড চালু" else "↔ ডানে-বামে সোয়াইপ মোড চালু",
                                if (vertical) "↕ Continuous vertical scroll enabled" else "↔ Horizontal swipe mode enabled"
                            )
                        },
                        onSaveLastPage = { pNo ->
                            if (ui.lastPdfPageByBookId[book.id] != pNo) {
                                repository.updateUiCustomization(
                                    ui.copy(lastPdfPageByBookId = ui.lastPdfPageByBookId + (book.id to pNo)),
                                    null,
                                    null
                                )
                            }
                        },
                        onToggleBookmark = { pageNo, bmLabel ->
                            val existing = ui.pdfBookmarks.find { it.bookId == book.id && it.pageNo == pageNo }
                            val updated = if (existing != null) {
                                ui.pdfBookmarks.filterNot { it.id == existing.id }
                            } else {
                                val dateStr = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                                listOf(
                                    org.sparc.studycircle.core.model.PdfBookmarkItem(
                                        id = java.util.UUID.randomUUID().toString(),
                                        bookId = book.id,
                                        pageNo = pageNo,
                                        label = bmLabel,
                                        createdAtLabel = dateStr
                                    )
                                ) + ui.pdfBookmarks
                            }
                            repository.updateUiCustomization(
                                ui.copy(pdfBookmarks = updated),
                                if (existing != null) "বুকমার্ক সরানো হয়েছে" else "🔖 পৃষ্ঠা $pageNo বুকমার্ক করা হয়েছে",
                                if (existing != null) "Bookmark removed" else "🔖 Page $pageNo bookmarked"
                            )
                        },
                        onRemoveBookmark = { bmId ->
                            repository.updateUiCustomization(
                                ui.copy(pdfBookmarks = ui.pdfBookmarks.filterNot { it.id == bmId }),
                                null,
                                null
                            )
                        },
                        onAddPageAnnotation = { pageNo, markerLabel, noteBody, vis ->
                            repository.addHighlightAndLogReading(
                                bookId = book.id,
                                chapterNo = chapter.chapterNo,
                                pageNo = pageNo,
                                selectedText = markerLabel,
                                note = noteBody,
                                isUnderline = false,
                                visibility = vis
                            )
                        },
                        onDownloadCloudPdf = { url, targetFile -> repository.downloadCloudPdf(url, targetFile) },
                        onQuickHighlightPage = { pageNo ->
                            repository.addHighlightAndLogReading(
                                bookId = book.id,
                                chapterNo = chapter.chapterNo,
                                pageNo = pageNo,
                                selectedText = "PDF Page $pageNo — $activeQuote",
                                note = "Saved from PDF Reader (Page $pageNo)",
                                isUnderline = false,
                                visibility = selectedPrivacy
                            )
                        }
                    )
                }
            }
        }

        // 2. Real Media3 ExoPlayer Audio Summary Waveform Scrubbing Bar
        item {
            val audioTitle = when {
                audioController.isTtsSpeaking ->
                    tr("🗣️ বাংলা/ইংরেজি অডিও রিডার (লাইভ ওয়ার্ড সিঙ্ক)", "🗣️ Live Voice Read-Along")
                audioController.isStreamingCloudAudio ->
                    tr("☁️ ক্লাউড বাংলা অডিও সামারি (Live MP3)", "☁️ Cloud Audio Summary (Live MP3)")
                else ->
                    tr("বাংলা অডিও সামারি (ExoPlayer)", "Chapter Audio Summary (ExoPlayer)")
            }
            SparcAudioWaveformBar(
                title = audioTitle,
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

        // 3. Pack 2: Custom Reader Comfort Mode (Font Size, Paper Color & Pomodoro Study Timer)
        item {
            SparcScholarlyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("📖 রিডার কমফোর্ট ও ফোকাস টাইমার", "📖 Reader Comfort & Pomodoro Timer"),
                        style = MaterialTheme.typography.titleMedium,
                        color = p.text
                    )
                    Text(
                        "${ui.readerFontSizeSp}sp",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp,
                        color = p.primary
                    )
                }

                // Paper Theme Pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReaderPaperTheme.entries.forEach { pt ->
                        val active = ui.readerPaperTheme == pt
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(pt.bgHex),
                            border = BorderStroke(if (active) 2.dp else 1.dp, if (active) p.primary else p.border),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    repository.updateUiCustomization(ui.copy(readerPaperTheme = pt))
                                }
                        ) {
                            Text(
                                text = pt.label(lang),
                                color = Color(pt.textHex),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(vertical = 7.dp, horizontal = 4.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Font Size + Line Spacing Controls
                Slider(
                    value = ui.readerFontSizeSp.toFloat(),
                    onValueChange = {
                        repository.updateUiCustomization(ui.copy(readerFontSizeSp = it.toInt().coerceIn(14, 24)))
                    },
                    valueRange = 14f..24f,
                    steps = 9
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = p.primary, modifier = Modifier.size(18.dp))
                        val mm = (pomoSecondsLeft / 60).toString().padStart(2, '0')
                        val ss = (pomoSecondsLeft % 60).toString().padStart(2, '0')
                        Text(
                            "$mm:$ss",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = p.text
                        )
                        listOf(15, 25, 45).forEach { m ->
                            val sel = pomoPresetMin == m
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) p.primarySoft else p.cardAlt,
                                modifier = Modifier.clickable {
                                    pomoRunning = false
                                    pomoPresetMin = m
                                    pomoSecondsLeft = m * 60
                                }
                            ) {
                                Text(
                                    "${m}m",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sel) p.primarySoftText else p.muted,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { pomoRunning = !pomoRunning },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (pomoRunning) p.accent else p.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (pomoRunning) tr("বিরতি", "Pause") else tr("শুরু", "Focus"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 4. Smart Daily Page Estimator
        item {
            SparcScholarlyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tr("পেজ এস্টিমেটর ($targetDays দিনে শেষ)", "Page Estimator (Finish in $targetDays days)"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = p.text
                    )
                    Surface(shape = RoundedCornerShape(8.dp), color = p.primarySoft) {
                        Text(
                            tr("দৈনিক $dailyPages পৃষ্ঠা", "$dailyPages pages / day"),
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

        // 5. Highlight Privacy Selector & Bite-Sized Flash Notes (Styled dynamically by Reader Paper Theme & Font Size!)
        item {
            SparcScholarlyCard(containerColor = paperBg) {
                Text(
                    trContent(chapter.title),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (ui.readerFontSizeSp + 1).sp,
                    color = paperText
                )
                Text(
                    tr("হাইলাইট প্রাইভেসি কন্ট্রোল ও ফ্ল্যাশ-নোটস", "Highlight Privacy Control & Flash Notes"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = paperMuted
                )

                Surface(shape = RoundedCornerShape(12.dp), color = p.cardAlt) {
                    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            HighlightVisibility.PRIVATE to tr("ব্যক্তিগত", "Private"),
                            HighlightVisibility.SUPERVISOR to tr("মেন্টর", "Mentor"),
                            HighlightVisibility.GROUP to tr("গ্রুপ", "Group")
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
                                            note = "Cite in proposal",
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
                    Text(
                        text = "• ${trContent(fn)}",
                        fontSize = ui.readerFontSizeSp.sp,
                        lineHeight = (ui.readerFontSizeSp * ui.readerLineSpacingMultiplier).sp,
                        color = paperText
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterCurriculumBuilderSheet(
    book: BookItem,
    initialChapterNo: Int,
    repository: SparcRepository,
    onDismiss: () -> Unit
) {
    val p = LocalSparcPalette.current
    var chapterNo by remember { mutableIntStateOf(initialChapterNo.coerceAtLeast(1)) }
    val existing = book.chapters.find { it.chapterNo == chapterNo }

    var title by remember(chapterNo, existing) {
        mutableStateOf(existing?.title ?: "অধ্যায় $chapterNo: নতুন শিরোনাম")
    }
    var isLong by remember(chapterNo, existing) {
        mutableStateOf(existing?.type == ChapterType.LONG)
    }
    var totalPages by remember(chapterNo, existing) {
        mutableStateOf((existing?.totalPages ?: 24).toString())
    }
    var flashNotesRaw by remember(chapterNo, existing) {
        mutableStateOf((existing?.flashNotes ?: emptyList()).joinToString("\n"))
    }
    var outcomesRaw by remember(chapterNo, existing) {
        mutableStateOf((existing?.learningOutcomes ?: emptyList()).joinToString("\n"))
    }
    var writingPrompt by remember(chapterNo, existing) {
        mutableStateOf(existing?.writingTaskPrompt ?: "এই অধ্যায়ের আলোকে একাডেমিক প্রপোজালের অনুচ্ছেদ লিখুন।")
    }
    var minWords by remember(chapterNo, existing) {
        mutableStateOf((existing?.minWords ?: 500).toString())
    }
    var maxWords by remember(chapterNo, existing) {
        mutableStateOf((existing?.maxWords ?: 700).toString())
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
                tr("📚 ইন-অ্যাপ অধ্যায় ও ফ্ল্যাশ নোটস কাস্টমাইজার", "📚 In-App Chapter & Flash Notes Builder"),
                style = MaterialTheme.typography.titleLarge,
                color = p.text
            )
            Text(book.title, fontSize = 12.sp, color = p.primary, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tr("অধ্যায় নম্বর (নতুন বা বিদ্যমান):", "Chapter Number:"), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { chapterNo = (chapterNo - 1).coerceAtLeast(1) }) { Text("-") }
                    Text("Ch $chapterNo", fontWeight = FontWeight.ExtraBold, color = p.primary)
                    OutlinedButton(onClick = { chapterNo += 1 }) { Text("+") }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(tr("অধ্যায়ের শিরোনাম", "Chapter Title")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = totalPages,
                    onValueChange = { totalPages = it.filter { ch -> ch.isDigit() } },
                    label = { Text(tr("মোট পৃষ্ঠা", "Total Pages")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = !isLong,
                    onClick = { isLong = false },
                    label = { Text("Standard (52m)") }
                )
                FilterChip(
                    selected = isLong,
                    onClick = { isLong = true },
                    label = { Text("Long (61m)") }
                )
            }

            OutlinedTextField(
                value = flashNotesRaw,
                onValueChange = { flashNotesRaw = it },
                label = { Text(tr("ফ্ল্যাশ নোটস (প্রতি লাইনে ১টি নোট)", "Flash Notes (1 note per line)")) },
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = outcomesRaw,
                onValueChange = { outcomesRaw = it },
                label = { Text(tr("লার্নিং আউটকাম (প্রতি লাইনে ১টি)", "Learning Outcomes (1 per line)")) },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = writingPrompt,
                onValueChange = { writingPrompt = it },
                label = { Text(tr("রাইটিং টাস্ক প্রম্পট", "Writing Task Prompt")) },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minWords,
                    onValueChange = { minWords = it.filter { ch -> ch.isDigit() } },
                    label = { Text(tr("সর্বনিম্ন শব্দ", "Min Words")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxWords,
                    onValueChange = { maxWords = it.filter { ch -> ch.isDigit() } },
                    label = { Text(tr("সর্বোচ্চ শব্দ", "Max Words")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            SparcPrimaryButton(
                text = tr("অধ্যায় ও কারিকুলাম সংরক্ষণ করুন", "Save Chapter & Curriculum"),
                onClick = {
                    repository.saveChapterCurriculum(
                        bookId = book.id,
                        chapterNo = chapterNo,
                        title = title,
                        chapterType = if (isLong) ChapterType.LONG else ChapterType.STANDARD,
                        totalPages = totalPages.toIntOrNull() ?: 24,
                        flashNotes = flashNotesRaw.lines(),
                        learningOutcomes = outcomesRaw.lines(),
                        writingPrompt = writingPrompt,
                        minWords = minWords.toIntOrNull() ?: 500,
                        maxWords = maxWords.toIntOrNull() ?: 700
                    )
                    onDismiss()
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudMediaUploadBottomSheet(
    books: List<BookItem>,
    initialBook: BookItem?,
    repository: SparcRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val p = LocalSparcPalette.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableIntStateOf(if (initialBook == null || books.isEmpty()) 1 else 0) } // 0 = Update Existing, 1 = Publish New / Guest PDF
    var selectedBookIdx by remember {
        mutableIntStateOf(
            initialBook?.let { b -> books.indexOfFirst { it.id == b.id }.coerceAtLeast(0) } ?: 0
        )
    }
    var chapterNo by remember { mutableIntStateOf(1) }

    var newBookTitle by remember { mutableStateOf("") }
    var newBookAuthor by remember { mutableStateOf("") }
    var sourceAttribution by remember { mutableStateOf("") }
    var newBookPrice by remember { mutableStateOf("0") }
    var permissionChecked by remember { mutableStateOf(true) }
    var inlineTemplateRaw by remember { mutableStateOf("") }

    var pickedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pickedPdfLabel by remember { mutableStateOf<String?>(null) }
    var pickedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var pickedAudioLabel by remember { mutableStateOf<String?>(null) }

    var isUploading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pickedPdfUri = uri
            pickedPdfLabel = resolveDisplayName(context, uri, "paper.pdf")
            errorMsg = null
        }
    }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pickedAudioUri = uri
            pickedAudioLabel = resolveDisplayName(context, uri, "summary.mp3")
            errorMsg = null
        }
    }

    val templateFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val txt = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText()
                    }.getOrNull()
                }
                if (!txt.isNullOrBlank()) {
                    inlineTemplateRaw = txt
                    errorMsg = null
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isUploading) onDismiss() },
        containerColor = p.card,
        contentColor = p.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tr("☁️ বই / গবেষক PDF ও অনুশীলনী টেমপ্লেট আপলোড", "☁️ Upload Author/Researcher PDF & Exercise Template"),
                style = MaterialTheme.typography.titleLarge,
                color = p.text
            )

            SparcSegmentedPills(
                options = listOf(
                    tr("বিদ্যমান বই আপডেট", "Update Existing"),
                    tr("নতুন বই / গবেষকের PDF", "New / Guest PDF")
                ),
                selectedIndex = mode,
                onSelect = {
                    mode = it
                    errorMsg = null
                }
            )

            if (mode == 0) {
                Text(tr("বই নির্বাচন করুন:", "Select Book:"), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = p.text)
                books.forEachIndexed { idx, bk ->
                    val selected = idx == selectedBookIdx
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) p.primarySoft else p.cardAlt,
                        border = BorderStroke(1.dp, if (selected) p.primary else p.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedBookIdx = idx }
                    ) {
                        Text(
                            text = if (selected) "✓ ${bk.title}" else bk.title,
                            color = if (selected) p.primarySoftText else p.text,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(tr("অধ্যায় নম্বর:", "Target Chapter #:"), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3).forEach { ch ->
                            val active = chapterNo == ch
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) p.primary else p.cardAlt,
                                modifier = Modifier.clickable { chapterNo = ch }
                            ) {
                                Text(
                                    text = "Ch $ch",
                                    color = if (active) Color.White else p.text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = newBookTitle,
                    onValueChange = { newBookTitle = it },
                    label = { Text(tr("বই বা গবেষণা প্রবন্ধের শিরোনাম", "Book or Research Paper Title")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newBookAuthor,
                        onValueChange = { newBookAuthor = it },
                        label = { Text(tr("মূল লেখক / গবেষকের নাম", "Original Author / Researcher")) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.35f)
                    )
                    OutlinedTextField(
                        value = newBookPrice,
                        onValueChange = { newBookPrice = it.filter { ch -> ch.isDigit() } },
                        label = { Text(tr("মূল্য ৳ (ফ্রি=0)", "Price ৳")) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.65f)
                    )
                }
                OutlinedTextField(
                    value = sourceAttribution,
                    onValueChange = { sourceAttribution = it },
                    label = { Text(tr("উৎস / জার্নাল / প্রকাশনী / DOI (ক্রেডিট)", "Source / Journal / DOI Attribution")) },
                    placeholder = { Text("e.g. Open Access Journal / DOI: 10.1000/xyz") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { permissionChecked = !permissionChecked }
                ) {
                    Checkbox(checked = permissionChecked, onCheckedChange = { permissionChecked = it })
                    Text(
                        tr(
                            "মূল লেখকের ক্রেডিট বজায় রেখে ওপেন-অ্যাক্সেস / শিক্ষামূলক পাঠচক্রের অনুমতি সাপেক্ষে আপলোড করছি",
                            "Uploading with proper author credit under Open-Access / educational study circle permission"
                        ),
                        fontSize = 11.5.sp,
                        color = p.muted
                    )
                }
            }

            var directChapterText by remember { mutableStateOf("") }
            var directSubtitleRaw by remember { mutableStateOf("") }
            var selectedContentMode by remember { mutableStateOf("text") }
            val directTextLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    scope.launch {
                        val txt = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText()
                            }.getOrNull()
                        }
                        if (!txt.isNullOrBlank()) {
                            if (txt.contains("-->") || txt.trimStart().startsWith("[")) {
                                directSubtitleRaw = txt
                            } else {
                                directChapterText = txt
                            }
                            errorMsg = null
                        }
                    }
                }
            }

            // Content Mode Selector: Direct Text vs PDF vs Hybrid
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "text" to tr("📝 সরাসরি টেক্সট", "📝 Direct Text"),
                    "pdf" to tr("📄 শুধু PDF", "📄 PDF Only"),
                    "hybrid" to tr("📚 টেক্সট + PDF", "📚 Hybrid")
                ).forEach { (mCode, mLabel) ->
                    val isSel = selectedContentMode == mCode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) p.primary else p.cardAlt,
                        border = BorderStroke(1.dp, if (isSel) p.primary else p.border),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedContentMode = mCode }
                    ) {
                        Text(
                            mLabel,
                            color = if (isSel) Color.White else p.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (selectedContentMode == "text" || selectedContentMode == "hybrid") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = p.cardAlt,
                    border = BorderStroke(1.dp, if (directChapterText.isNotBlank() || directSubtitleRaw.isNotBlank()) p.primary else p.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                tr("📝 অধ্যায়ের মূল টেক্সট (শব্দে-শব্দে অডিও সিঙ্ক)", "📝 Direct Chapter Text (Word-Synced)"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = p.text
                            )
                            Text(
                                text = tr("📂 .txt/.srt দিন", "📂 Pick .txt/.srt"),
                                color = p.primary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    directTextLauncher.launch(arrayOf("text/*", "application/json", "*/*"))
                                }
                            )
                        }
                        OutlinedTextField(
                            value = directChapterText,
                            onValueChange = { directChapterText = it },
                            placeholder = { Text(tr("PDF ছাড়াই সরাসরি অধ্যায়ের বাংলা/ইংরেজি পাঠ্য পেস্ট করুন...", "Paste full chapter text directly without PDF..."), fontSize = 11.5.sp) },
                            minLines = 4,
                            maxLines = 7,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (directSubtitleRaw.isNotBlank()) {
                            Text(
                                tr("✓ .srt / .vtt টাইমস্ট্যাম্প যুক্ত হয়েছে!", "✓ .srt / .vtt word timestamps attached!"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = p.primary
                            )
                        }
                    }
                }
            }

            // File Pickers: PDF (`sparc-books`) + MP3/Audio (`sparc-audio`)
            if (selectedContentMode == "pdf" || selectedContentMode == "hybrid") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = p.cardAlt,
                    border = BorderStroke(1.dp, if (pickedPdfUri != null) p.primary else p.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isUploading) { pdfLauncher.launch(arrayOf("application/pdf")) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = p.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pickedPdfLabel ?: tr("ফোন থেকে বই বা প্রবন্ধের PDF ফাইল সিলেক্ট করুন", "Select Book or Paper PDF File from Phone"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = p.text
                            )
                            Text(
                                text = tr("Bucket: sparc-books • সব সদস্যের রিডারে সরাসরি ওপেন হবে", "Bucket: sparc-books • Auto-loads in Smart PDF Reader"),
                                fontSize = 11.sp,
                                color = p.muted
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = p.cardAlt,
                border = BorderStroke(1.dp, if (pickedAudioUri != null) p.primary else p.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isUploading) { audioLauncher.launch(arrayOf("audio/*")) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, tint = p.accent)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pickedAudioLabel ?: tr("বাংলা অডিও ফাইল (MP3/M4A — শব্দে-শব্দে সিঙ্কের জন্য, ঐচ্ছিক)", "Audio File (MP3/M4A — Optional for Word Sync)"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = p.text
                        )
                        Text(
                            text = tr("অডিও না দিলেও বিল্ট-ইন ভয়েস রিডার দিয়ে শব্দে-শব্দে চলবে!", "Works with built-in Voice Reader even without MP3!"),
                            fontSize = 11.sp,
                            color = p.muted
                        )
                    }
                }
            }

            // Inline Standard Exercise Template Attachment!
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = p.cardAlt,
                border = BorderStroke(1.dp, if (inlineTemplateRaw.isNotBlank()) p.primary else p.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("📋 নির্ধারিত টেমপ্লেটে অনুশীলনী (ঐচ্ছিক)", "📋 Standard Exercise Template (Optional)"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = p.text
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = tr("নমুনা বসান", "Fill Sample"),
                                color = p.primary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    inlineTemplateRaw = ExerciseTemplateParser.generateSampleTemplate(lang)
                                }
                            )
                            Text("•", color = p.muted, fontSize = 11.sp)
                            Text(
                                text = tr("ফাইল দিন", "Pick File"),
                                color = p.primary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    templateFileLauncher.launch(arrayOf("text/*", "application/json", "*/*"))
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = inlineTemplateRaw,
                        onValueChange = { inlineTemplateRaw = it },
                        placeholder = { Text(tr("নমুনা টেমপ্লেট বা .txt/.csv/.json পেস্ট করুন...", "Paste standard exercise template here..."), fontSize = 11.5.sp) },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            errorMsg?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            if (isUploading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = p.primary)
                    Text(
                        tr("সার্ভারে বইয়ের টেক্সট, টাইমস্ট্যাম্প ও মিডিয়া আপলোড হচ্ছে...", "Syncing text, word-timestamps & media to Server..."),
                        fontSize = 12.sp,
                        color = p.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            SparcPrimaryButton(
                text = if (isUploading) {
                    tr("আপলোড হচ্ছে...", "Uploading...")
                } else {
                    tr("☁️ আপলোড ও পাঠচক্রে প্রকাশ করুন", "☁️ Upload & Publish to Study Circle")
                },
                enabled = !isUploading,
                onClick = {
                    scope.launch {
                        isUploading = true
                        errorMsg = null
                        val pdfBytes = pickedPdfUri?.let { uri ->
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            }
                        }
                        val audioBytes = pickedAudioUri?.let { uri ->
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            }
                        }
                        val targetBook = books.getOrNull(selectedBookIdx)
                        val res = repository.uploadBookPdfAndAudio(
                            targetBook = targetBook,
                            isNewBook = mode == 1,
                            newTitle = newBookTitle,
                            newAuthor = newBookAuthor,
                            newPriceBdt = newBookPrice.toDoubleOrNull() ?: 0.0,
                            chapterNo = chapterNo,
                            pdfBytes = pdfBytes,
                            pdfFileName = pickedPdfLabel,
                            audioBytes = audioBytes,
                            audioFileName = pickedAudioLabel,
                            sourceAttribution = sourceAttribution,
                            permissionDeclared = permissionChecked,
                            exerciseTemplateRaw = inlineTemplateRaw.takeIf { it.isNotBlank() },
                            directTextContent = directChapterText,
                            contentMode = selectedContentMode,
                            subtitleRaw = directSubtitleRaw
                        )
                        isUploading = false
                        res.onSuccess { onDismiss() }
                            .onFailure { errorMsg = it.message }
                    }
                }
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookCircleAndInviteBottomSheet(
    state: SparcAppState,
    book: BookItem,
    repository: SparcRepository,
    onNavigateToCircle: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = LocalSparcPalette.current
    val context = LocalContext.current

    val matchingCircles = remember(state.circles, book.id) {
        state.circles.filter { it.bookId == book.id }
    }
    val selectedCircle = matchingCircles.firstOrNull { state.currentUser.id in it.memberIds }
        ?: matchingCircles.firstOrNull()

    var newCircleName by remember(book.id) {
        mutableStateOf("${book.title.take(22)} পাঠচক্র")
    }
    var invitedIds by remember { mutableStateOf(setOf<String>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        contentColor = p.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    tr("👥 বইটির স্টাডি সার্কেল ও সহপাঠী আমন্ত্রণ", "👥 Book Study Circle & Peer Invites"),
                    style = MaterialTheme.typography.titleLarge,
                    color = p.text
                )
                Text(
                    "${book.title} • ${book.author}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.primary
                )
            }

            // 1. Existing Circles for this Book OR Instant Circle Creation
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = p.cardAlt,
                    border = BorderStroke(1.dp, p.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (matchingCircles.isNotEmpty()) {
                            Text(
                                tr("এই বইয়ের চলমান স্টাডি সার্কেল:", "Active Study Circles for this Book:"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = p.text
                            )
                            matchingCircles.forEach { c ->
                                val joined = state.currentUser.id in c.memberIds
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (joined) p.primarySoft else p.card,
                                    border = BorderStroke(1.dp, if (joined) p.primary else p.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(trContent(c.name), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = p.text)
                                            Text(
                                                "${c.memberIds.size}/${c.maxMembers} • ${c.sessionScheduleLabel}",
                                                fontSize = 11.sp,
                                                color = p.muted
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                if (joined) {
                                                    repository.selectActiveCircle(c.id)
                                                    onNavigateToCircle()
                                                } else {
                                                    if (repository.joinStudyCircle(c.id, c.inviteCode)) {
                                                        onNavigateToCircle()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                if (joined) tr("সার্কেলে যান", "Open Circle") else tr("জয়েন করুন", "Join"),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                tr("এই বইটির জন্য এখনো কোনো সার্কেল নেই — ১ ক্লিকে শুরু করুন:", "No circle yet for this book — start one now:"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = p.text
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newCircleName,
                                onValueChange = { newCircleName = it },
                                label = { Text(tr("নতুন সার্কেলের নাম", "New Circle Name")) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (newCircleName.isNotBlank()) {
                                        repository.createNewStudyCircle(newCircleName, book, minMembers = 5)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = p.primary)
                            ) {
                                Text(tr("+ খুলুন", "+ Start"))
                            }
                        }
                    }
                }
            }

            // 2. External WhatsApp / Messenger / Share Sheet Invite Button
            item {
                val circleLabel = selectedCircle?.name ?: newCircleName
                val codeInfo = selectedCircle?.inviteCode?.let { " (Invite Code: $it)" } ?: ""
                val shareMessage = tr(
                    "📚 আমি SPARC Study Circle-এ \"${book.title}\" (${book.author}) পড়ছি! আমাদের \"$circleLabel\" পাঠচক্রে যুক্ত হোন$codeInfo — রেফারেল কোড: ${state.currentUser.referralCode}",
                    "📚 I'm reading \"${book.title}\" (${book.author}) on SPARC Study Circle! Join our \"$circleLabel\" cohort$codeInfo — Referral Code: ${state.currentUser.referralCode}"
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = p.accentSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(sendIntent, "Invite Friends to Study Circle"))
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = p.accentText)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tr("📲 WhatsApp / Messenger-এ বন্ধুদের আমন্ত্রণ পাঠান", "📲 Share Study Circle Invite via WhatsApp / Apps"),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp,
                                color = p.accentText
                            )
                            Text(
                                tr("বইয়ের নাম, সার্কেল ও আপনার রেফারেল কোডসহ শেয়ার হবে", "Shares book title, circle name & your referral code"),
                                fontSize = 11.sp,
                                color = p.accentText
                            )
                        }
                    }
                }
            }

            // 3. Direct 1-Tap In-App Researcher Directory Invites
            item {
                Text(
                    tr("গবেষক ডিরেক্টরি থেকে সরাসরি আমন্ত্রণ পাঠান (+৫ পয়েন্ট):", "Invite Peers from Researcher Directory (+5 pts):"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = p.text
                )
            }

            items(state.directoryMembers.take(10), key = { it.id }) { peer ->
                val peerName = trContent(peer.name)
                val alreadyInvited = peer.id in invitedIds
                val circleTitle = selectedCircle?.name ?: newCircleName
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = p.cardAlt,
                    border = BorderStroke(1.dp, p.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SparcAvatarCircle(name = peerName, avatarUrl = peer.avatarUrl, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(peerName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = p.text)
                            Text("${peer.department} • ★ ${peer.rating}", fontSize = 11.sp, color = p.muted)
                        }
                        val inviteBody = tr(
                            "চলুন একসাথে \"${book.title}\" বইয়ের \"$circleTitle\" স্টাডি সার্কেলে পড়ি!",
                            "Let's read \"${book.title}\" together in the \"$circleTitle\" Study Circle!"
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (alreadyInvited) p.card else p.primarySoft,
                            modifier = Modifier.clickable(enabled = !alreadyInvited) {
                                repository.sendInviteMessage(peer, inviteBody)
                                invitedIds = invitedIds + peer.id
                            }
                        ) {
                            Text(
                                text = if (alreadyInvited) tr("✓ আমন্ত্রিত", "✓ Invited") else "Invite +",
                                color = if (alreadyInvited) p.muted else p.primarySoftText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveDisplayName(context: Context, uri: Uri, fallback: String): String {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: fallback
}

@Composable
private fun HardcopyOrderDialog(
    book: BookItem,
    walletBalanceBdt: Double = 0.0,
    bkashNumber: String = "01700-000000",
    nagadNumber: String = "01800-000000",
    borrowDepositBdt: Double = 150.0,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    val p = LocalSparcPalette.current
    var mode by remember { mutableStateOf("Purchase") }
    val cost = if (mode == "Purchase") book.physicalPriceBdt else borrowDepositBdt
    var paymentOption by remember(walletBalanceBdt, cost) {
        mutableStateOf(if (walletBalanceBdt >= cost && cost > 0) "wallet" else "cod")
    }
    var directTrxId by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        titleContentColor = p.text,
        textContentColor = p.text,
        title = { Text(tr("হার্ডকপি বই অর্ডার / সংগ্রহ", "Order or Borrow Hardcopy Book")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    tr(
                        "গোপনীয়তা: ঠিকানা ও নম্বর শুধুমাত্র SPARC অ্যাডমিন বা প্রতিনিধি দেখতে পাবেন।",
                        "Privacy: Visible strictly to SPARC Admin or Representative."
                    ),
                    fontSize = 12.sp,
                    color = p.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "Purchase",
                        onClick = { mode = "Purchase" },
                        label = { Text(tr("ক্রয় (৳${book.physicalPriceBdt.toInt()})", "Buy (৳${book.physicalPriceBdt.toInt()})")) }
                    )
                    FilterChip(
                        selected = mode == "Borrow",
                        onClick = { mode = "Borrow" },
                        label = { Text(tr("ধার (জামানত ৳${borrowDepositBdt.toInt()})", "Borrow (৳${borrowDepositBdt.toInt()} dep.)")) }
                    )
                }

                if (cost > 0) {
                    Text(
                        tr("পেমেন্ট মাধ্যম বেছে নিন:", "Select Payment Method:"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = p.text
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = paymentOption == "cod",
                            onClick = { paymentOption = "cod" },
                            label = { Text(tr("🚚 ক্যাশ অন ডেলিভারি", "🚚 COD"), fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = paymentOption == "direct_trx",
                            onClick = { paymentOption = "direct_trx" },
                            label = { Text(tr("📱 TrxID", "📱 TrxID"), fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = paymentOption == "wallet",
                            onClick = { paymentOption = "wallet" },
                            label = { Text(tr("💳 ওয়ালেট", "💳 Wallet"), fontSize = 11.sp) }
                        )
                    }
                    if (paymentOption == "direct_trx") {
                        Text(
                            "bKash: $bkashNumber • Nagad: $nagadNumber (৳${cost.toInt()})",
                            fontSize = 11.5.sp,
                            color = p.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = directTrxId,
                            onValueChange = { directTrxId = it },
                            label = { Text("bKash / Nagad TrxID") },
                            singleLine = true
                        )
                    }
                }

                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(tr("ডেলিভারি ঠিকানা", "Delivery Address")) })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(tr("মোবাইল নম্বর", "Phone Number")) })
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(mode, address, phone, paymentOption, directTrxId) },
                colors = ButtonDefaults.buttonColors(containerColor = p.primary)
            ) {
                Text(tr("অর্ডার নিশ্চিত করুন", "Confirm Order"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("বাতিল", "Cancel"), color = p.muted) }
        }
    )
}

