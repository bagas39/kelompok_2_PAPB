package com.example.cloble.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cloble.ui.theme.CloBleBrandColors
import com.example.cloble.ui.theme.CloBleColors

@Composable
fun CloBleLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    showWordmark: Boolean = false
) {
    Row(
        modifier = modifier.semantics { contentDescription = "Logo CloBle" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val corner = w * 0.24f

            drawRoundRect(
                color = CloBleBrandColors.Cobalt,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(corner, corner)
            )

            drawRoundRect(
                color = CloBleBrandColors.CobaltDeep,
                topLeft = Offset(w * 0.04f, h * 0.04f),
                size = Size(w * 0.92f, h * 0.92f),
                cornerRadius = CornerRadius(corner * 0.88f, corner * 0.88f),
                style = Stroke(width = w * 0.025f)
            )

            fun px(v: Float): Float = ((v - 14f) / 80f) * w
            val ringStroke = (3.8f / 80f) * w
            val monoStroke = (4.3f / 80f) * w

            val ringRadius = (27f / 80f) * w
            val ringTopLeft = Offset(w / 2f - ringRadius, h / 2f - ringRadius)
            val ringSize = Size(ringRadius * 2f, ringRadius * 2f)

            drawArc(
                color = CloBleBrandColors.IceRing,
                startAngle = -150f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
            )

            val topArrow = Path().apply {
                moveTo(px(78.27f), px(34.44f))
                lineTo(px(77.38f), px(40.50f))
                lineTo(px(71.69f), px(38.24f))
            }
            drawPath(
                path = topArrow,
                color = CloBleBrandColors.IceRing,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            drawArc(
                color = CloBleBrandColors.IceRing,
                startAngle = 30f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
            )

            val bottomArrow = Path().apply {
                moveTo(px(29.73f), px(73.56f))
                lineTo(px(30.62f), px(67.50f))
                lineTo(px(36.31f), px(69.76f))
            }
            drawPath(
                path = bottomArrow,
                color = CloBleBrandColors.IceRing,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val letterC = Path().apply {
                moveTo(px(49.0f), px(45.5f))
                cubicTo(
                    px(46.5f), px(41.8f),
                    px(40.5f), px(41.8f),
                    px(38.2f), px(46.0f)
                )
                cubicTo(
                    px(36.6f), px(49.2f),
                    px(36.6f), px(58.8f),
                    px(38.2f), px(62.0f)
                )
                cubicTo(
                    px(40.5f), px(66.2f),
                    px(46.5f), px(66.2f),
                    px(49.0f), px(62.5f)
                )
            }
            drawPath(
                path = letterC,
                color = Color.White,
                style = Stroke(width = monoStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val letterB = Path().apply {
                moveTo(px(56.5f), px(42.8f))
                lineTo(px(56.5f), px(65.2f))
                moveTo(px(56.5f), px(42.8f))
                lineTo(px(64.0f), px(42.8f))
                cubicTo(
                    px(68.8f), px(42.8f),
                    px(68.8f), px(53.4f),
                    px(64.0f), px(53.4f)
                )
                lineTo(px(56.5f), px(53.4f))
                lineTo(px(64.8f), px(53.4f))
                cubicTo(
                    px(70.2f), px(53.4f),
                    px(70.2f), px(65.2f),
                    px(64.8f), px(65.2f)
                )
                lineTo(px(56.5f), px(65.2f))
            }
            drawPath(
                path = letterB,
                color = Color.White,
                style = Stroke(width = monoStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        if (showWordmark) {
            Text(
                text = "CloBle",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = CloBleColors.TextPrimary
            )
        }
    }
}
