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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sparc.studycircle.core.theme.*

@Composable
fun SparcScholarlyCard(
    modifier: Modifier = Modifier,
    containerColor: Color = SparcSurface,
    borderColor: Color = SparcCardBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun SparcCircularGauge(
    percentage: Float, // 0f..100f
    centerTopText: String,
    centerSubText: String? = null,
    size: Dp = 74.dp,
    strokeWidth: Dp = 7.dp,
    activeColor: Color = SparcEmerald
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            drawArc(
                color = Color(0xFFE2E8F0),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = activeColor,
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
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = SparcInk
            )
            if (centerSubText != null) {
                Text(
                    text = centerSubText,
                    fontSize = 10.sp,
                    color = SparcMutedInk
                )
            }
        }
    }
}

@Composable
fun SparcSixStageStepper(
    currentStep: Int, // 1..6
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (step in 1..6) {
            val isCompletedOrCurrent = step <= currentStep
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isCompletedOrCurrent) SparcEmerald else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step.toString(),
                    color = if (isCompletedOrCurrent) Color.White else SparcMutedInk,
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
                        .background(
                            if (step < currentStep) SparcEmerald else Color(0xFFE2E8F0)
                        )
                )
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = Color(0xFFECEAE4),
        border = BorderStroke(1.dp, SparcCardBorder)
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
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) SparcEmerald else Color.Transparent)
                        .clickable { onSelect(idx) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (selected) Color.White else SparcInk,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
    bgColor: Color = SparcEmerald,
    textColor: Color = Color.White
) {
    val initials = name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "SP" }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.34f).sp
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
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = SparcEmerald,
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
    val barHeights = listOf(8, 16, 12, 22, 18, 10, 24, 15, 20, 12, 18, 9, 15, 21, 13, 10, 16, 8)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = SparcSurface,
        border = BorderStroke(1.dp, SparcCardBorder),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "$title • $timeLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = SparcInk
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    barHeights.forEachIndexed { idx, h ->
                        val active = idx < 9
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(h.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (active) SparcEmerald else Color(0xFFCBD5E1))
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SparcSubtleBg,
                        border = BorderStroke(1.dp, SparcCardBorder),
                        modifier = Modifier.clickable { onCycleSpeed() }
                    ) {
                        Text(
                            text = speedLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SparcEmerald)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause Audio Summary",
                    tint = Color.White
                )
            }
        }
    }
}
