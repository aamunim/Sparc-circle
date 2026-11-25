package org.sparc.studycircle.ui.assessment

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.*

@Composable
fun AssessmentAndSessionScreen(
    state: SparcAppState,
    repository: SparcRepository
) {
    val context = LocalContext.current
    val book = state.books.first()
    val chapter = book.chapters.first()
    val circle = state.circles.first()
    val progress = state.myChapterProgress[chapter.chapterNo] ?: ChapterProgressState(
        id = "init",
        circleId = circle.id,
        userId = state.currentUser.id,
        chapterNo = chapter.chapterNo
    )

    val groupThreshold = SparcDomainEngine.evaluateGroupSessionTrigger(
        totalMembers = circle.memberIds.size,
        memberStages = circle.memberIds.map { state.memberStageByUserId[it] ?: ProgressStage.NOT_STARTED },
        daysSinceLastSession = circle.daysSinceLastSession
    )

    var draftAnswers by remember(progress.submittedAnswers) {
        mutableStateOf(progress.submittedAnswers)
    }
    var draftRatings by remember(progress.selfRatings) {
        mutableStateOf(
            if (progress.selfRatings.size == chapter.learningOutcomes.size) progress.selfRatings
            else List(chapter.learningOutcomes.size) { 4 }
        )
    }
    var activeJitsiSession by remember { mutableStateOf<OnlineSessionItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 80% / 10-Day Group Session Threshold Banner (Section 5.4 & 7.2)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("দলীয় সেশন প্রোগ্রেশন (লক্ষ্যমাত্রা ৮০% বা ১০ দিন)", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { (groupThreshold.completionRatePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "অনুশীলনী জমা দিয়েছেন: ${groupThreshold.completionRatePercent.toInt()}% সদস্য • আগের সেশন: ${circle.daysSinceLastSession} দিন আগে",
                        style = MaterialTheme.typography.labelMedium
                    )
                    groupThreshold.triggerReasonBn?.let { reason ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(reason, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 6-Stage State Machine Card (Section 5.1)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "৬-ধাপের শিখন স্ট্যাটাস: ধাপ ${progress.stage.step}/৬ — ${progress.stage.labelBn}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "অধ্যায়ের ধরন: ${chapter.type.name} (${chapter.type.totalQuestions}টি প্রশ্ন • মোট ${chapter.type.totalMarks} • পাস ${chapter.type.passMarks} • রেড-ফ্ল্যাগ <${chapter.type.redFlagMarks})",
                        style = MaterialTheme.typography.labelMedium
                    )

                    if (progress.expectedCompletionDaysFromToday < 0 &&
                        progress.stage.step < ProgressStage.EXERCISES_SUBMITTED.step
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "💛 ${SparcDomainEngine.COMPASSIONATE_OVERDUE_MESSAGE}",
                                modifier = Modifier.padding(10.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (progress.stage == ProgressStage.NOT_STARTED) {
                            Button(onClick = { repository.startChapterReading(chapter, targetDays = 7) }) {
                                Text("Start Reading ঘোষণা দিন")
                            }
                        }
                        if (SparcDomainEngine.canModifyExpectedDate(progress) &&
                            progress.stage != ProgressStage.NOT_STARTED
                        ) {
                            OutlinedButton(onClick = {
                                repository.modifyExpectedDateOnce(chapter.chapterNo, progress.expectedCompletionDaysFromToday + 3)
                            }) {
                                Text("সম্ভাব্য তারিখ +৩ দিন বাড়ান (১ বার)")
                            }
                        }
                    }

                    // Stage 3: Section Checkboxes
                    Text("অধ্যায়ের সেকশন চেকলিস্ট:", style = MaterialTheme.typography.labelMedium)
                    chapter.sections.forEach { sec ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = sec.id in progress.checkedSectionIds,
                                onCheckedChange = { repository.toggleChapterSection(chapter, sec.id) },
                                enabled = progress.stage.step < ProgressStage.EXERCISES_SUBMITTED.step
                            )
                            Text(sec.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (progress.stage.step < ProgressStage.READING_COMPLETED.step) {
                        Button(
                            onClick = { repository.declareReadingCompleted(chapter) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("আমি এই অধ্যায় পড়া শেষ করেছি (Exercises আনলক করুন)")
                        }
                    }
                }
            }
        }

        // Stage 4 -> 5: Exercises (Auto A-E + Supervisor F-J) (Section 5.3)
        if (progress.stage.step >= ProgressStage.READING_COMPLETED.step) {
            val isLocked = progress.stage.step >= ProgressStage.EXERCISES_SUBMITTED.step
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (isLocked) "অনুশীলনী জমা সম্পন্ন (উত্তর লকড • Answer Key আনলকড)"
                            else "অধ্যায়ভিত্তিক অনুশীলনী (A–E অটো-স্কোরিং ও F–J বিশ্লেষণী প্রশ্ন)",
                            style = MaterialTheme.typography.titleMedium
                        )

                        chapter.questions.forEach { q ->
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "[${q.sectionCode}] প্রশ্ন ${q.questionNo} (${q.categoryLabel} • ${q.maxMarks} নম্বর)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(q.promptBn, fontWeight = FontWeight.Medium)

                                    if (q.isAutoScored) {
                                        q.options.forEach { opt ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = draftAnswers[q.id] == opt,
                                                    onClick = {
                                                        if (!isLocked) draftAnswers = draftAnswers + (q.id to opt)
                                                    },
                                                    enabled = !isLocked
                                                )
                                                Text(opt)
                                            }
                                        }
                                    } else {
                                        OutlinedTextField(
                                            value = draftAnswers[q.id] ?: "",
                                            onValueChange = {
                                                if (!isLocked) draftAnswers = draftAnswers + (q.id to it)
                                            },
                                            enabled = !isLocked,
                                            label = { Text("বাংলা বা ইংরেজিতে উত্তর লিখুন") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Answer Key & Supervisor Model Points ONLY visible AFTER submission!
                                    if (isLocked) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                q.lockedCorrectAnswer?.let { ans ->
                                                    Text("✔ সঠিক উত্তর: $ans", fontWeight = FontWeight.Bold)
                                                    Text(q.explanationBn, style = MaterialTheme.typography.bodyMedium)
                                                }
                                                if (q.supervisorModelPoints.isNotEmpty()) {
                                                    Text("সুপারভাইজার মডেল পয়েন্টস:", fontWeight = FontWeight.Bold)
                                                    q.supervisorModelPoints.forEach { pt ->
                                                        Text("• $pt", style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!isLocked) {
                            Button(
                                onClick = { repository.submitExercises(chapter, draftAnswers) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("উত্তর জমা দিন ও Answer Key আনলক করুন")
                            }
                        } else {
                            val total = progress.autoScore + (progress.supervisorScore ?: 0)
                            Text(
                                "আপনার বর্তমান ব্যক্তিগত স্কোর: $total / ${chapter.type.totalMarks} (অটো: ${progress.autoScore}, সুপারভাইজার: ${progress.supervisorScore ?: 0})",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "রি-অ্যাটেম্পট নিয়ম: প্রথম জমার ৪৮ ঘণ্টা পর অটো-স্কোরড অংশে ১ বার রি-অ্যাটেম্পট নেওয়া যাবে (প্রথম নম্বর সংরক্ষিত থাকবে)।",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        // Stage 5 -> 6: Self-Rating & Calibration Check (Section 5.1 & 5.3)
        if (progress.stage.step >= ProgressStage.EXERCISES_SUBMITTED.step) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("লার্নিং আউটকাম আত্মমূল্যায়ন (Self-Rating ১–৫)", style = MaterialTheme.typography.titleMedium)
                        chapter.learningOutcomes.forEachIndexed { idx, lo ->
                            Text(lo, style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = draftRatings[idx].toFloat(),
                                onValueChange = { v ->
                                    draftRatings = draftRatings.toMutableList().also { it[idx] = v.toInt() }
                                },
                                valueRange = 1f..5f,
                                steps = 3
                            )
                            Text("রেটিং: ${draftRatings[idx]}/৫", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(onClick = { repository.submitSelfRatings(chapter, draftRatings) }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("আত্মমূল্যায়ন সংরক্ষণ ও যাচাই করুন")
                        }
                        progress.calibrationFlags.forEach { flag ->
                            Text("ক্যালিব্রেশন নোট: ${flag.labelBn}", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }

        // Online Sessions (Jitsi In-App Live Video + Google Meet/Zoom + Slide Upload + Live Clinic) (Sec 5.2)
        item {
            Spacer(Modifier.height(4.dp))
            Text("অনলাইন সেশন (৪০ মিনিট) ও লাইভ রিসার্চ ক্লিনিক", style = MaterialTheme.typography.titleMedium)
        }

        items(state.sessions, key = { it.id }) { sess ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sess.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (sess.isResearchClinic) {
                            Badge { Text("Live Clinic") }
                        }
                    }
                    Text("${sess.scheduleLabel} • উপস্থাপক: ${sess.presenterName}", style = MaterialTheme.typography.bodyMedium)

                    if (!sess.isResearchClinic) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { repository.uploadSessionSlide(sess.id, "CPAR_Ch1_Presentation.pdf") }
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (sess.slideUploaded) "স্লাইড আপলোডকৃত ✔" else "স্লাইড আপলোড (২৪ ঘণ্টা আগে)")
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { activeJitsiSession = sess }) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ইন-অ্যাপ ভিডিও জয়েন (Jitsi)")
                        }
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sess.externalMeetingUrl)))
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Meet / Zoom")
                        }
                    }
                }
            }
        }
    }

    activeJitsiSession?.let { sess ->
        InAppJitsiVideoDialog(
            session = sess,
            displayName = state.currentUser.name,
            onDismiss = { activeJitsiSession = null }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InAppJitsiVideoDialog(
    session: OnlineSessionItem,
    displayName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onDismiss) { Text("লিভ সেশন") }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webViewClient = WebViewClient()
                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    request?.grant(request.resources)
                                }
                            }
                            val encodedName = Uri.encode(displayName)
                            loadUrl("https://meet.jit.si/${session.jitsiRoomName}#userInfo.displayName=\"$encodedName\"")
                        }
                    }
                )
            }
        }
    }
}
