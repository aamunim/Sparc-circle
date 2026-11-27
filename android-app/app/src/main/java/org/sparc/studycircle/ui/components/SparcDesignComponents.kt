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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
