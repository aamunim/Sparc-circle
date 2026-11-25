package org.sparc.studycircle.ui.circle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.*
import org.sparc.studycircle.core.theme.*
import org.sparc.studycircle.ui.components.*

@Composable
fun StudyCircleScreen(
    state: SparcAppState,
    repository: SparcRepository,
    onContinueReading: () -> Unit = {}
) {
    // Sub-tabs: 0 = Screen 5 (Study Circle Hub), 1 = Screen 6 (Supervisor Election, Poke & Debate)
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
                options = listOf("স্টাডি সার্কেল হাব", "ভোটিং, Poke ও ডিবেট পোল"),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        if (subTab == 0) {
            // SCREEN 5: Hero Cohort Hub, 83% Readiness Ring, 6-Stage Stepper & Daily Target
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SparcScholarlyCard {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SparcInk)) {
                                    append("CPAR Cohort-01 ")
                                }
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = SparcEmerald)) {
                                    append("(${circle.status.name.lowercase().replaceFirstChar { it.uppercase() }})")
                                }
                            },
                            fontSize = 19.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            SparcCircularGauge(
                                percentage = 83f,
                                centerTopText = "83%",
                                size = 84.dp,
                                strokeWidth = 8.dp
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Group Session Ready", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "সদস্য: ${circle.memberIds.size} জন (ন্যূনতম ${circle.minMembersRequired})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SparcMutedInk
                                )
                            }
                        }

                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Next Live Session: ")
                                }
                                append("Fri 8:30 PM (Jitsi Live)")
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )

                        SparcSixStageStepper(currentStep = myProgress.stage.step)

                        Text(
                            text = "Stage ${myProgress.stage.step} of 6: ${myProgress.stage.labelBn}",
                            style = MaterialTheme.typography.labelLarge,
                            color = SparcInk,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                // Daily Reading Target Card
                item {
                    SparcScholarlyCard {
                        Text("Daily Reading Target", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${myProgress.dailyTargetPages} Pages Today – Chapter 1: Practice Architectures – 18 of ${chapter.totalPages} pages read",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SparcInk
                        )
                        SparcPrimaryButton(
                            text = "Continue Reading (পড়া চালিয়ে যান)",
                            onClick = onContinueReading
                        )
                    }
                }

                // Peer Motivation (Poke) Horizontal Avatar Strip
                item {
                    SparcScholarlyCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Peer Motivation (Poke)", style = MaterialTheme.typography.titleMedium)
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = SparcSubtleBg,
                                border = BorderStroke(1.dp, SparcCardBorder),
                                modifier = Modifier.clickable {
                                    pokeTarget = circlePeers.lastOrNull()
                                }
                            ) {
                                Text(
                                    text = "Send Encouragement",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
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
                                        size = 52.dp,
                                        bgColor = if (stg.step < ProgressStage.EXERCISES_SUBMITTED.step)
                                            SparcAmber else SparcEmerald
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = peer.name.split(" ").first(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // SCREEN 6: Supervisor Election Seal, Poke Mute/1-Tap Reply & Debate Poll Bars
            val pokesSentToday = state.pokesToday.count { it.senderId == state.currentUser.id }
            var debateComment by remember { mutableStateOf("") }
            var selectedStance by remember { mutableStateOf("Support") }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Supervisor Election Card
                item {
                    SparcScholarlyCard {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SparcEmeraldContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Supervisor Election • CPAR Cohort-01",
                                fontWeight = FontWeight.Bold,
                                color = SparcEmeraldDark,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(SparcEmerald),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "★\nELECTED",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 11.sp
                                )
                            }
                            Column {
                                Text("ড. নুসরাত জাহান", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "(Member Voted • ${circle.supervisorVotes.size}/${circle.memberIds.size} Votes)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SparcMutedInk
                                )
                            }
                        }
                    }
                }

                // Peer Motivation & Quiet Hours + Mute Segmented Bar + 1-Tap Quick Reply
                item {
                    SparcScholarlyCard {
                        Text("Peer Motivation & Quiet Hours", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Pokes Left Today: ${(3 - pokesSentToday).coerceAtLeast(0)}/3 • Quiet Hours 10 PM - 7 AM",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SparcMutedInk
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Mute", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            MuteScope.entries.forEach { scope ->
                                val active = state.activeMuteScope == scope
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (active) SparcEmerald else SparcSurface,
                                    border = BorderStroke(1.dp, SparcEmerald),
                                    modifier = Modifier.clickable {
                                        repository.setPokeMuteScope(if (active) null else scope)
                                    }
                                ) {
                                    Text(
                                        text = scope.labelBn,
                                        color = if (active) Color.White else SparcInk,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        state.pokesToday.filter { it.receiverId == state.currentUser.id }.forEach { pk ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = SparcSubtleBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Incoming encouragement", fontSize = 11.sp, color = SparcMutedInk)
                                    Text(
                                        "${pk.senderName}: \"${pk.presetMessage}\"",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("1-tap quick reply:", fontSize = 12.sp, color = SparcMutedInk)
                                SparcDomainEngine.QUICK_POKE_REPLIES.forEach { rep ->
                                    val chosen = pk.quickReply == rep
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (chosen) SparcEmerald else SparcSubtleBg,
                                        border = BorderStroke(1.dp, SparcCardBorder),
                                        modifier = Modifier.clickable { repository.replyToPoke(pk.id, rep) }
                                    ) {
                                        Text(
                                            text = rep,
                                            color = if (chosen) Color.White else SparcInk,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Poll & Debate Corner with Horizontal Percentage Bars
                item {
                    val poll = state.polls.first()
                    val totalVotes = poll.votesByUser.size.coerceAtLeast(1)
                    SparcScholarlyCard {
                        Text("Chapter 1 Quick Poll & Debate Corner", style = MaterialTheme.typography.titleLarge)
                        Text(poll.question, style = MaterialTheme.typography.bodyMedium)

                        poll.options.forEachIndexed { idx, opt ->
                            val count = poll.votesByUser.values.count { it == idx }
                            val pct = ((count.toFloat() / totalVotes.toFloat()) * 100).toInt()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { repository.voteInPoll(poll.id, idx) },
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("$opt – $pct%", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                LinearProgressIndicator(
                                    progress = { (pct / 100f).coerceIn(0.08f, 1f) },
                                    color = if (idx % 2 == 0) SparcEmerald else SparcAmber,
                                    trackColor = SparcSubtleBg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                            }
                        }

                        HorizontalDivider(color = SparcCardBorder)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Stance:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            listOf("Support", "Counter").forEach { st ->
                                val active = selectedStance == st
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (active) SparcEmeraldContainer else SparcAmberContainer,
                                    modifier = Modifier.clickable { selectedStance = st }
                                ) {
                                    Text(
                                        text = st,
                                        color = if (active) SparcEmeraldDark else SparcAmberDark,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
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
                                colors = ButtonDefaults.buttonColors(containerColor = SparcEmerald)
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
            title = { Text("${receiver.name}-কে উৎসাহ পাঠান") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SparcDomainEngine.PRESET_POKE_MESSAGES.forEach { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SparcSubtleBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    repository.sendPokeToMember(circle.id, receiver, msg)
                                    pokeTarget = null
                                }
                        ) {
                            Text(msg, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pokeTarget = null }) { Text("বন্ধ করুন") }
            }
        )
    }
}
