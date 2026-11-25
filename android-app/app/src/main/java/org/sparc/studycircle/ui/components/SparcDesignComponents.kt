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
    timeLabel: String,
    speedLabel: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onCycleSpeed: () -> Unit
) {
    val p = LocalSparcPalette.current
    val barHeights = listOf(8, 18, 12, 22, 16, 10, 24, 15, 20, 12, 18, 9, 15, 21, 13, 10)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.primaryGradient)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "$timeLabel • $speedLabel",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onCycleSpeed() }
                    )
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    barHeights.forEachIndexed { idx, h ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(h.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (idx < 9) Color.White else Color.White.copy(alpha = 0.32f)
                                )
                        )
                    }
                }
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause Audio Summary",
                    tint = Color(0xFF0F172A)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparcSettingsBottomSheet(
    currentTheme: SparcVisualTheme,
    onSelectTheme: (SparcVisualTheme) -> Unit,
    onDismiss: () -> Unit
) {
    val p = LocalSparcPalette.current
    var streakNotifications by remember { mutableStateOf(true) }
    var quietHoursAuto by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        contentColor = p.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "অ্যাপ সেটিংস ও UI থিম পরিবর্তন",
                style = MaterialTheme.typography.titleLarge,
                color = p.text
            )
            Text(
                text = "আপনার পছন্দমতো যেকোনো একটি মডার্ন UI থিম বেছে নিন (অটো-সেভ হবে):",
                style = MaterialTheme.typography.labelMedium,
                color = p.muted
            )

            // 3 Visual Theme Cards
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
                    shape = RoundedCornerShape(18.dp),
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
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(gradColors)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = badge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = themeItem.titleBn,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (active) p.primarySoftText else p.text
                            )
                            Text(
                                text = themeItem.subtitleBn,
                                fontSize = 11.5.sp,
                                color = p.muted
                            )
                        }
                        if (active) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = p.primary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = p.border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("রিডিং স্ট্রিক রিমাইন্ডার", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text("স্ট্রিক ভাঙার আগে পুশ নোটিফিকেশন", fontSize = 11.5.sp, color = p.muted)
                }
                Switch(
                    checked = streakNotifications,
                    onCheckedChange = { streakNotifications = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Quiet Hours (রাত ১০টা – সকাল ৭টা)", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text("রাতে আসা Poke সকাল ৭টায় ডেলিভার হবে", fontSize = 11.5.sp, color = p.muted)
                }
                Switch(
                    checked = quietHoursAuto,
                    onCheckedChange = { quietHoursAuto = it }
                )
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}
