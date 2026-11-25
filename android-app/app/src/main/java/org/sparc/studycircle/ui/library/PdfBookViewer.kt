package org.sparc.studycircle.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.ui.components.SparcScholarlyCard
import java.io.File
import java.io.FileOutputStream

@Composable
fun NativePdfBookViewerCard(
    bookTitle: String,
    onPageChanged: (currentPage: Int, totalPages: Int) -> Unit = { _, _ -> },
    onQuickHighlightPage: (pageNo: Int) -> Unit = {}
) {
    val context = LocalContext.current
    val p = LocalSparcPalette.current

    var customPdfUri by remember { mutableStateOf<Uri?>(null) }
    var customPdfName by remember { mutableStateOf<String?>(null) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var totalPageCount by remember { mutableIntStateOf(6) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

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

    LaunchedEffect(customPdfUri, pageIndex) {
        val result = withContext(Dispatchers.IO) {
            renderPdfPageBitmap(context, customPdfUri, pageIndex)
        }
        if (result != null) {
            renderedBitmap = result.first
            totalPageCount = result.second
            onPageChanged(pageIndex + 1, result.second)
        }
    }

    SparcScholarlyCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customPdfName ?: "নেটিভ PDF রিডার • $bookTitle",
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text,
                    maxLines = 1
                )
                Text(
                    text = "পৃষ্ঠা ${pageIndex + 1} / $totalPageCount • Pinch-to-Zoom সাপোর্টেড",
                    fontSize = 11.5.sp,
                    color = p.muted
                )
            }

            // Open Any Real PDF File from Device Storage!
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = p.primarySoft,
                modifier = Modifier.clickable {
                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Open PDF",
                        tint = p.primarySoftText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "PDF খুলুন",
                        color = p.primarySoftText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Rendered Real PDF Page Canvas with Pinch-to-Zoom & Pan
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFE2E8F0))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (zoomScale * zoom).coerceIn(1f, 3f)
                        zoomScale = newScale
                        if (newScale > 1f) {
                            panOffsetX = (panOffsetX + pan.x).coerceIn(-300f, 300f)
                            panOffsetY = (panOffsetY + pan.y).coerceIn(-300f, 300f)
                        } else {
                            panOffsetX = 0f
                            panOffsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = renderedBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
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
        }

        // Page Navigation + Zoom Cycle + Quick Highlight Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalIconButton(
                    onClick = { if (pageIndex > 0) pageIndex-- },
                    enabled = pageIndex > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
                }
                Text(
                    text = "${pageIndex + 1} / $totalPageCount",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = p.text
                )
                FilledTonalIconButton(
                    onClick = { if (pageIndex < totalPageCount - 1) pageIndex++ },
                    enabled = pageIndex < totalPageCount - 1
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
                        zoomScale = when (zoomScale) {
                            1f -> 1.5f
                            1.5f -> 2f
                            else -> 1f
                        }
                        if (zoomScale == 1f) {
                            panOffsetX = 0f
                            panOffsetY = 0f
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = null, tint = p.text, modifier = Modifier.size(15.dp))
                        Text("${zoomScale}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.text)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = p.accentSoft,
                    modifier = Modifier.clickable { onQuickHighlightPage(pageIndex + 1) }
                ) {
                    Text(
                        text = "হাইলাইট +",
                        color = p.accentText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

private fun renderPdfPageBitmap(
    context: Context,
    customUri: Uri?,
    requestedPage: Int
): Pair<Bitmap, Int>? {
    val pfd: ParcelFileDescriptor = if (customUri != null) {
        context.contentResolver.openFileDescriptor(customUri, "r") ?: return null
    } else {
        val samplePdf = ensureSampleAcademicPdf(context)
        ParcelFileDescriptor.open(samplePdf, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    pfd.use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val count = renderer.pageCount.coerceAtLeast(1)
            val safeIndex = requestedPage.coerceIn(0, count - 1)
            renderer.openPage(safeIndex).use { page ->
                val bmpWidth = (page.width * 2).coerceAtLeast(800)
                val bmpHeight = (page.height * 2).coerceAtLeast(1100)
                val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(AndroidColor.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp to count
            }
        }
    }
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
            "• Draft your 500–700 word Felt-Concern statement with Gemini AI."
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
