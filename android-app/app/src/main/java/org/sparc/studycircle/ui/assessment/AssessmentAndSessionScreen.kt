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
import org.sparc.studycircle.core.theme.*
import org.sparc.studycircle.ui.components.*

@Composable
fun AssessmentAndSessionScreen(
    state: SparcAppState,
    repository: SparcRepository
) {
    // Sub-tabs: 0 = Screen 7 (Exercise Quiz & Self-Rating), 1 = Screen 8 (Live Video Session & Clinic), 2 = Screen 12 (Supervisor View)
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
                options = listOf("অনুশীলনী কুইজ", "লাইভ ভিডিও সেশন", "সুপারভাইজার ভিউ"),
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
        InAppJitsiVideoDialog(
            session = sess,
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
    val isLocked = progress.stage.step >= ProgressStage.EXERCISES_SUBMITTED.step
    var draftAnswers by remember(progress.submittedAnswers) {
        mutableStateOf(
            progress.submittedAnswers.ifEmpty {
                mapOf("q1" to "Sayings, Doings, and Relatings", "q2" to "মিথ্যা (False)")
            }
        )
    }
    var selfRatingValue by remember { mutableIntStateOf(4) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Warm Gold Compassionate Banner (Matching Screen 7 Mockup!)
        Surface(
            color = Color(0xFFF5B82E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = SparcDomainEngine.COMPASSIONATE_OVERDUE_MESSAGE,
                color = Color(0xFF1E293B),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Chapter ${chapter.chapterNo} Exercise • Long Chapter",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "(${chapter.type.totalQuestions} Questions • Pass ${chapter.type.passMarks}/${chapter.type.totalMarks})",
                    style = MaterialTheme.typography.titleMedium,
                    color = SparcMutedInk
                )
            }

            items(chapter.questions.size) { index ->
                val q = chapter.questions[index]
                SparcScholarlyCard {
                    Text(
                        text = "[${q.sectionCode}] ${q.categoryLabel} (${q.maxMarks} pt): ${q.promptBn}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (q.isAutoScored) {
                        q.options.forEach { opt ->
                            val selected = draftAnswers[q.id] == opt
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (selected) SparcEmerald else SparcSubtleBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isLocked) {
                                        draftAnswers = draftAnswers + (q.id to opt)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color.White,
                                            unselectedColor = SparcMutedInk
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = opt,
                                        color = if (selected) Color.White else SparcInk,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = draftAnswers[q.id] ?: "",
                            onValueChange = { if (!isLocked) draftAnswers = draftAnswers + (q.id to it) },
                            enabled = !isLocked,
                            minLines = 3,
                            placeholder = { Text("বাংলা বা ইংরেজিতে আপনার বিশ্লেষণ লিখুন...") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isLocked) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SparcEmeraldContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                q.lockedCorrectAnswer?.let { ans ->
                                    Text("✔ Answer Key: $ans", fontWeight = FontWeight.Bold, color = SparcEmeraldDark)
                                    Text(q.explanationBn, fontSize = 12.sp, color = SparcEmeraldDark)
                                }
                                if (q.supervisorModelPoints.isNotEmpty()) {
                                    Text("মডেল পয়েন্টস:", fontWeight = FontWeight.Bold, color = SparcEmeraldDark)
                                    q.supervisorModelPoints.forEach { pt ->
                                        Text("• $pt", fontSize = 12.sp, color = SparcEmeraldDark)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Self-Rating Calibration Slider Card
            item {
                SparcScholarlyCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Learning Outcome Self-Rating (1-5)", style = MaterialTheme.typography.titleMedium)
                        Text("$selfRatingValue / 5", fontWeight = FontWeight.Bold, color = SparcEmerald)
                    }
                    Slider(
                        value = selfRatingValue.toFloat(),
                        onValueChange = { selfRatingValue = it.toInt() },
                        valueRange = 1f..5f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = SparcEmerald,
                            activeTrackColor = SparcEmerald
                        )
                    )
                    SparcPrimaryButton(
                        text = if (!isLocked) "Submit & Unlock Answer Key" else "Save Self-Rating ($selfRatingValue/5)",
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
}

@Composable
private fun LiveSessionAndClinicView(
    sessions: List<OnlineSessionItem>,
    repository: SparcRepository,
    onJoinJitsi: (OnlineSessionItem) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val mainSession = sessions.first()
    val clinicSession = sessions.last()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // SCREEN 8: 40-Min Online Session with Jitsi 2x2 Tile Preview + Meet/Zoom
        item {
            SparcScholarlyCard {
                Text(
                    "Chapter 1 Online Session\n(Part 1 of 2 • 40 Min)",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Presenter: ${mainSession.presenterName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SparcMutedInk
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = SparcEmerald,
                    modifier = Modifier.clickable {
                        repository.uploadSessionSlide(mainSession.id, "CPAR_Ch1_Slides.pdf")
                    }
                ) {
                    Text(
                        text = if (mainSession.slideUploaded)
                            "✓ Slide Deck Uploaded (24h Verified)"
                        else "Upload Slide Deck (24h Before Session)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // 2x2 Live Researcher Video Tile Preview
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F172A))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoParticipantTile("তানভীর (উপস্থাপক)", Modifier.weight(1f))
                        VideoParticipantTile("ড. নুসরাত জাহান", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoParticipantTile("ফারহান সাদিক", Modifier.weight(1f))
                        VideoParticipantTile("মালিহা তাবাসসুম", Modifier.weight(1f))
                    }
                }

                SparcPrimaryButton(
                    text = "Join In-App Video (Jitsi)",
                    onClick = { onJoinJitsi(mainSession) }
                )

                OutlinedButton(
                    onClick = { onOpenExternal(mainSession.externalMeetingUrl) },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.2.dp, SparcEmerald),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Google Meet / Zoom", color = SparcEmerald, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Monthly Live Research Clinic Card
        item {
            SparcScholarlyCard {
                Text("Monthly Live Research Clinic", style = MaterialTheme.typography.titleLarge)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SparcAmber,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Live Q&A with Senior Researchers • Sat 9:00 PM",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                OutlinedButton(
                    onClick = { onJoinJitsi(clinicSession) },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.2.dp, SparcEmerald),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Join Clinic / Submit Research Question", color = SparcEmerald, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun VideoParticipantTile(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E293B))
            .padding(8.dp)
    ) {
        SparcAvatarCircle(
            name = name,
            size = 32.dp,
            bgColor = SparcEmerald,
            textColor = Color.White
        )
        Text(
            text = "🎙 $name",
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun SupervisorMobileGradingView() {
    var awardedMarks by remember { mutableIntStateOf(2) }
    var overrideApproved by remember { mutableStateOf(false) }

    // SCREEN 12: Supervisor Mobile Red-Flag & Manual Grading Dashboard
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SparcScholarlyCard(borderColor = Color(0xFFFCA5A5)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🚨 Needs Support Alert", style = MaterialTheme.typography.titleMedium, color = SparcCrimson)
                    Surface(shape = RoundedCornerShape(50), color = SparcCrimsonContainer) {
                        Text(
                            "3 Red Flags",
                            color = SparcCrimson,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text("সদস্য: সাদিয়া ইসলাম (Sociology)", fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(50), color = SparcAmberContainer) {
                    Text(
                        "Calibration: Over-confident (Self 4.5 • Score 38%)",
                        color = SparcAmberDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    "Poke Summary: মোট ৫টি Poke পেয়েছেন, ১টি রিপ্লাই দিয়েছেন (বার্তা গোপন রাখা হয়েছে)।",
                    fontSize = 12.sp,
                    color = SparcMutedInk
                )
            }
        }

        item {
            SparcScholarlyCard {
                Text("Manual Scoring [F–J]: Scenario Analysis", style = MaterialTheme.typography.titleMedium)
                Text(
                    "উত্তর: \"Sayings, Doings ও Relatings বিশ্লেষণ করে বিভাগে অংশগ্রহণমূলক সংলাপ তৈরি করা সম্ভব।\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SparcMutedInk
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..3).forEach { m ->
                        val active = awardedMarks == m
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) SparcEmerald else SparcSubtleBg,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { awardedMarks = m }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    "$m pts",
                                    color = if (active) Color.White else SparcInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                SparcPrimaryButton(
                    text = if (overrideApproved) "✓ Grade Saved & Override Enabled" else "Save Score & Approve Override",
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
                            loadUrl("https://meet.jit.si/${session.jitsiRoomName}#userInfo.displayName=\"$encodedName\"")
                        }
                    }
                )
            }
        }
    }
}
