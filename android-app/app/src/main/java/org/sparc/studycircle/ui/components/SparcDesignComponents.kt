package org.sparc.studycircle.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sparc.studycircle.core.model.SparcVisualTheme
import org.sparc.studycircle.core.theme.*

@Composable
fun SparcScholarlyCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalSparcPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = containerColor ?: p.card,
        border = BorderStroke(1.dp, borderColor ?: p.border),
        shadowElevation = if (p.isDark) 0.dp else 3.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
        // Modern 6-segment capsule bar on gradient hero cards
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
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (selected) Color.White else p.muted,
                        fontSize = 12.sp,
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
    textColor: Color = Color.White
) {
    val p = LocalSparcPalette.current
    val initials = name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "SP" }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape((size.value * 0.34f).dp))
            .background(bgColor ?: p.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = textColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.33f).sp
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparcSettingsBottomSheet(
    currentTheme: SparcVisualTheme,
    currentLanguage: String = "bn",
    apiVault: org.sparc.studycircle.core.model.ApiKeyVaultConfig = org.sparc.studycircle.core.model.ApiKeyVaultConfig(),
    isSupabaseConnected: Boolean = false,
    onSelectTheme: (SparcVisualTheme) -> Unit,
    onChangeLanguage: (String) -> Unit = {},
    onUpdateApiVault: (org.sparc.studycircle.core.model.ApiKeyVaultConfig, String) -> Unit = { _, _ -> },
    onLogout: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val p = LocalSparcPalette.current
    var settingsTab by remember { mutableIntStateOf(0) } // 0 = Theme & General, 1 = API Keys Vault
    var streakNotifications by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        contentColor = p.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "অ্যাপ সেটিংস ও API কনফিগারেশন",
                style = MaterialTheme.typography.titleLarge,
                color = p.text
            )

            SparcSegmentedPills(
                options = listOf("🎨 থিম ও ভাষা", "🔑 API Keys ম্যানেজার"),
                selectedIndex = settingsTab,
                onSelect = { settingsTab = it }
            )

            if (settingsTab == 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SparcSegmentedPills(
                        options = listOf("বাংলা (Bengali)", "English"),
                        selectedIndex = if (currentLanguage == "en") 1 else 0,
                        onSelect = { idx -> onChangeLanguage(if (idx == 1) "en" else "bn") }
                    )

                    listOf(
                        Triple(
                            SparcVisualTheme.COBALT_SAAS,
                            listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)),
                            "A"
                        ),
                        Triple(
                            SparcVisualTheme.MIDNIGHT_GLASS,
                            listOf(Color(0xFF0F172A), Color(0xFF10B981)),
                            "B"
                        ),
                        Triple(
                            SparcVisualTheme.OCEAN_HERO,
                            listOf(Color(0xFF0D9488), Color(0xFF0284C7)),
                            "C"
                        )
                    ).forEach { (themeItem, gradColors, badge) ->
                        val active = currentTheme == themeItem
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (active) p.primarySoft else p.cardAlt,
                            border = BorderStroke(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) p.primary else p.border
                            ),
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
                                    Text(themeItem.subtitleBn, fontSize = 11.sp, color = p.muted)
                                }
                                if (active) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = p.primary)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("রিডিং স্ট্রিক ও Quiet Hours (১০ PM–৭ AM)", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                            Text("অটো স্ট্রিক রিমাইন্ডার ও নাইট কিউ", fontSize = 11.sp, color = p.muted)
                        }
                        Switch(checked = streakNotifications, onCheckedChange = { streakNotifications = it })
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onLogout()
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, SparcCrimson),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("লগআউট করুন (Sign Out)", color = SparcCrimson, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                // TAB 2: Dynamic API Keys Vault Manager (Add / Update / Remove any API Key!)
                ApiKeysVaultPanel(
                    apiVault = apiVault,
                    isSupabaseConnected = isSupabaseConnected,
                    onUpdateVault = onUpdateApiVault
                )
            }
        }
    }
}

@Composable
private fun ApiKeysVaultPanel(
    apiVault: org.sparc.studycircle.core.model.ApiKeyVaultConfig,
    isSupabaseConnected: Boolean,
    onUpdateVault: (org.sparc.studycircle.core.model.ApiKeyVaultConfig, String) -> Unit
) {
    val p = LocalSparcPalette.current
    var geminiInput by remember(apiVault.geminiApiKey) { mutableStateOf(apiVault.geminiApiKey) }
    var bkashInput by remember(apiVault.bkashMerchantKey) { mutableStateOf(apiVault.bkashMerchantKey) }
    var sslInput by remember(apiVault.sslCommerzStoreId) { mutableStateOf(apiVault.sslCommerzStoreId) }
    var jitsiInput by remember(apiVault.customJitsiServerUrl) { mutableStateOf(apiVault.customJitsiServerUrl) }
    var supaUrlInput by remember(apiVault.customSupabaseUrl) { mutableStateOf(apiVault.customSupabaseUrl) }
    var supaAnonInput by remember(apiVault.customSupabaseAnonKey) { mutableStateOf(apiVault.customSupabaseAnonKey) }

    androidx.compose.foundation.lazy.LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSupabaseConnected) p.primarySoft else p.accentSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isSupabaseConnected)
                        "✓ লাইভ Supabase ডাটাবেস সংযুক্ত আছে"
                    else
                        "ℹ বর্তমানে অফলাইন ডেমো মোড চালু আছে",
                    color = if (isSupabaseConnected) p.primarySoftText else p.accentText,
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
                        "Gemini API Key সফলভাবে যুক্ত হয়েছে!"
                    )
                },
                onRemove = {
                    geminiInput = ""
                    onUpdateVault(
                        apiVault.copy(geminiApiKey = ""),
                        "Gemini API Key মুছে ফেলা হয়েছে।"
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
                        "bKash Merchant Key সংরক্ষিত হয়েছে!"
                    )
                },
                onRemove = {
                    bkashInput = ""
                    onUpdateVault(
                        apiVault.copy(bkashMerchantKey = ""),
                        "bKash Merchant Key মুছে ফেলা হয়েছে।"
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
                        "SSLCommerz Key সংরক্ষিত হয়েছে!"
                    )
                },
                onRemove = {
                    sslInput = ""
                    onUpdateVault(
                        apiVault.copy(sslCommerzStoreId = ""),
                        "SSLCommerz Key মুছে ফেলা হয়েছে।"
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
                        "কাস্টম ভিডিও সার্ভার URL সংরক্ষিত হয়েছে!"
                    )
                },
                onRemove = {
                    jitsiInput = ""
                    onUpdateVault(
                        apiVault.copy(customJitsiServerUrl = ""),
                        "কাস্টম ভিডিও সার্ভার URL রিসেট করা হয়েছে।"
                    )
                }
            )
        }

        item {
            ApiKeySlotEditor(
                title = "Custom Supabase URL (ঐচ্ছিক ওভাররাইড)",
                placeholder = "https://xxxx.supabase.co",
                currentSaved = apiVault.customSupabaseUrl,
                draftValue = supaUrlInput,
                onDraftChange = { supaUrlInput = it },
                onSave = {
                    onUpdateVault(
                        apiVault.copy(customSupabaseUrl = supaUrlInput.trim(), customSupabaseAnonKey = supaAnonInput.trim()),
                        "কাস্টম Supabase সংযোগ সংরক্ষিত হয়েছে!"
                    )
                },
                onRemove = {
                    supaUrlInput = ""
                    supaAnonInput = ""
                    onUpdateVault(
                        apiVault.copy(customSupabaseUrl = "", customSupabaseAnonKey = ""),
                        "কাস্টম Supabase ওভাররাইড মুছে ফেলা হয়েছে।"
                    )
                }
            )
        }

        item {
            OutlinedTextField(
                value = supaAnonInput,
                onValueChange = { supaAnonInput = it },
                label = { Text("Custom Supabase Anon Key (ঐচ্ছিক)") },
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
                    supaUrlInput = ""
                    supaAnonInput = ""
                    onUpdateVault(
                        org.sparc.studycircle.core.model.ApiKeyVaultConfig(),
                        "সব কাস্টম API Key মুছে ফেলা হয়েছে।"
                    )
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SparcCrimson),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("সব কাস্টম API Key মুছে ফেলুন (Clear All)", color = SparcCrimson, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
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
    val maskedPreview = if (currentSaved.length > 8) {
        "${currentSaved.take(5)}•••••${currentSaved.takeLast(3)}"
    } else if (hasKey) "••••••" else "যুক্ত নেই"

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
                        text = if (hasKey) "✓ $maskedPreview" else "যুক্ত নেই",
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
                    Text(if (hasKey) "আপডেট করুন" else "যুক্ত করুন (Save)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (hasKey) {
                    OutlinedButton(
                        onClick = onRemove,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SparcCrimson),
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("মুছুন (Remove)", color = SparcCrimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
