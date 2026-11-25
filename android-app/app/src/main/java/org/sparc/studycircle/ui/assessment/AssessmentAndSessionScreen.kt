package org.sparc.studycircle.ui.assessment

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.*
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.SparcCrimson
import org.sparc.studycircle.ui.components.*

@Composable
fun AssessmentAndSessionScreen(
    state: SparcAppState,
    repository: SparcRepository
) {
    var subTab by remember { mutableIntStateOf(0) }
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

    var activeJitsiSession by remember { mutableStateOf<OnlineSessionItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf("অনুশীলনী কুইজ", "লাইভ সেশন", "সুপারভাইজার ভিউ"),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        when (subTab) {
            0 -> QuizAndCalibrationView(chapter = chapter, progress = progress, repository = repository)
            1 -> LiveSessionAndClinicView(
                sessions = state.sessions,
                repository = repository,
                onJoinJitsi = { activeJitsiSession = it },
                onOpenExternal = { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            )
            2 -> SupervisorMobileGradingView()
        }
    }

    activeJitsiSession?.let { sess ->
        val jitsiBase = state.apiVault.customJitsiServerUrl.trim().trimEnd('/').ifBlank { "https://meet.jit.si" }
        InAppJitsiVideoDialog(
            session = sess,
            jitsiBaseUrl = jitsiBase,
            displayName = state.currentUser.name,
            onDismiss = { activeJitsiSession = null }
        )
    }
}

@Composable
private fun QuizAndCalibrationView(
    chapter: ChapterItem,
    progress: ChapterProgressState,
    repository: SparcRepository
) {
    val p = LocalSparcPalette.current
    val isLocked = progress.stage.step >= ProgressStage.EXERCISES_SUBMITTED.step
    var draftAnswers by remember(progress.submittedAnswers) {
        mutableStateOf(
            progress.submittedAnswers.ifEmpty {
                mapOf("q1" to "Sayings, Doings, and Relatings", "q2" to "মিথ্যা (False)")
            }
        )
    }
    var selfRatingValue by remember { mutableIntStateOf(4) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = p.accentSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = SparcDomainEngine.COMPASSIONATE_OVERDUE_MESSAGE,
                    color = p.accentText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "অধ্যায় ${chapter.chapterNo} • ${chapter.type.name} QUIZ",
                    style = MaterialTheme.typography.titleMedium,
                    color = p.primary
                )
                Text(
                    "পাস ${chapter.type.passMarks}/${chapter.type.totalMarks}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.muted
                )
            }
        }

        items(chapter.questions.size) { index ->
            val q = chapter.questions[index]
            SparcScholarlyCard {
                Text(
                    text = "[${q.sectionCode}] ${q.promptBn}",
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text
                )

                if (q.isAutoScored) {
                    q.options.forEach { opt ->
                        val selected = draftAnswers[q.id] == opt
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) p.primary else p.cardAlt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isLocked) {
                                    draftAnswers = draftAnswers + (q.id to opt)
                                }
                        ) {
                            Text(
                                text = if (selected) "✓ $opt" else opt,
                                color = if (selected) Color.White else p.text,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = draftAnswers[q.id] ?: "",
                        onValueChange = { if (!isLocked) draftAnswers = draftAnswers + (q.id to it) },
                        enabled = !isLocked,
                        minLines = 3,
                        placeholder = { Text("বাংলা বা ইংরেজিতে আপনার উত্তর লিখুন...") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isLocked) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.primarySoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            q.lockedCorrectAnswer?.let { ans ->
                                Text("✔ Answer Key: $ans", fontWeight = FontWeight.Bold, color = p.primarySoftText)
                                Text(q.explanationBn, fontSize = 12.sp, color = p.primarySoftText)
                            }
                            if (q.supervisorModelPoints.isNotEmpty()) {
                                Text("মডেল পয়েন্টস:", fontWeight = FontWeight.Bold, color = p.primarySoftText)
                                q.supervisorModelPoints.forEach { pt ->
                                    Text("• $pt", fontSize = 12.sp, color = p.primarySoftText)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SparcScholarlyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("লার্নিং আউটকাম সেলফ-রেটিং", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Text("$selfRatingValue / 5", fontWeight = FontWeight.ExtraBold, color = p.primary)
                }
                Slider(
                    value = selfRatingValue.toFloat(),
                    onValueChange = { selfRatingValue = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3
                )
                SparcPrimaryButton(
                    text = if (!isLocked) "উত্তর জমা দিন ও Answer Key খুলুন" else "সেলফ-রেটিং আপডেট করুন ($selfRatingValue/5)",
                    onClick = {
                        if (progress.stage.step < ProgressStage.READING_COMPLETED.step) {
                            repository.declareReadingCompleted(chapter)
                        }
                        if (!isLocked) {
                            repository.submitExercises(chapter, draftAnswers)
                        }
                        repository.submitSelfRatings(
                            chapter,
                            List(chapter.learningOutcomes.size) { selfRatingValue }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun LiveSessionAndClinicView(
    sessions: List<OnlineSessionItem>,
    repository: SparcRepository,
    onJoinJitsi: (OnlineSessionItem) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val p = LocalSparcPalette.current
    val mainSession = sessions.first()
    val clinicSession = sessions.last()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SparcScholarlyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("অনলাইন সেশন (পর্ব ১/২ • ৪০ মি.)", style = MaterialTheme.typography.titleMedium, color = p.text)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = p.primarySoft,
                        modifier = Modifier.clickable {
                            repository.uploadSessionSlide(mainSession.id, "CPAR_Ch1_Slides.pdf")
                        }
                    ) {
                        Text(
                            text = if (mainSession.slideUploaded) "✓ স্লাইড যাচাইকৃত" else "+ স্লাইড আপলোড",
                            color = p.primarySoftText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0B1120))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoParticipantTile("তানভীর (Live)", Modifier.weight(1f))
                        VideoParticipantTile("ড. নুসরাত জাহান", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoParticipantTile("ফারহান সাদিক", Modifier.weight(1f))
                        VideoParticipantTile("মালিহা তাবাসসুম", Modifier.weight(1f))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onJoinJitsi(mainSession) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Join Jitsi Video", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.cardAlt,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenExternal(mainSession.externalMeetingUrl) }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 11.dp)) {
                            Text("Meet / Zoom ↗", color = p.text, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }

        item {
            SparcScholarlyCard {
                Text("মাসিক লাইভ রিসার্চ ক্লিনিক", style = MaterialTheme.typography.titleMedium, color = p.text)
                Text(
                    "মাসের শেষ শনিবার রাত ৯:০০ • সিনিয়র গবেষকদের সাথে সরাসরি Q&A",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.muted
                )
                SparcPrimaryButton(
                    text = "ক্লিনিকে জয়েন / প্রশ্ন জমা দিন",
                    onClick = { onJoinJitsi(clinicSession) }
                )
            }
        }
    }
}

@Composable
private fun VideoParticipantTile(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .padding(8.dp)
    ) {
        SparcAvatarCircle(name = name, size = 28.dp)
        Text(
            text = name,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun SupervisorMobileGradingView() {
    val p = LocalSparcPalette.current
    var awardedMarks by remember { mutableIntStateOf(3) }
    var overrideApproved by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SparcScholarlyCard(borderColor = SparcCrimson.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Needs Support (≥৩ রেড ফ্ল্যাগ)", style = MaterialTheme.typography.titleMedium, color = SparcCrimson)
                    Surface(shape = RoundedCornerShape(50), color = SparcCrimson.copy(alpha = 0.15f)) {
                        Text(
                            "সাদিয়া ইসলাম",
                            color = SparcCrimson,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    "• ক্যালিব্রেশন: Over-confident (Self 4.5 • Score 38%)\n• প্রাপ্ত Poke সংখ্যা: ৫টি (বার্তা গোপন রাখা হয়েছে)",
                    fontSize = 12.sp,
                    color = p.muted
                )
            }
        }

        item {
            SparcScholarlyCard {
                Text("সুপারভাইজার ম্যানুয়াল স্কোরিং [F–J]", style = MaterialTheme.typography.titleMedium, color = p.text)
                Text(
                    "“Sayings, Doings ও Relatings বিশ্লেষণ করে বিভাগে অংশগ্রহণমূলক সংলাপ তৈরি করা সম্ভব।”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.muted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..3).forEach { m ->
                        val active = awardedMarks == m
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) p.primary else p.cardAlt,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { awardedMarks = m }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                Text(
                                    "$m pt",
                                    color = if (active) Color.White else p.text,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                SparcPrimaryButton(
                    text = if (overrideApproved) "✓ স্কোর সংরক্ষিত ও Override সক্রিয়" else "স্কোর সংরক্ষণ ও Override অনুমোদন",
                    onClick = { overrideApproved = true }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InAppJitsiVideoDialog(
    session: OnlineSessionItem,
    jitsiBaseUrl: String = "https://meet.jit.si",
    displayName: String,
    onDismiss: () -> Unit
) {
    val p = LocalSparcPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = p.card) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(session.title, style = MaterialTheme.typography.titleMedium, color = p.text, modifier = Modifier.weight(1f))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SparcCrimson)
                    ) {
                        Text("লিভ সেশন")
                    }
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
                            loadUrl("$jitsiBaseUrl/${session.jitsiRoomName}#userInfo.displayName=\"$encodedName\"")
                        }
                    }
                )
            }
        }
    }
}
