package org.sparc.studycircle.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sparc.studycircle.core.model.*
import org.sparc.studycircle.core.theme.*
import java.io.File
import java.net.URL

private val AvatarBitmapCache = LruCache<String, ImageBitmap>(32)

fun applyLauncherIconPreset(context: Context, chosen: LauncherIconPreset) {
    runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        LauncherIconPreset.entries.forEach { item ->
            val cn = ComponentName(pkg, "$pkg${item.aliasClassSuffix}")
            val newState = if (item == chosen) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(cn, newState, PackageManager.DONT_KILL_APP)
        }
    }
}

@Composable
fun SparcScholarlyCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    borderColor: Color? = null,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalSparcPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 18.dp else 22.dp),
        color = containerColor ?: p.card,
        border = BorderStroke(1.dp, borderColor ?: p.border),
        shadowElevation = if (p.isDark) 0.dp else 3.dp
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
            content = content
        )
    }
}

@Composable
fun SparcGradientHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalSparcPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(p.primaryGradient)
            .padding(18.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun SparcCircularGauge(
    percentage: Float,
    centerTopText: String,
    centerSubText: String? = null,
    size: Dp = 74.dp,
    strokeWidth: Dp = 7.dp,
    activeColor: Color? = null
) {
    val p = LocalSparcPalette.current
    val ringColor = activeColor ?: p.primary
    val trackColor = if (p.isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = (percentage.coerceIn(0f, 100f) / 100f) * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerTopText,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = p.text
            )
            if (centerSubText != null) {
                Text(
                    text = centerSubText,
                    fontSize = 10.sp,
                    color = p.muted
                )
            }
        }
    }
}

@Composable
fun SparcSixStageStepper(
    currentStep: Int,
    onLightGradient: Boolean = false,
    modifier: Modifier = Modifier
) {
    val p = LocalSparcPalette.current
    if (onLightGradient) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (step in 1..6) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (step <= currentStep) Color.White
                            else Color.White.copy(alpha = 0.28f)
                        )
                )
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (step in 1..6) {
                val active = step <= currentStep
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (active) p.primary else p.cardAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = step.toString(),
                        color = if (active) Color.White else p.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (step < 6) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.5.dp)
                            .padding(horizontal = 3.dp)
                            .background(if (step < currentStep) p.primary else p.cardAlt)
                    )
                }
            }
        }
    }
}

@Composable
fun SparcSegmentedPills(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalSparcPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = p.card,
        border = BorderStroke(1.dp, p.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { idx, title ->
                val selected = idx == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) p.primary else Color.Transparent)
                        .clickable { onSelect(idx) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (selected) Color.White else p.muted,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun SparcAvatarCircle(
    name: String,
    size: Dp = 40.dp,
    bgColor: Color? = null,
    textColor: Color = Color.White,
    avatarUrl: String = "",
    modifier: Modifier = Modifier
) {
    val p = LocalSparcPalette.current
    val initials = name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "SP" }

    var bitmapState by remember(avatarUrl) {
        mutableStateOf(if (avatarUrl.isNotBlank()) AvatarBitmapCache.get(avatarUrl) else null)
    }

    LaunchedEffect(avatarUrl) {
        if (avatarUrl.isNotBlank() && bitmapState == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = if (avatarUrl.startsWith("http")) {
                        URL(avatarUrl).openStream().use { it.readBytes() }
                    } else {
                        File(avatarUrl).takeIf { it.exists() }?.readBytes()
                    }
                    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                }.getOrNull()
            }
            if (loaded != null) {
                AvatarBitmapCache.put(avatarUrl, loaded)
                bitmapState = loaded
            }
        }
    }

    val shape = RoundedCornerShape((size.value * 0.34f).dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bgColor ?: p.primary),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmapState
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initials,
                color = textColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.33f).sp
            )
        }
    }
}

@Composable
fun SparcPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val p = LocalSparcPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = p.primary,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(vertical = 13.dp, horizontal = 20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun SparcAudioWaveformBar(
    title: String,
    currentMs: Long,
    durationMs: Long,
    speedLabel: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipSeconds: (Int) -> Unit,
    onCycleSpeed: () -> Unit
) {
    val p = LocalSparcPalette.current
    val safeDuration = durationMs.coerceAtLeast(1000L)
    val progressFraction = (currentMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60L
        val s = totalSec % 60L
        return "%02d:%02d".format(m, s)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.primaryGradient)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎧 $title", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.20f),
                    modifier = Modifier.clickable { onCycleSpeed() }
                ) {
                    Text(
                        text = speedLabel,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Slider(
                value = progressFraction,
                onValueChange = { frac -> onSeekTo((frac * safeDuration).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.30f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatMs(currentMs)} / ${formatMs(safeDuration)}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.clickable { onSkipSeconds(-10) }
                    ) {
                        Text("-10s", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause Audio Summary",
                            tint = Color(0xFF0F172A)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.clickable { onSkipSeconds(10) }
                    ) {
                        Text("+10s", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SparcRoleBadgesRow(
    coreRole: UserRole,
    activityBadges: List<ActivityRoleBadge> = emptyList(),
    onLightGradient: Boolean = false
) {
    val p = LocalSparcPalette.current
    val lang = LocalAppLanguage.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val roleBg = when {
            onLightGradient -> Color.White.copy(alpha = 0.22f)
            coreRole.isAdminLike -> Color(0xFFFEF3C7)
            coreRole == UserRole.SUPERVISOR -> p.primarySoft
            coreRole == UserRole.MODERATOR -> p.accentSoft
            else -> p.cardAlt
        }
        val roleText = when {
            onLightGradient -> Color.White
            coreRole.isAdminLike -> Color(0xFF92400E)
            coreRole == UserRole.SUPERVISOR -> p.primarySoftText
            coreRole == UserRole.MODERATOR -> p.accentText
            else -> p.text
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = roleBg,
            border = if (!onLightGradient) BorderStroke(0.8.dp, p.border) else null
        ) {
            Text(
                text = "${coreRole.badgeEmoji} ${coreRole.shortLabel(lang)}",
                color = roleText,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }

        activityBadges.distinct().forEach { badge ->
            Surface(
                shape = RoundedCornerShape(50),
                color = if (onLightGradient) Color.White.copy(alpha = 0.18f) else p.primarySoft
            ) {
                Text(
                    text = badge.label(lang),
                    color = if (onLightGradient) Color.White else p.primarySoftText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparcSettingsBottomSheet(
    currentTheme: SparcVisualTheme,
    currentLanguage: String = "bn",
    currentRole: UserRole = UserRole.MEMBER,
    appCustomization: AppCustomizationConfig = AppCustomizationConfig(),
    uiCustomization: UserUiCustomization = UserUiCustomization(),
    apiVault: ApiKeyVaultConfig = ApiKeyVaultConfig(),
    isServerConnected: Boolean = false,
    onSelectTheme: (SparcVisualTheme) -> Unit,
    onChangeLanguage: (String) -> Unit = {},
    onSwitchRolePreview: (UserRole) -> Unit = {},
    onUpdateUiCustomization: (UserUiCustomization, String?, String?) -> Unit = { _, _, _ -> },
    onSaveAppCustomization: (AppCustomizationConfig) -> Unit = {},
    onUpdateApiVault: (ApiKeyVaultConfig, String) -> Unit = { _, _ -> },
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val p = LocalSparcPalette.current
    val context = LocalContext.current
    var settingsTab by remember { mutableIntStateOf(0) } // 0 = Theme & Accent, 1 = App & Role, 2 = Brand & Pay, 3 = Rules, 4 = API Keys

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        contentColor = p.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tr("কাস্টমাইজেশন ও সেটিংস স্টুডিও", "Customization & Settings Studio"),
                style = MaterialTheme.typography.titleLarge,
                color = p.text
            )

            SparcSegmentedPills(
                options = listOf(
                    tr("🎨 থিম", "🎨 Theme"),
                    tr("📱 অ্যাপ", "📱 App"),
                    tr("🏛️ ব্র্যান্ড", "🏛️ Brand"),
                    tr("📜 নিয়ম", "📜 Rules"),
                    tr("🔑 API", "🔑 API")
                ),
                selectedIndex = settingsTab,
                onSelect = { settingsTab = it }
            )

            when (settingsTab) {
                0 -> ThemeAndAccentTab(
                    currentTheme = currentTheme,
                    currentLanguage = currentLanguage,
                    uiCustomization = uiCustomization,
                    onSelectTheme = onSelectTheme,
                    onChangeLanguage = onChangeLanguage,
                    onUpdateUiCustomization = onUpdateUiCustomization
                )
                1 -> AppIconAndLayoutTab(
                    currentLanguage = currentLanguage,
                    currentRole = currentRole,
                    uiCustomization = uiCustomization,
                    onSwitchRolePreview = onSwitchRolePreview,
                    onUpdateUiCustomization = { next, bn, en ->
                        if (next.launcherIconPreset != uiCustomization.launcherIconPreset) {
                            applyLauncherIconPreset(context, next.launcherIconPreset)
                        }
                        onUpdateUiCustomization(next, bn, en)
                    },
                    onLogout = {
                        onDismiss()
                        onLogout()
                    },
                    onDeleteAccount = {
                        onDismiss()
                        onDeleteAccount()
                    }
                )
                2 -> BrandAndPaymentAdminTab(
                    currentRole = currentRole,
                    appCustomization = appCustomization,
                    onSwitchToAdmin = { onSwitchRolePreview(UserRole.ADMIN) },
                    onSave = onSaveAppCustomization
                )
                3 -> SparcFixedGuidelinesTab()
                else -> ApiKeysVaultPanel(
                    apiVault = apiVault,
                    isServerConnected = isServerConnected,
                    onUpdateVault = onUpdateApiVault
                )
            }
        }
    }
}

@Composable
private fun SparcFixedGuidelinesTab() {
    val p = LocalSparcPalette.current
    val rules = listOf(
        Triple(
            "১. দুই-স্তরের ইউজার রোল ও দায়িত্ব",
            "1. Two-Tier Roles & Responsibilities",
            tr(
                "সিস্টেমে ৪টি মূল রোল রয়েছে: Researcher (বই পড়া ও অনুশীলনী), Circle Lead (ভোটে নির্বাচিত সেশন ও প্রেজেন্টার সমন্বয়ক), Mentor (SPARC-নিযুক্ত বিশেষজ্ঞ: F–J মূল্যায়ন, রেড-ফ্ল্যাগ ও Peer Buddy সহায়তা) এবং Admin (কপিরাইট, বই অর্ডার ও মেন্টর নিয়োগ)। পাশাপাশি অ্যাক্টিভিটি ব্যাজ হিসেবে থাকে: গ্রুপ উদ্যোক্তা (+৫০ পয়েন্ট), উপস্থাপক, সহ-লেখক ও সহপাঠী সঙ্গী।",
                "Core roles: Researcher (reading & practice), Circle Lead (member-elected session & presenter lead), Mentor (SPARC-appointed expert for F–J grading, red-flags & Peer Buddy pairing), and Admin (copyright, book orders & mentor assignment). Dynamic badges: Circle Host (+50 pts), Presenter, Co-Author & Peer Buddy."
            )
        ),
        Triple(
            "২. পাঠ ও অনুশীলনের ধারাবাহিকতা",
            "2. Consistent Reading & Practice",
            tr(
                "প্রতিটি বই বা গবেষণা প্রবন্ধ মনোযোগ দিয়ে পড়ার পর ফ্ল্যাশ নোটস দেখে নিজের বোধগম্যতা যাচাই করুন এবং নির্ধারিত পাঠ অনুশীলনী সম্পন্ন করুন।",
                "Read each chapter or research paper attentively, review the flash notes, and complete the guided practice exercises honestly."
            )
        ),
        Triple(
            "৩. স্টাডি সার্কেল ও সহপাঠী শিষ্টাচার",
            "3. Study Circle & Peer Etiquette",
            tr(
                "সহপাঠীদের উৎসাহ দিতে Poke পাঠানোর সময় শালীনতা বজায় রাখুন (রাত ১০টা–সকাল ৭টা Quiet Hours)। বিতর্ক কর্নারে সবসময় যুক্তিভিত্তিক ও শ্রদ্ধাশীল মতামত দিন।",
                "Encourage peers respectfully within daily Poke limits (10 PM–7 AM Quiet Hours) and keep all debate arguments constructive and scholarly."
            )
        ),
        Triple(
            "৪. মানব মূল্যায়ন বনাম এআই (AI) সতর্কতা",
            "4. Human Evaluation Over AI",
            tr(
                "এআই (AI) ভুল বা যান্ত্রিক পরামর্শ দিতে পারে। তাই চূড়ান্ত গবেষণাপত্র ও অনুচ্ছেদ সবসময় মানব মেন্টর বা সুপারভাইজারের কাছে Evaluate করার জন্য জমা দিন।",
                "AI tools can hallucinate or miss deep academic nuance. Always prioritize submitting your writing to a human mentor for evaluation."
            )
        ),
        Triple(
            "৫. অন্য লেখকের PDF, কপিরাইট ও গোপনীয়তা",
            "5. Copyright, Attribution & Privacy",
            tr(
                "অন্য লেখক বা গবেষকের PDF প্রবন্ধ আপলোড করলে অবশ্যই মূল লেখকের নাম ও উৎস/DOI উল্লেখ করতে হবে এবং শুধুমাত্র ওপেন-অ্যাক্সেস বা শিক্ষামূলক পাঠচক্রে অনুমোদিত ম্যাটেরিয়াল ব্যবহার করতে হবে।",
                "Always credit original authors and DOI/source when uploading external PDFs, and share only open-access or educationally permitted works."
            )
        )
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 22.dp)
    ) {
        item {
            Text(
                tr("📜 স্টাডি সার্কেল ও প্ল্যাটফর্মের নিয়মাবলী", "📜 Study Circle & Platform Guidelines"),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = p.primary
            )
        }
        items(rules.size) { idx ->
            val (titleBn, titleEn, body) = rules[idx]
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = p.cardAlt,
                border = BorderStroke(1.dp, p.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        tr(titleBn, titleEn),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = p.text
                    )
                    Text(body, fontSize = 12.sp, color = p.muted, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun ThemeAndAccentTab(
    currentTheme: SparcVisualTheme,
    currentLanguage: String,
    uiCustomization: UserUiCustomization,
    onSelectTheme: (SparcVisualTheme) -> Unit,
    onChangeLanguage: (String) -> Unit,
    onUpdateUiCustomization: (UserUiCustomization, String?, String?) -> Unit
) {
    val p = LocalSparcPalette.current
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            SparcSegmentedPills(
                options = listOf("বাংলা (Bengali)", "English"),
                selectedIndex = if (currentLanguage == "en") 1 else 0,
                onSelect = { idx -> onChangeLanguage(if (idx == 1) "en" else "bn") }
            )
        }

        item {
            Text(
                tr("🎨 ৬টি কাস্টম অ্যাকসেন্ট কালার পিকার:", "🎨 6 Custom Accent Color Presets:"),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = p.text
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    val isDefault = uiCustomization.accentPreset == null
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDefault) p.primarySoft else p.cardAlt,
                        border = BorderStroke(if (isDefault) 2.dp else 1.dp, if (isDefault) p.primary else p.border),
                        modifier = Modifier.clickable {
                            onUpdateUiCustomization(
                                uiCustomization.copy(accentPreset = null),
                                "থিম ডিফল্ট কালার চালু হয়েছে",
                                "Theme default color restored"
                            )
                        }
                    ) {
                        Text(
                            tr("ডিফল্ট", "Default"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDefault) p.primarySoftText else p.text,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
                items(SparcAccentPreset.entries) { preset ->
                    val selected = uiCustomization.accentPreset == preset
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) p.primarySoft else p.cardAlt,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(preset.primaryHex) else p.border),
                        modifier = Modifier.clickable {
                            onUpdateUiCustomization(
                                uiCustomization.copy(accentPreset = preset),
                                "অ্যাকসেন্ট কালার পরিবর্তন হয়েছে: ${preset.labelBn}",
                                "Accent color changed to: ${preset.labelEn}"
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(preset.primaryHex), Color(preset.primaryEndHex))
                                        )
                                    )
                            )
                            Text(
                                preset.label(currentLanguage),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = p.text
                            )
                        }
                    }
                }
            }
        }

        items(
            listOf(
                Triple(SparcVisualTheme.COBALT_SAAS, listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)), "A"),
                Triple(SparcVisualTheme.MIDNIGHT_GLASS, listOf(Color(0xFF0F172A), Color(0xFF10B981)), "B"),
                Triple(SparcVisualTheme.OCEAN_HERO, listOf(Color(0xFF0D9488), Color(0xFF0284C7)), "C")
            )
        ) { (themeItem, gradColors, badge) ->
            val active = currentTheme == themeItem
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (active) p.primarySoft else p.cardAlt,
                border = BorderStroke(if (active) 2.dp else 1.dp, if (active) p.primary else p.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTheme(themeItem) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(gradColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(badge, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            themeItem.titleBn,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (active) p.primarySoftText else p.text
                        )
                        Text(themeItem.subtitle(currentLanguage), fontSize = 11.sp, color = p.muted)
                    }
                    if (active) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = p.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppIconAndLayoutTab(
    currentLanguage: String,
    currentRole: UserRole,
    uiCustomization: UserUiCustomization,
    onSwitchRolePreview: (UserRole) -> Unit,
    onUpdateUiCustomization: (UserUiCustomization, String?, String?) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit = {}
) {
    val p = LocalSparcPalette.current
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    val tabChoices = listOf(
        "CIRCLE" to tr("সার্কেল", "Circle"),
        "LIBRARY" to tr("লাইব্রেরি", "Library"),
        "DIRECTORY" to tr("ডিরেক্টরি", "Directory"),
        "ASSESSMENT" to tr("পাঠ ও অনুশীলন", "Read & Practice"),
        "RESEARCH" to tr("পাবলিশ", "Publish")
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        // Role Switcher (Preview / Testing Mode)
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = p.cardAlt,
                border = BorderStroke(1.dp, p.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr("🎭 রোল সুইচার (Role Preview & Permission Test)", "🎭 Role Switcher (Preview & Permission Test)"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp,
                        color = p.primary
                    )
                    Text(
                        tr(
                            "বর্তমান সক্রিয় রোল: ${currentRole.badgeEmoji} ${currentRole.labelBn} • এক ট্যাপে যেকোনো রোলের UI ও পারমিশন পরীক্ষা করুন:",
                            "Active role: ${currentRole.badgeEmoji} ${currentRole.labelEn} • Tap to preview role permissions:"
                        ),
                        fontSize = 11.5.sp,
                        color = p.muted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            UserRole.MEMBER,
                            UserRole.MODERATOR,
                            UserRole.SUPERVISOR,
                            UserRole.ADMIN
                        ).forEach { r ->
                            val selected = currentRole == r || (r == UserRole.ADMIN && currentRole == UserRole.SPARC_REP)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) p.primary else p.card,
                                border = BorderStroke(1.dp, if (selected) p.primary else p.border),
                                modifier = Modifier.clickable { onSwitchRolePreview(r) }
                            ) {
                                Text(
                                    text = "${r.badgeEmoji} ${r.shortLabel(currentLanguage)}",
                                    color = if (selected) Color.White else p.text,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                tr("📱 ফোনের হোম-স্ক্রিন লঞ্চার আইকন স্টাইল:", "📱 Phone Home-Screen Launcher Icon:"),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = p.text
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    LauncherIconPreset.CLASSIC to Color(0xFF2563EB),
                    LauncherIconPreset.EMERALD to Color(0xFF10B981),
                    LauncherIconPreset.GOLD to Color(0xFFD97706)
                ).forEach { (preset, previewColor) ->
                    val active = uiCustomization.launcherIconPreset == preset
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) p.primarySoft else p.cardAlt,
                        border = BorderStroke(if (active) 2.dp else 1.dp, if (active) p.primary else p.border),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onUpdateUiCustomization(
                                    uiCustomization.copy(launcherIconPreset = preset),
                                    "ফোনের অ্যাপ লঞ্চার আইকন পরিবর্তন করা হয়েছে: ${preset.labelBn}",
                                    "App launcher icon updated to: ${preset.labelEn}"
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(previewColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("★", color = Color.White, fontWeight = FontWeight.ExtraBold)
                            }
                            Text(
                                preset.label(currentLanguage),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = p.text
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                tr("🧭 অ্যাপ ওপেন করলে ডিফল্ট যে ট্যাবে যাবে:", "🧭 Default Opening Tab:"),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = p.text
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tabChoices) { (code, label) ->
                    val active = uiCustomization.defaultLandingTab == code
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) p.primary else p.cardAlt,
                        modifier = Modifier.clickable {
                            onUpdateUiCustomization(
                                uiCustomization.copy(defaultLandingTab = code),
                                "ডিফল্ট ওপেনিং ট্যাব সেট হয়েছে: $label",
                                "Default landing tab set to: $label"
                            )
                        }
                    ) {
                        Text(
                            label,
                            color = if (active) Color.White else p.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = p.cardAlt,
                border = BorderStroke(1.dp, p.border)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr("হোম ড্যাশবোর্ড উইজেট কাস্টমাইজেশন", "Home Dashboard Widgets"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = p.text
                    )
                    ToggleRowItem(
                        title = tr("কমপ্যাক্ট কার্ড লেআউট (Compact View)", "Compact Card Layout"),
                        checked = uiCustomization.compactHomeCards,
                        onCheckedChange = {
                            onUpdateUiCustomization(uiCustomization.copy(compactHomeCards = it), null, null)
                        }
                    )
                    ToggleRowItem(
                        title = tr("দৈনিক লক্ষ্য ও লাইভ সেশন কুইক কার্ড", "Daily Target & Next Session Cards"),
                        checked = uiCustomization.showHomeStatsRow,
                        onCheckedChange = {
                            onUpdateUiCustomization(uiCustomization.copy(showHomeStatsRow = it), null, null)
                        }
                    )
                    ToggleRowItem(
                        title = tr("সহপাঠী উৎসাহ (Poke) কার্ড দেখান", "Show Peer Motivation (Poke) Card"),
                        checked = uiCustomization.showHomePokeCard,
                        onCheckedChange = {
                            onUpdateUiCustomization(uiCustomization.copy(showHomePokeCard = it), null, null)
                        }
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showPrivacyDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, p.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        tr("🔒 গোপনীয়তা নীতি ও ডেটা সুরক্ষা (Privacy Policy)", "🔒 Privacy Policy & Data Safety"),
                        color = p.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, SparcCrimson),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("লগআউট করুন (Sign Out)", "Sign Out"), color = SparcCrimson, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = {
                        deleteConfirmText = ""
                        showDeleteConfirmDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        tr("🗑️ স্থায়ীভাবে অ্যাকাউন্ট ও ডেটা মুছুন (Delete Account)", "🗑️ Permanently Delete Account & Data"),
                        color = SparcCrimson,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(tr("🔒 SPARC গোপনীয়তা নীতি", "🔒 SPARC Privacy Policy"), fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    tr(
                        "SPARC Study Circle আপনার একাডেমিক প্রোফাইল, পাঠ অগ্রগতি, রাইটিং পাণ্ডুলিপি এবং ওয়ালেট লেনদেন শুধুমাত্র পাঠচক্র পরিচালনা ও মেন্টর মূল্যায়নের জন্য সুরক্ষিত রাখে। কোনো তৃতীয় পক্ষের কাছে ডেটা বিক্রি বা বিজ্ঞাপনে ব্যবহার করা হয় না। ফোনের নোটিফিকেশন রাত ১০টা থেকে সকাল ৭টা পর্যন্ত স্বয়ংক্রিয়ভাবে সাইলেন্ট (Quiet Hours) থাকে।",
                        "SPARC Study Circle stores your academic profile, reading progress, manuscripts, and wallet ledger solely for study circle collaboration and human mentor evaluation. Data is never sold or shared for ads. Notifications observe 10 PM–7 AM Quiet Hours."
                    ),
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(tr("বন্ধ করুন", "Close"))
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        val canDelete = deleteConfirmText.trim().equals("DELETE", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
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
                            "সতর্কতা: আপনার প্রোফাইল, পাঠ অগ্রগতি, রাইটিং এবং ওয়ালেট ডেটা সার্ভার থেকে স্থায়ীভাবে মুছে যাবে। এটি আর ফিরিয়ে আনা যাবে না। নিশ্চিত করতে নিচে ইংরেজিতে DELETE লিখুন:",
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
                        showDeleteConfirmDialog = false
                        onDeleteAccount()
                    }
                ) {
                    Text(tr("স্থায়ীভাবে মুছুন", "Delete Permanently"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(tr("বাতিল", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun ToggleRowItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val p = LocalSparcPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BrandAndPaymentAdminTab(
    currentRole: UserRole,
    appCustomization: AppCustomizationConfig,
    onSwitchToAdmin: () -> Unit,
    onSave: (AppCustomizationConfig) -> Unit
) {
    val p = LocalSparcPalette.current
    if (!currentRole.isAdminLike) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = p.accentSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        tr("🛡️ শুধুমাত্র অ্যাডমিন (SPARC টিম) সংরক্ষিত", "🛡️ Restricted to Admin (SPARC Team)"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        color = p.accentText
                    )
                    Text(
                        tr(
                            "গ্লোবাল ব্র্যান্ডিং, নোটিশ ব্যানার ও অফিশিয়াল পেমেন্ট নম্বর শুধুমাত্র অ্যাডমিন পরিবর্তন করতে পারেন। টেস্টিংয়ের জন্য নিচের বাটনে ট্যাপ করে Admin মোডে যেতে পারেন।",
                            "Global branding, announcement banners, and payment merchant numbers can only be modified by Admin. Tap below to preview as Admin."
                        ),
                        fontSize = 12.sp,
                        color = p.accentText
                    )
                }
            }
            SparcPrimaryButton(
                text = tr("🛡️ Admin (Preview) রোলে সুইচ করুন", "🛡️ Switch to Admin (Preview) Role"),
                onClick = onSwitchToAdmin
            )
        }
        return
    }

    var appTitle by remember(appCustomization) { mutableStateOf(appCustomization.appTitle) }
    var taglineBn by remember(appCustomization) { mutableStateOf(appCustomization.appTaglineBn) }
    var taglineEn by remember(appCustomization) { mutableStateOf(appCustomization.appTaglineEn) }
    var splashBn by remember(appCustomization) { mutableStateOf(appCustomization.splashQuoteBn) }
    var noticeBn by remember(appCustomization) { mutableStateOf(appCustomization.announcementBannerBn) }
    var noticeEn by remember(appCustomization) { mutableStateOf(appCustomization.announcementBannerEn) }
    var showNotice by remember(appCustomization) { mutableStateOf(appCustomization.showAnnouncementBanner) }
    var bkashNo by remember(appCustomization) { mutableStateOf(appCustomization.bkashMerchantNumber) }
    var nagadNo by remember(appCustomization) { mutableStateOf(appCustomization.nagadMerchantNumber) }
    var rocketNo by remember(appCustomization) { mutableStateOf(appCustomization.rocketMerchantNumber) }
    var supFee by remember(appCustomization) { mutableStateOf(appCustomization.defaultSupervisionFeeBdt.toInt().toString()) }
    var depositFee by remember(appCustomization) { mutableStateOf(appCustomization.borrowDepositBdt.toInt().toString()) }
    var minTopup by remember(appCustomization) { mutableStateOf(appCustomization.minTopupBdt.toInt().toString()) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 22.dp)
    ) {
        item {
            Text(
                tr("🏛️ ব্র্যান্ডিং, নোটিশ ব্যানার ও অফিশিয়াল পেমেন্ট নম্বর", "🏛️ Branding, Notice Banner & Payment Config"),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = p.primary
            )
        }
        item {
            OutlinedTextField(
                value = appTitle,
                onValueChange = { appTitle = it },
                label = { Text(tr("অ্যাপের শিরোনাম (App Title)", "App Title")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = taglineBn,
                onValueChange = { taglineBn = it },
                label = { Text(tr("ট্যাগলাইন (বাংলা)", "Tagline (Bengali)")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = taglineEn,
                onValueChange = { taglineEn = it },
                label = { Text(tr("ট্যাগলাইন (English)", "Tagline (English)")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = splashBn,
                onValueChange = { splashBn = it },
                label = { Text(tr("স্প্ল্যাশ কোট / মটো", "Splash Screen Quote")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ToggleRowItem(
                title = tr("টপ নোটিশ/ঘোষণা ব্যানার চালু রাখুন", "Show Top Announcement Banner"),
                checked = showNotice,
                onCheckedChange = { showNotice = it }
            )
            OutlinedTextField(
                value = noticeBn,
                onValueChange = { noticeBn = it },
                label = { Text(tr("নোটিশ ব্যানার টেক্সট (বাংলা)", "Announcement Banner (Bengali)")) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = noticeEn,
                onValueChange = { noticeEn = it },
                label = { Text(tr("নোটিশ ব্যানার টেক্সট (English)", "Announcement Banner (English)")) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bkashNo,
                    onValueChange = { bkashNo = it },
                    label = { Text("bKash No.") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = nagadNo,
                    onValueChange = { nagadNo = it },
                    label = { Text("Nagad No.") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rocketNo,
                    onValueChange = { rocketNo = it },
                    label = { Text("Rocket No.") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minTopup,
                    onValueChange = { minTopup = it.filter { ch -> ch.isDigit() } },
                    label = { Text(tr("ন্যূনতম টপ-আপ ৳", "Min Top-Up ৳")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = supFee,
                    onValueChange = { supFee = it.filter { ch -> ch.isDigit() } },
                    label = { Text(tr("মেন্টর ফি ৳", "Mentor Fee ৳")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = depositFee,
                    onValueChange = { depositFee = it.filter { ch -> ch.isDigit() } },
                    label = { Text(tr("বই ধার জামানত ৳", "Borrow Deposit ৳")) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            SparcPrimaryButton(
                text = tr("সেভ ও সার্ভারে সিঙ্ক করুন (Save Brand Config)", "Save & Sync Brand Config"),
                onClick = {
                    onSave(
                        appCustomization.copy(
                            appTitle = appTitle.trim().ifBlank { "SPARC Study Circle" },
                            appTaglineBn = taglineBn.trim().ifBlank { "বই পড়তে পড়তে লেখা শেখা" },
                            appTaglineEn = taglineEn.trim().ifBlank { "Learning Academic Writing While Reading" },
                            splashQuoteBn = splashBn.trim().ifBlank { appCustomization.splashQuoteBn },
                            announcementBannerBn = noticeBn.trim(),
                            announcementBannerEn = noticeEn.trim(),
                            showAnnouncementBanner = showNotice,
                            bkashMerchantNumber = bkashNo.trim().ifBlank { "01700-000000" },
                            nagadMerchantNumber = nagadNo.trim().ifBlank { "01800-000000" },
                            rocketMerchantNumber = rocketNo.trim().ifBlank { "01900-000000" },
                            defaultSupervisionFeeBdt = supFee.toDoubleOrNull() ?: 300.0,
                            borrowDepositBdt = depositFee.toDoubleOrNull() ?: 150.0,
                            minTopupBdt = (minTopup.toDoubleOrNull() ?: 500.0).coerceAtLeast(50.0)
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun ApiKeysVaultPanel(
    apiVault: ApiKeyVaultConfig,
    isServerConnected: Boolean,
    onUpdateVault: (ApiKeyVaultConfig, String) -> Unit
) {
    val p = LocalSparcPalette.current
    val isEn = LocalAppLanguage.current == "en"
    var geminiInput by remember(apiVault.geminiApiKey) { mutableStateOf(apiVault.geminiApiKey) }
    var bkashInput by remember(apiVault.bkashMerchantKey) { mutableStateOf(apiVault.bkashMerchantKey) }
    var sslInput by remember(apiVault.sslCommerzStoreId) { mutableStateOf(apiVault.sslCommerzStoreId) }
    var jitsiInput by remember(apiVault.customJitsiServerUrl) { mutableStateOf(apiVault.customJitsiServerUrl) }
    var serverUrlInput by remember(apiVault.customSupabaseUrl) { mutableStateOf(apiVault.customSupabaseUrl) }
    var serverKeyInput by remember(apiVault.customSupabaseAnonKey) { mutableStateOf(apiVault.customSupabaseAnonKey) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isServerConnected) p.primarySoft else p.accentSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isServerConnected)
                        tr("✓ লাইভ সার্ভার সংযুক্ত আছে", "✓ Connected to Live Server")
                    else
                        tr("ℹ বর্তমানে অফলাইন ডেমো মোড চালু আছে", "ℹ Running in Offline Demo Mode"),
                    color = if (isServerConnected) p.primarySoftText else p.accentText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        item {
            ApiKeySlotEditor(
                title = "Google Gemini API Key (AI Writing Coach)",
                placeholder = "AIzaSy...",
                currentSaved = apiVault.geminiApiKey,
                draftValue = geminiInput,
                onDraftChange = { geminiInput = it },
                onSave = {
                    onUpdateVault(
                        apiVault.copy(geminiApiKey = geminiInput.trim()),
                        if (isEn) "Gemini API Key saved!" else "Gemini API Key সফলভাবে যুক্ত হয়েছে!"
                    )
                },
                onRemove = {
                    geminiInput = ""
                    onUpdateVault(
                        apiVault.copy(geminiApiKey = ""),
                        if (isEn) "Gemini API Key removed." else "Gemini API Key মুছে ফেলা হয়েছে।"
                    )
                }
            )
        }

        item {
            ApiKeySlotEditor(
                title = "bKash PGW App / Merchant Key",
                placeholder = "bkash_app_key_...",
                currentSaved = apiVault.bkashMerchantKey,
                draftValue = bkashInput,
                onDraftChange = { bkashInput = it },
                onSave = {
                    onUpdateVault(
                        apiVault.copy(bkashMerchantKey = bkashInput.trim()),
                        if (isEn) "bKash Merchant Key saved!" else "bKash Merchant Key সংরক্ষিত হয়েছে!"
                    )
                },
                onRemove = {
                    bkashInput = ""
                    onUpdateVault(
                        apiVault.copy(bkashMerchantKey = ""),
                        if (isEn) "bKash Merchant Key removed." else "bKash Merchant Key মুছে ফেলা হয়েছে।"
                    )
                }
            )
        }

        item {
            ApiKeySlotEditor(
                title = "SSLCommerz Store ID / API Key",
                placeholder = "sparc_live_store...",
                currentSaved = apiVault.sslCommerzStoreId,
                draftValue = sslInput,
                onDraftChange = { sslInput = it },
                onSave = {
                    onUpdateVault(
                        apiVault.copy(sslCommerzStoreId = sslInput.trim()),
                        if (isEn) "SSLCommerz Key saved!" else "SSLCommerz Key সংরক্ষিত হয়েছে!"
                    )
                },
                onRemove = {
                    sslInput = ""
                    onUpdateVault(
                        apiVault.copy(sslCommerzStoreId = ""),
                        if (isEn) "SSLCommerz Key removed." else "SSLCommerz Key মুছে ফেলা হয়েছে।"
                    )
                }
            )
        }

        item {
            ApiKeySlotEditor(
                title = "Custom Jitsi Video Server URL",
                placeholder = "https://meet.jit.si",
                currentSaved = apiVault.customJitsiServerUrl,
                draftValue = jitsiInput,
                onDraftChange = { jitsiInput = it },
                onSave = {
                    onUpdateVault(
                        apiVault.copy(customJitsiServerUrl = jitsiInput.trim()),
                        if (isEn) "Custom Jitsi Server URL saved!" else "কাস্টম ভিডিও সার্ভার URL সংরক্ষিত হয়েছে!"
                    )
                },
                onRemove = {
                    jitsiInput = ""
                    onUpdateVault(
                        apiVault.copy(customJitsiServerUrl = ""),
                        if (isEn) "Custom Jitsi Server URL reset." else "কাস্টম ভিডিও সার্ভার URL রিসেট করা হয়েছে।"
                    )
                }
            )
        }

        item {
            ApiKeySlotEditor(
                title = tr("কাস্টম সার্ভার URL (ঐচ্ছিক ওভাররাইড)", "Custom Server URL (Optional Override)"),
                placeholder = "https://api.sparc-server.org",
                currentSaved = apiVault.customSupabaseUrl,
                draftValue = serverUrlInput,
                onDraftChange = { serverUrlInput = it },
                onSave = {
                    onUpdateVault(
                        apiVault.copy(customSupabaseUrl = serverUrlInput.trim(), customSupabaseAnonKey = serverKeyInput.trim()),
                        if (isEn) "Custom Server connection saved!" else "কাস্টম সার্ভার সংযোগ সংরক্ষিত হয়েছে!"
                    )
                },
                onRemove = {
                    serverUrlInput = ""
                    serverKeyInput = ""
                    onUpdateVault(
                        apiVault.copy(customSupabaseUrl = "", customSupabaseAnonKey = ""),
                        if (isEn) "Custom Server override removed." else "কাস্টম সার্ভার ওভাররাইড মুছে ফেলা হয়েছে।"
                    )
                }
            )
        }

        item {
            OutlinedTextField(
                value = serverKeyInput,
                onValueChange = { serverKeyInput = it },
                label = { Text(tr("কাস্টম সার্ভার অ্যাক্সেস কী (ঐচ্ছিক)", "Custom Server Access Key (Optional)")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedButton(
                onClick = {
                    geminiInput = ""
                    bkashInput = ""
                    sslInput = ""
                    jitsiInput = ""
                    serverUrlInput = ""
                    serverKeyInput = ""
                    onUpdateVault(
                        ApiKeyVaultConfig(),
                        if (isEn) "All custom API keys cleared." else "সব কাস্টম API Key মুছে ফেলা হয়েছে।"
                    )
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SparcCrimson),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(tr("সব কাস্টম API Key মুছে ফেলুন (Clear All)", "Clear All Custom API Keys"), color = SparcCrimson, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
private fun ApiKeySlotEditor(
    title: String,
    placeholder: String,
    currentSaved: String,
    draftValue: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit
) {
    val p = LocalSparcPalette.current
    val hasKey = currentSaved.isNotBlank()
    val notSetLabel = tr("যুক্ত নেই", "Not set")
    val maskedPreview = if (currentSaved.length > 8) {
        "${currentSaved.take(5)}•••••${currentSaved.takeLast(3)}"
    } else if (hasKey) "••••••" else notSetLabel

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = p.cardAlt,
        border = BorderStroke(1.dp, p.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = p.text, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasKey) p.primarySoft else p.card
                ) {
                    Text(
                        text = if (hasKey) "✓ $maskedPreview" else notSetLabel,
                        color = if (hasKey) p.primarySoftText else p.muted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            OutlinedTextField(
                value = draftValue,
                onValueChange = onDraftChange,
                placeholder = { Text(placeholder, fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    enabled = draftValue.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (hasKey) tr("আপডেট করুন", "Update") else tr("যুক্ত করুন (Save)", "Save Key"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (hasKey) {
                    OutlinedButton(
                        onClick = onRemove,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SparcCrimson),
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text(tr("মুছুন (Remove)", "Remove"), color = SparcCrimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
