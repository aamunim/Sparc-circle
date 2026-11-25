package org.sparc.studycircle.ui.research

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.ui.components.*
import java.io.File
import java.io.FileOutputStream

@Composable
fun ResearchAndWalletScreen(
    state: SparcAppState,
    repository: SparcRepository,
    initialSubTab: Int = 0
) {
    var subTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            SparcSegmentedPills(
                options = listOf("AI রাইটিং ও টিম", "ক্রেডিট ওয়ালেট", "পডিয়াম ও সার্টিফিকেট"),
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
    val p = LocalSparcPalette.current
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
    val toneScore = state.lastAiFeedback?.academicToneScore ?: 88

    val coAuthors = remember(state.currentUser, state.directoryMembers) {
        SparcDomainEngine.suggestCoAuthorTeam(state.currentUser, state.directoryMembers, desiredTeamSize = 4)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SparcScholarlyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("CPAR PROPOSAL • STEP 1/7", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = p.muted)
                        Text("Felt-Concern Statement", style = MaterialTheme.typography.titleLarge, color = p.text)
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = p.primarySoft) {
                        Text(
                            "$displayWords / 700w",
                            color = p.primarySoftText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Gemini AI Writing Coach Card
                Surface(shape = RoundedCornerShape(16.dp), color = p.cardAlt) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SparcCircularGauge(
                            percentage = toneScore.toFloat(),
                            centerTopText = "$toneScore",
                            centerSubText = "TONE",
                            size = 64.dp,
                            strokeWidth = 6.dp
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("✨ Gemini AI Writing Coach", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = p.text)
                            Text("• Research Site ও অংশগ্রহণকারীর যুক্তি সুদৃঢ়", fontSize = 12.sp, color = p.muted)
                            Text("• ২য় অনুচ্ছেদে প্যাসিভ বাক্য সংক্ষিপ্ত করুন", fontSize = 12.sp, color = p.muted)
                        }
                    }
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    minLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.primarySoft,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { scope.launch { repository.analyzeWritingWithAi(chapter, draft) } }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                            Text("AI ফিডব্যাক নিন", color = p.primarySoftText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Button(
                        onClick = { repository.submitChapterWritingTask(chapter, draft) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("রাইটিং জমা দিন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            SparcScholarlyCard {
                Text("স্মার্ট কো-অথর ম্যাচমেকিং (৪ সদস্যের টিম)", style = MaterialTheme.typography.titleMedium, color = p.text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    coAuthors.forEachIndexed { idx, c ->
                        val skillLabel = c.complementarySkills.firstOrNull() ?: "Research"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (idx % 2 == 0) p.primarySoft else p.accentSoft
                        ) {
                            Text(
                                text = "${c.user.name.split(" ").first()} • $skillLabel",
                                color = if (idx % 2 == 0) p.primarySoftText else p.accentText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
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
    val p = LocalSparcPalette.current
    var selectedAmt by remember { mutableIntStateOf(1000) }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.BKASH_PGW) }
    var trxIdInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Digital Pass Style Gradient Wallet Card
        item {
            SparcGradientHeroCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("SPARC RESEARCH WALLET", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("BDT PASS", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "৳ ${state.currentUser.walletBalanceBdt.toInt()}.00",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "রেফারেল কোড: ${state.currentUser.referralCode} (+৳৫০ বোনাস)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        item {
            SparcScholarlyCard {
                Text("ক্রেডিট টপ-আপ করুন (ন্যূনতম ৳৫০০)", style = MaterialTheme.typography.titleMedium, color = p.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500, 1000, 2000).forEach { amt ->
                        val active = selectedAmt == amt
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) p.primary else p.cardAlt,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedAmt = amt }
                        ) {
                            Text(
                                text = "৳$amt",
                                color = if (active) Color.White else p.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }

                PaymentMethod.entries.forEach { pm ->
                    val active = selectedMethod == pm
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) p.primarySoft else p.cardAlt,
                        border = BorderStroke(1.dp, if (active) p.primary else p.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMethod = pm }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = active, onClick = { selectedMethod = pm })
                            Spacer(Modifier.width(6.dp))
                            Text(pm.labelBn, fontWeight = FontWeight.SemiBold, color = p.text)
                        }
                    }
                }

                if (selectedMethod == PaymentMethod.MANUAL_TRX) {
                    OutlinedTextField(
                        value = trxIdInput,
                        onValueChange = { trxIdInput = it },
                        label = { Text("bKash / Nagad TrxID") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SparcPrimaryButton(
                    text = "৳$selectedAmt ওয়ালেটে লোড করুন",
                    onClick = {
                        repository.topUpWallet(selectedAmt.toDouble(), selectedMethod, trxIdInput)
                        trxIdInput = ""
                    }
                )
            }
        }

        item {
            val deal = state.supervisionDeals.first()
            SparcScholarlyCard {
                Text("পেইড সুপারভিশন ও নেগোসিয়েশন", style = MaterialTheme.typography.titleMedium, color = p.text)
                Text("${deal.researchGroupName} • সুপারভাইজার: ${deal.supervisorName}", color = p.muted, fontSize = 12.5.sp)
                Text("${deal.agreedSessions} সেশন চূড়ান্ত • ফি: ৳${deal.agreedFeeBdt.toInt()}", fontWeight = FontWeight.Bold, color = p.text)
                SparcPrimaryButton(
                    text = if (deal.isPaidViaWallet) "✓ ওয়ালেট থেকে পরিশোধিত" else "ওয়ালেট ক্রেডিট থেকে পরিশোধ করুন",
                    enabled = !deal.isPaidViaWallet,
                    onClick = { repository.paySupervisionViaWallet(deal.id) }
                )
            }
        }
    }
}

@Composable
private fun StreakLeaderboardAndCertificateView(state: SparcAppState) {
    val p = LocalSparcPalette.current
    val context = LocalContext.current
    val ranked = remember(state.currentUser, state.directoryMembers) {
        (listOf(state.currentUser) + state.directoryMembers)
            .sortedByDescending { it.gamificationPoints }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Researcher of the Month 3-Card Podium
        item {
            SparcScholarlyCard {
                Text("Researcher of the Month পডিয়াম", style = MaterialTheme.typography.titleMedium, color = p.text)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val second = ranked.getOrNull(1)
                    val first = ranked.getOrNull(0)
                    val third = ranked.getOrNull(2)

                    if (second != null) {
                        PodiumTile("#2 ${second.name.split(" ").first()}", "${second.gamificationPoints} pts", p.cardAlt, p.text, Modifier.weight(1f))
                    }
                    if (first != null) {
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(p.primaryGradient)
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("#1 ${first.name.split(" ").first()}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                Text("${first.gamificationPoints} pts", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                            }
                        }
                    }
                    if (third != null) {
                        PodiumTile("#3 ${third.name.split(" ").first()}", "${third.gamificationPoints} pts", p.accentSoft, p.accentText, Modifier.weight(1f))
                    }
                }
            }
        }

        // Streak Milestones
        item {
            SparcScholarlyCard {
                Text("🔥 রিডিং স্ট্রিক মাইলফলক: ${state.currentUser.readingStreakDays} দিন সক্রিয়", style = MaterialTheme.typography.titleMedium, color = p.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PodiumTile("৩ দিন", "সম্পন্ন ✓", p.primarySoft, p.primarySoftText, Modifier.weight(1f))
                    PodiumTile("৭ দিন", "আনলকড 🔥", p.accentSoft, p.accentText, Modifier.weight(1f))
                    PodiumTile("১৫ দিন", "লক্ষ্যমাত্রা", p.cardAlt, p.muted, Modifier.weight(1f))
                }
            }
        }

        // Completion Certificate & Social Share Card
        item {
            SparcScholarlyCard {
                Text("Study-Circle Completion Certificate", style = MaterialTheme.typography.titleMedium, color = p.text)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = p.cardAlt,
                    border = BorderStroke(1.5.dp, p.primary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("SPARC VERIFIED CERTIFICATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = p.primary)
                        Text(state.currentUser.name, style = MaterialTheme.typography.titleLarge, color = p.text)
                        Text("QR ID: SPARC-2026-8F4A2C", fontSize = 11.5.sp, color = p.muted)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            shareBrandedProgressCard(
                                context = context,
                                researcherName = state.currentUser.name,
                                streakDays = state.currentUser.readingStreakDays,
                                badges = state.currentUser.earnedBadges
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("LinkedIn শেয়ার", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = p.cardAlt,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                shareBrandedProgressCard(
                                    context = context,
                                    researcherName = state.currentUser.name,
                                    streakDays = state.currentUser.readingStreakDays,
                                    badges = state.currentUser.earnedBadges
                                )
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 11.dp)) {
                            Text("Facebook শেয়ার", color = p.text, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumTile(
    top: String,
    sub: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Surface(shape = RoundedCornerShape(14.dp), color = bg, modifier = modifier) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(top, color = fg, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
            Text(sub, color = fg.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1)
        }
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
        val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#1D4ED8") }
        canvas.drawRect(0f, 0f, 1080f, 560f, bgPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 46f
            isFakeBoldText = true
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#DBEAFE")
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
