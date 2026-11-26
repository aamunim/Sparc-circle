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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
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
import org.sparc.studycircle.core.theme.tr
import org.sparc.studycircle.core.theme.trContent
import org.sparc.studycircle.ui.components.*

@Composable
fun AssessmentAndSessionScreen(
    state: SparcAppState,
    repository: SparcRepository
) {
    var subTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val circle = state.circles.find { it.id == state.activeCircleId } ?: state.circles.firstOrNull()
    val book = circle?.let { c -> state.books.find { it.id == c.bookId } } ?: state.books.firstOrNull()
    if (circle == null || book == null || book.chapters.isEmpty()) {
        val p = LocalSparcPalette.current
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            SparcScholarlyCard {
                Text(
                    tr("📝 এখনো কোনো পাঠ অনুশীলনী সক্রিয় নেই", "📝 No Active Guided Reading & Practice Yet"),
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text
                )
                Text(
                    tr(
                        "অনুশীলনী, লাইভ সেশন ও মেন্টর মূল্যায়ন ব্যবহার করতে আগে একটি বই ও স্টাডি সার্কেলে যুক্ত হন।",
                        "Join or create a Study Circle around a published book to access exercises, live sessions, and Mentor evaluation."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.muted
                )
            }
        }
        return
    }
    var selectedChIdx by remember(book.id) {
        mutableIntStateOf(
            book.chapters.indexOfFirst { it.chapterNo == circle.currentGroupChapter }.coerceAtLeast(0)
        )
    }
    val safeChIdx = selectedChIdx.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
    val chapter = book.chapters[safeChIdx]
    val progress = state.myChapterProgress[chapter.chapterNo] ?: ChapterProgressState(
        id = "init",
        circleId = circle.id,
        userId = state.currentUser.id,
        chapterNo = chapter.chapterNo
    )

    var activeJitsiSession by remember { mutableStateOf<OnlineSessionItem?>(null) }
    var showAddQuestionSheet by remember { mutableStateOf(false) }
    var showTemplateImportSheet by remember { mutableStateOf(false) }

    val allUsersById = remember(state.currentUser, state.directoryMembers) {
        (listOf(state.currentUser) + state.directoryMembers).associateBy { it.id }
    }
    val canManageCircle = SparcDomainEngine.canUserManageCircle(state.currentUser, circle)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf(
                    tr("পাঠ অনুশীলনী", "Reading Practice"),
                    tr("লাইভ সেশন", "Live Session"),
                    tr("মেন্টর প্যানেল", "Mentor Panel")
                ),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        when (subTab) {
            0 -> QuizAndCalibrationView(
                book = book,
                selectedChIdx = safeChIdx,
                onSelectChapter = { selectedChIdx = it },
                chapter = chapter,
                progress = progress,
                peerBuddyProfile = progress.peerBuddyId?.let { allUsersById[it] },
                canEditCurriculum = state.currentUser.role.isMentorOrAdmin,
                repository = repository,
                onOpenAddQuestion = { showAddQuestionSheet = true },
                onOpenTemplateImport = { showTemplateImportSheet = true }
            )
            1 -> LiveSessionAndClinicView(
                sessions = state.sessions,
                circleMembers = circle.memberIds.mapNotNull { allUsersById[it] }.ifEmpty { listOf(state.currentUser) + state.directoryMembers.take(4) },
                canAssignPresenter = canManageCircle,
                repository = repository,
                onJoinJitsi = { activeJitsiSession = it },
                onOpenExternal = { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            )
            2 -> MentorGradingAndPeerBuddyView(
                state = state,
                circle = circle,
                chapter = chapter,
                allUsersById = allUsersById,
                repository = repository
            )
        }
    }

    if (showAddQuestionSheet) {
        CustomQuizQuestionBuilderSheet(
            book = book,
            chapter = chapter,
            repository = repository,
            onDismiss = { showAddQuestionSheet = false }
        )
    }

    if (showTemplateImportSheet) {
        ExerciseTemplateImporterSheet(
            book = book,
            initialChapterNo = chapter.chapterNo,
            repository = repository,
            onDismiss = { showTemplateImportSheet = false }
        )
    }

    activeJitsiSession?.let { sess ->
        val jitsiBase = state.apiVault.customJitsiServerUrl.trim().trimEnd('/').ifBlank { "https://meet.jit.si" }
        InAppJitsiVideoDialog(
            session = sess,
            jitsiBaseUrl = jitsiBase,
            displayName = trContent(state.currentUser.name),
            onDismiss = { activeJitsiSession = null }
        )
    }
}

@Composable
private fun QuizAndCalibrationView(
    book: BookItem,
    selectedChIdx: Int,
    onSelectChapter: (Int) -> Unit,
    chapter: ChapterItem,
    progress: ChapterProgressState,
    peerBuddyProfile: UserProfile?,
    canEditCurriculum: Boolean,
    repository: SparcRepository,
    onOpenAddQuestion: () -> Unit,
    onOpenTemplateImport: () -> Unit
) {
    val p = LocalSparcPalette.current
    val isLocked = progress.stage.step >= ProgressStage.EXERCISES_SUBMITTED.step
    var draftAnswers by remember(progress.submittedAnswers, chapter.questions) {
        mutableStateOf(
            progress.submittedAnswers.ifEmpty {
                buildMap {
                    chapter.questions.getOrNull(0)?.let { put(it.id, "Sayings, Doings, and Relatings") }
                    chapter.questions.getOrNull(1)?.let { put(it.id, "মিথ্যা (False)") }
                }
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
                        val active = idx == selectedChIdx
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) p.primary else p.card,
                            border = BorderStroke(1.dp, if (active) p.primary else p.border),
                            modifier = Modifier.clickable { onSelectChapter(idx) }
                        ) {
                            Text(
                                tr("অধ্যায় ${ch.chapterNo}", "Ch ${ch.chapterNo}"),
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
                    color = p.cardAlt,
                    border = BorderStroke(1.dp, p.border),
                    modifier = Modifier.clickable { onOpenTemplateImport() }
                ) {
                    Text(
                        tr("📋 টেমপ্লেট", "📋 Template"),
                        color = p.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
                if (canEditCurriculum) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = p.primarySoft,
                        modifier = Modifier.clickable { onOpenAddQuestion() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = p.primarySoftText, modifier = Modifier.size(14.dp))
                            Text(tr("+ প্রশ্ন", "+ Add"), color = p.primarySoftText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (peerBuddyProfile != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = p.primarySoft,
                    border = BorderStroke(1.dp, p.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tr("🤝 আপনার সহপাঠী সঙ্গী (Peer Buddy): ${trContent(peerBuddyProfile.name)}", "🤝 Your Peer Buddy: ${trContent(peerBuddyProfile.name)}"),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp,
                                color = p.primarySoftText
                            )
                            Text(
                                tr("মেন্টর আপনার পাঠ সহায়তার জন্য তাকে যুক্ত করেছেন।", "Assigned by your Mentor to support your chapter progress."),
                                fontSize = 11.5.sp,
                                color = p.primarySoftText.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = p.accentSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = trContent(SparcDomainEngine.COMPASSIONATE_OVERDUE_MESSAGE),
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
                    tr("অধ্যায় ${chapter.chapterNo} • পাঠ অনুশীলনী", "Chapter ${chapter.chapterNo} • Guided Practice"),
                    style = MaterialTheme.typography.titleMedium,
                    color = p.primary
                )
                Text(
                    tr("লক্ষ্যমাত্রা ${chapter.type.passMarks}/${chapter.type.totalMarks}", "Target ${chapter.type.passMarks}/${chapter.type.totalMarks}"),
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
                    text = "[${q.sectionCode}] ${trContent(q.promptBn)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text
                )

                if (q.isAutoScored) {
                    q.options.forEach { opt ->
                        val selected = draftAnswers[q.id] == opt
                        val displayOpt = trContent(opt)
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
                                text = if (selected) "✓ $displayOpt" else displayOpt,
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
                        placeholder = { Text(tr("বাংলা বা ইংরেজিতে আপনার উত্তর লিখুন...", "Write your analytical response...")) },
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
                                Text("✔ Answer Key: ${trContent(ans)}", fontWeight = FontWeight.Bold, color = p.primarySoftText)
                                Text(trContent(q.explanationBn), fontSize = 12.sp, color = p.primarySoftText)
                            }
                            if (q.supervisorModelPoints.isNotEmpty()) {
                                Text(tr("মডেল পয়েন্টস:", "Supervisor Model Points:"), fontWeight = FontWeight.Bold, color = p.primarySoftText)
                                q.supervisorModelPoints.forEach { pt ->
                                    Text("• ${trContent(pt)}", fontSize = 12.sp, color = p.primarySoftText)
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
                    Text(tr("লার্নিং আউটকাম সেলফ-রেটিং", "Learning Outcome Self-Rating"), style = MaterialTheme.typography.titleMedium, color = p.text)
                    Text("$selfRatingValue / 5", fontWeight = FontWeight.ExtraBold, color = p.primary)
                }
                Slider(
                    value = selfRatingValue.toFloat(),
                    onValueChange = { selfRatingValue = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3
                )
                SparcPrimaryButton(
                    text = if (!isLocked) {
                        tr("উত্তর জমা দিন ও Answer Key খুলুন", "Submit Answers & Unlock Answer Key")
                    } else {
                        tr("সেলফ-রেটিং আপডেট করুন ($selfRatingValue/5)", "Update Self-Rating ($selfRatingValue/5)")
                    },
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
    circleMembers: List<UserProfile>,
    canAssignPresenter: Boolean,
    repository: SparcRepository,
    onJoinJitsi: (OnlineSessionItem) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val p = LocalSparcPalette.current
    if (sessions.isEmpty()) {
        val instantSession = OnlineSessionItem(
            id = "instant-live",
            circleId = null,
            chapterNo = 1,
            title = tr("স্টাডি সার্কেল লাইভ রুম", "Study Circle Live Room"),
            scheduleLabel = tr("৪০ মিনিট সেশন", "40 Min Session"),
            presenterName = "",
            hoursUntilSession = 0,
            jitsiRoomName = "SPARC_Circle_Live",
            externalMeetingUrl = "https://meet.jit.si/SPARC_Circle_Live"
        )
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            SparcScholarlyCard {
                Text(
                    tr("🎥 নির্ধারিত কোনো অনলাইন সেশন নেই", "🎥 No Scheduled Online Session Yet"),
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text
                )
                Text(
                    tr(
                        "সার্কেলের ৮০% সদস্য অনুশীলনী সম্পন্ন করলে স্বয়ংক্রিয়ভাবে সেশন ঘোষিত হবে, অথবা এখনই ইনস্ট্যান্ট লাইভ রুমে যুক্ত হতে পারেন।",
                        "A group session is announced automatically at 80% completion, or you can open an instant live room now."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.muted
                )
                SparcPrimaryButton(
                    text = tr("📹 ইনস্ট্যান্ট Jitsi লাইভ রুমে জয়েন করুন", "📹 Join Instant Jitsi Live Room"),
                    onClick = { onJoinJitsi(instantSession) }
                )
            }
        }
        return
    }
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
                    Text(
                        tr("অনলাইন সেশন (পর্ব ১/২ • ৪০ মি.)", "Online Session (Part 1/2 • 40 Min)"),
                        style = MaterialTheme.typography.titleMedium,
                        color = p.text
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = p.primarySoft,
                        modifier = Modifier.clickable {
                            repository.uploadSessionSlide(mainSession.id, "CPAR_Ch1_Slides.pdf")
                        }
                    ) {
                        Text(
                            text = if (mainSession.slideUploaded) tr("✓ স্লাইড যাচাইকৃত", "✓ Slides Verified") else tr("+ স্লাইড আপলোড", "+ Upload Slides"),
                            color = p.primarySoftText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Chapter Presenter Activity Badge & Rotation Selector
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = p.primarySoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🎤 ${tr("অধ্যায়ের উপস্থাপক (Chapter Presenter)", "Chapter Presenter")}: ${trContent(mainSession.presenterName)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp,
                            color = p.primarySoftText
                        )
                        if (canAssignPresenter && circleMembers.isNotEmpty()) {
                            Text(
                                tr("সার্কেল লিড / মেন্টর কন্ট্রোল — পরবর্তী উপস্থাপক নির্বাচন করুন:", "Circle Lead / Mentor — Rotate Presenter:"),
                                fontSize = 11.sp,
                                color = p.primarySoftText.copy(alpha = 0.85f)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(circleMembers.size) { idx ->
                                    val member = circleMembers[idx]
                                    val isCurrent = mainSession.presenterUserId == member.id ||
                                        mainSession.presenterName == member.name
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isCurrent) p.primary else p.card,
                                        modifier = Modifier.clickable {
                                            repository.assignSessionPresenter(mainSession.id, member)
                                        }
                                    ) {
                                        Text(
                                            text = if (isCurrent) "🎤 ${trContent(member.name).split(" ").first()}" else trContent(member.name).split(" ").first(),
                                            color = if (isCurrent) Color.White else p.text,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
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
                        VideoParticipantTile("🎤 ${trContent(mainSession.presenterName)}", Modifier.weight(1f))
                        VideoParticipantTile("🎓 ${trContent("ড. নুসরাত জাহান")}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoParticipantTile("⭐ ${trContent("ফারহান সাদিক")}", Modifier.weight(1f))
                        VideoParticipantTile(trContent("মালিহা তাবাসসুম"), Modifier.weight(1f))
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
                Text(tr("মাসিক লাইভ রিসার্চ ক্লিনিক", "Monthly Live Research Clinic"), style = MaterialTheme.typography.titleMedium, color = p.text)
                Text(
                    tr(
                        "মাসের শেষ শনিবার রাত ৯:০০ • সিনিয়র গবেষক ও মেন্টরদের সাথে সরাসরি Q&A",
                        "Last Saturday of Month, 9:00 PM • Live Q&A with Senior Mentors"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.muted
                )
                SparcPrimaryButton(
                    text = tr("ক্লিনিকে জয়েন / প্রশ্ন জমা দিন", "Join Clinic / Submit Question"),
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
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun MentorGradingAndPeerBuddyView(
    state: SparcAppState,
    circle: StudyCircleGroup,
    chapter: ChapterItem,
    allUsersById: Map<String, UserProfile>,
    repository: SparcRepository
) {
    val p = LocalSparcPalette.current
    val isAuthorizedMentor = state.currentUser.role.isMentorOrAdmin || circle.supervisorId == state.currentUser.id

    if (!isAuthorizedMentor) {
        val myProg = state.myChapterProgress[chapter.chapterNo]
        val myBuddy = myProg?.peerBuddyId?.let { allUsersById[it] }
        val iAmBuddyFor = state.circleMemberProgress
            .filter { it.peerBuddyId == state.currentUser.id }
            .mapNotNull { allUsersById[it.userId] }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SparcScholarlyCard {
                    Text(
                        tr("🎓 মেন্টর মূল্যায়ন ও সহপাঠী সাপোর্ট স্ট্যাটাস", "🎓 Mentor Evaluation & Peer Buddy Status"),
                        style = MaterialTheme.typography.titleMedium,
                        color = p.primary
                    )
                    Text(
                        tr(
                            "উন্মুক্ত প্রশ্ন (F–J) মূল্যায়ন, রেড-ফ্ল্যাগ পর্যবেক্ষণ এবং Peer Buddy নির্ধারণ শুধুমাত্র SPARC-নিযুক্ত মেন্টর ও অ্যাডমিন পরিচালনা করেন।",
                            "Open-question grading (F–J), Red-Flag monitoring, and Peer Buddy pairing are managed by your SPARC Mentor & Admin."
                        ),
                        fontSize = 12.sp,
                        color = p.muted
                    )
                    val supScoreLabel = myProg?.supervisorScore?.let { "$it" } ?: tr("মূল্যায়নাধীন", "Pending Review")
                    Text(
                        tr("• আপনার বর্তমান মেন্টর স্কোর: $supScoreLabel", "• Your current Mentor score: $supScoreLabel"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = p.text
                    )
                    if (myBuddy != null) {
                        Text(
                            tr("• 🤝 আপনার Peer Buddy: ${trContent(myBuddy.name)}", "• 🤝 Your Peer Buddy: ${trContent(myBuddy.name)}"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = p.primary
                        )
                    }
                    if (iAmBuddyFor.isNotEmpty()) {
                        val buddyNames = iAmBuddyFor.map { trContent(it.name) }.joinToString()
                        Text(
                            tr(
                                "• 🤝 আপনি সহপাঠী সঙ্গী (Peer Buddy) হিসেবে যুক্ত আছেন: $buddyNames",
                                "• 🤝 You are assigned as Peer Buddy for: $buddyNames"
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = p.primary
                        )
                    }
                    SparcPrimaryButton(
                        text = tr("🎓 Mentor (Preview) মোডে প্যানেল দেখুন", "🎓 Preview Mentor Panel"),
                        onClick = { repository.switchMyRolePreview(UserRole.SUPERVISOR) }
                    )
                }
            }
        }
        return
    }

    val openQuestions = remember(chapter.questions) { chapter.questions.filter { !it.isAutoScored } }
    val memberCandidates = remember(circle.memberIds, allUsersById) {
        circle.memberIds.mapNotNull { allUsersById[it] }
            .filter { !it.role.isMentorOrAdmin }
            .ifEmpty { state.directoryMembers }
    }
    var selectedStudentIdx by remember { mutableIntStateOf(0) }
    val safeStudent = memberCandidates.getOrElse(selectedStudentIdx.coerceIn(0, (memberCandidates.size - 1).coerceAtLeast(0))) {
        state.currentUser
    }
    val studentProgress = state.circleMemberProgress.find {
        it.userId == safeStudent.id && it.chapterNo == chapter.chapterNo
    } ?: ChapterProgressState(
        id = "temp-${safeStudent.id}",
        circleId = circle.id,
        userId = safeStudent.id,
        chapterNo = chapter.chapterNo,
        stage = state.memberStageByUserId[safeStudent.id] ?: ProgressStage.READING_IN_PROGRESS,
        autoScore = 18
    )

    var draftOpenScores by remember(safeStudent.id, chapter.id, studentProgress.openQuestionScores) {
        mutableStateOf(
            openQuestions.associate { q ->
                q.id to (studentProgress.openQuestionScores[q.id] ?: 2)
            }
        )
    }
    val aheadPeers = remember(memberCandidates, state.memberStageByUserId, safeStudent.id) {
        memberCandidates.filter { cand ->
            cand.id != safeStudent.id &&
                (state.memberStageByUserId[cand.id]?.step ?: 3) >= ProgressStage.EXERCISES_SUBMITTED.step
        }.ifEmpty { memberCandidates.filter { it.id != safeStudent.id } }
    }
    var selectedBuddyId by remember(safeStudent.id, studentProgress.peerBuddyId) {
        mutableStateOf(studentProgress.peerBuddyId ?: aheadPeers.firstOrNull()?.id)
    }
    val strugglingRows = remember(state.circleMemberProgress) {
        state.circleMemberProgress.filter { it.needsSupport || it.redFlagIndicators.isNotEmpty() || it.calibrationFlags.isNotEmpty() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Red-Flag Alerts & Peer Buddy Support Card
        if (strugglingRows.isNotEmpty()) {
            item {
                SparcScholarlyCard(borderColor = SparcCrimson.copy(alpha = 0.5f)) {
                    Text(
                        tr("🚨 রেড-ফ্ল্যাগ অ্যালার্ট ও Peer Buddy সহায়তা (${strugglingRows.size})", "🚨 Red-Flag Alerts & Peer Buddy Support (${strugglingRows.size})"),
                        style = MaterialTheme.typography.titleMedium,
                        color = SparcCrimson
                    )
                    strugglingRows.forEach { row ->
                        val uName = allUsersById[row.userId]?.name?.let { trContent(it) } ?: "Researcher"
                        val currentBuddyName = row.peerBuddyId?.let { allUsersById[it]?.name }?.let { trContent(it) }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = p.cardAlt,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚠️ $uName", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = SparcCrimson)
                                    val idxInList = memberCandidates.indexOfFirst { it.id == row.userId }
                                    if (idxInList >= 0) {
                                        Text(
                                            tr("মূল্যায়ন করুন ›", "Select ›"),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = p.primary,
                                            modifier = Modifier.clickable { selectedStudentIdx = idxInList }
                                        )
                                    }
                                }
                                row.redFlagIndicators.forEach { rf ->
                                    Text("• ${trContent(rf)}", fontSize = 11.5.sp, color = p.muted)
                                }
                                if (currentBuddyName != null) {
                                    Text(
                                        tr("🤝 যুক্ত Peer Buddy: $currentBuddyName", "🤝 Assigned Peer Buddy: $currentBuddyName"),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = p.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Select Researcher to Grade (F–J 0–3), Assign Peer Buddy & Unlock Chapter
        item {
            SparcScholarlyCard {
                Text(
                    tr("🎓 মেন্টর মূল্যায়ন ও সাপোর্ট কনসোল (অধ্যায় ${chapter.chapterNo})", "🎓 Mentor Grading & Support Console (Ch ${chapter.chapterNo})"),
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text
                )
                Text(tr("গবেষক নির্বাচন করুন:", "Select Researcher:"), fontSize = 12.sp, color = p.muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(memberCandidates.size) { idx ->
                        val m = memberCandidates[idx]
                        val active = idx == selectedStudentIdx
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) p.primary else p.cardAlt,
                            modifier = Modifier.clickable { selectedStudentIdx = idx }
                        ) {
                            Text(
                                trContent(m.name),
                                color = if (active) Color.White else p.text,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Text(
                    tr(
                        "ধাপ: ${studentProgress.stage.labelBn} • অটো স্কোর: ${studentProgress.autoScore} • মেন্টর স্কোর: ${studentProgress.supervisorScore ?: 0}",
                        "Stage: ${studentProgress.stage.labelEn} • Auto: ${studentProgress.autoScore} • Mentor: ${studentProgress.supervisorScore ?: 0}"
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.primary
                )

                // Open Questions [F–J] 0–3 Grading
                openQuestions.forEach { oq ->
                    val ansText = studentProgress.submittedAnswers[oq.id]
                        ?: tr("(এখনো উত্তর জমা দেয়নি — ডেমো উত্তর মূল্যায়ন করতে পারেন)", "(Answer not yet submitted)")
                    val currentMark = draftOpenScores[oq.id] ?: 2
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.cardAlt,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("[${oq.sectionCode}] ${trContent(oq.promptBn)}", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = p.text)
                            Text("📝 $ansText", fontSize = 12.sp, color = p.muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (0..3).forEach { m ->
                                    val chosen = currentMark == m
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (chosen) p.primary else p.card,
                                        border = BorderStroke(1.dp, if (chosen) p.primary else p.border),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { draftOpenScores = draftOpenScores + (oq.id to m) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                            Text("$m / 3", color = if (chosen) Color.White else p.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (studentProgress.writingContent.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = p.cardAlt, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr("✍️ জমাকৃত রাইটিং টাস্ক:", "✍️ Submitted Writing Draft:"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = p.primary)
                            Text(studentProgress.writingContent.take(220), fontSize = 11.5.sp, color = p.text)
                        }
                    }
                }

                // Peer Buddy Pairing Selector
                if (aheadPeers.isNotEmpty()) {
                    Text(
                        tr("🤝 সহপাঠী সঙ্গী (Peer Buddy) যুক্ত করুন:", "🤝 Assign a Peer Buddy:"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = p.text
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(aheadPeers.size) { bIdx ->
                            val buddy = aheadPeers[bIdx]
                            val picked = selectedBuddyId == buddy.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (picked) p.primarySoft else p.cardAlt,
                                border = BorderStroke(1.dp, if (picked) p.primary else p.border),
                                modifier = Modifier.clickable { selectedBuddyId = buddy.id }
                            ) {
                                Text(
                                    text = "🤝 ${trContent(buddy.name)}",
                                    color = if (picked) p.primarySoftText else p.text,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            repository.mentorEvaluateAndSupport(
                                circleId = circle.id,
                                targetUserId = safeStudent.id,
                                chapter = chapter,
                                extendDays = 3
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(tr("⏳ +৩ দিন ডেডলাইন", "⏳ +3d Deadline"), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            repository.mentorEvaluateAndSupport(
                                circleId = circle.id,
                                targetUserId = safeStudent.id,
                                chapter = chapter,
                                manualUnlock = true,
                                approveWriting = true
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(tr("🔓 অধ্যায় আনলক", "🔓 Manual Unlock"), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                SparcPrimaryButton(
                    text = tr("✓ নম্বর, রাইটিং অনুমোদন ও Peer Buddy সেভ করুন", "✓ Save Grades, Writing Approval & Peer Buddy"),
                    onClick = {
                        repository.mentorEvaluateAndSupport(
                            circleId = circle.id,
                            targetUserId = safeStudent.id,
                            chapter = chapter,
                            openScores = draftOpenScores,
                            peerBuddyId = selectedBuddyId,
                            approveWriting = studentProgress.writingSubmitted
                        )
                    }
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
                    Text(trContent(session.title), style = MaterialTheme.typography.titleMedium, color = p.text, modifier = Modifier.weight(1f))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SparcCrimson)
                    ) {
                        Text(tr("লিভ সেশন", "Leave Session"))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomQuizQuestionBuilderSheet(
    book: BookItem,
    chapter: ChapterItem,
    repository: SparcRepository,
    onDismiss: () -> Unit
) {
    val p = LocalSparcPalette.current
    var isAutoScored by remember { mutableStateOf(true) }
    var sectionCode by remember { mutableStateOf("C") }
    var promptBn by remember { mutableStateOf("") }
    var opt1 by remember { mutableStateOf("") }
    var opt2 by remember { mutableStateOf("") }
    var opt3 by remember { mutableStateOf("") }
    var correctIdx by remember { mutableIntStateOf(0) }
    var explanationBn by remember { mutableStateOf("") }
    var modelPointsRaw by remember { mutableStateOf("১ পয়েন্ট: মূল তাত্ত্বিক ধারণা চিহ্নিত করা\n১ পয়েন্ট: বাস্তব উদাহরণ সংযোগ\n১ পয়েন্ট: সমালোচনামূলক মূল্যায়ন") }

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
                tr("📝 কাস্টম কুইজ ও সৃজনশীল প্রশ্ন বিল্ডার", "📝 Custom Quiz & Creative Question Builder"),
                style = MaterialTheme.typography.titleLarge,
                color = p.text
            )
            Text(
                "${book.title} • ${tr("অধ্যায়", "Chapter")} ${chapter.chapterNo}",
                fontSize = 12.sp,
                color = p.primary,
                fontWeight = FontWeight.Bold
            )

            SparcSegmentedPills(
                options = listOf(
                    tr("MCQ (Auto ১ নম্বর)", "MCQ (Auto 1 pt)"),
                    tr("সৃজনশীল (Supervisor ৩ নম্বর)", "Creative (3 pts)")
                ),
                selectedIndex = if (isAutoScored) 0 else 1,
                onSelect = {
                    isAutoScored = (it == 0)
                    sectionCode = if (isAutoScored) "C" else "H"
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("সেকশন কোড:", "Section:"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                val secChoices = if (isAutoScored) listOf("A", "B", "C", "D", "E") else listOf("F", "G", "H", "I", "J")
                secChoices.forEach { sc ->
                    FilterChip(
                        selected = sectionCode == sc,
                        onClick = { sectionCode = sc },
                        label = { Text(sc) }
                    )
                }
            }

            OutlinedTextField(
                value = promptBn,
                onValueChange = { promptBn = it },
                label = { Text(tr("প্রশ্ন লিখুন (বাংলা বা ইংরেজি)", "Question Prompt")) },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (isAutoScored) {
                listOf(
                    Triple(0, opt1, { v: String -> opt1 = v }),
                    Triple(1, opt2, { v: String -> opt2 = v }),
                    Triple(2, opt3, { v: String -> opt3 = v })
                ).forEach { (idx, value, setter) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RadioButton(selected = correctIdx == idx, onClick = { correctIdx = idx })
                        OutlinedTextField(
                            value = value,
                            onValueChange = setter,
                            label = { Text(tr("অপশন ${idx + 1} (সঠিক হলে গোল বাটন টিক দিন)", "Option ${idx + 1} (Select radio if correct)")) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = modelPointsRaw,
                    onValueChange = { modelPointsRaw = it },
                    label = { Text(tr("সুপারভাইজার মডেল পয়েন্টস (প্রতি লাইনে ১টি)", "Supervisor Model Points (1 per line)")) },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = explanationBn,
                onValueChange = { explanationBn = it },
                label = { Text(tr("Answer Key ব্যাখ্যা (ঐচ্ছিক)", "Answer Key Explanation (Optional)")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            SparcPrimaryButton(
                text = tr("প্রশ্ন যুক্ত ও লাইভ সিঙ্ক করুন", "Add Question & Sync Live"),
                enabled = promptBn.isNotBlank() && (!isAutoScored || (opt1.isNotBlank() && opt2.isNotBlank())),
                onClick = {
                    val opts = listOf(opt1, opt2, opt3).filter { it.isNotBlank() }
                    val chosenAns = opts.getOrElse(correctIdx) { opts.firstOrNull() ?: "" }
                    repository.addCustomChapterQuestion(
                        bookId = book.id,
                        chapterNo = chapter.chapterNo,
                        isAutoScored = isAutoScored,
                        sectionCode = sectionCode,
                        promptBn = promptBn,
                        options = opts,
                        correctAnswer = chosenAns,
                        explanationBn = explanationBn,
                        modelPoints = modelPointsRaw.lines()
                    )
                    onDismiss()
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

