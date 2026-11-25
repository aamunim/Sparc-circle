package org.sparc.studycircle.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.ui.assessment.AssessmentAndSessionScreen
import org.sparc.studycircle.ui.circle.StudyCircleScreen
import org.sparc.studycircle.ui.library.LibraryAndReaderScreen
import org.sparc.studycircle.ui.onboarding.OnboardingAndDirectoryScreen
import org.sparc.studycircle.ui.research.ResearchAndWalletScreen

private enum class MainTab(val titleBn: String, val icon: ImageVector) {
    DIRECTORY("ডিরেক্টরি", Icons.Default.People),
    LIBRARY("লাইব্রেরি", Icons.AutoMirrored.Filled.MenuBook),
    CIRCLE("সার্কেল", Icons.Default.Groups),
    ASSESSMENT("মূল্যায়ন", Icons.AutoMirrored.Filled.FactCheck),
    RESEARCH("রিসার্চ", Icons.Default.WorkspacePremium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparcMainScaffold(repository: SparcRepository) {
    val state by repository.state.collectAsState()
    var currentTab by remember { mutableStateOf(MainTab.LIBRARY) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SPARC Study Circle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "বই পড়তে পড়তে লেখা শেখা",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    AssistChip(
                        onClick = { currentTab = MainTab.RESEARCH },
                        label = { Text("🔥 ${state.currentUser.readingStreakDays} দিন") }
                    )
                    Spacer(Modifier.width(6.dp))
                    AssistChip(
                        onClick = { currentTab = MainTab.RESEARCH },
                        label = { Text("৳${state.currentUser.walletBalanceBdt.toInt()}") }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.titleBn) },
                        label = { Text(tab.titleBn) }
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
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { repository.dismissBanner() }) {
                            Text("বন্ধ করুন")
                        }
                    }
                }
            }

            when (currentTab) {
                MainTab.DIRECTORY -> OnboardingAndDirectoryScreen(state = state, repository = repository)
                MainTab.LIBRARY -> LibraryAndReaderScreen(state = state, repository = repository)
                MainTab.CIRCLE -> StudyCircleScreen(state = state, repository = repository)
                MainTab.ASSESSMENT -> AssessmentAndSessionScreen(state = state, repository = repository)
                MainTab.RESEARCH -> ResearchAndWalletScreen(state = state, repository = repository)
            }
        }
    }
}
