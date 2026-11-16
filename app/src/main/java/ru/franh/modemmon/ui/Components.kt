package ru.franh.modemmon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.ui.theme.Bg
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.Surface
import ru.franh.modemmon.ui.theme.SurfaceHigh
import ru.franh.modemmon.vm.Trend
import kotlin.math.max
import kotlin.math.min

@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    color: Color = Surface,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .border(1.dp, Outline, RoundedCornerShape(14.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
fun StatusPill(online: Boolean, text: String) {
    val color = if (online) Good else Color(0xFFFF5A5F)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TrendArrow(trend: Trend) {
    val (glyph, color) = when (trend) {
        Trend.UP -> "↑" to Good
        Trend.DOWN -> "↓" to Color(0xFFFF5A5F)
        Trend.FLAT -> "→" to Muted
    }
    Text(
        text = glyph,
        color = color,
        fontSize = 30.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun InfoRow(label: String, value: String, accent: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = accent,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Muted,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
    )
}

@Composable
fun MetricChip(
    title: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val bg = if (selected) color.copy(alpha = 0.16f) else SurfaceHigh
    val fg = if (selected) color else Muted
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                1.dp,
                if (selected) color.copy(alpha = 0.6f) else Outline,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * График истории: линия, мягкая заливка и пунктир пика сессии.
 * Ось Y автоматически подстраивается под видимые точки.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    peak: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 110.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(Bg)
            .border(1.dp, Outline, RoundedCornerShape(14.dp))
    ) {
        if (values.size < 2) {
            Text(
                text = "накапливаю данные…",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }

        Canvas(Modifier.fillMaxWidth().height(height).padding(8.dp)) {
            val w = size.width
            val h = size.height

            var lo = values.minOrNull() ?: 0f
            var hi = values.maxOrNull() ?: 1f
            if (peak != null) {
                lo = min(lo, peak)
                hi = max(hi, peak)
            }
            if (hi - lo < 1f) {
                hi += 0.5f
                lo -= 0.5f
            }
            val pad = (hi - lo) * 0.15f
            lo -= pad
            hi += pad

            fun yOf(v: Float): Float = h - (v - lo) / (hi - lo) * h
            fun xOf(i: Int): Float = i.toFloat() / (values.size - 1) * w

            // сетка
            for (k in 1..3) {
                val y = h * k / 4f
                drawLine(
                    color = Outline.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            // пик
            if (peak != null) {
                val py = yOf(peak)
                drawLine(
                    color = Good.copy(alpha = 0.55f),
                    start = Offset(0f, py),
                    end = Offset(w, py),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            val line = Path()
            val fill = Path()
            values.forEachIndexed { i, v ->
                val x = xOf(i)
                val y = yOf(v)
                if (i == 0) {
                    line.moveTo(x, y)
                    fill.moveTo(x, h)
                    fill.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    fill.lineTo(x, y)
                }
            }
            fill.lineTo(w, h)
            fill.close()

            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.30f), Color.Transparent)
                )
            )
            drawPath(
                path = line,
                color = color,
                style = Stroke(width = 2.5f * density, cap = StrokeCap.Round)
            )

            // текущая точка
            val lastX = xOf(values.lastIndex)
            val lastY = yOf(values.last())
            drawCircle(color = color, radius = 4f * density, center = Offset(lastX, lastY))
            drawCircle(
                color = color.copy(alpha = 0.25f),
                radius = 9f * density,
                center = Offset(lastX, lastY)
            )
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    CardBox {
        Text(text, color = Muted, fontSize = 13.sp)
    }
}

/** Полоса заполнения с подписью и значением. */
@Composable
fun ProgressRow(
    label: String,
    value: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Muted, fontSize = 12.sp)
            Text(value, fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceHigh)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

/** Крупное число с подписью — для ключевых показателей. */
@Composable
fun StatTile(
    value: String,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceHigh)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Column {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(caption, fontSize = 10.sp, color = Muted)
        }
    }
}
