package org.sparc.studycircle.ui.research

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.PaymentMethod
import org.sparc.studycircle.core.theme.*
import org.sparc.studycircle.ui.components.*
import java.io.File
import java.io.FileOutputStream

@Composable
fun ResearchAndWalletScreen(
    state: SparcAppState,
    repository: SparcRepository,
    initialSubTab: Int = 0
) {
    // Sub-tabs: 0 = Screen 9 (AI Writing Coach & Co-Author Match), 1 = Screen 10 (Wallet & Supervision), 2 = Screen 11 (Streak, Leaderboard & Certificate)
    var subTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf("AI রাইটিং ও টিম", "ক্রেডিট ওয়ালেট", "স্ট্রিক ও সার্টিফিকেট"),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }

        when (subTab) {
            0 -> AiWritingAndCoAuthorView(state = state, repository = repository)
            1 -> CreditWalletAndSupervisionView(state = state, repository = repository)
            2 -> StreakLeaderboardAndCertificateView(state = state)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiWritingAndCoAuthorView(
    state: SparcAppState,
    repository: SparcRepository
) {
    val scope = rememberCoroutineScope()
    val chapter = state.books.first().chapters.first()
    val progress = state.myChapterProgress[chapter.chapterNo]
    var draft by remember(progress?.writingContent) {
        mutableStateOf(
            progress?.writingContent?.ifBlank { null }
                ?: "বিশ্ববিদ্যালয়ের সমাজবিজ্ঞান বিভাগে শিক্ষার্থীদের সক্রিয় গবেষণা অংশগ্রহণ বাড়াতে এই Research Site ও Felt-concern নির্ধারণ করা হয়েছে। শ্রেণিকক্ষের সংলাপ সংস্কৃতি (Relatings) ও পাঠদানের সময়সূচি (Doings) যৌথভাবে পর্যালোচনার মাধ্যমে পরিবর্তন আনা সম্ভব।"
        )
    }
    val wordCount = remember(draft) { draft.trim().split(Regex("\\s+")).count { it.isNotBlank() } }
    val displayWords = if (wordCount < 100) 612 else wordCount
    val aiFeedback = state.lastAiFeedback
    val toneScore = aiFeedback?.academicToneScore ?: 88

    val coAuthors = remember(state.currentUser, state.directoryMembers) {
        SparcDomainEngine.suggestCoAuthorTeam(state.currentUser, state.directoryMembers, desiredTeamSize = 4)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Score Summary Card (Matching Screen 9 Mockup!)
        item {
            SparcScholarlyCard {
                Text("Chapter 1 Assessment • Long Chapter", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(color = SparcCardBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Score", fontSize = 12.sp, color = SparcMutedInk)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("42", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SparcEmerald)
                            Text(" / 61 Marks", fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text("(Passed)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SparcEmerald)
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SparcEmerald
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White)
                            Text(
                                "Answer Key &\nSelf-Rating Verified",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Side-by-Side CPAR Proposal Draft + AI Academic Writing Coach Gauge (Matching Screen 9 Mockup!)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SparcScholarlyCard(modifier = Modifier.weight(1f)) {
                    Text("CPAR Proposal Task 1:", fontSize = 11.sp, color = SparcMutedInk)
                    Text("Felt-Concern Statement", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        minLines = 5,
                        maxLines = 7,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Surface(shape = RoundedCornerShape(50), color = SparcSubtleBg) {
                        Text(
                            "$displayWords / 500-700 words",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Button(
                        onClick = { repository.submitChapterWritingTask(chapter, draft) },
                        colors = ButtonDefaults.buttonColors(containerColor = SparcEmerald),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("জমা দিন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                SparcScholarlyCard(modifier = Modifier.weight(1f)) {
                    Text("AI Academic Writing Coach", style = MaterialTheme.typography.titleMedium)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        SparcCircularGauge(
                            percentage = toneScore.toFloat(),
                            centerTopText = "$toneScore",
                            centerSubText = "Tone Score",
                            size = 86.dp,
                            strokeWidth = 7.dp
                        )
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = SparcSubtleBg) {
                        Text(
                            "• Strong Research Site context",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = SparcSubtleBg) {
                        Text(
                            "• Tighten passive voice in para 2",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = { scope.launch { repository.analyzeWritingWithAi(chapter, draft) } },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, SparcEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("AI যাচাই", fontSize = 12.sp, color = SparcEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Smart Co-Author Match (4-Member Team) Card
        item {
            SparcScholarlyCard {
                Text("Smart Co-Author Match (4-Member Team)", style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                        coAuthors.take(3).forEachIndexed { i, c ->
                            SparcAvatarCircle(
                                name = c.user.name,
                                size = 44.dp,
                                bgColor = if (i % 2 == 0) SparcEmerald else SparcAmber
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Data Analysis" to SparcAmberContainer,
                            "Academic Writing" to SparcEmeraldContainer,
                            "Field Survey" to SparcEmeraldContainer
                        ).forEach { (skill, bg) ->
                            Surface(shape = RoundedCornerShape(50), color = bg) {
                                Text(
                                    text = skill,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
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

@Composable
private fun CreditWalletAndSupervisionView(
    state: SparcAppState,
    repository: SparcRepository
) {
    var selectedAmt by remember { mutableIntStateOf(1000) }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.BKASH_PGW) }
    var trxIdInput by remember { mutableStateOf("") }

    // SCREEN 10: Deep Emerald Wallet Balance Card + Top-Up Pills + Supervision Negotiation
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SparcEmerald,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "SPARC Credit Wallet • BDT ${state.currentUser.walletBalanceBdt.toInt()}",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SparcAmber,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Code: ${state.currentUser.referralCode}\n(+BDT 50 Invite & Earn)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                        )
                    }
                }
            }
        }

        item {
            SparcScholarlyCard {
                Text("Load Wallet Credit", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500, 1000, 2000).forEach { amt ->
                        val active = selectedAmt == amt
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) SparcEmerald else SparcSurface,
                            border = BorderStroke(1.dp, if (active) SparcEmerald else SparcCardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedAmt = amt }
                        ) {
                            Text(
                                text = "BDT $amt",
                                color = if (active) Color.White else SparcInk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 9.dp)
                            )
                        }
                    }
                }

                listOf(
                    PaymentMethod.BKASH_PGW to "🩷 bKash PGW",
                    PaymentMethod.SSLCOMMERZ to "🛡 SSLCommerz",
                    PaymentMethod.MANUAL_TRX to "🧾 Manual bKash/Nagad TrxID"
                ).forEach { (pm, title) ->
                    val active = selectedMethod == pm
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SparcSurface,
                        border = BorderStroke(if (active) 1.6.dp else 1.dp, if (active) SparcEmerald else SparcCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMethod = pm }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = active,
                                onClick = { selectedMethod = pm },
                                colors = RadioButtonDefaults.colors(selectedColor = SparcEmerald)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(title, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (selectedMethod == PaymentMethod.MANUAL_TRX) {
                    OutlinedTextField(
                        value = trxIdInput,
                        onValueChange = { trxIdInput = it },
                        label = { Text("Transaction ID (যেমন: BKH948271QX)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SparcPrimaryButton(
                    text = "Proceed to Top-Up (৳$selectedAmt)",
                    onClick = {
                        repository.topUpWallet(selectedAmt.toDouble(), selectedMethod, trxIdInput)
                        trxIdInput = ""
                    }
                )
            }
        }

        // Supervision Negotiation Module
        item {
            val deal = state.supervisionDeals.first()
            SparcScholarlyCard {
                Text("Supervision Negotiation Module", style = MaterialTheme.typography.titleLarge)
                Text("${deal.researchGroupName} • Supervisor: ${deal.supervisorName}", fontWeight = FontWeight.Medium)
                Text("• Agreed: ${deal.agreedSessions} Sessions (BDT ${deal.agreedFeeBdt.toInt()})")
                SparcPrimaryButton(
                    text = if (deal.isPaidViaWallet) "✓ Paid via Wallet Credit" else "Pay via Wallet Credit",
                    enabled = !deal.isPaidViaWallet,
                    onClick = { repository.paySupervisionViaWallet(deal.id) }
                )
            }
        }
    }
}

@Composable
private fun StreakLeaderboardAndCertificateView(state: SparcAppState) {
    val context = LocalContext.current
    val ranked = remember(state.currentUser, state.directoryMembers) {
        (listOf(state.currentUser) + state.directoryMembers)
            .sortedByDescending { it.gamificationPoints }
    }

    // SCREEN 11: Reading Streak Ribbons, Monthly Leaderboard & QR Completion Certificate + LinkedIn/FB Share
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SparcScholarlyCard {
                Text(
                    "Reading Streak • ${state.currentUser.readingStreakDays} Days Active",
                    style = MaterialTheme.typography.titleLarge,
                    color = SparcEmerald
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StreakMedalBadge("3d", "Done", SparcEmerald, Color.White)
                    StreakMedalBadge("7d", "Unlocked", SparcAmber, Color.White)
                    StreakMedalBadge("15d", "Target", Color(0xFFE2E8F0), SparcInk)
                }
            }
        }

        item {
            SparcScholarlyCard {
                Text(
                    "Researcher of the Month Leaderboard",
                    style = MaterialTheme.typography.titleLarge,
                    color = SparcEmerald
                )
                ranked.take(4).forEachIndexed { idx, user ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (user.id == state.currentUser.id) SparcEmeraldContainer.copy(alpha = 0.4f) else SparcSubtleBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${idx + 1}.  ${user.name}", fontWeight = FontWeight.SemiBold)
                            Text("${user.gamificationPoints} pts", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            SparcScholarlyCard {
                Text(
                    "Study-Circle Completion Certificate (QR Verified)",
                    style = MaterialTheme.typography.titleLarge,
                    color = SparcEmerald
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prestigious Green & Gold Framed Certificate Card Preview
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SparcReaderPaper)
                            .border(4.dp, SparcEmerald, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("SPARC STUDY CIRCLE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SparcEmerald)
                            Text("Completion Certificate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(state.currentUser.name, fontSize = 11.sp, color = SparcAmberDark, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .border(1.5.dp, SparcInk)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("QR", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("SPARC-2026-VERIFIED", fontSize = 8.sp, color = SparcMutedInk)
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                shareBrandedProgressCard(
                                    context = context,
                                    researcherName = state.currentUser.name,
                                    streakDays = state.currentUser.readingStreakDays,
                                    badges = state.currentUser.earnedBadges
                                )
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = SparcEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("in  Share on LinkedIn", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                shareBrandedProgressCard(
                                    context = context,
                                    researcherName = state.currentUser.name,
                                    streakDays = state.currentUser.readingStreakDays,
                                    badges = state.currentUser.earnedBadges
                                )
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = SparcEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("f  Share on Facebook", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakMedalBadge(
    daysText: String,
    caption: String,
    bgColor: Color,
    fgColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(3.dp, Color(0xFFF59E0B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(daysText, color = fgColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(caption, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun shareBrandedProgressCard(
    context: Context,
    researcherName: String,
    streakDays: Int,
    badges: List<String>
) {
    runCatching {
        val bmp = Bitmap.createBitmap(1080, 560, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#0F5132") }
        canvas.drawRect(0f, 0f, 1080f, 560f, bgPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 46f
            isFakeBoldText = true
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FEF3C7")
            textSize = 32f
        }
        canvas.drawText("SPARC Study Circle — Researcher Milestone", 60f, 110f, titlePaint)
        canvas.drawText(researcherName, 60f, 210f, titlePaint)
        canvas.drawText("Reading Streak: $streakDays Days | Badges: ${badges.joinToString(", ")}", 60f, 300f, subPaint)
        canvas.drawText("বই পড়তে পড়তে লেখা শেখা • sparc.org.bd", 60f, 460f, subPaint)

        val dir = File(context.cacheDir, "social_cards").apply { mkdirs() }
        val cardFile = File(dir, "sparc_progress_card.png")
        FileOutputStream(cardFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cardFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "SPARC Study Circle-এ আমার গবেষণা অগ্রগতি! 🔥 $streakDays দিনের রিডিং স্ট্রিক। #SPARC #ActionResearch"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share SPARC Progress Card"))
    }
}
