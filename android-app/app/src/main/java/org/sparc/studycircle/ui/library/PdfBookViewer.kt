package org.sparc.studycircle.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import android.view.ViewGroup
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.sparc.studycircle.core.media.SparcAudioController
import org.sparc.studycircle.core.model.*
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.SparcCrimson
import org.sparc.studycircle.core.theme.tr
import org.sparc.studycircle.ui.components.SparcScholarlyCard
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private val PdfBitmapCache = object : LruCache<String, Bitmap>(28 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
}
private val PdfRenderMutex = Mutex()

@Composable
fun NativePdfBookViewerCard(
    bookId: String = "default_book",
    bookTitle: String,
    bookAuthor: String = "SPARC Author",
    chapters: List<ChapterItem> = emptyList(),
    cloudPdfUrl: String = "",
    paperTheme: ReaderPaperTheme = ReaderPaperTheme.WARM_SEPIA,
    trimMargins: Boolean = true,
    scrollVertical: Boolean = false,
    initialSavedPage: Int = 1,
    bookmarks: List<PdfBookmarkItem> = emptyList(),
    bookHighlights: List<ReadingHighlight> = emptyList(),
    audioController: SparcAudioController? = null,
    onChangePaperTheme: (ReaderPaperTheme) -> Unit = {},
    onToggleTrimMargins: (Boolean) -> Unit = {},
    onToggleScrollVertical: (Boolean) -> Unit = {},
    onSaveLastPage: (Int) -> Unit = {},
    onToggleBookmark: (pageNo: Int, label: String) -> Unit = { _, _ -> },
    onRemoveBookmark: (bookmarkId: String) -> Unit = {},
    onAddPageAnnotation: (pageNo: Int, markerStyle: String, noteText: String, visibility: HighlightVisibility) -> Unit = { _, _, _, _ -> },
    onDownloadCloudPdf: suspend (String, File) -> Result<File> = { _, f -> Result.success(f) },
    onPageChanged: (currentPage: Int, totalPages: Int) -> Unit = { _, _ -> },
    onQuickHighlightPage: (pageNo: Int) -> Unit = {}
) {
    val context = LocalContext.current
    val p = LocalSparcPalette.current

    val hasValidCloudUrl = cloudPdfUrl.startsWith("http") && !cloudPdfUrl.contains("storage.sparc.org.bd")

    var customPdfUri by remember(bookId, cloudPdfUrl) { mutableStateOf<Uri?>(null) }
    var customPdfName by remember(bookId, cloudPdfUrl) { mutableStateOf<String?>(null) }
    var cachedCloudFile by remember(cloudPdfUrl) { mutableStateOf<File?>(null) }
    var isDownloadingCloud by remember(cloudPdfUrl) { mutableStateOf(false) }

    var pageIndex by remember(bookId, cloudPdfUrl) {
        mutableIntStateOf((initialSavedPage - 1).coerceAtLeast(0))
    }
    var totalPageCount by remember { mutableIntStateOf(6) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var showFullScreenReader by remember { mutableStateOf(false) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            customPdfUri = uri
            customPdfName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected_Book.pdf"
            pageIndex = 0
            zoomScale = 1f
            panOffsetX = 0f
            panOffsetY = 0f
        }
    }

    LaunchedEffect(cloudPdfUrl) {
        if (hasValidCloudUrl) {
            isDownloadingCloud = true
            val cacheTarget = File(
                context.cacheDir,
                "cloud_pdfs/sparc_${cloudPdfUrl.hashCode().toUInt()}.pdf"
            )
            val downloaded = onDownloadCloudPdf(cloudPdfUrl, cacheTarget).getOrNull()
            cachedCloudFile = downloaded
            isDownloadingCloud = false
        } else {
            cachedCloudFile = null
        }
    }

    LaunchedEffect(customPdfUri, cachedCloudFile, pageIndex, trimMargins) {
        val result = renderPdfPageCached(
            context = context,
            customUri = customPdfUri,
            cachedCloudFile = cachedCloudFile,
            requestedPage = pageIndex,
            trimMargins = trimMargins,
            isThumbnail = false
        )
        if (result != null) {
            renderedBitmap = result.first
            totalPageCount = result.second
            val safeClamped = pageIndex.coerceIn(0, (result.second - 1).coerceAtLeast(0))
            if (safeClamped != pageIndex) pageIndex = safeClamped
            onPageChanged(safeClamped + 1, result.second)
            onSaveLastPage(safeClamped + 1)

            // Prefetch next page in background for instant page turns
            if (safeClamped + 1 < result.second) {
                renderPdfPageCached(
                    context = context,
                    customUri = customPdfUri,
                    cachedCloudFile = cachedCloudFile,
                    requestedPage = safeClamped + 1,
                    trimMargins = trimMargins,
                    isThumbnail = false
                )
            }
        }
    }

    val currentPageNo = pageIndex + 1
    val isCurrentPageBookmarked = bookmarks.any { it.bookId == bookId && it.pageNo == currentPageNo }
    val pageNotesCount = bookHighlights.count { it.pageNo == currentPageNo }
    val progressFraction = if (totalPageCount > 0) currentPageNo.toFloat() / totalPageCount.toFloat() else 0f
    val paperFilter = remember(paperTheme) { buildPdfPaperColorFilter(paperTheme) }
    val canvasFrameBg = Color(paperTheme.bgHex)

    SparcScholarlyCard {
        // Top Header Row: Title + Resume Info + Open Local PDF + Full-Screen Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customPdfName
                        ?: if (cachedCloudFile != null) "☁️ $bookTitle"
                        else "📄 $bookTitle",
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isDownloadingCloud) {
                        tr("সার্ভার থেকে বই ডাউনলোড হচ্ছে...", "Downloading book from Server...")
                    } else {
                        tr(
                            "পৃষ্ঠা $currentPageNo / $totalPageCount (${(progressFraction * 100).roundToInt()}%) • ডাবল-ট্যাপে জুম",
                            "Page $currentPageNo / $totalPageCount (${(progressFraction * 100).roundToInt()}%) • Double-tap zoom"
                        )
                    },
                    fontSize = 11.5.sp,
                    color = p.muted
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = p.cardAlt,
                    border = BorderStroke(1.dp, p.border),
                    modifier = Modifier.clickable {
                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = p.text, modifier = Modifier.size(15.dp))
                        Text(tr("ফাইল", "Open"), color = p.text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = p.primary,
                    modifier = Modifier.clickable { showFullScreenReader = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(tr("ফুল-স্ক্রিন", "Full Screen"), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // Thin Reading Progress Bar
        LinearProgressIndicator(
            progress = { progressFraction.coerceIn(0f, 1f) },
            color = p.primary,
            trackColor = p.cardAlt,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
        )

        // Quick Smart Toolbar Strip (Paper Filter + Margin Trim + Bookmark)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(
                    ReaderPaperTheme.PURE_WHITE to "☀️",
                    ReaderPaperTheme.WARM_SEPIA to "📜",
                    ReaderPaperTheme.SOFT_MINT to "🌿",
                    ReaderPaperTheme.AMOLED_NIGHT to "🌙"
                ).forEach { (pt, icon) ->
                    val active = paperTheme == pt
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (active) p.primarySoft else p.cardAlt,
                        border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) p.primary else p.border),
                        modifier = Modifier.clickable { onChangePaperTheme(pt) }
                    ) {
                        Text(
                            text = icon,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = if (trimMargins) p.primarySoft else p.cardAlt,
                    border = BorderStroke(1.dp, if (trimMargins) p.primary else p.border),
                    modifier = Modifier.clickable { onToggleTrimMargins(!trimMargins) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = if (trimMargins) p.primarySoftText else p.muted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (trimMargins) tr("ট্রিম অন", "Trim On") else tr("ট্রিম", "Trim"),
                            color = if (trimMargins) p.primarySoftText else p.muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onToggleBookmark(currentPageNo, "$bookTitle — পৃষ্ঠা $currentPageNo")
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (isCurrentPageBookmarked) Color(0xFFD97706) else p.muted,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // Inline PDF Canvas (Taller 430dp + ColorMatrix + Double-Tap Zoom + Sticky Badge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(430.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(canvasFrameBg)
                .border(1.dp, p.border, RoundedCornerShape(16.dp))
                .pdfZoomPanGesture(
                    zoomScale = zoomScale,
                    onZoomPan = { newScale, dx, dy ->
                        zoomScale = newScale
                        if (newScale > 1.02f) {
                            panOffsetX = (panOffsetX + dx).coerceIn(-420f, 420f)
                            panOffsetY = (panOffsetY + dy).coerceIn(-420f, 420f)
                        } else {
                            panOffsetX = 0f
                            panOffsetY = 0f
                        }
                    }
                )
                .pointerInput(zoomScale) {
                    detectTapGestures(
                        onTap = { showFullScreenReader = true },
                        onDoubleTap = {
                            if (zoomScale > 1.2f) {
                                zoomScale = 1f
                                panOffsetX = 0f
                                panOffsetY = 0f
                            } else {
                                zoomScale = 2.2f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = renderedBitmap
            if (isDownloadingCloud) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = p.primary)
                    Text(
                        tr("ক্লাউড PDF লোড হচ্ছে...", "Loading Cloud PDF..."),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(paperTheme.textHex)
                    )
                }
            } else if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF Page $currentPageNo",
                    colorFilter = paperFilter,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (trimMargins) 4.dp else 8.dp)
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale,
                            translationX = panOffsetX,
                            translationY = panOffsetY
                        )
                )
            } else {
                CircularProgressIndicator(color = p.primary)
            }

            if (pageNotesCount > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFF59E0B),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clickable { showFullScreenReader = true }
                ) {
                    Text(
                        text = "📌 $pageNotesCount",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Fast Page Scrubber + Prev/Next + Highlight Button
        if (totalPageCount > 1) {
            Slider(
                value = pageIndex.toFloat().coerceIn(0f, (totalPageCount - 1).toFloat()),
                onValueChange = { v ->
                    val next = v.roundToInt().coerceIn(0, totalPageCount - 1)
                    if (next != pageIndex) {
                        pageIndex = next
                        zoomScale = 1f
                        panOffsetX = 0f
                        panOffsetY = 0f
                    }
                },
                valueRange = 0f..(totalPageCount - 1).coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(thumbColor = p.primary, activeTrackColor = p.primary),
                modifier = Modifier.height(26.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        if (pageIndex > 0) {
                            pageIndex--
                            zoomScale = 1f
                            panOffsetX = 0f
                            panOffsetY = 0f
                        }
                    },
                    enabled = pageIndex > 0,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
                }
                Text(
                    text = "$currentPageNo / $totalPageCount",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = p.text
                )
                FilledTonalIconButton(
                    onClick = {
                        if (pageIndex < totalPageCount - 1) {
                            pageIndex++
                            zoomScale = 1f
                            panOffsetX = 0f
                            panOffsetY = 0f
                        }
                    },
                    enabled = pageIndex < totalPageCount - 1,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = p.cardAlt,
                    border = BorderStroke(1.dp, p.border),
                    modifier = Modifier.clickable {
                        zoomScale = when {
                            zoomScale < 1.4f -> 1.6f
                            zoomScale < 2.1f -> 2.3f
                            else -> 1f
                        }
                        if (zoomScale == 1f) {
                            panOffsetX = 0f
                            panOffsetY = 0f
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = p.text, modifier = Modifier.size(14.dp))
                        Text("${(zoomScale * 10).roundToInt() / 10f}x", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = p.text)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = p.accentSoft,
                    modifier = Modifier.clickable { onQuickHighlightPage(currentPageNo) }
                ) {
                    Text(
                        text = tr("📌 নোট +", "📌 Note +"),
                        color = p.accentText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // ========================================================================
    // ALL-IN-ONE FULL-SCREEN IMMERSIVE SMART PDF STUDIO (Mockups 1 + 2 + 3 + 4)
    // ========================================================================
    if (showFullScreenReader) {
        FullScreenSmartPdfDialog(
            bookId = bookId,
            bookTitle = customPdfName ?: bookTitle,
            bookAuthor = bookAuthor,
            chapters = chapters,
            customUri = customPdfUri,
            cachedCloudFile = cachedCloudFile,
            totalPageCount = totalPageCount,
            initialPageIndex = pageIndex,
            paperTheme = paperTheme,
            trimMargins = trimMargins,
            scrollVertical = scrollVertical,
            bookmarks = bookmarks.filter { it.bookId == bookId },
            bookHighlights = bookHighlights,
            audioController = audioController,
            onChangePaperTheme = onChangePaperTheme,
            onToggleTrimMargins = onToggleTrimMargins,
            onToggleScrollVertical = onToggleScrollVertical,
            onPageSelected = { idx ->
                pageIndex = idx
                onPageChanged(idx + 1, totalPageCount)
                onSaveLastPage(idx + 1)
            },
            onToggleBookmark = onToggleBookmark,
            onRemoveBookmark = onRemoveBookmark,
            onAddPageAnnotation = onAddPageAnnotation,
            onDismiss = { showFullScreenReader = false }
        )
    }
}

@Composable
private fun FullScreenSmartPdfDialog(
    bookId: String,
    bookTitle: String,
    bookAuthor: String,
    chapters: List<ChapterItem>,
    customUri: Uri?,
    cachedCloudFile: File?,
    totalPageCount: Int,
    initialPageIndex: Int,
    paperTheme: ReaderPaperTheme,
    trimMargins: Boolean,
    scrollVertical: Boolean,
    bookmarks: List<PdfBookmarkItem>,
    bookHighlights: List<ReadingHighlight>,
    audioController: SparcAudioController?,
    onChangePaperTheme: (ReaderPaperTheme) -> Unit,
    onToggleTrimMargins: (Boolean) -> Unit,
    onToggleScrollVertical: (Boolean) -> Unit,
    onPageSelected: (Int) -> Unit,
    onToggleBookmark: (Int, String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onAddPageAnnotation: (Int, String, String, HighlightVisibility) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val safeTotal = totalPageCount.coerceAtLeast(1)

    var currentPageIndex by remember { mutableIntStateOf(initialPageIndex.coerceIn(0, safeTotal - 1)) }
    var chromeVisible by remember { mutableStateOf(true) }
    var showStudioPenBar by remember { mutableStateOf(false) }       // Mockup 2: Marker & Cite toolbar
    var showAnnotationsDrawer by remember { mutableStateOf(false) }  // Mockup 2: Shared Circle Notes drawer
    var showFilmstripDock by remember { mutableStateOf(false) }      // Mockup 3: Thumbnail strip + Scrubber + Audio
    var showNavigatorSheet by remember { mutableStateOf(false) }     // Mockup 4: Go-to-Page + Chapters/Bookmarks/Notes
    var citeCopiedToast by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = currentPageIndex,
        pageCount = { safeTotal }
    )
    val verticalListState = rememberLazyListState(initialFirstVisibleItemIndex = currentPageIndex)

    LaunchedEffect(pagerState.currentPage, scrollVertical) {
        if (!scrollVertical && pagerState.currentPage != currentPageIndex) {
            currentPageIndex = pagerState.currentPage
            onPageSelected(currentPageIndex)
        }
    }

    LaunchedEffect(verticalListState.firstVisibleItemIndex, scrollVertical) {
        if (scrollVertical && verticalListState.firstVisibleItemIndex != currentPageIndex) {
            currentPageIndex = verticalListState.firstVisibleItemIndex.coerceIn(0, safeTotal - 1)
            onPageSelected(currentPageIndex)
        }
    }

    fun jumpToPage(targetIndex: Int) {
        val clamped = targetIndex.coerceIn(0, safeTotal - 1)
        currentPageIndex = clamped
        onPageSelected(clamped)
        scope.launch {
            if (scrollVertical) {
                verticalListState.scrollToItem(clamped)
            } else {
                pagerState.scrollToPage(clamped)
            }
        }
    }

    val currentPageNo = currentPageIndex + 1
    val isBookmarked = bookmarks.any { it.pageNo == currentPageNo }
    val currentPageHighlights = remember(bookHighlights, currentPageNo) {
        bookHighlights.filter { it.pageNo == currentPageNo }
    }
    val paperFilter = remember(paperTheme) { buildPdfPaperColorFilter(paperTheme) }
    val bgContainer = if (paperTheme == ReaderPaperTheme.AMOLED_NIGHT) Color(0xFF070B14) else Color(paperTheme.bgHex)
    val isOfflineReady = cachedCloudFile != null || customUri != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgContainer)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // ================================================================
            // MAIN PDF READING CANVAS (Horizontal Swipe OR Continuous Vertical)
            // ================================================================
            if (scrollVertical) {
                LazyColumn(
                    state = verticalListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 64.dp, bottom = 112.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(safeTotal, key = { it }) { idx ->
                        val pagePins = bookHighlights.count { it.pageNo == idx + 1 }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SinglePdfPageSurface(
                                context = context,
                                customUri = customUri,
                                cachedCloudFile = cachedCloudFile,
                                pageIndex = idx,
                                trimMargins = trimMargins,
                                paperTheme = paperTheme,
                                paperFilter = paperFilter,
                                pinCount = pagePins,
                                fillViewportHeight = false,
                                onSingleTap = { chromeVisible = !chromeVisible },
                                onOpenPins = {
                                    currentPageIndex = idx
                                    showAnnotationsDrawer = true
                                }
                            )
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.45f)
                            ) {
                                Text(
                                    text = tr("পৃষ্ঠা ${idx + 1} / $safeTotal", "Page ${idx + 1} / $safeTotal"),
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { idx ->
                    val pagePins = bookHighlights.count { it.pageNo == idx + 1 }
                    SinglePdfPageSurface(
                        context = context,
                        customUri = customUri,
                        cachedCloudFile = cachedCloudFile,
                        pageIndex = idx,
                        trimMargins = trimMargins,
                        paperTheme = paperTheme,
                        paperFilter = paperFilter,
                        pinCount = pagePins,
                        fillViewportHeight = true,
                        onSingleTap = { chromeVisible = !chromeVisible },
                        onOpenPins = { showAnnotationsDrawer = true }
                    )
                }
            }

            // Ultra-thin Top Reading Progress Strip
            LinearProgressIndicator(
                progress = { (currentPageNo.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f) },
                color = Color(0xFF10B981),
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
            )

            // ================================================================
            // TOP AUTO-HIDE BAR + MOCKUP 2 FLOATING MARKER PEN STUDIO
            // ================================================================
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.90f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bookTitle,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = tr("পৃষ্ঠা $currentPageNo / $safeTotal", "Page $currentPageNo / $safeTotal"),
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                        if (isOfflineReady) {
                                            Surface(shape = RoundedCornerShape(50), color = Color(0xFF10B981).copy(alpha = 0.22f)) {
                                                Text(
                                                    text = tr("অফলাইন প্রস্তুত", "Offline Ready"),
                                                    color = Color(0xFF6EE7B7),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Top Right Action Icons: Scroll/Swipe, Marker Studio, Filmstrip, Navigator/Bookmark
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { onToggleScrollVertical(!scrollVertical) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (scrollVertical) Icons.Default.SwapVert else Icons.Default.SwapHoriz,
                                        contentDescription = "Scroll Mode",
                                        tint = Color(0xFF93C5FD),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { showStudioPenBar = !showStudioPenBar },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Annotation Studio",
                                        tint = if (showStudioPenBar) Color(0xFFFBBF24) else Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val stamp = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())
                                        onToggleBookmark(currentPageNo, "[P.$currentPageNo] $bookTitle ($stamp)")
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (isBookmarked) Color(0xFFFBBF24) else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { showNavigatorSheet = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Jump & Bookmarks",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Mockup 2: Floating Highlighter, Mint Marker, Sticky Pin & Cite APA Pill
                    AnimatedVisibility(visible = showStudioPenBar) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StudioToolChip(
                                    emoji = "🟡",
                                    label = tr("হাইলাইটার", "Highlighter"),
                                    onClick = {
                                        onAddPageAnnotation(currentPageNo, "🟡 Yellow Highlight", "গুরুত্বপূর্ণ উদ্ধৃতি — পৃষ্ঠা $currentPageNo", HighlightVisibility.GROUP)
                                        showAnnotationsDrawer = true
                                    }
                                )
                                StudioToolChip(
                                    emoji = "🟢",
                                    label = tr("মিন্ট মার্কার", "Mint Marker"),
                                    onClick = {
                                        onAddPageAnnotation(currentPageNo, "🟢 Mint Concept", "মূল তাত্ত্বিক ধারণা — পৃষ্ঠা $currentPageNo", HighlightVisibility.GROUP)
                                        showAnnotationsDrawer = true
                                    }
                                )
                                StudioToolChip(
                                    emoji = "📌",
                                    label = tr("স্টিকি পিন", "Sticky Pin"),
                                    onClick = { showAnnotationsDrawer = true }
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1D4ED8),
                                    modifier = Modifier.clickable {
                                        val apa = "$bookAuthor. $bookTitle (p. $currentPageNo)."
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("APA Citation", apa))
                                        citeCopiedToast = true
                                    }
                                ) {
                                    Text(
                                        text = "Cite APA",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (citeCopiedToast) {
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(2200L)
                            citeCopiedToast = false
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF065F46),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text(
                                text = tr("✓ APA সাইটেশন কপি হয়েছে!", "✓ APA Citation copied to clipboard!"),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // ================================================================
            // BOTTOM CONTROLS: MOCKUP 1 FLOATING ZEN PILL vs MOCKUP 3 FILMSTRIP + AUDIO DOCK
            // ================================================================
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 10.dp, end = 10.dp, bottom = 16.dp)
            ) {
                if (!showFilmstripDock) {
                    // Mockup 1: Sleek Translucent Floating Zen Capsule Pill (Zero Horizontal/Vertical Clipping)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF1E293B).copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        shadowElevation = 10.dp,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Text(
                                text = "$currentPageNo / $safeTotal",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.clickable { showFilmstripDock = true }
                            )

                            Box(Modifier.width(1.dp).height(15.dp).background(Color.White.copy(alpha = 0.22f)))

                            Text(
                                text = if (trimMargins) "✂️ +35%" else "✂️ Trim",
                                color = if (trimMargins) Color(0xFF6EE7B7) else Color(0xFFCBD5E1),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.clickable { onToggleTrimMargins(!trimMargins) }
                            )

                            Box(Modifier.width(1.dp).height(15.dp).background(Color.White.copy(alpha = 0.22f)))

                            val themeLabel = when (paperTheme) {
                                ReaderPaperTheme.PURE_WHITE -> "☀️ White"
                                ReaderPaperTheme.WARM_SEPIA -> "📜 Sepia"
                                ReaderPaperTheme.SOFT_MINT -> "🌿 Mint"
                                ReaderPaperTheme.AMOLED_NIGHT -> "🌙 OLED"
                            }
                            Text(
                                text = themeLabel,
                                color = Color(0xFFFDE68A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.clickable {
                                    val all = ReaderPaperTheme.entries
                                    val next = all[(all.indexOf(paperTheme) + 1) % all.size]
                                    onChangePaperTheme(next)
                                }
                            )

                            Box(Modifier.width(1.dp).height(15.dp).background(Color.White.copy(alpha = 0.22f)))

                            Text(
                                text = "📌 ${currentPageHighlights.size}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.clickable { showAnnotationsDrawer = true }
                            )

                            if (audioController != null) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.clickable { showFilmstripDock = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (audioController.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Audio",
                                            tint = Color(0xFF064E3B),
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable { audioController.togglePlayPause() }
                                        )
                                        Text(
                                            text = formatClockMs(audioController.currentPositionMs),
                                            color = Color(0xFF064E3B),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Mockup 3: Expanded Dark OLED Page Thumbnail Filmstrip + Scrubber + Mini Audio Bar
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF0B1120).copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        shadowElevation = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ReaderPaperTheme.entries.forEach { pt ->
                                        val sel = paperTheme == pt
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (sel) Color(0xFF10B981) else Color(0xFF1E293B),
                                            modifier = Modifier.clickable { onChangePaperTheme(pt) }
                                        ) {
                                            Text(
                                                text = when (pt) {
                                                    ReaderPaperTheme.PURE_WHITE -> "☀️ সাদা"
                                                    ReaderPaperTheme.WARM_SEPIA -> "📜 সেপিয়া"
                                                    ReaderPaperTheme.SOFT_MINT -> "🌿 মিন্ট"
                                                    ReaderPaperTheme.AMOLED_NIGHT -> "🌙 ডার্ক"
                                                },
                                                color = if (sel) Color(0xFF064E3B) else Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.width(6.dp))
                                IconButton(onClick = { showFilmstripDock = false }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Collapse", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                                }
                            }

                            // Thumbnail Filmstrip
                            val thumbListState = rememberLazyListState(initialFirstVisibleItemIndex = (currentPageIndex - 2).coerceAtLeast(0))
                            LaunchedEffect(currentPageIndex) {
                                thumbListState.animateScrollToItem((currentPageIndex - 2).coerceAtLeast(0))
                            }
                            LazyRow(
                                state = thumbListState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(safeTotal) { tIdx ->
                                    val selected = tIdx == currentPageIndex
                                    PdfThumbnailTile(
                                        context = context,
                                        customUri = customUri,
                                        cachedCloudFile = cachedCloudFile,
                                        pageIndex = tIdx,
                                        selected = selected,
                                        paperFilter = paperFilter,
                                        onClick = { jumpToPage(tIdx) }
                                    )
                                }
                            }

                            // Scrubber Slider
                            if (safeTotal > 1) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Slider(
                                        value = currentPageIndex.toFloat(),
                                        onValueChange = { jumpToPage(it.roundToInt()) },
                                        valueRange = 0f..(safeTotal - 1).toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF10B981),
                                            activeTrackColor = Color(0xFF10B981)
                                        ),
                                        modifier = Modifier.weight(1f).height(24.dp)
                                    )
                                    Text(
                                        text = "$currentPageNo/$safeTotal",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Docked Mini Audio Summary Bar
                            if (audioController != null) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF0F292E),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            IconButton(
                                                onClick = { audioController.togglePlayPause() },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF10B981))
                                            ) {
                                                Icon(
                                                    imageVector = if (audioController.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = Color(0xFF064E3B),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tr("🎧 অডিও সামারি", "🎧 Audio Summary"),
                                                    color = Color.White,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${formatClockMs(audioController.currentPositionMs)} / ${formatClockMs(audioController.durationMs)}",
                                                    color = Color(0xFF6EE7B7),
                                                    fontSize = 10.5.sp,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "-10s",
                                                color = Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(alpha = 0.12f))
                                                    .clickable { audioController.skipSeconds(-10) }
                                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                                            )
                                            Text(
                                                text = "+10s",
                                                color = Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(alpha = 0.12f))
                                                    .clickable { audioController.skipSeconds(10) }
                                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                                            )
                                            Text(
                                                text = "${audioController.speed}x",
                                                color = Color(0xFFFDE68A),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(alpha = 0.12f))
                                                    .clickable { audioController.cyclePlaybackSpeed() }
                                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================================================================
            // MOCKUP 2 DRAWER: PAGE ANNOTATIONS (Private / Mentor / Study Circle)
            // ================================================================
            AnimatedVisibility(
                visible = showAnnotationsDrawer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
            ) {
                PageAnnotationsSheet(
                    pageNo = currentPageNo,
                    highlights = currentPageHighlights,
                    onAddNote = { noteBody, vis ->
                        onAddPageAnnotation(currentPageNo, "📌 Sticky Note (P.$currentPageNo)", noteBody, vis)
                    },
                    onClose = { showAnnotationsDrawer = false }
                )
            }

            // ================================================================
            // MOCKUP 4 DRAWER: GO-TO-PAGE + CHAPTERS / BOOKMARKS / HIGHLIGHTS
            // ================================================================
            if (showNavigatorSheet) {
                QuickJumpAndBookmarksSheet(
                    currentPageNo = currentPageNo,
                    totalPages = safeTotal,
                    chapters = chapters,
                    bookmarks = bookmarks,
                    allHighlights = bookHighlights,
                    onJumpToPage = { targetPageNo ->
                        showNavigatorSheet = false
                        jumpToPage(targetPageNo - 1)
                    },
                    onAddBookmarkHere = { customLabel ->
                        onToggleBookmark(currentPageNo, customLabel.ifBlank { "[P.$currentPageNo] $bookTitle" })
                    },
                    onRemoveBookmark = onRemoveBookmark,
                    onDismiss = { showNavigatorSheet = false }
                )
            }
        }
    }
}

@Composable
private fun SinglePdfPageSurface(
    context: Context,
    customUri: Uri?,
    cachedCloudFile: File?,
    pageIndex: Int,
    trimMargins: Boolean,
    paperTheme: ReaderPaperTheme,
    paperFilter: ColorFilter,
    pinCount: Int,
    fillViewportHeight: Boolean,
    onSingleTap: () -> Unit,
    onOpenPins: () -> Unit
) {
    var bmp by remember(customUri, cachedCloudFile, pageIndex, trimMargins) { mutableStateOf<Bitmap?>(null) }
    var zoomScale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var panX by remember(pageIndex) { mutableFloatStateOf(0f) }
    var panY by remember(pageIndex) { mutableFloatStateOf(0f) }

    LaunchedEffect(customUri, cachedCloudFile, pageIndex, trimMargins) {
        val loaded = renderPdfPageCached(
            context = context,
            customUri = customUri,
            cachedCloudFile = cachedCloudFile,
            requestedPage = pageIndex,
            trimMargins = trimMargins,
            isThumbnail = false
        )
        bmp = loaded?.first
    }

    val boxModifier = if (fillViewportHeight) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(min = 480.dp, max = 680.dp)
            .clip(RoundedCornerShape(14.dp))
            .shadow(4.dp, RoundedCornerShape(14.dp))
    }

    Box(
        modifier = boxModifier
            .background(Color(paperTheme.bgHex))
            .pdfZoomPanGesture(
                zoomScale = zoomScale,
                onZoomPan = { newScale, dx, dy ->
                    zoomScale = newScale
                    if (newScale > 1.02f) {
                        panX = (panX + dx).coerceIn(-650f, 650f)
                        panY = (panY + dy).coerceIn(-650f, 650f)
                    } else {
                        panX = 0f
                        panY = 0f
                    }
                }
            )
            .pointerInput(zoomScale) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        if (zoomScale > 1.2f) {
                            zoomScale = 1f
                            panX = 0f
                            panY = 0f
                        } else {
                            zoomScale = 2.25f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val currentBmp = bmp
        if (currentBmp != null) {
            Image(
                bitmap = currentBmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                colorFilter = paperFilter,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (trimMargins) 4.dp else 10.dp, vertical = if (fillViewportHeight) 44.dp else 6.dp)
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = panX,
                        translationY = panY
                    )
            )
        } else {
            CircularProgressIndicator(color = Color(0xFF10B981))
        }

        if (pinCount > 0) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFF59E0B),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .clickable { onOpenPins() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.PushPin, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text("$pinCount", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
private fun PdfThumbnailTile(
    context: Context,
    customUri: Uri?,
    cachedCloudFile: File?,
    pageIndex: Int,
    selected: Boolean,
    paperFilter: ColorFilter,
    onClick: () -> Unit
) {
    var thumb by remember(customUri, cachedCloudFile, pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(customUri, cachedCloudFile, pageIndex) {
        thumb = renderPdfPageCached(
            context = context,
            customUri = customUri,
            cachedCloudFile = cachedCloudFile,
            requestedPage = pageIndex,
            trimMargins = false,
            isThumbnail = true
        )?.first
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E293B))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color(0xFF10B981) else Color(0xFF334155),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val b = thumb
            if (b != null) {
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = null,
                    colorFilter = paperFilter,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("${pageIndex + 1}", color = Color(0xFF94A3B8), fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = "${pageIndex + 1}",
            color = if (selected) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
        )
    }
}

@Composable
private fun StudioToolChip(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(emoji, fontSize = 15.sp)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
    }
}

@Composable
private fun PageAnnotationsSheet(
    pageNo: Int,
    highlights: List<ReadingHighlight>,
    onAddNote: (String, HighlightVisibility) -> Unit,
    onClose: () -> Unit
) {
    var filterVis by remember { mutableStateOf<HighlightVisibility?>(null) }
    var draftNote by remember { mutableStateOf("") }
    var postVis by remember { mutableStateOf(HighlightVisibility.GROUP) }

    val filtered = remember(highlights, filterVis) {
        if (filterVis == null) highlights else highlights.filter { it.visibility == filterVis }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = Color.White,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 390.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("📌 পৃষ্ঠা $pageNo নোটসমূহ (Study Circle)", "📌 Page $pageNo Annotations"),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp,
                    color = Color(0xFF0F172A)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF475569))
                }
            }

            // Filter Pills: All / Private / Mentor / Group
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val tabs = listOf(
                    null to tr("সব (${highlights.size})", "All (${highlights.size})"),
                    HighlightVisibility.PRIVATE to tr("ব্যক্তিগত", "Private"),
                    HighlightVisibility.SUPERVISOR to tr("মেন্টর", "Mentor"),
                    HighlightVisibility.GROUP to tr("সার্কেল গ্রুপ", "Group")
                )
                tabs.forEach { (vis, label) ->
                    val sel = filterVis == vis
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (sel) Color(0xFF1D4ED8) else Color(0xFFF1F5F9),
                        modifier = Modifier.clickable { filterVis = vis }
                    ) {
                        Text(
                            text = label,
                            color = if (sel) Color.White else Color(0xFF334155),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = tr("এই পৃষ্ঠায় এখনো কোনো নোট নেই। নিচে প্রথম নোট লিখুন!", "No notes on this page yet. Add the first note below!"),
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { h ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(h.userName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                    Text(h.visibility.labelBn, fontSize = 10.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    text = h.note.ifBlank { h.highlightedText },
                                    fontSize = 12.sp,
                                    color = Color(0xFF334155)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Add Sticky Note Input
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draftNote,
                    onValueChange = { draftNote = it },
                    placeholder = { Text(tr("এই পৃষ্ঠার নোট লিখুন (গ্রুপে শেয়ারযোগ্য)...", "Add a note on Page $pageNo..."), fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    enabled = draftNote.isNotBlank(),
                    onClick = {
                        onAddNote(draftNote.trim(), postVis)
                        draftNote = ""
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
                ) {
                    Text(tr("পিন +", "Pin +"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickJumpAndBookmarksSheet(
    currentPageNo: Int,
    totalPages: Int,
    chapters: List<ChapterItem>,
    bookmarks: List<PdfBookmarkItem>,
    allHighlights: List<ReadingHighlight>,
    onJumpToPage: (Int) -> Unit,
    onAddBookmarkHere: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pageInput by remember { mutableStateOf(currentPageNo.toString()) }
    var bookmarkTitleInput by remember { mutableStateOf("") }
    var activeTab by remember { mutableIntStateOf(1) } // 0 = Chapters, 1 = Bookmarks, 2 = Notes

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        contentColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mockup 4: Go to Page [ 18 ] Bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = tr("🔢 পৃষ্ঠায় যান:", "🔢 Go to Page:"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { pageInput = it.filter { ch -> ch.isDigit() }.take(4) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.width(90.dp)
                    )
                    Text("/ $totalPages", fontSize = 12.sp, color = Color(0xFF64748B))
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            val pNo = pageInput.toIntOrNull()?.coerceIn(1, totalPages) ?: currentPageNo
                            onJumpToPage(pNo)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Text(tr("যান ›", "Jump ›"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Segmented Tabs: Chapters | Bookmarks | Highlights
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        tr("অধ্যায় (${chapters.size})", "Chapters (${chapters.size})"),
                        tr("বুকমার্ক (${bookmarks.size})", "Bookmarks (${bookmarks.size})"),
                        tr("নোট (${allHighlights.size})", "Notes (${allHighlights.size})")
                    ).forEachIndexed { idx, title ->
                        val sel = activeTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) Color.White else Color.Transparent)
                                .clickable { activeTab = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 11.5.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }
            }

            if (activeTab == 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = bookmarkTitleInput,
                        onValueChange = { bookmarkTitleInput = it },
                        placeholder = { Text(tr("পৃষ্ঠা $currentPageNo বুকমার্কের নাম...", "Bookmark title for P.$currentPageNo..."), fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onAddBookmarkHere(bookmarkTitleInput.trim())
                            bookmarkTitleInput = ""
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                    ) {
                        Text(tr("🔖 সেভ", "🔖 Save"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                when (activeTab) {
                    0 -> {
                        var runningPage = 1
                        items(chapters.size) { cIdx ->
                            val ch = chapters[cIdx]
                            val startP = runningPage.coerceAtMost(totalPages)
                            runningPage += ch.totalPages.coerceAtLeast(1)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onJumpToPage(startP) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${ch.chapterNo}. ${ch.title}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("P.$startP", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color(0xFF059669))
                                }
                            }
                        }
                    }
                    1 -> {
                        if (bookmarks.isEmpty()) {
                            item {
                                Text(
                                    tr("এখনো কোনো বুকমার্ক যোগ করা হয়নি।", "No bookmarks saved yet."),
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        } else {
                            items(bookmarks, key = { it.id }) { bm ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onJumpToPage(bm.pageNo) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(bm.label, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color(0xFF0F172A))
                                            if (bm.createdAtLabel.isNotBlank()) {
                                                Text(bm.createdAtLabel, fontSize = 10.5.sp, color = Color(0xFF64748B))
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("P.${bm.pageNo}", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFFD97706))
                                            IconButton(onClick = { onRemoveBookmark(bm.id) }, modifier = Modifier.size(26.dp)) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = SparcCrimson, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (allHighlights.isEmpty()) {
                            item {
                                Text(
                                    tr("এখনো কোনো পৃষ্ঠা নোট সংরক্ষিত নেই।", "No page highlights or notes yet."),
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        } else {
                            items(allHighlights, key = { it.id }) { hl ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onJumpToPage(hl.pageNo.coerceAtLeast(1)) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(hl.note.ifBlank { hl.highlightedText }, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2)
                                            Text("${hl.userName} • ${hl.visibility.labelBn}", fontSize = 10.5.sp, color = Color(0xFF64748B))
                                        }
                                        Text("P.${hl.pageNo}", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFF2563EB))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gesture modifier that passes 1-finger swipe/scroll straight through to Pager/LazyColumn when
 * `zoomScale <= 1.02f`, while handling 2-finger pinch-zoom at all times and 1-finger pan when zoomed!
 */
private fun Modifier.pdfZoomPanGesture(
    zoomScale: Float,
    onZoomPan: (newScale: Float, dx: Float, dy: Float) -> Unit
): Modifier = this.pointerInput(zoomScale) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var currentScale = zoomScale
        do {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2 || currentScale > 1.02f) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (abs(zoomChange - 1f) > 0.005f || abs(panChange.x) > 0.5f || abs(panChange.y) > 0.5f) {
                    currentScale = (currentScale * zoomChange).coerceIn(1f, 3.5f)
                    onZoomPan(currentScale, panChange.x, panChange.y)
                    event.changes.forEach { ch ->
                        if (ch.positionChanged()) ch.consume()
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

private fun buildPdfPaperColorFilter(theme: ReaderPaperTheme): ColorFilter {
    val matrix = when (theme) {
        ReaderPaperTheme.PURE_WHITE -> ColorMatrix(
            floatArrayOf(
                1.05f, 0f, 0f, 0f, -6f,
                0f, 1.05f, 0f, 0f, -6f,
                0f, 0f, 1.05f, 0f, -6f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        ReaderPaperTheme.WARM_SEPIA -> ColorMatrix(
            floatArrayOf(
                0.82f, 0f, 0f, 0f, 38f,
                0f, 0.83f, 0f, 0f, 28f,
                0f, 0f, 0.80f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        ReaderPaperTheme.SOFT_MINT -> ColorMatrix(
            floatArrayOf(
                0.89f, 0f, 0f, 0f, 8f,
                0f, 0.81f, 0f, 0f, 46f,
                0f, 0f, 0.82f, 0f, 36f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        ReaderPaperTheme.AMOLED_NIGHT -> ColorMatrix(
            floatArrayOf(
                -0.827f, 0f, 0f, 0f, 226f,
                0f, -0.820f, 0f, 0f, 232f,
                0f, 0f, -0.776f, 0f, 240f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
    return ColorFilter.colorMatrix(matrix)
}

private fun formatClockMs(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(sec / 60L, sec % 60L)
}

private suspend fun renderPdfPageCached(
    context: Context,
    customUri: Uri?,
    cachedCloudFile: File?,
    requestedPage: Int,
    trimMargins: Boolean,
    isThumbnail: Boolean
): Pair<Bitmap, Int>? = withContext(Dispatchers.IO) {
    val docKey = customUri?.toString()
        ?: cachedCloudFile?.absolutePath
        ?: "sample_cpar_pdf"
    val cacheKey = "$docKey|p=$requestedPage|trim=$trimMargins|thumb=$isThumbnail"
    val countKey = "$docKey|count"

    PdfRenderMutex.withLock {
        val cachedBmp = PdfBitmapCache.get(cacheKey)
        if (cachedBmp != null && !cachedBmp.isRecycled) {
            val knownCount = PdfCountRegistry[countKey] ?: 6
            return@withLock cachedBmp to knownCount
        }

        runCatching {
            val pfd: ParcelFileDescriptor = when {
                customUri != null ->
                    context.contentResolver.openFileDescriptor(customUri, "r") ?: return@runCatching null
                cachedCloudFile != null && cachedCloudFile.exists() && cachedCloudFile.length() > 512L ->
                    ParcelFileDescriptor.open(cachedCloudFile, ParcelFileDescriptor.MODE_READ_ONLY)
                else -> {
                    val samplePdf = ensureSampleAcademicPdf(context)
                    ParcelFileDescriptor.open(samplePdf, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }

            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val count = renderer.pageCount.coerceAtLeast(1)
                    PdfCountRegistry[countKey] = count
                    val safeIndex = requestedPage.coerceIn(0, count - 1)
                    renderer.openPage(safeIndex).use { page ->
                        val scale = if (isThumbnail) 0.35f else 2.35f
                        val bmpWidth = (page.width * scale).roundToInt().coerceIn(if (isThumbnail) 140 else 900, 2200)
                        val bmpHeight = (page.height * scale).roundToInt().coerceIn(if (isThumbnail) 190 else 1240, 3000)
                        val rawBmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(rawBmp)
                        canvas.drawColor(AndroidColor.WHITE)
                        page.render(rawBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val finalBmp = if (trimMargins && !isThumbnail) {
                            trimPdfWhitespace(rawBmp)
                        } else {
                            rawBmp
                        }
                        PdfBitmapCache.put(cacheKey, finalBmp)
                        finalBmp to count
                    }
                }
            }
        }.getOrNull()
    }
}

private val PdfCountRegistry = java.util.concurrent.ConcurrentHashMap<String, Int>()

/**
 * Automatically detects and trims outer empty white margins (clamped safely to <= 22% per side)
 * so academic book/journal text appears ~35% larger on phone screens without manual zooming.
 */
private fun trimPdfWhitespace(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    if (w < 200 || h < 200) return src

    var minX = w
    var maxX = 0
    var minY = h
    var maxY = 0
    val step = 6

    var y = 8
    while (y < h - 8) {
        var x = 8
        while (x < w - 8) {
            val px = src.getPixel(x, y)
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            if (r < 242 || g < 242 || b < 242) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            x += step
        }
        y += step
    }

    if (maxX <= minX + 80 || maxY <= minY + 80) return src

    val padX = (w * 0.025f).roundToInt()
    val padY = (h * 0.022f).roundToInt()
    val maxCropX = (w * 0.22f).roundToInt()
    val maxCropY = (h * 0.20f).roundToInt()

    val left = (minX - padX).coerceIn(0, maxCropX)
    val top = (minY - padY).coerceIn(0, maxCropY)
    val right = (maxX + padX).coerceIn(w - maxCropX, w)
    val bottom = (maxY + padY).coerceIn(h - maxCropY, h)

    val cropW = (right - left).coerceAtLeast(w / 2)
    val cropH = (bottom - top).coerceAtLeast(h / 2)
    if (cropW >= w - 12 && cropH >= h - 12) return src

    return runCatching {
        Bitmap.createBitmap(src, left, top, cropW, cropH)
    }.getOrDefault(src)
}

private fun ensureSampleAcademicPdf(context: Context): File {
    val outFile = File(context.cacheDir, "sparc_cpar_ch1_sample.pdf")
    if (outFile.exists() && outFile.length() > 1024) return outFile

    val doc = PdfDocument()
    val pageTitles = listOf(
        "Chapter 1: Critical Participatory Action Research (CPAR)",
        "1.1 Traditional Action Research vs. CPAR",
        "1.2 Opening Communicative Space & Public Spheres",
        "1.3 Practice Architectures: Sayings, Doings & Relatings",
        "1.4 Identifying a Shared Felt-Concern at Your Site",
        "Chapter 1 Key Takeaways & Proposal Preparation"
    )
    val pageBodies = listOf(
        listOf(
            "SPARC Study Circle — Licensed First Chapter Trial",
            "Authors: Stephen Kemmis, Robin McTaggart & Rhonda Nixon",
            "",
            "Core Philosophy: 'Learning Academic Writing While Reading'.",
            "In CPAR, participants are not passive subjects studied by external",
            "experts; instead, practitioners collectively investigate and transform",
            "their own educational and social practices."
        ),
        listOf(
            "Why 'Critical' Participatory Action Research?",
            "",
            "1. Technical Action Research aims solely at improving outcomes.",
            "2. Practical Action Research informs wise practitioner judgment.",
            "3. Critical Participatory Action Research examines whether existing",
            "   practices are untoward: irrational, unsustainable, or unjust."
        ),
        listOf(
            "Habermas & Communicative Action in Study Circles:",
            "",
            "Before collecting field data, CPAR researchers establish an inclusive",
            "'Communicative Space' where students, teachers, and stakeholders",
            "can speak freely without hierarchical fear or coercion.",
            "Mutual understanding and unforced consensus guide every decision."
        ),
        listOf(
            "HIGHLIGHTED CONCEPT (Practice Architectures):",
            "\"Sayings, doings and relatings hang together in the project",
            "of a practice.\"",
            "",
            "• Cultural-discursive arrangements -> shape SAYINGS (language/ideas)",
            "• Material-economic arrangements   -> shape DOINGS (activities/space)",
            "• Social-political arrangements    -> shape RELATINGS (power/solidarity)"
        ),
        listOf(
            "Preparing Your Chapter 1 Writing Task (500–700 Words):",
            "",
            "Select a real classroom, department, or community setting.",
            "Ask three diagnostic questions:",
            "1. What is happening here currently? (Sayings, Doings, Relatings)",
            "2. Why is this situation a shared 'Felt-concern'?",
            "3. Who will join your Public Sphere to investigate it?"
        ),
        listOf(
            "Ready for Stage 4 -> Stage 5 Assessment!",
            "",
            "• Complete all 4 section checkboxes in the Assessment tab.",
            "• Declare 'Reading Completed' to unlock the 36-question Long Quiz.",
            "• Submit answers to unlock the Answer Key & 1–5 Self-Rating.",
            "• Draft your 500–700 word Felt-Concern statement for Mentor review."
        )
    )

    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#1D4ED8")
        textSize = 13f
        isFakeBoldText = true
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#0F172A")
        textSize = 19f
        isFakeBoldText = true
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#334155")
        textSize = 14f
    }
    val highlightBoxPaint = Paint().apply {
        color = AndroidColor.parseColor("#FEF3C7")
    }

    pageTitles.forEachIndexed { idx, heading ->
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, idx + 1).create()
        val page = doc.startPage(pageInfo)
        val c = page.canvas
        c.drawColor(AndroidColor.parseColor("#FCFBF8"))

        c.drawText("SPARC STUDY CIRCLE • CHAPTER 1 FREE TRIAL", 48f, 52f, headerPaint)
        c.drawLine(48f, 64f, 547f, 64f, headerPaint)
        c.drawText(heading, 48f, 108f, titlePaint)

        if (idx == 3) {
            c.drawRect(44f, 136f, 550f, 220f, highlightBoxPaint)
        }

        var y = 156f
        pageBodies[idx].forEach { line ->
            c.drawText(line, 48f, y, bodyPaint)
            y += 28f
        }

        c.drawLine(48f, 780f, 547f, 780f, bodyPaint)
        c.drawText("Page ${idx + 1} of ${pageTitles.size} • sparc.org.bd", 48f, 804f, bodyPaint)
        doc.finishPage(page)
    }

    FileOutputStream(outFile).use { doc.writeTo(it) }
    doc.close()
    return outFile
}
