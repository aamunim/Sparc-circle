package org.sparc.studycircle.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.sparc.studycircle.BuildConfig
import org.sparc.studycircle.core.data.SparcAppState
import org.sparc.studycircle.core.data.SparcRepository
import org.sparc.studycircle.core.model.*
import org.sparc.studycircle.core.theme.LocalSparcPalette
import org.sparc.studycircle.core.theme.SparcCrimson
import org.sparc.studycircle.core.theme.tr
import org.sparc.studycircle.core.theme.trContent
import org.sparc.studycircle.ui.components.SparcAvatarCircle
import org.sparc.studycircle.ui.components.applyLauncherIconPreset
import kotlin.math.roundToInt

private const val PORTAL_BASE_URL = "https://sparc-circle.vercel.app"

@Composable
fun SparcSettingsScreen(
    state: SparcAppState,
    repository: SparcRepository,
    onClose: () -> Unit,
    onOpenProfileEdit: () -> Unit
) {
    BackHandler(onBack = onClose)

    val p = LocalSparcPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui = state.uiCustomization
    val user = state.currentUser
    val displayName = trContent(user.name)

    var rulesExpanded by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }

    fun openExternalUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
    ) {
        // --------------------------------------------------------------------
        // Curved Gradient Full-Page Top Header + Profile Identity Hero
        // --------------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(p.primaryGradient)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.20f),
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { onClose() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = tr("ফিরে যান", "Back"),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = tr("সেটিংস ও পছন্দসমূহ", "Settings & Preferences"),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = tr("থিম, স্মার্ট রিডার, নোটিফিকেশন ও অ্যাকাউন্ট", "Theme, Smart Reader, Alerts & Account"),
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f))
                    ) {
                        Text(
                            text = if (state.isSupabaseConnected) tr("🟢 লাইভ", "🟢 Live") else tr("⚪ লোকাল", "⚪ Local"),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Profile Summary Hero Card inside Header
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SparcAvatarCircle(
                            name = displayName,
                            avatarUrl = user.avatarUrl,
                            size = 48.dp,
                            bgColor = Color.White.copy(alpha = 0.26f)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = displayName,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val instPart = user.institution.takeIf { it.isNotBlank() } ?: user.department
                            Text(
                                text = "${user.role.badgeEmoji} ${user.role.shortLabel(state.appLanguage)} • $instPart",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.White.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = "🔥 ${user.readingStreakDays} ${tr("দিন স্ট্রিক", "d streak")}",
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color(0xFFFDE68A)
                                ) {
                                    Text(
                                        text = "৳${user.walletBalanceBdt.toInt()}",
                                        color = Color(0xFF0F172A),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            modifier = Modifier.clickable { onOpenProfileEdit() }
                        ) {
                            Text(
                                text = tr("প্রোফাইল ›", "Profile ›"),
                                color = Color(0xFF0F172A),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------------------
        // Scrollable Full-Page Settings Sections
        // --------------------------------------------------------------------
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ================================================================
            // SECTION 1: Appearance Studio (Language, Theme, Accent, App Icon)
            // ================================================================
            item {
                SettingsSectionCard(
                    eyebrow = tr("অ্যাপিয়ারেন্স স্টুডিও", "APPEARANCE STUDIO"),
                    title = tr("ভাষা ও ভিজ্যুয়াল থিম", "Language & Visual Theme"),
                    eyebrowColor = p.primary,
                    trailingHeader = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = p.cardAlt,
                            border = BorderStroke(1.dp, p.border)
                        ) {
                            Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf("bn" to "বাংলা", "en" to "EN").forEach { (code, label) ->
                                    val active = state.appLanguage == code
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(if (active) p.primary else Color.Transparent)
                                            .clickable { repository.setAppLanguage(code) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (active) Color.White else p.muted,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) {
                    // 3 Visual Theme Mini-Preview Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themeOptions = listOf(
                            Triple(SparcVisualTheme.COBALT_SAAS, "Cobalt", listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))),
                            Triple(SparcVisualTheme.MIDNIGHT_GLASS, "Midnight", listOf(Color(0xFF0F172A), Color(0xFF10B981))),
                            Triple(SparcVisualTheme.OCEAN_HERO, "Ocean", listOf(Color(0xFF0D9488), Color(0xFF0284C7)))
                        )
                        themeOptions.forEach { (t, shortTitle, grad) ->
                            val selected = state.visualTheme == t
                            val sub = when (t) {
                                SparcVisualTheme.COBALT_SAAS -> tr("ক্লিন লাইট", "Clean Light")
                                SparcVisualTheme.MIDNIGHT_GLASS -> tr("ডার্ক গ্লাস", "Dark Glass")
                                SparcVisualTheme.OCEAN_HERO -> tr("টিল হিরো", "Teal Hero")
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) p.primarySoft else p.cardAlt,
                                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) p.primary else p.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { repository.setVisualTheme(t) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(9.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(26.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Brush.linearGradient(grad)),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .padding(end = 6.dp)
                                                    .size(15.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = shortTitle,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = if (selected) p.primarySoftText else p.text
                                    )
                                    Text(
                                        text = sub,
                                        fontSize = 10.5.sp,
                                        color = p.muted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = p.border.copy(alpha = 0.6f))

                    // Accent Color Swatches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = tr("অ্যাকসেন্ট রঙ", "Accent Color"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = p.text
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isDefault = ui.accentPreset == null
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDefault) p.primary else p.cardAlt,
                                border = BorderStroke(1.dp, if (isDefault) p.primary else p.border),
                                modifier = Modifier.clickable {
                                    repository.updateUiCustomization(
                                        ui.copy(accentPreset = null),
                                        "ডিফল্ট অ্যাকসেন্ট চালু হয়েছে",
                                        "Default accent restored"
                                    )
                                }
                            ) {
                                Text(
                                    text = tr("ডিফল্ট", "Auto"),
                                    color = if (isDefault) Color.White else p.text,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            SparcAccentPreset.entries.forEach { preset ->
                                val active = ui.accentPreset == preset
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(preset.primaryHex), Color(preset.primaryEndHex))
                                            )
                                        )
                                        .then(
                                            if (active) Modifier.border(2.dp, p.text, CircleShape) else Modifier
                                        )
                                        .clickable {
                                            repository.updateUiCustomization(
                                                ui.copy(accentPreset = preset),
                                                "অ্যাকসেন্ট কালার: ${preset.labelBn}",
                                                "Accent: ${preset.labelEn}"
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (active) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = preset.label(state.appLanguage),
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = p.border.copy(alpha = 0.6f))

                    // Phone Home-Screen Launcher Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = tr("ফোন লঞ্চার আইকন", "Phone App Icon"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = p.text
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple(LauncherIconPreset.CLASSIC, "Classic", Color(0xFF2563EB)),
                                Triple(LauncherIconPreset.EMERALD, "Emerald", Color(0xFF10B981)),
                                Triple(LauncherIconPreset.GOLD, "Gold", Color(0xFFD97706))
                            ).forEach { (preset, label, dotColor) ->
                                val active = ui.launcherIconPreset == preset
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (active) p.primary else p.cardAlt,
                                    border = BorderStroke(1.dp, if (active) p.primary else p.border),
                                    modifier = Modifier.clickable {
                                        if (preset != ui.launcherIconPreset) {
                                            applyLauncherIconPreset(context, preset)
                                        }
                                        repository.updateUiCustomization(
                                            ui.copy(launcherIconPreset = preset),
                                            "অ্যাপ আইকন পরিবর্তন হয়েছে: ${preset.labelBn}",
                                            "App icon changed to: ${preset.labelEn}"
                                        )
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(if (active) Color.White else dotColor)
                                        )
                                        Text(
                                            text = label,
                                            color = if (active) Color.White else p.text,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================================================================
            // SECTION 2: Smart Reader & Audio Read-Along Comfort
            // ================================================================
            item {
                val paper = ui.readerPaperTheme
                SettingsSectionCard(
                    eyebrow = tr("স্মার্ট রিডার ও READ-ALONG", "SMART READER & READ-ALONG"),
                    title = tr("পড়ার আরাম ও ফন্ট সেটিংস", "Reading Comfort & Font Size"),
                    eyebrowColor = Color(0xFF059669),
                    trailingHeader = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = p.primarySoft
                        ) {
                            Text(
                                text = "${ui.readerFontSizeSp} sp",
                                color = p.primarySoftText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                ) {
                    Slider(
                        value = ui.readerFontSizeSp.toFloat(),
                        onValueChange = { nextVal ->
                            val rounded = nextVal.roundToInt().coerceIn(14, 24)
                            if (rounded != ui.readerFontSizeSp) {
                                repository.updateUiCustomization(ui.copy(readerFontSizeSp = rounded), null, null)
                            }
                        },
                        valueRange = 14f..24f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = p.primary,
                            activeTrackColor = p.primary
                        )
                    )

                    // 4 Paper Theme Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val papers = listOf(
                            ReaderPaperTheme.PURE_WHITE to tr("সাদা", "White"),
                            ReaderPaperTheme.WARM_SEPIA to tr("সেপিয়া", "Sepia"),
                            ReaderPaperTheme.SOFT_MINT to tr("মিন্ট", "Mint"),
                            ReaderPaperTheme.AMOLED_NIGHT to tr("নাইট", "Night")
                        )
                        papers.forEach { (pt, shortLabel) ->
                            val active = paper == pt
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(pt.bgHex),
                                border = BorderStroke(
                                    if (active) 2.dp else 1.dp,
                                    if (active) p.primary else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        repository.updateUiCustomization(
                                            ui.copy(readerPaperTheme = pt),
                                            "রিডার পেপার থিম: ${pt.labelBn}",
                                            "Reader paper theme: ${pt.labelEn}"
                                        )
                                    }
                            ) {
                                Text(
                                    text = if (active) "$shortLabel ✓" else shortLabel,
                                    color = Color(pt.textHex),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Live Paper & Font Sample Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(paper.bgHex),
                        border = BorderStroke(1.dp, p.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr(
                                "নমুনা পাঠ: যৌথ পাঠ ও গবেষণার মাধ্যমে চিন্তাশীল লেখক হয়ে উঠুন।",
                                "Sample text: Build scholarly writing habits through collaborative reading."
                            ),
                            color = Color(paper.textHex),
                            fontSize = ui.readerFontSizeSp.sp,
                            lineHeight = (ui.readerFontSizeSp * 1.5f).sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // ================================================================
            // SECTION 3: Workspace & Notifications (Quiet Hours)
            // ================================================================
            item {
                val tabChoices = listOf(
                    "CIRCLE" to tr("সার্কেল", "Circle"),
                    "LIBRARY" to tr("লাইব্রেরি", "Library"),
                    "ASSESSMENT" to tr("পাঠ ও অনুশীলন", "Practice"),
                    "RESEARCH" to tr("পাবলিশ", "Publish"),
                    "DIRECTORY" to tr("ডিরেক্টরি", "Directory")
                )
                SettingsSectionCard(
                    eyebrow = tr("ওয়ার্কস্পেস ও নোটিফিকেশন", "WORKSPACE & ALERTS"),
                    title = tr("হোম লেআউট ও নীরব সময়", "Home Layout & Quiet Hours"),
                    eyebrowColor = p.primary
                ) {
                    Text(
                        text = tr("🧭 অ্যাপ ওপেন করলে ডিফল্ট যে ট্যাবে যাবে", "🧭 Default Opening Tab"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = p.muted
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tabChoices) { (code, label) ->
                            val active = ui.defaultLandingTab == code
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) p.primary else p.cardAlt,
                                border = BorderStroke(1.dp, if (active) p.primary else p.border),
                                modifier = Modifier.clickable {
                                    repository.updateUiCustomization(
                                        ui.copy(defaultLandingTab = code),
                                        "ডিফল্ট ট্যাব সেট হয়েছে: $label",
                                        "Default tab set to: $label"
                                    )
                                }
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) Color.White else p.text,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = p.border.copy(alpha = 0.6f))

                    SettingsSwitchRow(
                        title = tr("🌙 নীরব সময় (রাত ১০টা – সকাল ৭টা সাইলেন্ট)", "🌙 Quiet Hours (10:00 PM – 7:00 AM Silent)"),
                        subtitle = tr("রাতে স্টাডি পোক ও অ্যালার্ট শব্দ করবে না", "Mutes notification alerts overnight"),
                        checked = ui.quietHoursEnabled,
                        onCheckedChange = {
                            repository.updateUiCustomization(ui.copy(quietHoursEnabled = it), null, null)
                        }
                    )
                    SettingsSwitchRow(
                        title = tr("কমপ্যাক্ট হোম কার্ড লেআউট", "Compact Home Card Layout"),
                        checked = ui.compactHomeCards,
                        onCheckedChange = {
                            repository.updateUiCustomization(ui.copy(compactHomeCards = it), null, null)
                        }
                    )
                    SettingsSwitchRow(
                        title = tr("দৈনিক পাঠের লক্ষ্য ও লাইভ সেশন কার্ড", "Daily Reading Target & Live Session Card"),
                        checked = ui.showHomeStatsRow,
                        onCheckedChange = {
                            repository.updateUiCustomization(ui.copy(showHomeStatsRow = it), null, null)
                        }
                    )
                    SettingsSwitchRow(
                        title = tr("সহপাঠী উৎসাহ (Poke) কার্ড দেখান", "Show Peer Motivation (Poke) Card"),
                        checked = ui.showHomePokeCard,
                        onCheckedChange = {
                            repository.updateUiCustomization(ui.copy(showHomePokeCard = it), null, null)
                        }
                    )
                }
            }

            // ================================================================
            // SECTION 4: Guidelines, Portal Shortcut & Account Security
            // ================================================================
            item {
                SettingsSectionCard(
                    eyebrow = tr("নীতিমালা ও অ্যাকাউন্ট", "GUIDELINES & SECURITY"),
                    title = tr("একাডেমিক নিয়মাবলী ও নিরাপত্তা", "Academic Rules & Account Safety"),
                    eyebrowColor = p.primary
                ) {
                    // Collapsible 5 Academic Guidelines
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = p.cardAlt,
                        border = BorderStroke(1.dp, p.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rulesExpanded = !rulesExpanded }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("📜 স্টাডি সার্কেলের ৫টি মূল নিয়মাবলী", "📜 5 Core Study Circle Guidelines"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = p.text
                                )
                                Icon(
                                    imageVector = if (rulesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = p.muted
                                )
                            }
                            AnimatedVisibility(visible = rulesExpanded) {
                                Column(
                                    modifier = Modifier.padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    academicGuidelinesList().forEach { (headingBn, headingEn, bodyBn, bodyEn) ->
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = tr(headingBn, headingEn),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = p.primary
                                            )
                                            Text(
                                                text = tr(bodyBn, bodyEn),
                                                fontSize = 11.5.sp,
                                                color = p.muted,
                                                lineHeight = 17.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Public Certificate Verification Link
                    SettingsNavRow(
                        title = tr("🎓 পাবলিক সার্টিফিকেট যাচাই করুন", "🎓 Verify SPARC Certificate Online"),
                        trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        onClick = { openExternalUrl("$PORTAL_BASE_URL/verify") }
                    )

                    // Privacy Policy
                    SettingsNavRow(
                        title = tr("🔒 গোপনীয়তা নীতি ও ডেটা সুরক্ষা", "🔒 Privacy Policy & Data Safety"),
                        trailingIcon = Icons.Default.ChevronRight,
                        onClick = { showPrivacyDialog = true }
                    )

                    // Web Admin & Mentor Portal Shortcut (Visible ONLY to Admin / SPARC Rep / Mentor)
                    if (user.role.isMentorOrAdmin) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = p.primarySoft,
                            border = BorderStroke(1.dp, p.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openExternalUrl(PORTAL_BASE_URL) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr("🌐 ওয়েব অ্যাডমিন ও মেন্টর পোর্টাল খুলুন", "🌐 Open Web Admin & Mentor Portal"),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.5.sp,
                                        color = p.primarySoftText
                                    )
                                    Text(
                                        text = tr(
                                            "ব্র্যান্ডিং, পেমেন্ট অনুমোদন, মেন্টর কিউ ও API ভল্ট নিয়ন্ত্রণ",
                                            "Manage branding, payments, grading queue & API Vault"
                                        ),
                                        fontSize = 11.sp,
                                        color = p.primarySoftText.copy(alpha = 0.85f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = p.primarySoftText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Password Change & Server Sync Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPasswordDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, p.border),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp), tint = p.text)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = tr("পাসওয়ার্ড পরিবর্তন", "Change Password"),
                                color = p.text,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = { scope.launch { repository.syncFromSupabaseNow(silent = false) } },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, p.border),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp), tint = p.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = tr("সার্ভার সিঙ্ক", "Sync Now"),
                                color = p.primary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = p.border.copy(alpha = 0.6f))

                    // Sign Out & Delete Account Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onClose()
                                repository.logout()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.2.dp, SparcCrimson),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = tr("সাইন আউট", "Sign Out"),
                                color = SparcCrimson,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SparcCrimson.copy(alpha = 0.10f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    deleteConfirmText = ""
                                    showDeleteDialog = true
                                }
                        ) {
                            Text(
                                text = tr("🗑️ অ্যাকাউন্ট মুছুন", "🗑️ Delete Account"),
                                color = SparcCrimson,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // App Version Footer
            item {
                Text(
                    text = "SPARC Study Circle v${BuildConfig.VERSION_NAME} • ${tr("বিজ্ঞাপনমুক্ত একাডেমিক প্ল্যাটফর্ম", "Ad-Free Academic Platform")}",
                    color = p.muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
            }
        }
    }

    // ------------------------------------------------------------------------
    // Dialog 1: Privacy Policy
    // ------------------------------------------------------------------------
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Text(tr("🔒 SPARC গোপনীয়তা নীতি", "🔒 SPARC Privacy Policy"), fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Text(
                    tr(
                        "SPARC Study Circle আপনার একাডেমিক প্রোফাইল, পাঠ অগ্রগতি, রাইটিং পাণ্ডুলিপি এবং ওয়ালেট লেনদেন শুধুমাত্র পাঠচক্র পরিচালনা ও মেন্টর মূল্যায়নের জন্য সুরক্ষিত রাখে। কোনো তৃতীয় পক্ষের কাছে ডেটা বিক্রি বা বিজ্ঞাপনে ব্যবহার করা হয় না।",
                        "SPARC Study Circle stores your academic profile, reading progress, manuscripts, and wallet ledger solely for study circle collaboration and human mentor evaluation. Data is never sold or shared for advertising."
                    ),
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPrivacyDialog = false
                    openExternalUrl("$PORTAL_BASE_URL/privacy")
                }) {
                    Text(tr("ওয়েব নীতি খুলুন ↗", "Full Web Policy ↗"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(tr("বন্ধ করুন", "Close"))
                }
            }
        )
    }

    // ------------------------------------------------------------------------
    // Dialog 2: Self-Service Password Change
    // ------------------------------------------------------------------------
    if (showPasswordDialog) {
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var localErr by remember { mutableStateOf<String?>(null) }
        var savingPass by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!savingPass) showPasswordDialog = false },
            title = {
                Text(tr("🔑 নতুন পাসওয়ার্ড সেট করুন", "🔑 Change Password"), fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it; localErr = null },
                        label = { Text(tr("নতুন পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)", "New Password (min 6 chars)")) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it; localErr = null },
                        label = { Text(tr("পুনরায় নতুন পাসওয়ার্ড দিন", "Confirm New Password")) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    localErr?.let {
                        Text(it, color = SparcCrimson, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !savingPass && newPass.length >= 6,
                    onClick = {
                        if (newPass != confirmPass) {
                            localErr = if (state.appLanguage == "en") "Passwords do not match." else "দুটি পাসওয়ার্ড মেলেনি।"
                            return@Button
                        }
                        savingPass = true
                        scope.launch {
                            repository.changeMyPassword(newPass)
                                .onSuccess {
                                    savingPass = false
                                    showPasswordDialog = false
                                }
                                .onFailure { e ->
                                    savingPass = false
                                    localErr = e.message ?: "Error"
                                }
                        }
                    }
                ) {
                    Text(if (savingPass) tr("সেভ হচ্ছে…", "Saving…") else tr("আপডেট করুন", "Update"))
                }
            },
            dismissButton = {
                TextButton(enabled = !savingPass, onClick = { showPasswordDialog = false }) {
                    Text(tr("বাতিল", "Cancel"))
                }
            }
        )
    }

    // ------------------------------------------------------------------------
    // Dialog 3: Permanent Account Deletion
    // ------------------------------------------------------------------------
    if (showDeleteDialog) {
        val canDelete = deleteConfirmText.trim().equals("DELETE", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    tr("⚠️ স্থায়ীভাবে অ্যাকাউন্ট মুছবেন?", "⚠️ Permanently Delete Account?"),
                    color = SparcCrimson,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        tr(
                            "সতর্কতা: আপনার প্রোফাইল, পাঠ অগ্রগতি, রাইটিং এবং ওয়ালেট ডেটা সার্ভার থেকে স্থায়ীভাবে মুছে যাবে। নিশ্চিত করতে নিচে ইংরেজিতে DELETE লিখুন:",
                            "Warning: Your profile, reading progress, manuscripts, and wallet history will be permanently erased from the server. Type DELETE below to confirm:"
                        ),
                        fontSize = 12.sp,
                        color = p.muted
                    )
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        label = { Text("DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = canDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = SparcCrimson),
                    onClick = {
                        showDeleteDialog = false
                        onClose()
                        scope.launch {
                            repository.deleteMyAccount()
                        }
                    }
                ) {
                    Text(tr("স্থায়ীভাবে মুছুন", "Delete Permanently"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(tr("বাতিল", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    eyebrow: String,
    title: String,
    eyebrowColor: Color,
    trailingHeader: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalSparcPalette.current
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = p.card,
        border = BorderStroke(1.dp, p.border),
        shadowElevation = if (p.isDark) 0.dp else 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = eyebrow,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = eyebrowColor,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = p.text
                    )
                }
                trailingHeader?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val p = LocalSparcPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 11.sp, color = p.muted)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val p = LocalSparcPalette.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = p.cardAlt,
        border = BorderStroke(1.dp, p.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.text,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = p.muted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private data class GuidelineItem(
    val titleBn: String,
    val titleEn: String,
    val bodyBn: String,
    val bodyEn: String
)

private fun academicGuidelinesList(): List<GuidelineItem> = listOf(
    GuidelineItem(
        "১. দুই-স্তরের ইউজার রোল ও দায়িত্ব",
        "1. Two-Tier Roles & Responsibilities",
        "গবেষক (বই পড়া ও অনুশীলনী), সার্কেল লিড (সেশন সমন্বয়ক), মেন্টর (উন্মুক্ত প্রশ্ন ও রাইটিং মূল্যায়ন) এবং অ্যাডমিন।",
        "Researcher (reading & exercises), Circle Lead (session coordinator), Mentor (rubric grading & writing review), and Admin."
    ),
    GuidelineItem(
        "২. পাঠ ও অনুশীলনের ধারাবাহিকতা",
        "2. Consistent Reading & Practice",
        "প্রতিটি অধ্যায় মনোযোগ দিয়ে পড়ার পর ফ্ল্যাশ নোট দেখে নির্ধারিত অনুশীলনী সম্পন্ন করুন।",
        "Read each chapter attentively, review flash notes, and complete guided exercises honestly."
    ),
    GuidelineItem(
        "৩. সহপাঠী শিষ্টাচার ও নীরব সময়",
        "3. Peer Etiquette & Quiet Hours",
        "বিতর্ক থ্রেডে যুক্তিভিত্তিক ও শ্রদ্ধাশীল মতামত দিন; রাত ১০টা থেকে সকাল ৭টা পর্যন্ত নোটিফিকেশন সাইলেন্ট থাকে।",
        "Keep debate discussions scholarly and respectful; notifications observe 10 PM–7 AM Quiet Hours."
    ),
    GuidelineItem(
        "৪. মানব মেন্টর মূল্যায়নকে অগ্রাধিকার",
        "4. Human Evaluation Priority",
        "এআই শুধুমাত্র প্রাথমিক ভাষাগত ইঙ্গিত দেয়; চূড়ান্ত গবেষণাপত্র সবসময় মানব মেন্টরের কাছে জমা দিন।",
        "AI hints assist with language structure only; always submit manuscripts to your human Mentor."
    ),
    GuidelineItem(
        "৫. কপিরাইট ও উৎস উল্লেখ",
        "5. Copyright & Attribution",
        "শুধুমাত্র ওপেন-অ্যাক্সেস বা শিক্ষামূলক পাঠচক্রে অনুমোদিত বই ও প্রবন্ধ মূল লেখকের নামসহ ব্যবহার করুন।",
        "Share only open-access or educationally permitted texts with proper author and source attribution."
    )
)
