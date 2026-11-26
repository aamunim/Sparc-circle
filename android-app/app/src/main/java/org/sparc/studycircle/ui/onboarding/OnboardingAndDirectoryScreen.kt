package org.sparc.studycircle.ui.onboarding

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.UserProfile
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.tr
import org.sparc.studycircle.core.theme.trContent
import org.sparc.studycircle.ui.components.*
import java.io.File

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
    repository: SparcRepository,
    initialSubTab: Int = 0
) {
    val p = LocalSparcPalette.current
    var subTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDept by remember { mutableStateOf("All Depts") }
    var inviteTarget by remember { mutableStateOf<UserProfile?>(null) }

    val filteredMembers = remember(state.directoryMembers, selectedDept, searchQuery) {
        state.directoryMembers.filter { m ->
            val deptMatch = selectedDept == "All Depts" || m.department == selectedDept
            val searchMatch = searchQuery.isBlank() ||
                m.name.contains(searchQuery, ignoreCase = true) ||
                m.institution.contains(searchQuery, ignoreCase = true) ||
                m.skills.any { it.contains(searchQuery, ignoreCase = true) }
            deptMatch && searchMatch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf(
                    tr("গবেষক ডিরেক্টরি", "Researcher Directory"),
                    tr("প্রোফাইল ও ফটো স্টুডিও", "Profile & Photo Studio")
                ),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        if (subTab == 1) {
            OnboardingProfileSetupView(
                currentUser = state.currentUser,
                repository = repository,
                onSave = { name, dept, inst, bio, goalMin, interests, skills, refCode ->
                    repository.updateProfileAndReferral(
                        name = name,
                        department = dept,
                        interests = interests,
                        skills = skills,
                        appliedReferralCode = refCode,
                        institution = inst,
                        bio = bio,
                        dailyReadingGoalMinutes = goalMin
                    )
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
                        placeholder = { Text(tr("গবেষক, বিশ্ববিদ্যালয় বা দক্ষতা দিয়ে খুঁজুন...", "Search researchers, institution or skills...")) },
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

                val inbox = state.messages.filter { it.receiverId == state.currentUser.id }.take(3)
                if (inbox.isNotEmpty()) {
                    item {
                        SparcScholarlyCard(containerColor = p.primarySoft) {
                            Text(
                                tr("📩 প্রাপ্ত গবেষক আমন্ত্রণ (${inbox.size})", "📩 Received Invitations (${inbox.size})"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = p.primarySoftText
                            )
                            inbox.forEach { msg ->
                                Text(
                                    "• ${trContent(msg.senderName)}: ${trContent(msg.body)}",
                                    fontSize = 12.sp,
                                    color = p.primarySoftText
                                )
                            }
                        }
                    }
                }

                val activeCircle = state.circles.find { it.id == state.activeCircleId } ?: state.circles.firstOrNull()
                val peerBuddyUserIds = state.circleMemberProgress.mapNotNull { it.peerBuddyId }.toSet()

                items(filteredMembers, key = { it.id }) { member ->
                    val memberName = trContent(member.name)
                    val activityBadges = SparcDomainEngine.resolveUserActivityBadges(
                        userId = member.id,
                        earnedBadges = member.earnedBadges,
                        activeCircle = activeCircle,
                        sessions = state.sessions,
                        coAuthorUserIds = state.coAuthorUserIds,
                        peerBuddyUserIds = peerBuddyUserIds
                    )
                    SparcScholarlyCard(compact = state.uiCustomization.compactHomeCards) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SparcAvatarCircle(
                                name = memberName,
                                avatarUrl = member.avatarUrl,
                                size = 44.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(memberName, style = MaterialTheme.typography.titleMedium, color = p.text)
                                val instSuffix = if (member.institution.isNotBlank()) " • ${member.institution}" else ""
                                Text(
                                    "${member.department}$instSuffix • ★ ${member.rating} • 🔥 ${member.readingStreakDays}d",
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

                        SparcRoleBadgesRow(
                            coreRole = member.role,
                            activityBadges = activityBadges
                        )

                        if (member.bio.isNotBlank()) {
                            Text(member.bio, fontSize = 12.sp, color = p.muted)
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    repository: SparcRepository,
    onSave: (String, String, String, String, Int, List<String>, List<String>, String) -> Unit
) {
    val p = LocalSparcPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(currentUser.id) { mutableStateOf(currentUser.name) }
    var department by remember(currentUser.id) { mutableStateOf(currentUser.department) }
    var institution by remember(currentUser.id) { mutableStateOf(currentUser.institution) }
    var bio by remember(currentUser.id) { mutableStateOf(currentUser.bio) }
    var dailyGoalMin by remember(currentUser.id) { mutableIntStateOf(currentUser.dailyReadingGoalMinutes) }
    var interests by remember(currentUser.id) { mutableStateOf(currentUser.researchInterests.toSet()) }
    var skills by remember(currentUser.id) { mutableStateOf(currentUser.skills.toSet()) }
    var customSkillInput by remember { mutableStateOf("") }
    var referralInput by remember { mutableStateOf("") }
    var uploadingAvatar by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            uploadingAvatar = true
            scope.launch {
                val pair = withContext(Dispatchers.IO) {
                    readAvatarBytesAndCache(context, uri)
                }
                if (pair != null) {
                    repository.uploadProfileAvatar(
                        imageBytes = pair.first,
                        fileName = pair.second,
                        localCacheFallbackPath = pair.third
                    )
                }
                uploadingAvatar = false
            }
        }
    }

    val allSkillTags = remember(skills) { (ALL_SKILL_TAGS + skills).distinct() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SparcScholarlyCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SparcAvatarCircle(
                        name = name,
                        avatarUrl = currentUser.avatarUrl,
                        size = 64.dp
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            tr("প্রোফাইল ছবি ও পরিচয় কাস্টমাইজ করুন", "Customize Profile Photo & Bio"),
                            style = MaterialTheme.typography.titleMedium,
                            color = p.text
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = p.primarySoft,
                            modifier = Modifier.clickable(enabled = !uploadingAvatar) {
                                avatarPicker.launch("image/*")
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = p.primarySoftText, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (uploadingAvatar) tr("আপলোড হচ্ছে...", "Uploading...")
                                    else tr("গ্যালারি থেকে ছবি দিন", "Upload Photo"),
                                    color = p.primarySoftText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("গবেষকের নাম", "Researcher Name")) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text(tr("বিভাগ", "Department")) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = institution,
                        onValueChange = { institution = it },
                        label = { Text(tr("বিশ্ববিদ্যালয়/প্রতিষ্ঠান", "University / Inst.")) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(tr("সংক্ষিপ্ত বায়ো / গবেষণার লক্ষ্য", "Short Bio / Academic Goal")) },
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    tr("⏱️ দৈনিক পড়ার লক্ষ্যমাত্রা (Daily Reading Goal):", "⏱️ Daily Reading Goal:"),
                    style = MaterialTheme.typography.titleMedium,
                    color = p.text
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        val active = dailyGoalMin == mins
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) p.primary else p.cardAlt,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { dailyGoalMin = mins }
                        ) {
                            Text(
                                text = tr("$mins মি.", "${mins}m"),
                                color = if (active) Color.White else p.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

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

                Text(tr("কো-অথর ম্যাচমেকিং স্কিলস (কাস্টম ট্যাগ যোগ করুন):", "Co-Author Skills (Tap or Add Custom):"), style = MaterialTheme.typography.titleMedium, color = p.text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allSkillTags.forEach { sk ->
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customSkillInput,
                        onValueChange = { customSkillInput = it },
                        placeholder = { Text(tr("নতুন স্কিল লিখুন (যেমন SPSS, NVivo)...", "Add custom skill (e.g. SPSS, NVivo)..."), fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (customSkillInput.isNotBlank()) {
                                skills = skills + customSkillInput.trim()
                                customSkillInput = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.primary)
                    ) {
                        Text("+ Add")
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
                            label = { Text(tr("রেফারেল কোড (ঐচ্ছিক)", "Referral Code (Optional)")) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(tr("+৳৫০ বোনাস!", "+৳50 Bonus!"), color = p.accentText, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }

                SparcPrimaryButton(
                    text = tr("প্রোফাইল ও কাস্টমাইজেশন সেভ করুন", "Save Profile & Customization"),
                    onClick = {
                        onSave(
                            name,
                            department,
                            institution,
                            bio,
                            dailyGoalMin,
                            interests.toList(),
                            skills.toList(),
                            referralInput
                        )
                    }
                )
            }
        }
    }
}

private fun readAvatarBytesAndCache(context: Context, uri: Uri): Triple<ByteArray, String, String>? = runCatching {
    var name = "avatar.jpg"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) {
            name = c.getString(idx) ?: name
        }
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val cacheDir = File(context.cacheDir, "avatars").apply { mkdirs() }
    val localFile = File(cacheDir, "${System.currentTimeMillis()}_$name")
    localFile.writeBytes(bytes)
    Triple(bytes, name, localFile.absolutePath)
}.getOrNull()
