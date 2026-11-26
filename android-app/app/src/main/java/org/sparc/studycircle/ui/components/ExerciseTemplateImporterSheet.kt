package org.sparc.studycircle.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.ExerciseTemplateParser
import org.sparc.studycircle.core.model.BookItem
import org.sparc.studycircle.core.theme.LocalAppLanguage
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseTemplateImporterSheet(
    book: BookItem,
    initialChapterNo: Int = 1,
    repository: SparcRepository,
    onDismiss: () -> Unit
) {
    val p = LocalSparcPalette.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var chapterNo by remember { mutableIntStateOf(initialChapterNo.coerceAtLeast(1)) }
    var rawText by remember { mutableStateOf("") }
    var replaceExisting by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf<String?>(null) }

    val sampleTemplate = remember(lang) { ExerciseTemplateParser.generateSampleTemplate(lang) }
    val liveParsed = remember(rawText) {
        if (rawText.isBlank()) null else ExerciseTemplateParser.parse(rawText).getOrNull()
    }

    val saveSampleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(sampleTemplate.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
                statusNote = if (lang == "en") "✓ Sample template saved to device!" else "✓ নমুনা টেমপ্লেট ফোনে সেভ হয়েছে!"
            }
        }
    }

    val openTemplateFileLauncher = rememberLauncherForActivityResult(
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
                    rawText = loaded
                    statusNote = if (lang == "en") "✓ Template file loaded!" else "✓ টেমপ্লেট ফাইল লোড হয়েছে!"
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
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                tr("📋 নির্ধারিত টেমপ্লেটে অনুশীলনী আপলোড", "📋 Upload Exercises via Standard Template"),
                style = MaterialTheme.typography.titleLarge,
                color = p.text
            )
            Text(
                "${book.title} • ${book.author}",
                fontSize = 12.sp,
                color = p.primary,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tr("অধ্যায় নম্বর:", "Target Chapter:"), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { chapterNo = (chapterNo - 1).coerceAtLeast(1) }) { Text("-") }
                    Text("Ch $chapterNo", fontWeight = FontWeight.ExtraBold, color = p.primary)
                    OutlinedButton(onClick = { chapterNo += 1 }) { Text("+") }
                }
            }

            // 1-Click Sample Template Copy + Download Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = p.primarySoft,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("SPARC Exercise Template", sampleTemplate))
                            if (rawText.isBlank()) rawText = sampleTemplate
                            statusNote = if (lang == "en") {
                                "✓ Sample template copied & filled below!"
                            } else {
                                "✓ নমুনা টেমপ্লেট কপি হয়েছে এবং নিচের বক্সে বসানো হয়েছে!"
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = p.primarySoftText, modifier = Modifier.size(15.dp))
                        Text(
                            tr("নমুনা কপি করুন", "Copy Sample"),
                            color = p.primarySoftText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = p.cardAlt,
                    border = BorderStroke(1.dp, p.border),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { saveSampleLauncher.launch("SPARC_Exercise_Template.txt") }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = p.text, modifier = Modifier.size(15.dp))
                        Text(
                            tr("নমুনা .txt সেভ", "Save Sample .txt"),
                            color = p.text,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Pack 3: 1-Click AI Exercise Generator Prompt Copy Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = p.accentSoft,
                border = BorderStroke(1.dp, p.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val promptText = ExerciseTemplateParser.generateAiPrompt(lang, chapterNo, book.title)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("SPARC AI Exercise Prompt", promptText))
                        statusNote = if (lang == "en") {
                            "🤖 AI Prompt copied! Paste it with your PDF into any AI, then paste the generated exercises below."
                        } else {
                            "🤖 AI প্রম্পট কপি হয়েছে! যেকোনো AI-তে আপনার PDF-সহ পেস্ট করে তৈরি হওয়া প্রশ্ন নিচে বসিয়ে দিন।"
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        tr(
                            "🤖 ১-ক্লিকে AI অনুশীলনী প্রম্পট কপি করুন (PDF থেকে অটো প্রশ্ন তৈরি)",
                            "🤖 1-Click Copy AI Prompt (Generate Exercises from PDF)"
                        ),
                        color = p.accentText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = p.accentText, modifier = Modifier.size(16.dp))
                }
            }

            // Upload .txt / .csv / .json File from Phone
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = p.cardAlt,
                border = BorderStroke(1.dp, p.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        openTemplateFileLauncher.launch(arrayOf("text/*", "application/json", "*/*"))
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = p.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tr("ফোন থেকে পূরণকৃত টেমপ্লেট ফাইল (.txt / .csv / .json) আপলোড করুন", "Select Filled Template File (.txt / .csv / .json)"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = p.text
                        )
                        Text(
                            tr("অথবা নিচে সরাসরি পেস্ট/এডিট করুন", "Or paste/edit the template text directly below"),
                            fontSize = 11.sp,
                            color = p.muted
                        )
                    }
                }
            }

            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                label = { Text(tr("অনুশীলনী টেমপ্লেট টেক্সট (.txt / CSV / JSON)", "Exercise Template Content")) },
                placeholder = { Text("CHAPTER_TITLE: ...\n[MCQ | C]\nQ: ...\nOPT*: ...") },
                minLines = 6,
                maxLines = 10,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (liveParsed != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = p.primarySoft, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        tr(
                            "✓ প্রস্তুত: ${liveParsed.questions.size}টি অনুশীলনী প্রশ্ন ও ${liveParsed.flashNotes.size}টি ফ্ল্যাশ নোট শনাক্ত হয়েছে",
                            "✓ Ready: ${liveParsed.questions.size} questions & ${liveParsed.flashNotes.size} flash notes detected"
                        ),
                        color = p.primarySoftText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            statusNote?.let { note ->
                Text(note, fontSize = 12.sp, color = p.primary, fontWeight = FontWeight.SemiBold)
            }

            SparcSegmentedPills(
                options = listOf(
                    tr("বিদ্যমান প্রশ্নের সাথে যুক্ত করুন", "Append to Existing"),
                    tr("পুরনো প্রশ্ন প্রতিস্থাপন করুন", "Replace Old Set")
                ),
                selectedIndex = if (replaceExisting) 1 else 0,
                onSelect = { replaceExisting = (it == 1) }
            )

            SparcPrimaryButton(
                text = tr("অনুশীলনী ইমপোর্ট ও লাইভ সিঙ্ক করুন", "Import Exercises & Sync Live"),
                enabled = liveParsed != null,
                onClick = {
                    val res = repository.importExerciseTemplate(
                        bookId = book.id,
                        overrideChapterNo = chapterNo,
                        rawTemplate = rawText,
                        replaceExistingQuestions = replaceExisting
                    )
                    res.onSuccess { onDismiss() }
                        .onFailure { statusNote = it.message }
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
