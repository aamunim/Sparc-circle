package org.sparc.studycircle.ui.research

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.domain.SparcDomainEngine
import org.sparc.studycircle.core.model.PaymentMethod
import java.io.File
import java.io.FileOutputStream

@Composable
fun ResearchAndWalletScreen(
    state: SparcAppState,
    repository: SparcRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chapter = state.books.first().chapters.first()
    val progress = state.myChapterProgress[chapter.chapterNo]

    var writingDraft by remember(progress?.writingContent) {
        mutableStateOf(
            progress?.writingContent?.ifBlank { null }
                ?: "বিশ্ববিদ্যালয় পর্যায়ে সমাজবিজ্ঞান গবেষণার ক্লাসরুমে শিক্ষার্থীদের সক্রিয় অংশগ্রহণ বৃদ্ধির লক্ষ্যে এই গবেষণা সাইট ও Felt-concern নির্ধারণ করা হয়েছে..."
        )
    }

    // Wallet Top-Up State (Section 2.3)
    var topUpAmount by remember { mutableIntStateOf(500) }
    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod.BKASH_PGW) }
    var manualTrxInput by remember { mutableStateOf("") }

    val coAuthorSuggestions = remember(state.currentUser, state.directoryMembers) {
        SparcDomainEngine.suggestCoAuthorTeam(state.currentUser, state.directoryMembers, desiredTeamSize = 4)
    }

    val leaderboard = remember(state.currentUser, state.directoryMembers) {
        (listOf(state.currentUser) + state.directoryMembers)
            .sortedByDescending { it.gamificationPoints + (it.readingStreakDays * 10) }
    }

    val currentWordCount = remember(writingDraft) {
        writingDraft.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Progressive Research Writing Module + Gemini AI Assistant (Section 6.1)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "রিসার্চ-রাইটিং মডিউল (অধ্যায় ১ → ৭ম অধ্যায়ে পূর্ণাঙ্গ CPAR প্রপোজাল)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(chapter.writingTaskPrompt, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "শব্দসংখ্যা: $currentWordCount / লক্ষ্যমাত্রা ${chapter.minWords}–${chapter.maxWords} শব্দ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = writingDraft,
                        onValueChange = { writingDraft = it },
                        minLines = 5,
                        label = { Text("আপনার একাডেমিক খসড়া (বাংলা / English)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            scope.launch { repository.analyzeWritingWithAi(chapter, writingDraft) }
                        }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI ফিডব্যাক নিন")
                        }
                        Button(onClick = { repository.submitChapterWritingTask(chapter, writingDraft) }) {
                            Text("রাইটিং জমা দিন")
                        }
                    }

                    state.lastAiFeedback?.let { ai ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "🤖 AI Writing Assistant • একাডেমিক টোন স্কোর: ${ai.academicToneScore}/100",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(ai.toneFeedback, style = MaterialTheme.typography.bodyMedium)
                                ai.structureSuggestions.forEach { s -> Text("• $s", style = MaterialTheme.typography.bodyMedium) }
                                ai.grammarSuggestions.forEach { g -> Text("• $g", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
        }

        // 2. Smart Co-Author Matchmaking (Section 6.2)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("স্মার্ট কো-অথর ম্যাচমেকিং (৪–৫ জনের আদর্শ টিম)", style = MaterialTheme.typography.titleMedium)
                    }
                    coAuthorSuggestions.forEach { cand ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "${cand.user.name} (${cand.user.department}) • ম্যাচ স্কোর ${cand.matchScore.toInt()}",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "পরিপূরক দক্ষতা: ${cand.complementarySkills.joinToString(", ").ifBlank { "Shared Skills" }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Paid Supervision Negotiation & Wallet Payment (Section 6.3)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("পেইড সুপারভিশন ও নেগোসিয়েশন মডিউল", style = MaterialTheme.typography.titleMedium)
                    state.supervisionDeals.forEach { deal ->
                        Text("${deal.researchGroupName} — টপিক: ${deal.paperTopic}", fontWeight = FontWeight.Medium)
                        Text("সুপারভাইজার: ${deal.supervisorName} • ${deal.agreedSessions} সেশন • ফি: ৳${deal.agreedFeeBdt.toInt()}")
                        if (deal.isPaidViaWallet) {
                            Badge { Text("ওয়ালেট থেকে পরিশোধিত ✔") }
                        } else {
                            Button(onClick = { repository.paySupervisionViaWallet(deal.id) }) {
                                Text("ওয়ালেট ক্রেডিট থেকে পরিশোধ করুন (৳${deal.agreedFeeBdt.toInt()})")
                            }
                        }
                    }
                }
            }
        }

        // 4. Credit Wallet (SSLCommerz / bKash PGW / Manual TrxID) (Section 2.3)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ক্রেডিট ওয়ালেট: ৳${state.currentUser.walletBalanceBdt.toInt()}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(500, 1000, 2000).forEach { amt ->
                            FilterChip(
                                selected = topUpAmount == amt,
                                onClick = { topUpAmount = amt },
                                label = { Text("৳$amt") }
                            )
                        }
                    }
                    PaymentMethod.entries.forEach { pm ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedPaymentMethod == pm,
                                onClick = { selectedPaymentMethod = pm }
                            )
                            Text(pm.labelBn)
                        }
                    }
                    if (selectedPaymentMethod == PaymentMethod.MANUAL_TRX) {
                        OutlinedTextField(
                            value = manualTrxInput,
                            onValueChange = { manualTrxInput = it },
                            label = { Text("bKash / Nagad Transaction ID") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Button(
                        onClick = {
                            repository.topUpWallet(topUpAmount.toDouble(), selectedPaymentMethod, manualTrxInput)
                            manualTrxInput = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("৳$topUpAmount ওয়ালেটে লোড করুন")
                    }
                }
            }
        }

        // 5. Researcher of the Month Leaderboard & Shareable Social Card (Section 2.4 & 6.4)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Researcher of the Month লিডারবোর্ড", style = MaterialTheme.typography.titleMedium)
                    }
                    leaderboard.take(4).forEachIndexed { idx, u ->
                        Text(
                            "#${idx + 1} ${u.name} (${u.department}) — ${u.gamificationPoints} pts • 🔥 ${u.readingStreakDays}d",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    HorizontalDivider()
                    Text(
                        "অর্জিত ব্যাজ ও সার্টিফিকেট: ${state.currentUser.earnedBadges.joinToString(" • ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = {
                            shareBrandedProgressCard(
                                context = context,
                                researcherName = state.currentUser.name,
                                streakDays = state.currentUser.readingStreakDays,
                                badges = state.currentUser.earnedBadges
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("LinkedIn / Facebook-এ প্রগ্রেস কার্ড শেয়ার করুন")
                    }
                }
            }
        }

        // Wallet Transaction Ledger
        items(state.walletHistory.take(4), key = { it.id }) { tx ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tx.description, fontWeight = FontWeight.Medium)
                        Text("${tx.reference} • ${tx.status}", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        text = if (tx.amountBdt >= 0) "+৳${tx.amountBdt.toInt()}" else "-৳${(-tx.amountBdt).toInt()}",
                        fontWeight = FontWeight.Bold,
                        color = if (tx.amountBdt >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
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
