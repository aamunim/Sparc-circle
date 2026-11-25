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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PictureAsPdf
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.BookItem
import org.sparc.studycircle.core.model.HighlightVisibility
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.tr
import org.sparc.studycircle.core.theme.trContent
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
    var uploadSheetInitialBook by remember { mutableStateOf<BookItem?>(null) }
    var showUploadSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf(
                    tr("ডিজিটাল লাইব্রেরি", "Digital Library"),
                    tr("স্মার্ট রিডার ও অডিও", "Smart Reader & Audio")
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
                onOrderHardcopy = { book -> orderDialogBook = book },
                onOpenCloudUpload = { targetBook ->
                    uploadSheetInitialBook = targetBook
                    showUploadSheet = true
                }
            )
        } else {
            SmartPaperReaderView(
                book = selectedBook,
                repository = repository,
                onOpenCloudUpload = {
                    uploadSheetInitialBook = selectedBook
                    showUploadSheet = true
                }
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

    if (showUploadSheet) {
        CloudMediaUploadBottomSheet(
            books = state.books,
            initialBook = uploadSheetInitialBook,
            repository = repository,
            onDismiss = { showUploadSheet = false }
        )
    }
}

@Composable
private fun AcademicLibraryCatalogView(
    books: List<BookItem>,
    onReadFreeChapter: (BookItem) -> Unit,
    onOrderHardcopy: (BookItem) -> Unit,
    onOpenCloudUpload: (BookItem?) -> Unit
) {
    val p = LocalSparcPalette.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cloud Supabase Storage Upload Studio Banner Card
        item {
            SparcGradientHeroCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = tr("☁️ ক্লাউড বুক ও অডিও স্টুডিও", "☁️ Cloud Book & Audio Studio"),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = tr(
                                "আসল বইয়ের PDF ও বাংলা অডিও সামারি (MP3) সরাসরি Supabase Storage-এ আপলোড করুন",
                                "Upload real Book PDFs & Bangla MP3 Audio Summaries directly to Supabase Storage"
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

        items(books, key = { it.id }) { book ->
            val hasCloudPdf = book.pdfUrl.startsWith("http") && !book.pdfUrl.contains("storage.sparc.org.bd")
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
                            if (hasCloudPdf) {
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
                        }

                        Text(book.title, style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            tr(
                                "${book.author} • ${book.totalChapters} অধ্যায় • ৳${book.physicalPriceBdt.toInt()}",
                                "${book.author} • ${book.totalChapters} Chapters • ৳${book.physicalPriceBdt.toInt()}"
                            ),
                            fontSize = 12.sp,
                            color = p.muted
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onReadFreeChapter(book) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text(tr("পড়ুন ও শুনুন", "Read & Listen"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.cardAlt,
                        modifier = Modifier
                            .weight(0.95f)
                            .clickable { onOrderHardcopy(book) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 11.dp)) {
                            Text(tr("হার্ডকপি", "Hardcopy"), color = p.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.primarySoft,
                        modifier = Modifier.clickable { onOpenCloudUpload(book) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Upload PDF/Audio", tint = p.primarySoftText, modifier = Modifier.size(18.dp))
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
    repository: SparcRepository,
    onOpenCloudUpload: () -> Unit
) {
    val p = LocalSparcPalette.current
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Chapter Selector + Quick Cloud Upload Trigger
        item {
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
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = p.primarySoft,
                    modifier = Modifier.clickable { onOpenCloudUpload() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = p.primarySoftText, modifier = Modifier.size(15.dp))
                        Text(tr("PDF/MP3 আপলোড", "Upload PDF/MP3"), color = p.primarySoftText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 1. Real Native Android PdfRenderer Viewer + Auto Cloud Supabase Storage PDF Download!
        item {
            NativePdfBookViewerCard(
                bookTitle = book.title,
                cloudPdfUrl = book.pdfUrl,
                onDownloadCloudPdf = { url, targetFile -> repository.downloadCloudPdf(url, targetFile) },
                onQuickHighlightPage = { pageNo ->
                    repository.addHighlightAndLogReading(
                        bookId = book.id,
                        chapterNo = chapter.chapterNo,
                        pageNo = pageNo,
                        selectedText = "PDF Page $pageNo — $activeQuote",
                        note = "Saved from PDF Reader",
                        isUnderline = false,
                        visibility = selectedPrivacy
                    )
                }
            )
        }

        // 2. Real Media3 ExoPlayer Audio Summary Player (Direct Supabase Storage MP3 Streaming!)
        item {
            val audioTitle = if (audioController.isStreamingCloudAudio) {
                tr("☁️ ক্লাউড বাংলা অডিও সামারি (Live MP3)", "☁️ Cloud Audio Summary (Live MP3)")
            } else {
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

        // 3. Smart Daily Page Estimator
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

        // 4. Highlight Privacy Selector & Bite-Sized Flash Notes
        item {
            SparcScholarlyCard(containerColor = p.readerPaper) {
                Text(
                    tr("হাইলাইট প্রাইভেসি কন্ট্রোল ও ফ্ল্যাশ-নোটস", "Highlight Privacy Control & Flash Notes"),
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text
                )

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
                    Text("• ${trContent(fn)}", style = MaterialTheme.typography.bodyMedium, color = p.muted)
                }
            }
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
    val scope = rememberCoroutineScope()

    var mode by remember { mutableIntStateOf(0) } // 0 = Attach to Existing Book, 1 = Publish New Book
    var selectedBookIdx by remember {
        mutableIntStateOf(
            initialBook?.let { b -> books.indexOfFirst { it.id == b.id }.coerceAtLeast(0) } ?: 0
        )
    }
    var chapterNo by remember { mutableIntStateOf(1) }

    var newBookTitle by remember { mutableStateOf("") }
    var newBookAuthor by remember { mutableStateOf("") }
    var newBookPrice by remember { mutableStateOf("450") }

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
            pickedPdfLabel = resolveDisplayName(context, uri, "book.pdf")
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

    ModalBottomSheet(
        onDismissRequest = { if (!isUploading) onDismiss() },
        containerColor = p.card,
        contentColor = p.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tr("☁️ Supabase Storage-এ PDF ও অডিও আপলোড", "☁️ Upload Book PDF & Audio to Supabase Storage"),
                style = MaterialTheme.typography.titleLarge,
                color = p.text
            )

            SparcSegmentedPills(
                options = listOf(
                    tr("বিদ্যমান বই আপডেট", "Update Existing Book"),
                    tr("নতুন বই প্রকাশ", "Publish New Book")
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
                    Text(tr("অডিও সামারির অধ্যায় নম্বর:", "Chapter # for Audio Summary:"), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
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
                    label = { Text(tr("নতুন বইয়ের শিরোনাম", "New Book Title")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newBookAuthor,
                        onValueChange = { newBookAuthor = it },
                        label = { Text(tr("লেখক", "Author")) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = newBookPrice,
                        onValueChange = { newBookPrice = it },
                        label = { Text(tr("মূল্য (৳)", "Price (৳)")) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.7f)
                    )
                }
            }

            // File Pickers: PDF (`sparc-books`) + MP3/Audio (`sparc-audio`)
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
                            text = pickedPdfLabel ?: tr("ফোন থেকে বইয়ের PDF ফাইল সিলেক্ট করুন", "Select Book PDF File from Phone"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = p.text
                        )
                        Text(
                            text = tr("Bucket: sparc-books • সব ইউজারের রিডারে সরাসরি ডাউনলোড হবে", "Bucket: sparc-books • Auto-loads in all users' PDF Reader"),
                            fontSize = 11.sp,
                            color = p.muted
                        )
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
                            text = pickedAudioLabel ?: tr("ফোন থেকে বাংলা অডিও সামারি (MP3/M4A/WAV) সিলেক্ট করুন", "Select Bangla Audio Summary (MP3/M4A/WAV)"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = p.text
                        )
                        Text(
                            text = tr("Bucket: sparc-audio • ExoPlayer-এ সরাসরি লাইভ স্ট্রিম হবে", "Bucket: sparc-audio • Streams directly in ExoPlayer"),
                            fontSize = 11.sp,
                            color = p.muted
                        )
                    }
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
                        tr("Supabase Storage-এ ফাইল আপলোড হচ্ছে... অনুগ্রহ করে অপেক্ষা করুন", "Uploading to Supabase Storage... please wait"),
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
                    tr("☁️ Supabase Storage-এ আপলোড ও প্রকাশ করুন", "☁️ Upload to Supabase Storage & Publish")
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
                        val targetBook = books.getOrElse(selectedBookIdx) { books.first() }
                        val res = repository.uploadBookPdfAndAudio(
                            targetBook = targetBook,
                            isNewBook = mode == 1,
                            newTitle = newBookTitle,
                            newAuthor = newBookAuthor,
                            newPriceBdt = newBookPrice.toDoubleOrNull() ?: 450.0,
                            chapterNo = chapterNo,
                            pdfBytes = pdfBytes,
                            pdfFileName = pickedPdfLabel,
                            audioBytes = audioBytes,
                            audioFileName = pickedAudioLabel
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
        title = { Text(tr("হার্ডকপি বই অর্ডার / সংগ্রহ", "Order or Borrow Hardcopy Book")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr(
                        "গোপনীয়তা: শুধুমাত্র SPARC অ্যাডমিন, শিক্ষক বা প্রতিনিধি দেখতে পাবেন।",
                        "Privacy: Visible strictly to SPARC Admin, Supervisor, or Representative."
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
                        label = { Text(tr("সংগ্রহ (Borrow)", "Borrow")) }
                    )
                }
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(tr("ঠিকানা", "Delivery Address")) })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(tr("মোবাইল নম্বর", "Phone Number")) })
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(mode, address, phone) },
                colors = ButtonDefaults.buttonColors(containerColor = p.primary)
            ) {
                Text(tr("নিশ্চিত করুন", "Confirm Order"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("বাতিল", "Cancel"), color = p.muted) }
        }
    )
}
