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
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.tr
import org.sparc.studycircle.core.theme.trContent
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
    val p = LocalSparcPalette.current
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
                options = listOf(
                    tr("গবেষক ডিরেক্টরি", "Researcher Directory"),
                    tr("প্রোফাইল ও স্কিল সেটআপ", "Profile & Skills Setup")
                ),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        if (subTab == 1) {
            OnboardingProfileSetupView(
                currentUser = state.currentUser,
                onSave = { name, dept, interests, skills, refCode ->
                    repository.updateProfileAndReferral(name, dept, interests, skills, refCode)
                    subTab = 0
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(tr("গবেষক বা দক্ষতা দিয়ে খুঁজুন...", "Search researchers or skills...")) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = p.card,
                            focusedContainerColor = p.card,
                            unfocusedBorderColor = p.border
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ALL_DEPARTMENTS) { dept ->
                            val selected = selectedDept == dept
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) p.primary else p.card,
                                border = BorderStroke(1.dp, if (selected) p.primary else p.border),
                                modifier = Modifier.clickable { selectedDept = dept }
                            ) {
                                Text(
                                    text = if (dept == "All Depts") tr("সব বিভাগ", "All Depts") else dept,
                                    color = if (selected) Color.White else p.text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                items(filteredMembers, key = { it.id }) { member ->
                    val memberName = trContent(member.name)
                    SparcScholarlyCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SparcAvatarCircle(name = memberName, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(memberName, style = MaterialTheme.typography.titleMedium, color = p.text)
                                Text(
                                    "${member.department} • ★ ${member.rating} • 🔥 ${member.readingStreakDays}d",
                                    fontSize = 12.sp,
                                    color = p.muted
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = p.primarySoft,
                                modifier = Modifier.clickable { inviteTarget = member }
                            ) {
                                Text(
                                    text = "Invite +",
                                    color = p.primarySoftText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            member.skills.forEach { sk ->
                                Surface(shape = RoundedCornerShape(10.dp), color = p.cardAlt) {
                                    Text(
                                        text = sk,
                                        fontSize = 11.5.sp,
                                        color = p.text,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inviteTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { inviteTarget = null },
            containerColor = p.card,
            contentColor = p.text,
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
                Text("Invite ${trContent(target.name)}", style = MaterialTheme.typography.titleLarge, color = p.text)
                Text(tr("কুইক মেসেজ টেমপ্লেট বেছে নিন:", "Choose a quick invitation template:"), fontSize = 12.sp, color = p.muted)
                templates.forEachIndexed { idx, tpl ->
                    val selected = idx == chosenIdx
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) p.primarySoft else p.cardAlt,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) p.primary else p.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosenIdx = idx }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = trContent(tpl),
                                color = if (selected) p.primarySoftText else p.text,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = p.primary)
                            }
                        }
                    }
                }
                SparcPrimaryButton(
                    text = tr("আমন্ত্রণ পাঠান (+৫ পয়েন্ট)", "Send Invitation (+5 pts)"),
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
    val p = LocalSparcPalette.current
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
                Text("STEP 2 OF 2 • RESEARCHER SETUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.primary)
                Text(
                    tr("গবেষক প্রোফাইল ও দক্ষতা নির্বাচন", "Researcher Profile & Skill Tags"),
                    style = MaterialTheme.typography.titleLarge,
                    color = p.text
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("গবেষকের নাম", "Researcher Name")) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text(tr("বিভাগ (Department)", "Department")) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(tr("রিসার্চ ইন্টারেস্ট:", "Research Interests:"), style = MaterialTheme.typography.titleMedium, color = p.text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ALL_INTEREST_TAGS.forEach { tag ->
                        val active = tag in interests
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) p.primary else p.cardAlt,
                            modifier = Modifier.clickable {
                                interests = if (active) interests - tag else interests + tag
                            }
                        ) {
                            Text(
                                text = if (active) "✓ $tag" else tag,
                                color = if (active) Color.White else p.text,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Text(tr("কো-অথর ম্যাচমেকিং স্কিলস:", "Co-Author Matchmaking Skills:"), style = MaterialTheme.typography.titleMedium, color = p.text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ALL_SKILL_TAGS.forEach { sk ->
                        val active = sk in skills
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) p.primarySoft else p.cardAlt,
                            border = BorderStroke(1.dp, if (active) p.primary else p.border),
                            modifier = Modifier.clickable {
                                skills = if (active) skills - sk else skills + sk
                            }
                        ) {
                            Text(
                                text = if (active) "✓ $sk" else sk,
                                color = if (active) p.primarySoftText else p.text,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Surface(shape = RoundedCornerShape(14.dp), color = p.accentSoft) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = referralInput,
                            onValueChange = { referralInput = it },
                            label = { Text(tr("রেফারেল কোড", "Referral Code")) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(tr("+৳৫০ বোনাস!", "+৳50 Bonus!"), color = p.accentText, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }

                SparcPrimaryButton(
                    text = tr("প্রোফাইল সংরক্ষণ করুন", "Save Researcher Profile"),
                    onClick = { onSave(name, department, interests.toList(), skills.toList(), referralInput) }
                )
            }
        }
    }
}
