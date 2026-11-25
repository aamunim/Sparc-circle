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
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.ui.components.*

@Composable
fun StudyCircleScreen(
    state: SparcAppState,
    repository: SparcRepository,
    onContinueReading: () -> Unit = {},
    onOpenLiveSession: () -> Unit = {}
) {
    val p = LocalSparcPalette.current
    var subTab by remember { mutableIntStateOf(0) }
    val circle = state.circles.first()
    val chapter = state.books.first().chapters.first()
    val myProgress = state.myChapterProgress[chapter.chapterNo]
        ?: ChapterProgressState("p-init", circle.id, state.currentUser.id, chapter.chapterNo)

    val allUsersById = remember(state.currentUser, state.directoryMembers) {
        (listOf(state.currentUser) + state.directoryMembers).associateBy { it.id }
    }
    val circlePeers = circle.memberIds.mapNotNull { allUsersById[it] }
        .filter { it.id != state.currentUser.id }

    var pokeTarget by remember { mutableStateOf<UserProfile?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf("হোম ও সার্কেল হাব", "ভোটিং, Poke ও ডিবেট"),
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
                                        text = "ACTIVE COHORT • ${circle.memberIds.size}/${circle.minMembersRequired}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = circle.name,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "সুপারভাইজার: ড. নুসরাত জাহান",
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
                                    Text("83%", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text("Ready", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
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
                                text = "ধাপ ${myProgress.stage.step}/৬: ${myProgress.stage.labelBn}",
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
                            Text("আজকের লক্ষ্যমাত্রা", fontSize = 11.sp, color = p.muted)
                            Text(
                                "${myProgress.dailyTargetPages} পৃষ্ঠা / দিন",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = p.primary
                            )
                            Text("১৮/${chapter.totalPages} পৃষ্ঠা সম্পন্ন", fontSize = 11.sp, color = p.muted)
                            SparcPrimaryButton(
                                text = "পড়া শুরু",
                                onClick = onContinueReading
                            )
                        }

                        SparcScholarlyCard(modifier = Modifier.weight(1f)) {
                            Text("পরবর্তী লাইভ সেশন", fontSize = 11.sp, color = p.muted)
                            Text(
                                "শুক্রবার ৮:৩০",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = p.text
                            )
                            Text("Jitsi In-App • ৪০ মি.", fontSize = 11.sp, color = p.muted)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = p.primarySoft,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenLiveSession() }
                            ) {
                                Text(
                                    text = "সেশন রুম ›",
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
                    SparcScholarlyCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("পিয়ার মোটিভেশন (Poke)", style = MaterialTheme.typography.titleMedium, color = p.text)
                                Text("আজ বাকি ২/৩ • Quiet Hours ১০ PM–৭ AM", fontSize = 11.5.sp, color = p.muted)
                            }
                            Text(
                                text = "সব দেখুন ›",
                                color = p.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { subTab = 1 }
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(circlePeers, key = { it.id }) { peer ->
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
                                        name = peer.name,
                                        size = 46.dp,
                                        bgColor = if (stg.step < ProgressStage.EXERCISES_SUBMITTED.step) p.accent else p.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(peer.name.split(" ").first(), fontSize = 11.sp, color = p.text, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Sub-tab 1: Supervisor Voting, Poke Quiet Hours/Mute/1-Tap Reply & Poll/Debate
            val pokesSentToday = state.pokesToday.count { it.senderId == state.currentUser.id }
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
                                Text("সুপারভাইজার ইলেকশন", fontSize = 11.5.sp, color = p.muted)
                                Text("ড. নুসরাত জাহান", style = MaterialTheme.typography.titleMedium, color = p.text)
                            }
                            Surface(shape = RoundedCornerShape(12.dp), color = p.primarySoft) {
                                Text(
                                    text = "${circle.supervisorVotes.size}/${circle.memberIds.size} ভোটে নির্বাচিত",
                                    color = p.primarySoftText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    SparcScholarlyCard {
                        Text("Poke কন্ট্রোল ও Quiet Hours", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            "আজ বাকি: ${(3 - pokesSentToday).coerceAtLeast(0)}/৩ • রাত ১০টা–৭টা Quiet Hours",
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
                                        text = "মিউট ${scope.labelBn}",
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
                                    Text("${pk.senderName}: \"${pk.presetMessage}\"", fontWeight = FontWeight.Bold, color = p.text)
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
                                                    text = rep,
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
                        Text("কুইক পোল ও ডিবেট কর্নার", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(poll.question, style = MaterialTheme.typography.bodyMedium, color = p.muted)

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
                                    Text(opt, fontSize = 12.5.sp, color = p.text, modifier = Modifier.weight(1f))
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
                                placeholder = { Text("যুক্তি লিখুন...") },
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
                                Text("পোস্ট")
                            }
                        }
                    }
                }
            }
        }
    }

    pokeTarget?.let { receiver ->
        AlertDialog(
            onDismissRequest = { pokeTarget = null },
            containerColor = p.card,
            titleContentColor = p.text,
            textContentColor = p.text,
            title = { Text("${receiver.name}-কে উৎসাহ পাঠান") },
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
                            Text(msg, color = p.text, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pokeTarget = null }) { Text("বন্ধ করুন", color = p.primary) }
            }
        )
    }
}
