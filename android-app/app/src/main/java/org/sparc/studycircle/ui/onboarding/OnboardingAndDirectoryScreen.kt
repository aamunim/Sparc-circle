package org.sparc.studycircle.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.UserProfile
import org.sparc.studycircle.core.theme.*
import org.sparc.studycircle.ui.components.*

private val ALL_DEPARTMENTS = listOf(
    "All Depts", "Sociology", "Education & Research (IER)", "Anthropology", "Economics"
)
private val ALL_INTEREST_TAGS = listOf(
    "Action Research", "Higher Education", "Rural Development", "Public Policy"
)
private val ALL_SKILL_TAGS = listOf(
    "Academic Writing", "Literature Review", "Data Analysis", "Field Survey"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingAndDirectoryScreen(
    state: SparcAppState,
    repository: SparcRepository
) {
    // Sub-tabs: 0 = Screen 2 (Researcher Directory), 1 = Screen 1 (Onboarding & Profile Setup)
    var subTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDept by remember { mutableStateOf("All Depts") }
    var inviteTarget by remember { mutableStateOf<UserProfile?>(null) }

    val filteredMembers = remember(state.directoryMembers, selectedDept, searchQuery) {
        state.directoryMembers.filter { m ->
            val deptMatch = selectedDept == "All Depts" || m.department == selectedDept
            val searchMatch = searchQuery.isBlank() ||
                m.name.contains(searchQuery, ignoreCase = true) ||
                m.skills.any { it.contains(searchQuery, ignoreCase = true) }
            deptMatch && searchMatch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf("গবেষক ডিরেক্টরি (১৮৪+)", "প্রোফাইল ও স্কিল সেটআপ"),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        if (subTab == 1) {
            // SCREEN 1: Onboarding, Skills & Referral Bonus Setup
            OnboardingProfileSetupView(
                currentUser = state.currentUser,
                onSave = { name, dept, interests, skills, refCode ->
                    repository.updateProfileAndReferral(name, dept, interests, skills, refCode)
                    subTab = 0
                }
            )
        } else {
            // SCREEN 2: Researcher Directory + Quick Invite Bottom Sheet
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Researcher Directory (184 Members)",
                        style = MaterialTheme.typography.titleLarge,
                        color = SparcInk
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("নাম বা দক্ষতা দিয়ে খুঁজুন...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = SparcSurface,
                            focusedContainerColor = SparcSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ALL_DEPARTMENTS) { dept ->
                            val selected = selectedDept == dept
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (selected) SparcEmerald else SparcSurface,
                                border = BorderStroke(1.dp, if (selected) SparcEmerald else SparcCardBorder),
                                modifier = Modifier.clickable { selectedDept = dept }
                            ) {
                                Text(
                                    text = if (dept == "All Depts") "সব বিভাগ" else dept,
                                    color = if (selected) Color.White else SparcInk,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                items(filteredMembers, key = { it.id }) { member ->
                    SparcScholarlyCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SparcAvatarCircle(
                                name = member.name,
                                size = 48.dp,
                                bgColor = SparcEmeraldDark
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${member.name} (${member.department})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = SparcAmberContainer
                                    ) {
                                        Text(
                                            text = "★ ${member.rating} Star",
                                            color = SparcAmberDark,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = SparcAmberContainer
                                    ) {
                                        Text(
                                            text = "🔥 ${member.readingStreakDays}d Streak",
                                            color = SparcAmberDark,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            member.skills.forEach { sk ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = SparcSubtleBg
                                ) {
                                    Text(
                                        text = sk,
                                        fontSize = 12.sp,
                                        color = SparcInk,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { inviteTarget = member },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.2.dp, SparcEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Invite to Circle (গ্রুপে আমন্ত্রণ জানান)",
                                color = SparcEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Sleek Material 3 ModalBottomSheet matching Screen 2 mockup!
    inviteTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { inviteTarget = null },
            containerColor = SparcSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            var chosenIdx by remember { mutableIntStateOf(0) }
            val templates = SparcDomainEngine.QUICK_INVITE_TEMPLATES

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Invite ${target.name}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "কুইক মেসেজ টেমপ্লেট বেছে নিন:",
                    style = MaterialTheme.typography.labelMedium,
                    color = SparcMutedInk
                )
                templates.forEachIndexed { idx, tpl ->
                    val selected = idx == chosenIdx
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) SparcEmeraldContainer.copy(alpha = 0.35f) else SparcSurface,
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) SparcEmerald else SparcCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosenIdx = idx }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tpl,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SparcEmerald
                                )
                            }
                        }
                    }
                }
                SparcPrimaryButton(
                    text = "Send Invite (+5 pts) ⊕",
                    onClick = {
                        repository.sendInviteMessage(target, templates[chosenIdx])
                        inviteTarget = null
                    }
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingProfileSetupView(
    currentUser: UserProfile,
    onSave: (String, String, List<String>, List<String>, String) -> Unit
) {
    var name by remember { mutableStateOf(currentUser.name) }
    var department by remember { mutableStateOf(currentUser.department) }
    var interests by remember { mutableStateOf(currentUser.researchInterests.toSet()) }
    var skills by remember { mutableStateOf(currentUser.skills.toSet()) }
    var referralInput by remember { mutableStateOf("SPARC-FARHAN09") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SparcScholarlyCard {
                Text(
                    text = "Create Researcher Profile • Step 2 of 2",
                    style = MaterialTheme.typography.labelLarge,
                    color = SparcMutedInk
                )
                Text(
                    text = "SPARC Study Circle",
                    style = MaterialTheme.typography.headlineSmall,
                    color = SparcEmerald
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("গবেষকের নাম (Name)") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("বিভাগ (Department)") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Research Interests (গবেষণার আগ্রহ):", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ALL_INTEREST_TAGS.forEach { tag ->
                        val active = tag in interests
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) SparcEmerald else SparcSurface,
                            border = BorderStroke(1.dp, if (active) SparcEmerald else SparcCardBorder),
                            modifier = Modifier.clickable {
                                interests = if (active) interests - tag else interests + tag
                            }
                        ) {
                            Text(
                                text = if (active) "✓ $tag" else tag,
                                color = if (active) Color.White else SparcInk,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Text("Core Research Skills (কো-অথর ম্যাচমেকিং):", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ALL_SKILL_TAGS.forEachIndexed { idx, sk ->
                        val active = sk in skills
                        val bg = when {
                            active && idx % 2 == 1 -> SparcAmberContainer
                            active -> SparcEmerald
                            else -> SparcSurface
                        }
                        val fg = when {
                            active && idx % 2 == 1 -> SparcAmberDark
                            active -> Color.White
                            else -> SparcInk
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = bg,
                            border = BorderStroke(1.dp, if (active) Color.Transparent else SparcCardBorder),
                            modifier = Modifier.clickable {
                                skills = if (active) skills - sk else skills + sk
                            }
                        ) {
                            Text(
                                text = "✦ $sk",
                                color = fg,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SparcParchment,
                    border = BorderStroke(1.dp, SparcCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Have a Referral Code? (Optional)", fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = referralInput,
                                onValueChange = { referralInput = it },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF15803D)
                            ) {
                                Text(
                                    text = "+BDT 50\nBonus",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                SparcPrimaryButton(
                    text = "Complete Setup & Join Circle",
                    onClick = {
                        onSave(name, department, interests.toList(), skills.toList(), referralInput)
                    }
                )
            }
        }
    }
}
