package org.sparc.studycircle.ui.circle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.*

@Composable
fun StudyCircleScreen(
    state: SparcAppState,
    repository: SparcRepository
) {
    val circle = state.circles.first()
    val allUsersById = remember(state.currentUser, state.directoryMembers) {
        (listOf(state.currentUser) + state.directoryMembers).associateBy { it.id }
    }
    val circleMembers = circle.memberIds.mapNotNull { allUsersById[it] }
    val supervisor = circle.supervisorId?.let { allUsersById[it] }

    var pokeReceiver by remember { mutableStateOf<UserProfile?>(null) }
    var debateStance by remember { mutableStateOf("পক্ষে (Support)") }
    var debateInput by remember { mutableStateOf("") }

    val pokesSentByMeToday = state.pokesToday.count { it.senderId == state.currentUser.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Circle Header & Threshold Activation (Section 4.1)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(circle.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Badge(
                            containerColor = if (circle.status == CircleStatus.ACTIVE)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        ) {
                            Text(circle.status.labelBn, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text("বই: ${circle.bookTitle}", style = MaterialTheme.typography.bodyMedium)
                    val ratio = (circle.memberIds.size.toFloat() / circle.minMembersRequired.toFloat()).coerceAtMost(1f)
                    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "সদস্য সংখ্যা: ${circle.memberIds.size} / ন্যূনতম ${circle.minMembersRequired} জন (থ্রেশহোল্ড পূর্ণ হলে Active)",
                        style = MaterialTheme.typography.labelMedium
                    )

                    HorizontalDivider()
                    // Dual Supervisor Assignment & Voting (Section 4.2)
                    Text(
                        text = if (supervisor != null) {
                            val modeBn = if (circle.supervisorAssignmentType == SupervisorAssignmentType.ADMIN_ASSIGNED)
                                "অ্যাডমিন অ্যাসাইনড" else "সদস্যদের ভোটে নির্বাচিত"
                            "সুপারভাইজার: ${supervisor.name} ($modeBn)"
                        } else {
                            "সুপারভাইজার এখনো নির্বাচিত হয়নি — ভোট দিন:"
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(circleMembers.filter { it.id != state.currentUser.id }) { candidate ->
                            AssistChip(
                                onClick = { repository.voteForSupervisor(circle.id, candidate.id) },
                                leadingIcon = {
                                    Icon(Icons.Default.HowToVote, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("${candidate.name} (★${candidate.rating})") }
                            )
                        }
                    }
                }
            }
        }

        // Peer Motivation ('Poke' Button) Controls & Mute Settings (Section 4.3)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "পিয়ার মোটিভেশন ('Poke' বাটন) • আজ পাঠানো হয়েছে: $pokesSentByMeToday/৩",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "নিয়ম: রাত ১০টা–সকাল ৭টা Quiet Hours। দিনে সর্বোচ্চ ৩টি নোটিফিকেশন তাৎক্ষণিক যাবে, বাকিগুলো ডেইলি সামারিতে। সুপারভাইজার শুধু Poke সংখ্যা দেখবেন, বার্তা নয়।",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (state.activeMuteScope == null) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        MuteScope.entries.forEach { scope ->
                            FilterChip(
                                selected = state.activeMuteScope == scope,
                                onClick = {
                                    repository.setPokeMuteScope(if (state.activeMuteScope == scope) null else scope)
                                },
                                label = { Text("মিউট: ${scope.labelBn}") }
                            )
                        }
                    }
                }
            }
        }

        // Group Member Progress List (Public Status Only — No Private Scores! Sec 5.4)
        item {
            Text("সদস্যদের প্রগ্রেস ও উৎসাহ প্রদান (নম্বর গোপন রাখা হয়েছে)", style = MaterialTheme.typography.titleMedium)
        }

        items(circleMembers, key = { it.id }) { member ->
            val stg = state.memberStageByUserId[member.id] ?: ProgressStage.NOT_STARTED
            val canBePoked = member.id != state.currentUser.id &&
                stg.step < ProgressStage.EXERCISES_SUBMITTED.step

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(member.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "ধাপ ${stg.step}/৬: ${stg.labelBn}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (canBePoked) {
                        FilledTonalButton(
                            onClick = { pokeReceiver = member },
                            enabled = pokesSentByMeToday < 3
                        ) {
                            Text("👋 Poke")
                        }
                    }
                }
            }
        }

        // Received Pokes with 1-Tap Quick Reply ("ধন্যবাদ!" / "করছি!")
        val receivedPokes = state.pokesToday.filter { it.receiverId == state.currentUser.id }
        if (receivedPokes.isNotEmpty()) {
            item {
                Text("আপনার প্রাপ্ত উৎসাহ বার্তা (১-ট্যাপ রিপ্লাই)", style = MaterialTheme.typography.titleMedium)
            }
            items(receivedPokes, key = { it.id }) { pk ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${pk.senderName}: \"${pk.presetMessage}\"", fontWeight = FontWeight.Medium)
                        if (pk.quickReply != null) {
                            Text("আপনার রিপ্লাই: ${pk.quickReply}", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SparcDomainEngine.QUICK_POKE_REPLIES.forEach { reply ->
                                    OutlinedButton(onClick = { repository.replyToPoke(pk.id, reply) }) {
                                        Text(reply)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Poll & Debate Corner (Section 4.3)
        item {
            Spacer(Modifier.height(4.dp))
            Text("কুইক পোল ও ডিবেট কর্নার", style = MaterialTheme.typography.titleMedium)
        }

        items(state.polls, key = { it.id }) { poll ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(poll.question, fontWeight = FontWeight.SemiBold)
                    val myChoice = poll.votesByUser[state.currentUser.id]
                    poll.options.forEachIndexed { idx, opt ->
                        val count = poll.votesByUser.values.count { it == idx }
                        OutlinedCard(
                            onClick = { repository.voteInPoll(poll.id, idx) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = myChoice == idx, onClick = { repository.voteInPoll(poll.id, idx) })
                                Text(opt, modifier = Modifier.weight(1f))
                                Text("$count ভোট", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("বিতর্ক ও মতামত যোগ করুন:", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("পক্ষে (Support)", "বিপক্ষে (Counter)", "সমন্বয় (Synthesis)").forEach { st ->
                            FilterChip(
                                selected = debateStance == st,
                                onClick = { debateStance = st },
                                label = { Text(st) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = debateInput,
                        onValueChange = { debateInput = it },
                        label = { Text("অধ্যায়ভিত্তিক যুক্তি লিখুন") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        repository.postDebateComment(debateStance, debateInput)
                        debateInput = ""
                    }) {
                        Text("মতামত প্রকাশ করুন")
                    }
                    state.debateComments.forEach { c ->
                        HorizontalDivider()
                        Text("${c.authorName} • [${c.stance}]", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(c.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    pokeReceiver?.let { receiver ->
        AlertDialog(
            onDismissRequest = { pokeReceiver = null },
            title = { Text("${receiver.name}-কে উৎসাহ দিন") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SparcDomainEngine.PRESET_POKE_MESSAGES.forEach { msg ->
                        OutlinedCard(
                            onClick = {
                                repository.sendPokeToMember(circle.id, receiver, msg)
                                pokeReceiver = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(msg, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pokeReceiver = null }) { Text("বন্ধ করুন") }
            }
        )
    }
}
