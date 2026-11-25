package org.sparc.studycircle.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.tr
import org.sparc.studycircle.core.theme.trContent
import org.sparc.studycircle.ui.assessment.AssessmentAndSessionScreen
import org.sparc.studycircle.ui.circle.StudyCircleScreen
import org.sparc.studycircle.ui.components.SparcAvatarCircle
import org.sparc.studycircle.ui.components.SparcSettingsBottomSheet
import org.sparc.studycircle.ui.library.LibraryAndReaderScreen
import org.sparc.studycircle.ui.onboarding.OnboardingAndDirectoryScreen
import org.sparc.studycircle.ui.research.ResearchAndWalletScreen

enum class MainTab(val titleBn: String, val titleEn: String, val icon: ImageVector) {
    DIRECTORY("ডিরেক্টরি", "Directory", Icons.Default.People),
    LIBRARY("লাইব্রেরি", "Library", Icons.AutoMirrored.Filled.MenuBook),
    CIRCLE("সার্কেল", "Circle", Icons.Default.Explore),
    ASSESSMENT("মূল্যায়ন", "Assess", Icons.AutoMirrored.Filled.FactCheck),
    RESEARCH("রিসার্চ", "Research", Icons.Default.Science);

    fun label(lang: String): String = if (lang == "en") titleEn else titleBn
}

@Composable
fun SparcMainScaffold(repository: SparcRepository) {
    val state by repository.state.collectAsState()
    val p = LocalSparcPalette.current
    val scope = rememberCoroutineScope()
    val displayUserName = trContent(state.currentUser.name)

    val initialTab = remember(state.uiCustomization.defaultLandingTab) {
        runCatching { MainTab.valueOf(state.uiCustomization.defaultLandingTab) }.getOrDefault(MainTab.CIRCLE)
    }
    var currentTab by remember { mutableStateOf(initialTab) }
    var researchInitialSubTab by remember { mutableIntStateOf(0) }
    var libraryInitialSubTab by remember { mutableIntStateOf(0) }
    var directoryInitialSubTab by remember { mutableIntStateOf(0) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var announcementDismissedLocally by remember { mutableStateOf(false) }

    // Continuous Live Multi-User Supabase Sync (every 12s while authenticated)
    LaunchedEffect(state.isAuthenticated, state.isSupabaseConnected) {
        if (state.isAuthenticated && state.isSupabaseConnected) {
            while (true) {
                repository.syncFromSupabaseNow(silent = true)
                delay(12_000L)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Dynamic Top Header
            if (p.useHeroHeader) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(p.primaryGradient)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SparcAvatarCircle(
                            name = displayUserName,
                            avatarUrl = state.currentUser.avatarUrl,
                            size = 42.dp,
                            bgColor = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.clickable {
                                directoryInitialSubTab = 1
                                currentTab = MainTab.DIRECTORY
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    directoryInitialSubTab = 1
                                    currentTab = MainTab.DIRECTORY
                                }
                        ) {
                            Text(
                                text = state.appCustomization.appTitle,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = displayUserName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.20f),
                            modifier = Modifier.clickable {
                                researchInitialSubTab = 2
                                currentTab = MainTab.RESEARCH
                            }
                        ) {
                            Text(
                                text = "🔥 ${state.currentUser.readingStreakDays}d",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White,
                            modifier = Modifier.clickable {
                                researchInitialSubTab = 1
                                currentTab = MainTab.RESEARCH
                            }
                        ) {
                            Text(
                                text = "৳${state.currentUser.walletBalanceBdt.toInt()}",
                                color = Color(0xFF0F172A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        IconButton(onClick = { scope.launch { repository.syncFromSupabaseNow(silent = false) } }) {
                            Icon(Icons.Default.Sync, contentDescription = "Live Sync", tint = Color.White)
                        }
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings & Customization", tint = Color.White)
                        }
                    }
                }
            } else {
                Surface(
                    color = p.card,
                    border = BorderStroke(0.5.dp, p.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SparcAvatarCircle(
                            name = displayUserName,
                            avatarUrl = state.currentUser.avatarUrl,
                            size = 40.dp,
                            modifier = Modifier.clickable {
                                directoryInitialSubTab = 1
                                currentTab = MainTab.DIRECTORY
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    directoryInitialSubTab = 1
                                    currentTab = MainTab.DIRECTORY
                                }
                        ) {
                            Text(
                                text = displayUserName,
                                style = MaterialTheme.typography.titleMedium,
                                color = p.text
                            )
                            val subInfo = if (state.currentUser.institution.isNotBlank()) {
                                "${state.currentUser.department} • ${state.currentUser.institution}"
                            } else {
                                "${state.currentUser.department} • ★ ${state.currentUser.rating}"
                            }
                            Text(
                                text = subInfo,
                                style = MaterialTheme.typography.labelMedium,
                                color = p.muted,
                                maxLines = 1
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = p.accentSoft,
                            modifier = Modifier.clickable {
                                researchInitialSubTab = 2
                                currentTab = MainTab.RESEARCH
                            }
                        ) {
                            Text(
                                text = "🔥 ${state.currentUser.readingStreakDays}d",
                                color = p.accentText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = p.primarySoft,
                            modifier = Modifier.clickable {
                                researchInitialSubTab = 1
                                currentTab = MainTab.RESEARCH
                            }
                        ) {
                            Text(
                                text = "৳${state.currentUser.walletBalanceBdt.toInt()}",
                                color = p.primarySoftText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        IconButton(onClick = { scope.launch { repository.syncFromSupabaseNow(silent = false) } }) {
                            Icon(Icons.Default.Sync, contentDescription = "Live Sync", tint = p.primary)
                        }
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings & Customization", tint = p.text)
                        }
                    }
                }
            }

            // Pack 1: Live Dynamic Organization Notice / Announcement Banner
            val noticeText = tr(state.appCustomization.announcementBannerBn, state.appCustomization.announcementBannerEn)
            if (state.appCustomization.showAnnouncementBanner && noticeText.isNotBlank() && !announcementDismissedLocally) {
                Surface(
                    color = p.accentSoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = noticeText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = p.accentText,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { announcementDismissedLocally = true },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss Notice", tint = p.accentText, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            state.statusBannerMessage?.let { msg ->
                Surface(
                    color = p.primarySoft,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelLarge,
                            color = p.primarySoftText,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { repository.dismissBanner() }) {
                            Text(tr("ঠিক আছে", "OK"), color = p.primarySoftText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Screen Body (padded at bottom for the Floating Capsule Dock)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 82.dp)
            ) {
                when (currentTab) {
                    MainTab.DIRECTORY -> OnboardingAndDirectoryScreen(
                        state = state,
                        repository = repository,
                        initialSubTab = directoryInitialSubTab
                    )
                    MainTab.LIBRARY -> LibraryAndReaderScreen(
                        state = state,
                        repository = repository,
                        initialSubTab = libraryInitialSubTab
                    )
                    MainTab.CIRCLE -> StudyCircleScreen(
                        state = state,
                        repository = repository,
                        onContinueReading = {
                            libraryInitialSubTab = 1
                            currentTab = MainTab.LIBRARY
                        },
                        onOpenLiveSession = {
                            currentTab = MainTab.ASSESSMENT
                        }
                    )
                    MainTab.ASSESSMENT -> AssessmentAndSessionScreen(state = state, repository = repository)
                    MainTab.RESEARCH -> ResearchAndWalletScreen(
                        state = state,
                        repository = repository,
                        initialSubTab = researchInitialSubTab
                    )
                }
            }
        }

        // Sleek Floating Capsule Bottom Navigation Dock
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = p.dockBg,
            border = BorderStroke(1.dp, p.dockBorder),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    val labelSelectedColor = if (p.useHeroHeader) Color.White else p.primary
                    val tabTitle = tab.label(state.appLanguage)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { currentTab = tab }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) p.primary else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tabTitle,
                                tint = if (selected) Color.White else p.dockInactive,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tabTitle,
                            fontSize = 10.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) labelSelectedColor else p.dockInactive
                        )
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        SparcSettingsBottomSheet(
            currentTheme = state.visualTheme,
            currentLanguage = state.appLanguage,
            appCustomization = state.appCustomization,
            uiCustomization = state.uiCustomization,
            apiVault = state.apiVault,
            isSupabaseConnected = state.isSupabaseConnected,
            onSelectTheme = { chosen -> repository.setVisualTheme(chosen) },
            onChangeLanguage = { lang -> repository.setAppLanguage(lang) },
            onUpdateUiCustomization = { newUi, bnMsg, enMsg ->
                repository.updateUiCustomization(newUi, bnMsg, enMsg)
            },
            onSaveAppCustomization = { newAppCfg ->
                announcementDismissedLocally = false
                repository.saveAppCustomization(newAppCfg)
            },
            onUpdateApiVault = { vault, msg -> repository.updateApiVault(vault, msg) },
            onLogout = { repository.logout() },
            onDismiss = { showSettingsSheet = false }
        )
    }
}
