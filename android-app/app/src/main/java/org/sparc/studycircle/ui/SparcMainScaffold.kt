package org.sparc.studycircle.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.theme.*
import org.sparc.studycircle.ui.assessment.AssessmentAndSessionScreen
import org.sparc.studycircle.ui.circle.StudyCircleScreen
import org.sparc.studycircle.ui.components.SparcAvatarCircle
import org.sparc.studycircle.ui.library.LibraryAndReaderScreen
import org.sparc.studycircle.ui.onboarding.OnboardingAndDirectoryScreen
import org.sparc.studycircle.ui.research.ResearchAndWalletScreen

enum class MainTab(val titleBn: String, val icon: ImageVector) {
    DIRECTORY("ডিরেক্টরি", Icons.Default.People),
    LIBRARY("লাইব্রেরি", Icons.AutoMirrored.Filled.MenuBook),
    CIRCLE("সার্কেল", Icons.Default.Groups),
    ASSESSMENT("মূল্যায়ন", Icons.AutoMirrored.Filled.FactCheck),
    RESEARCH("রিসার্চ", Icons.Default.Science)
}

@Composable
fun SparcMainScaffold(repository: SparcRepository) {
    val state by repository.state.collectAsState()
    var currentTab by remember { mutableStateOf(MainTab.CIRCLE) }
    var researchInitialSubTab by remember { mutableIntStateOf(0) }
    var libraryInitialSubTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = SparcParchment,
        topBar = {
            Surface(
                color = SparcParchment,
                modifier = Modifier.statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SparcAvatarCircle(name = state.currentUser.name, size = 42.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.currentUser.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = SparcInk
                        )
                        Text(
                            text = "(${state.currentUser.department})",
                            style = MaterialTheme.typography.labelMedium,
                            color = SparcMutedInk
                        )
                    }

                    // 7d Streak Amber Pill (Jumps to Screen 11: Streak & Leaderboard)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SparcAmberContainer,
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.clickable {
                            researchInitialSubTab = 2
                            currentTab = MainTab.RESEARCH
                        }
                    ) {
                        Text(
                            text = "🔥 ${state.currentUser.readingStreakDays}d Streak",
                            color = SparcAmberDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    // BDT Wallet Pill (Jumps to Screen 10: Credit Wallet)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SparcAmberContainer,
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.clickable {
                            researchInitialSubTab = 1
                            currentTab = MainTab.RESEARCH
                        }
                    ) {
                        Text(
                            text = "💳 ৳${state.currentUser.walletBalanceBdt.toInt()}",
                            color = SparcAmberDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = SparcSurface,
                tonalElevation = 3.dp
            ) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SparcEmerald,
                            selectedTextColor = SparcEmerald,
                            indicatorColor = SparcEmeraldContainer,
                            unselectedIconColor = SparcMutedInk,
                            unselectedTextColor = SparcMutedInk
                        ),
                        icon = { Icon(tab.icon, contentDescription = tab.titleBn) },
                        label = {
                            Text(
                                text = tab.titleBn,
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            state.statusBannerMessage?.let { msg ->
                Surface(
                    color = SparcEmeraldContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelLarge,
                            color = SparcEmeraldDark,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { repository.dismissBanner() }) {
                            Text("ঠিক আছে", color = SparcEmeraldDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            when (currentTab) {
                MainTab.DIRECTORY -> OnboardingAndDirectoryScreen(state = state, repository = repository)
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
}
