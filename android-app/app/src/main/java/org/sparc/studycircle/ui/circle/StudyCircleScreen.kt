package org.sparc.studycircle.ui.circle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun StudyCircleScreen(
    state: SparcAppState,
    repository: SparcRepository,
    onContinueReading: () -> Unit = {},
    onOpenLiveSession: () -> Unit = {}
) {
    val p = LocalSparcPalette.current
    val lang = LocalAppLanguage.current
    var subTab by remember { mutableIntStateOf(0) }
    val circle = state.circles.find { it.id == state.activeCircleId } ?: state.circles.first()
    val chapter = (state.books.find { it.id == circle.bookId } ?: state.books.first()).chapters.first()
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
    var showCreateCircleDialog by remember { mutableStateOf(false) }

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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live Circle Switcher + Create / Join Any Circle Row
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
                                    if (isJoined) {
                                        repository.selectActiveCircle(c.id)
                                    } else {
                                        repository.joinStudyCircle(c.id)
                                    }
                                }
                            ) {
                                Text(
                                    text = if (isJoined) {
                                        "${trContent(c.name).take(22)} (${c.memberIds.size})"
                                    } else {
                                        "${tr("জয়েন +", "Join +")} ${trContent(c.name).take(18)}"
                                    },
                                    color = if (isSelected) Color.White else p.text,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = p.primarySoft,
                                modifier = Modifier.clickable { showCreateCircleDialog = true }
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

                // Sleek v3.0 Gradient Hero Cohort Card with 6-Segment Capsule Track
                item {
                    SparcGradientHeroCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.White.copy(alpha = 0.20f)
                                ) {
                                    Text(
                                        text = "${circle.status.label(lang).uppercase()} • ${circle.memberIds.size}/${circle.minMembersRequired}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = trContent(circle.name),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${tr("সুপারভাইজার", "Supervisor")}: $electedSupervisorName",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${((myProgress.stage.step * 100) / 6)}%", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text("Stage", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
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
                        }
                    }
                }

                // 2-Column Bento Quick Action Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SparcScholarlyCard(modifier = Modifier.weight(1f)) {
                            Text(tr("আজকের লক্ষ্যমাত্রা", "Daily Target"), fontSize = 11.sp, color = p.muted)
                            Text(
                                tr("${myProgress.dailyTargetPages} পৃষ্ঠা / দিন", "${myProgress.dailyTargetPages} pages / day"),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = p.primary
                            )
                            Text(
                                tr("অধ্যায় ১ • ${chapter.totalPages} পৃষ্ঠা", "Ch 1 • ${chapter.totalPages} pages"),
                                fontSize = 11.sp,
                                color = p.muted
                            )
                            SparcPrimaryButton(
                                text = tr("পড়া শুরু", "Start Reading"),
                                onClick = onContinueReading
                            )
                        }

                        SparcScholarlyCard(modifier = Modifier.weight(1f)) {
                            Text(tr("পরবর্তী লাইভ সেশন", "Next Live Session"), fontSize = 11.sp, color = p.muted)
                            Text(
                                tr("শুক্রবার ৮:৩০", "Fri 8:30 PM"),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = p.text
                            )
                            Text(tr("Jitsi In-App • ৪০ মি.", "Jitsi In-App • 40m"), fontSize = 11.sp, color = p.muted)
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

                // Peer Motivation (Poke) Card
                item {
                    val sentToday = state.pokesToday.count { it.senderId == state.currentUser.id }
                    val leftToday = (3 - sentToday).coerceAtLeast(0)
                    SparcScholarlyCard {
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

    if (showCreateCircleDialog) {
        var newCircleName by remember { mutableStateOf("") }
        var selectedBookIdx by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showCreateCircleDialog = false },
            containerColor = p.card,
            titleContentColor = p.text,
            textContentColor = p.text,
            title = { Text(tr("নতুন স্টাডি সার্কেল তৈরি করুন", "Create New Study Circle")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newCircleName,
                        onValueChange = { newCircleName = it },
                        label = { Text(tr("সার্কেলের নাম", "Circle Name")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.books.forEachIndexed { idx, bk ->
                        FilterChip(
                            selected = selectedBookIdx == idx,
                            onClick = { selectedBookIdx = idx },
                            label = { Text(bk.title.take(32)) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCircleName.isNotBlank()) {
                            repository.createNewStudyCircle(newCircleName, state.books[selectedBookIdx], 5)
                            showCreateCircleDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = p.primary)
                ) {
                    Text(tr("তৈরি করুন", "Create"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCircleDialog = false }) {
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
