package org.sparc.studycircle.ui.circle

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
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
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.*
import org.sparc.studycircle.core.theme.LocalAppLanguage
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.tr
import org.sparc.studycircle.core.theme.trContent
import org.sparc.studycircle.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyCircleScreen(
    state: SparcAppState,
    repository: SparcRepository,
    onContinueReading: () -> Unit = {},
    onOpenLiveSession: () -> Unit = {}
) {
    val p = LocalSparcPalette.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val compact = state.uiCustomization.compactHomeCards

    var subTab by remember { mutableIntStateOf(0) }
    val circle = state.circles.find { it.id == state.activeCircleId } ?: state.circles.first()
    val circleBook = state.books.find { it.id == circle.bookId } ?: state.books.first()
    val chapter = circleBook.chapters.find { it.chapterNo == circle.currentGroupChapter } ?: circleBook.chapters.first()
    val myProgress = state.myChapterProgress[chapter.chapterNo]
        ?: ChapterProgressState("p-init", circle.id, state.currentUser.id, chapter.chapterNo)

    val allUsersById = remember(state.currentUser, state.directoryMembers) {
        (listOf(state.currentUser) + state.directoryMembers).associateBy { it.id }
    }
    val circlePeers = circle.memberIds.mapNotNull { allUsersById[it] }
        .filter { it.id != state.currentUser.id }
        .ifEmpty { state.directoryMembers.take(5) }

    val electedSupervisorName = circle.supervisorId?.let { allUsersById[it]?.name }
        ?.let { trContent(it) }
        ?: tr("ড. নুসরাত জাহান", "Dr. Nusrat Jahan")

    var pokeTarget by remember { mutableStateOf<UserProfile?>(null) }
    var showCreateCircleSheet by remember { mutableStateOf(false) }
    var showCustomizeCircleSheet by remember { mutableStateOf(false) }
    var privateCircleToJoin by remember { mutableStateOf<StudyCircleGroup?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf(
                    tr("হোম ও সার্কেল হাব", "Home & Circle Hub"),
                    tr("ভোটিং, Poke ও ডিবেট", "Voting, Poke & Debate")
                ),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        if (subTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
            ) {
                // Live Circle Switcher + Private/Public Join + Create Circle Row
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.circles, key = { it.id }) { c ->
                            val isSelected = c.id == circle.id
                            val isJoined = state.currentUser.id in c.memberIds
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) p.primary else p.card,
                                border = BorderStroke(1.dp, if (isSelected) p.primary else p.border),
                                modifier = Modifier.clickable {
                                    when {
                                        isJoined -> repository.selectActiveCircle(c.id)
                                        c.isPrivate -> privateCircleToJoin = c
                                        else -> repository.joinStudyCircle(c.id)
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (c.isPrivate) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else p.muted,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isJoined) {
                                            "${trContent(c.name).take(20)} (${c.memberIds.size}/${c.maxMembers})"
                                        } else {
                                            "${tr("জয়েন +", "Join +")} ${trContent(c.name).take(16)}"
                                        },
                                        color = if (isSelected) Color.White else p.text,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = p.primarySoft,
                                modifier = Modifier.clickable { showCreateCircleSheet = true }
                            ) {
                                Text(
                                    text = tr("+ নতুন সার্কেল", "+ New Circle"),
                                    color = p.primarySoftText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                // Sleek Gradient Hero Cohort Card with Privacy Badge, Schedule & Customize Trigger
                item {
                    SparcGradientHeroCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White.copy(alpha = 0.20f)
                                    ) {
                                        Text(
                                            text = "${circle.status.label(lang).uppercase()} • ${circle.memberIds.size}/${circle.maxMembers}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White.copy(alpha = 0.20f)
                                    ) {
                                        Text(
                                            text = if (circle.isPrivate) "🔒 ${circle.inviteCode ?: "PRIVATE"}" else "🌐 PUBLIC",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = trContent(circle.name),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${tr("সুপারভাইজার", "Supervisor")}: $electedSupervisorName • ${tr("অধ্যায়", "Ch")} ${circle.currentGroupChapter}",
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.20f),
                                modifier = Modifier.clickable { showCustomizeCircleSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = "Customize Circle", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text(tr("সেটআপ", "Rules"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        SparcSixStageStepper(currentStep = myProgress.stage.step, onLightGradient = true)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr(
                                    "ধাপ ${myProgress.stage.step}/৬: ${myProgress.stage.labelBn}",
                                    "Stage ${myProgress.stage.step}/6: ${myProgress.stage.labelEn}"
                                ),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${((myProgress.stage.step * 100) / 6)}%",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        if (circle.circleRules.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "📌 ${circle.circleRules}",
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                // Optional 2-Column Bento Quick Action Cards (Controlled via Pack 5 Home Widget Toggle!)
                if (state.uiCustomization.showHomeStatsRow) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SparcScholarlyCard(modifier = Modifier.weight(1f), compact = compact) {
                                Text(tr("আজকের লক্ষ্যমাত্রা", "Daily Target"), fontSize = 11.sp, color = p.muted)
                                Text(
                                    tr("${myProgress.dailyTargetPages} পৃষ্ঠা / দিন", "${myProgress.dailyTargetPages} pages / day"),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = p.primary
                                )
                                Text(
                                    tr("অধ্যায় ${chapter.chapterNo} • গোল ${state.currentUser.dailyReadingGoalMinutes} মি.", "Ch ${chapter.chapterNo} • Goal ${state.currentUser.dailyReadingGoalMinutes}m"),
                                    fontSize = 11.sp,
                                    color = p.muted
                                )
                                SparcPrimaryButton(
                                    text = tr("পড়া শুরু", "Start Reading"),
                                    onClick = onContinueReading
                                )
                            }

                            SparcScholarlyCard(modifier = Modifier.weight(1f), compact = compact) {
                                Text(tr("সাপ্তাহিক সেশন রুটিন", "Session Schedule"), fontSize = 11.sp, color = p.muted)
                                Text(
                                    circle.sessionScheduleLabel,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = p.text,
                                    maxLines = 2
                                )
                                Text(
                                    text = circle.defaultMeetingUrl.removePrefix("https://").take(22),
                                    fontSize = 11.sp,
                                    color = p.muted,
                                    maxLines = 1,
                                    modifier = Modifier.clickable {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(circle.defaultMeetingUrl)))
                                        }
                                    }
                                )
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = p.primarySoft,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenLiveSession() }
                                ) {
                                    Text(
                                        text = tr("সেশন রুম ›", "Session Room ›"),
                                        color = p.primarySoftText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .padding(vertical = 12.dp)
                                            .wrapContentWidth(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                    }
                }

                // Optional Peer Motivation (Poke) Card (Controlled via Pack 5 Home Widget Toggle!)
                if (state.uiCustomization.showHomePokeCard) {
                    item {
                        val sentToday = state.pokesToday.count { it.senderId == state.currentUser.id }
                        val leftToday = (3 - sentToday).coerceAtLeast(0)
                        SparcScholarlyCard(compact = compact) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(tr("পিয়ার মোটিভেশন (Poke)", "Peer Motivation (Poke)"), style = MaterialTheme.typography.titleMedium, color = p.text)
                                    Text(
                                        tr("আজ বাকি $leftToday/৩ • Quiet Hours ১০ PM–৭ AM", "$leftToday/3 left today • Quiet Hours 10 PM–7 AM"),
                                        fontSize = 11.5.sp,
                                        color = p.muted
                                    )
                                }
                                Text(
                                    text = tr("সব দেখুন ›", "View All ›"),
                                    color = p.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { subTab = 1 }
                                )
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(circlePeers, key = { it.id }) { peer ->
                                    val peerDisplayName = trContent(peer.name)
                                    val stg = state.memberStageByUserId[peer.id] ?: ProgressStage.READING_STARTED
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable {
                                            if (stg.step < ProgressStage.EXERCISES_SUBMITTED.step) {
                                                pokeTarget = peer
                                            }
                                        }
                                    ) {
                                        SparcAvatarCircle(
                                            name = peerDisplayName,
                                            avatarUrl = peer.avatarUrl,
                                            size = 46.dp,
                                            bgColor = if (stg.step < ProgressStage.EXERCISES_SUBMITTED.step) p.accent else p.primary
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(peerDisplayName.split(" ").first(), fontSize = 11.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Sub-tab 1: Supervisor Voting, Poke Quiet Hours/Mute/1-Tap Reply & Poll/Debate
            val pokesSentToday = state.pokesToday.count { it.senderId == state.currentUser.id }
            val remainingPokes = (3 - pokesSentToday).coerceAtLeast(0)
            var debateComment by remember { mutableStateOf("") }
            var selectedStance by remember { mutableStateOf("Support") }

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
                            Column {
                                Text(tr("সুপারভাইজার ইলেকশন", "Supervisor Election"), fontSize = 11.5.sp, color = p.muted)
                                Text(electedSupervisorName, style = MaterialTheme.typography.titleMedium, color = p.text)
                            }
                            Surface(shape = RoundedCornerShape(12.dp), color = p.primarySoft) {
                                Text(
                                    text = tr(
                                        "${circle.supervisorVotes.size}/${circle.memberIds.size.coerceAtLeast(1)} ভোট",
                                        "${circle.supervisorVotes.size}/${circle.memberIds.size.coerceAtLeast(1)} votes"
                                    ),
                                    color = p.primarySoftText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(circlePeers.take(5), key = { it.id }) { cand ->
                                val myVote = circle.supervisorVotes[state.currentUser.id] == cand.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (myVote) p.primary else p.cardAlt,
                                    modifier = Modifier.clickable {
                                        repository.voteForSupervisor(circle.id, cand.id)
                                    }
                                ) {
                                    Text(
                                        text = if (myVote) "✓ ${trContent(cand.name).split(" ").first()}" else "${tr("ভোট:", "Vote:")} ${trContent(cand.name).split(" ").first()}",
                                        color = if (myVote) Color.White else p.text,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SparcScholarlyCard {
                        Text(tr("Poke কন্ট্রোল ও Quiet Hours", "Poke Controls & Quiet Hours"), style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            tr(
                                "আজ বাকি: $remainingPokes/৩ • রাত ১০টা–৭টা Quiet Hours",
                                "Remaining today: $remainingPokes/3 • Quiet Hours 10 PM–7 AM"
                            ),
                            fontSize = 12.sp,
                            color = p.muted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MuteScope.entries.forEach { scope ->
                                val active = state.activeMuteScope == scope
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) p.primary else p.cardAlt,
                                    modifier = Modifier.clickable {
                                        repository.setPokeMuteScope(if (active) null else scope)
                                    }
                                ) {
                                    Text(
                                        text = "${tr("মিউট", "Mute")} ${scope.label(lang)}",
                                        color = if (active) Color.White else p.text,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        state.pokesToday.filter { it.receiverId == state.currentUser.id }.forEach { pk ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = p.cardAlt,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("${trContent(pk.senderName)}: \"${trContent(pk.presetMessage)}\"", fontWeight = FontWeight.Bold, color = p.text)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SparcDomainEngine.QUICK_POKE_REPLIES.forEach { rep ->
                                            val chosen = pk.quickReply == rep
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (chosen) p.primary else p.card,
                                                border = BorderStroke(1.dp, p.border),
                                                modifier = Modifier.clickable { repository.replyToPoke(pk.id, rep) }
                                            ) {
                                                Text(
                                                    text = trContent(rep),
                                                    color = if (chosen) Color.White else p.text,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    val poll = state.polls.first()
                    val totalVotes = poll.votesByUser.size.coerceAtLeast(1)
                    SparcScholarlyCard {
                        Text(tr("কুইক পোল ও ডিবেট কর্নার", "Quick Poll & Debate Corner"), style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(trContent(poll.question), style = MaterialTheme.typography.bodyMedium, color = p.muted)

                        poll.options.forEachIndexed { idx, opt ->
                            val count = poll.votesByUser.values.count { it == idx }
                            val pct = ((count.toFloat() / totalVotes.toFloat()) * 100).toInt()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { repository.voteInPoll(poll.id, idx) },
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(trContent(opt), fontSize = 12.5.sp, color = p.text, modifier = Modifier.weight(1f))
                                    Text("$pct%", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = p.primary)
                                }
                                LinearProgressIndicator(
                                    progress = { (pct / 100f).coerceIn(0.08f, 1f) },
                                    color = if (idx % 2 == 0) p.primary else p.accent,
                                    trackColor = p.cardAlt,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Support", "Counter").forEach { st ->
                                val active = selectedStance == st
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (active) p.primarySoft else p.cardAlt,
                                    modifier = Modifier.clickable { selectedStance = st }
                                ) {
                                    Text(
                                        text = st,
                                        color = if (active) p.primarySoftText else p.muted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = debateComment,
                                onValueChange = { debateComment = it },
                                placeholder = { Text(tr("যুক্তি লিখুন...", "Share your argument...")) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    repository.postDebateComment(selectedStance, debateComment)
                                    debateComment = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = p.primary)
                            ) {
                                Text(tr("পোস্ট", "Post"))
                            }
                        }

                        state.debateComments.take(6).forEach { dc ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = p.cardAlt,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "${trContent(dc.authorName)} • ${dc.stance}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = p.primary
                                    )
                                    Text(dc.body, fontSize = 12.5.sp, color = p.text)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateCircleSheet) {
        CircleCustomizationBottomSheet(
            isCreateMode = true,
            books = state.books,
            existingCircle = null,
            onDismiss = { showCreateCircleSheet = false },
            onSaveCreate = { name, bk, minM, maxM, isPriv, code, sched, url, rules ->
                repository.createNewStudyCircle(
                    name = name,
                    book = bk,
                    minMembers = minM,
                    maxMembers = maxM,
                    isPrivate = isPriv,
                    inviteCode = code,
                    scheduleLabel = sched,
                    meetingUrl = url,
                    rules = rules
                )
                showCreateCircleSheet = false
            },
            onSaveUpdate = { _, _, _, _, _, _, _, _, _ -> }
        )
    }

    if (showCustomizeCircleSheet) {
        CircleCustomizationBottomSheet(
            isCreateMode = false,
            books = state.books,
            existingCircle = circle,
            onDismiss = { showCustomizeCircleSheet = false },
            onSaveCreate = { _, _, _, _, _, _, _, _, _ -> },
            onSaveUpdate = { name, minM, maxM, isPriv, code, sched, url, rules, activeCh ->
                repository.updateStudyCircleCustomization(
                    circleId = circle.id,
                    name = name,
                    minMembers = minM,
                    maxMembers = maxM,
                    isPrivate = isPriv,
                    inviteCode = code,
                    scheduleLabel = sched,
                    meetingUrl = url,
                    rules = rules,
                    currentGroupChapter = activeCh
                )
                showCustomizeCircleSheet = false
            }
        )
    }

    privateCircleToJoin?.let { targetCircle ->
        var enteredCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { privateCircleToJoin = null },
            containerColor = p.card,
            titleContentColor = p.text,
            textContentColor = p.text,
            title = { Text(tr("🔒 প্রাইভেট সার্কেলে জয়েন করুন", "🔒 Join Private Study Circle")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        tr(
                            "\"${targetCircle.name}\"-এ যুক্ত হতে সার্কেলের Invite Code লিখুন:",
                            "Enter the Invite Code to join \"${targetCircle.name}\":"
                        ),
                        fontSize = 12.5.sp,
                        color = p.muted
                    )
                    OutlinedTextField(
                        value = enteredCode,
                        onValueChange = { enteredCode = it.uppercase() },
                        label = { Text("Invite Code (e.g. SPARC-XXXXX)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (repository.joinStudyCircle(targetCircle.id, enteredCode)) {
                            privateCircleToJoin = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = p.primary)
                ) {
                    Text(tr("জয়েন করুন", "Join Circle"))
                }
            },
            dismissButton = {
                TextButton(onClick = { privateCircleToJoin = null }) {
                    Text(tr("বাতিল", "Cancel"), color = p.muted)
                }
            }
        )
    }

    pokeTarget?.let { receiver ->
        AlertDialog(
            onDismissRequest = { pokeTarget = null },
            containerColor = p.card,
            titleContentColor = p.text,
            textContentColor = p.text,
            title = { Text(tr("${receiver.name}-কে উৎসাহ পাঠান", "Send Encouragement to ${trContent(receiver.name)}")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SparcDomainEngine.PRESET_POKE_MESSAGES.forEach { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = p.cardAlt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    repository.sendPokeToMember(circle.id, receiver, msg)
                                    pokeTarget = null
                                }
                        ) {
                            Text(trContent(msg), color = p.text, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pokeTarget = null }) { Text(tr("বন্ধ করুন", "Close"), color = p.primary) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CircleCustomizationBottomSheet(
    isCreateMode: Boolean,
    books: List<BookItem>,
    existingCircle: StudyCircleGroup?,
    onDismiss: () -> Unit,
    onSaveCreate: (String, BookItem, Int, Int, Boolean, String?, String, String, String) -> Unit,
    onSaveUpdate: (String, Int, Int, Boolean, String?, String, String, String, Int) -> Unit
) {
    val p = LocalSparcPalette.current
    var name by remember { mutableStateOf(existingCircle?.name ?: "") }
    var selectedBookIdx by remember { mutableIntStateOf(0) }
    var minMembers by remember { mutableIntStateOf(existingCircle?.minMembersRequired ?: 5) }
    var maxMembers by remember { mutableIntStateOf(existingCircle?.maxMembers ?: 25) }
    var isPrivate by remember { mutableStateOf(existingCircle?.isPrivate ?: false) }
    var inviteCode by remember { mutableStateOf(existingCircle?.inviteCode ?: "") }
    var scheduleLabel by remember { mutableStateOf(existingCircle?.sessionScheduleLabel ?: "শুক্রবার রাত ৮:৩০") }
    var meetingUrl by remember { mutableStateOf(existingCircle?.defaultMeetingUrl ?: "https://meet.jit.si/SPARC_Circle_Live") }
    var rules by remember {
        mutableStateOf(
            existingCircle?.circleRules
                ?: "নিয়মিত অধ্যায় পড়া ও সহপাঠীদের প্রতি শ্রদ্ধাশীল থেকে আলোচনায় অংশ নেওয়া।"
        )
    }
    var activeChapter by remember { mutableIntStateOf(existingCircle?.currentGroupChapter ?: 1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        contentColor = p.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    text = if (isCreateMode) tr("⭕ নতুন কাস্টম স্টাডি সার্কেল তৈরি করুন", "⭕ Create Custom Study Circle")
                    else tr("⚙️ স্টাডি সার্কেল রুলস ও শিডিউল কাস্টমাইজ করুন", "⚙️ Customize Circle Rules & Schedule"),
                    style = MaterialTheme.typography.titleLarge,
                    color = p.text
                )
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("সার্কেলের নাম", "Circle Name")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isCreateMode) {
                item {
                    Text(tr("বই নির্বাচন করুন:", "Select Book:"), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(books.size) { idx ->
                            val bk = books[idx]
                            val sel = selectedBookIdx == idx
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (sel) p.primary else p.cardAlt,
                                modifier = Modifier.clickable { selectedBookIdx = idx }
                            ) {
                                Text(
                                    bk.title.take(28),
                                    color = if (sel) Color.White else p.text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr("গ্রুপের বর্তমান অ্যাক্টিভ অধ্যায়:", "Current Active Chapter:"), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { activeChapter = (activeChapter - 1).coerceAtLeast(1) }) { Text("-") }
                            Text("$activeChapter", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = p.primary)
                            OutlinedButton(onClick = { activeChapter += 1 }) { Text("+") }
                        }
                    }
                }
            }

            item {
                SparcSegmentedPills(
                    options = listOf(
                        tr("🌐 Public (সবার জন্য উন্মুক্ত)", "🌐 Public Open"),
                        tr("🔒 Private (Invite Code)", "🔒 Private Code")
                    ),
                    selectedIndex = if (isPrivate) 1 else 0,
                    onSelect = { isPrivate = (it == 1) }
                )
                if (isPrivate) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.uppercase() },
                        label = { Text(tr("গোপন Invite Code (ফাঁকা রাখলে অটো তৈরি হবে)", "Custom Invite Code (Leave blank to auto-generate)")) },
                        placeholder = { Text("SPARC-2026") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Text(
                    tr("সদস্য সীমা: ন্যূনতম $minMembers জন • সর্বোচ্চ $maxMembers জন", "Member Limits: Min $minMembers • Max $maxMembers"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
                Slider(
                    value = minMembers.toFloat(),
                    onValueChange = {
                        minMembers = it.toInt().coerceIn(2, 20)
                        if (maxMembers < minMembers) maxMembers = minMembers
                    },
                    valueRange = 2f..20f
                )
                Slider(
                    value = maxMembers.toFloat(),
                    onValueChange = { maxMembers = it.toInt().coerceIn(minMembers, 30) },
                    valueRange = 3f..30f
                )
            }

            item {
                OutlinedTextField(
                    value = scheduleLabel,
                    onValueChange = { scheduleLabel = it },
                    label = { Text(tr("সাপ্তাহিক সেশনের দিন ও সময়", "Weekly Session Day & Time")) },
                    placeholder = { Text("প্রতি শুক্রবার রাত ৯:০০") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = meetingUrl,
                    onValueChange = { meetingUrl = it },
                    label = { Text(tr("ডিফল্ট লাইভ মিটিং লিংক (Jitsi / Meet / Zoom)", "Default Meeting Link (Jitsi / Meet / Zoom)")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = rules,
                    onValueChange = { rules = it },
                    label = { Text(tr("সার্কেলের নিয়মাবলি ও মটো", "Circle Rules & Motto")) },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SparcPrimaryButton(
                    text = if (isCreateMode) tr("কাস্টম সার্কেল তৈরি করুন", "Create Custom Circle")
                    else tr("সার্কেল সেটিংস সংরক্ষণ করুন", "Save Circle Customization"),
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (isCreateMode) {
                            onSaveCreate(
                                name,
                                books[selectedBookIdx],
                                minMembers,
                                maxMembers,
                                isPrivate,
                                inviteCode.ifBlank { null },
                                scheduleLabel,
                                meetingUrl,
                                rules
                            )
                        } else {
                            onSaveUpdate(
                                name,
                                minMembers,
                                maxMembers,
                                isPrivate,
                                inviteCode.ifBlank { null },
                                scheduleLabel,
                                meetingUrl,
                                rules,
                                activeChapter
                            )
                        }
                    }
                )
            }
        }
    }
}
