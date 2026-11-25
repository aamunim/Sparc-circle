package org.sparc.studycircle.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.UserProfile

private val ALL_DEPARTMENTS = listOf(
    "All", "Sociology", "Education & Research (IER)", "Anthropology", "Economics"
)
private val ALL_INTEREST_TAGS = listOf(
    "Action Research", "Higher Education", "Rural Development", "Pedagogy", "Public Policy"
)
private val ALL_SKILL_TAGS = listOf(
    "Data Analysis", "Academic Writing", "Literature Review", "Field Survey", "Qualitative Coding"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingAndDirectoryScreen(
    state: SparcAppState,
    repository: SparcRepository
) {
    var showProfileEditor by remember { mutableStateOf(false) }
    var selectedDepartment by remember { mutableStateOf("All") }
    var selectedInterest by remember { mutableStateOf<String?>(null) }
    var inviteTarget by remember { mutableStateOf<UserProfile?>(null) }

    val filteredMembers = remember(state.directoryMembers, selectedDepartment, selectedInterest) {
        state.directoryMembers.filter { m ->
            val deptOk = selectedDepartment == "All" || m.department == selectedDepartment
            val interestOk = selectedInterest == null || selectedInterest in m.researchInterests
            deptOk && interestOk
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current Researcher Profile Card (Section 2.1)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.currentUser.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${state.currentUser.department} • রেটিং ★ ${state.currentUser.rating} • ${state.currentUser.gamificationPoints} pts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { showProfileEditor = !showProfileEditor }) {
                            Text(if (showProfileEditor) "সংক্ষিপ্ত করুন" else "প্রোফাইল ও স্কিল এডিট")
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.currentUser.skills.forEach { skill ->
                            SuggestionChip(onClick = {}, label = { Text("🛠 $skill") })
                        }
                        state.currentUser.earnedBadges.forEach { badge ->
                            SuggestionChip(onClick = {}, label = { Text("🏅 $badge") })
                        }
                    }

                    Text(
                        "আপনার রেফারেল কোড: ${state.currentUser.referralCode} (শেয়ার করলে উভয়েই ৳৫০ বোনাস!)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (showProfileEditor) {
                        ProfileSetupPanel(
                            currentUser = state.currentUser,
                            onSave = { name, dept, interests, skills, refCode ->
                                repository.updateProfileAndReferral(name, dept, interests, skills, refCode)
                                showProfileEditor = false
                            }
                        )
                    }
                }
            }
        }

        // Member Directory Filter Controls (Section 2.2)
        item {
            Text("গবেষক ডিরেক্টরি ও ফিল্টারিং", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("ডিপার্টমেন্ট অনুযায়ী ফিল্টার:", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ALL_DEPARTMENTS) { dept ->
                    FilterChip(
                        selected = selectedDepartment == dept,
                        onClick = { selectedDepartment = dept },
                        label = { Text(if (dept == "All") "সব বিভাগ" else dept) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("রিসার্চ ইন্টারেস্ট অনুযায়ী ফিল্টার:", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ALL_INTEREST_TAGS) { tag ->
                    FilterChip(
                        selected = selectedInterest == tag,
                        onClick = { selectedInterest = if (selectedInterest == tag) null else tag },
                        label = { Text(tag) }
                    )
                }
            }
        }

        // Filtered Member Cards
        items(filteredMembers, key = { it.id }) { member ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${member.department} • 🔥 ${member.readingStreakDays}d স্ট্রিক",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(" ${member.rating}", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        "আগ্রহ: ${member.researchInterests.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "দক্ষতা (Skills): ${member.skills.joinToString(" • ")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { inviteTarget = member }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("গ্রুপে আমন্ত্রণ / মেসেজ")
                        }
                    }
                }
            }
        }

        // Recent In-App Messages
        if (state.messages.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("সাম্প্রতিক ইন-অ্যাপ বার্তা", style = MaterialTheme.typography.titleMedium)
            }
            items(state.messages, key = { it.id }) { msg ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${msg.senderName} • ${msg.timestampLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    inviteTarget?.let { target ->
        QuickInviteDialog(
            target = target,
            onDismiss = { inviteTarget = null },
            onSend = { text ->
                repository.sendInviteMessage(target, text)
                inviteTarget = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSetupPanel(
    currentUser: UserProfile,
    onSave: (String, String, List<String>, List<String>, String) -> Unit
) {
    var name by remember { mutableStateOf(currentUser.name) }
    var department by remember { mutableStateOf(currentUser.department) }
    var selectedInterests by remember { mutableStateOf(currentUser.researchInterests.toSet()) }
    var selectedSkills by remember { mutableStateOf(currentUser.skills.toSet()) }
    var referralInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("নাম (Name)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = department,
            onValueChange = { department = it },
            label = { Text("ডিপার্টমেন্ট (Department)") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("রিসার্চ ইন্টারেস্ট ট্যাগ নির্বাচন করুন:", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ALL_INTEREST_TAGS.forEach { tag ->
                FilterChip(
                    selected = tag in selectedInterests,
                    onClick = {
                        selectedInterests = if (tag in selectedInterests) selectedInterests - tag else selectedInterests + tag
                    },
                    label = { Text(tag) }
                )
            }
        }
        Text("স্কিল ট্যাগ (কো-অথর ম্যাচমেকিংয়ের জন্য):", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ALL_SKILL_TAGS.forEach { skill ->
                FilterChip(
                    selected = skill in selectedSkills,
                    onClick = {
                        selectedSkills = if (skill in selectedSkills) selectedSkills - skill else selectedSkills + skill
                    },
                    label = { Text(skill) }
                )
            }
        }
        OutlinedTextField(
            value = referralInput,
            onValueChange = { referralInput = it },
            label = { Text("রেফারেল কোড (ঐচ্ছিক, যেমন: SPARC-FARHAN09)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                onSave(name, department, selectedInterests.toList(), selectedSkills.toList(), referralInput)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("সংরক্ষণ করুন")
        }
    }
}

@Composable
private fun QuickInviteDialog(
    target: UserProfile,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var messageText by remember { mutableStateOf(SparcDomainEngine.QUICK_INVITE_TEMPLATES.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${target.name}-কে আমন্ত্রণ জানান") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("কুইক টেমপ্লেট বেছে নিন:", style = MaterialTheme.typography.labelMedium)
                SparcDomainEngine.QUICK_INVITE_TEMPLATES.forEach { tpl ->
                    OutlinedCard(
                        onClick = { messageText = tpl },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tpl, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("বার্তা কাস্টমাইজ করুন") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSend(messageText) }) { Text("পাঠান") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
