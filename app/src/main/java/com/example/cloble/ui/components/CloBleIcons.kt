package com.example.cloble.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object CloBleIcons {

    val ArrowLeft: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowLeft",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 12f)
                lineTo(5f, 12f)
                moveTo(12f, 19f)
                lineTo(5f, 12f)
                lineTo(12f, 5f)
            }
        }.build()
    }

    val FilterFunnel: ImageVector by lazy {
        ImageVector.Builder(
            name = "FilterFunnel",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 3f)
                lineTo(2f, 3f)
                lineTo(10f, 12.46f)
                lineTo(10f, 19f)
                lineTo(14f, 21f)
                lineTo(14f, 12.46f)
                close()
            }
        }.build()
    }

    val Bell: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bell",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 8f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18f, 8f)
                curveTo(18f, 15f, 21f, 17f, 21f, 17f)
                lineTo(3f, 17f)
                curveTo(3f, 17f, 6f, 15f, 6f, 8f)
                close()
                moveTo(10.3f, 21f)
                curveTo(10.67f, 21.62f, 11.31f, 22f, 12f, 22f)
                curveTo(12.69f, 22f, 13.33f, 21.62f, 13.7f, 21f)
            }
        }.build()
    }

    val LayersOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "LayersOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                lineTo(2f, 7f)
                lineTo(12f, 12f)
                lineTo(22f, 7f)
                close()
                moveTo(2f, 12f)
                lineTo(12f, 17f)
                lineTo(22f, 12f)
                moveTo(2f, 17f)
                lineTo(12f, 22f)
                lineTo(22f, 17f)
            }
        }.build()
    }

    val LayersFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "LayersFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2.5f)
                lineTo(2.5f, 7.2f)
                lineTo(12f, 11.9f)
                lineTo(21.5f, 7.2f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2.5f, 12.2f)
                lineTo(12f, 16.9f)
                lineTo(21.5f, 12.2f)
                moveTo(2.5f, 16.8f)
                lineTo(12f, 21.5f)
                lineTo(21.5f, 16.8f)
            }
        }.build()
    }

    val ShirtOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShirtOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20.38f, 3.46f)
                lineTo(16f, 2f)
                curveTo(15.2f, 4.2f, 13.7f, 5.2f, 12f, 5.2f)
                curveTo(10.3f, 5.2f, 8.8f, 4.2f, 8f, 2f)
                lineTo(3.62f, 3.46f)
                curveTo(2.65f, 3.79f, 2f, 4.7f, 2f, 5.73f)
                lineTo(2f, 8.5f)
                curveTo(2f, 9.33f, 2.67f, 10f, 3.5f, 10f)
                lineTo(6f, 10f)
                lineTo(6f, 20f)
                curveTo(6f, 21.1f, 6.9f, 22f, 8f, 22f)
                lineTo(16f, 22f)
                curveTo(17.1f, 22f, 18f, 21.1f, 18f, 20f)
                lineTo(18f, 10f)
                lineTo(20.5f, 10f)
                curveTo(21.33f, 10f, 22f, 9.33f, 22f, 8.5f)
                lineTo(22f, 5.73f)
                curveTo(22f, 4.7f, 21.35f, 3.79f, 20.38f, 3.46f)
                close()
            }
        }.build()
    }

    val ShirtFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShirtFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(20.38f, 3.46f)
                lineTo(16f, 2f)
                curveTo(15.2f, 4.2f, 13.7f, 5.2f, 12f, 5.2f)
                curveTo(10.3f, 5.2f, 8.8f, 4.2f, 8f, 2f)
                lineTo(3.62f, 3.46f)
                curveTo(2.65f, 3.79f, 2f, 4.7f, 2f, 5.73f)
                lineTo(2f, 8.5f)
                curveTo(2f, 9.33f, 2.67f, 10f, 3.5f, 10f)
                lineTo(6f, 10f)
                lineTo(6f, 20f)
                curveTo(6f, 21.1f, 6.9f, 22f, 8f, 22f)
                lineTo(16f, 22f)
                curveTo(17.1f, 22f, 18f, 21.1f, 18f, 20f)
                lineTo(18f, 10f)
                lineTo(20.5f, 10f)
                curveTo(21.33f, 10f, 22f, 9.33f, 22f, 8.5f)
                lineTo(22f, 5.73f)
                curveTo(22f, 4.7f, 21.35f, 3.79f, 20.38f, 3.46f)
                close()
            }
        }.build()
    }

    val ChatOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChatOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7.9f, 20f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 4f, 16.1f)
                lineTo(2f, 22f)
                close()
            }
        }.build()
    }

    val ChatFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChatFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7.9f, 20f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 4f, 16.1f)
                lineTo(2f, 22f)
                close()
            }
        }.build()
    }

    val UserOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "UserOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 8f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8f, 8f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16f, 8f)
                close()
                moveTo(5f, 20f)
                curveTo(5f, 16.69f, 8.13f, 14f, 12f, 14f)
                curveTo(15.87f, 14f, 19f, 16.69f, 19f, 20f)
            }
        }.build()
    }

    val UserFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "UserFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(16f, 8f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8f, 8f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16f, 8f)
                close()
                moveTo(4.5f, 20f)
                curveTo(4.5f, 16.41f, 7.86f, 13.5f, 12f, 13.5f)
                curveTo(16.14f, 13.5f, 19.5f, 16.41f, 19.5f, 20f)
                lineTo(19.5f, 21f)
                lineTo(4.5f, 21f)
                close()
            }
        }.build()
    }

    val ImagePlaceholder: ImageVector by lazy {
        ImageVector.Builder(
            name = "ImagePlaceholder",
            defaultWidth = 28.dp,
            defaultHeight = 28.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 3f)
                lineTo(19f, 3f)
                curveTo(20.1f, 3f, 21f, 3.9f, 21f, 5f)
                lineTo(21f, 19f)
                curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
                lineTo(5f, 21f)
                curveTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
                lineTo(3f, 5f)
                curveTo(3f, 3.9f, 3.9f, 3f, 5f, 3f)
                close()
                moveTo(10.5f, 8.5f)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 7.5f, 8.5f)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10.5f, 8.5f)
                close()
                moveTo(21f, 15f)
                lineTo(16f, 10f)
                lineTo(5f, 21f)
            }
        }.build()
    }

    val ChevronDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronDown",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 14.dp,
            defaultHeight = 14.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 6f)
                lineTo(9f, 17f)
                lineTo(4f, 12f)
            }
        }.build()
    }

    val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "Plus",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }.build()
    }

    val HeartOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeartOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 14f)
                curveTo(20.5f, 12.5f, 21.5f, 10.5f, 21.5f, 8.5f)
                curveTo(21.5f, 5.5f, 19f, 3f, 16f, 3f)
                curveTo(14.2f, 3f, 12.8f, 4f, 12f, 5.5f)
                curveTo(11.2f, 4f, 9.8f, 3f, 8f, 3f)
                curveTo(5f, 3f, 2.5f, 5.5f, 2.5f, 8.5f)
                curveTo(2.5f, 10.5f, 3.5f, 12.5f, 5f, 14f)
                lineTo(12f, 21f)
                close()
            }
        }.build()
    }

    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "CheckCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 11.08f)
                lineTo(22f, 12f)
                curveTo(22f, 17.52f, 17.52f, 22f, 12f, 22f)
                curveTo(6.48f, 22f, 2f, 17.52f, 2f, 12f)
                curveTo(2f, 6.48f, 6.48f, 2f, 12f, 2f)
                curveTo(14.84f, 2f, 17.4f, 3.19f, 19.19f, 5.09f)
                moveTo(22f, 4f)
                lineTo(12f, 14.01f)
                lineTo(9f, 11.01f)
            }
        }.build()
    }

    val StarFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "StarFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color(0xFFF59E0B)),
                stroke = SolidColor(Color(0xFFF59E0B)),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                lineTo(15.09f, 8.26f)
                lineTo(22f, 9.27f)
                lineTo(17f, 14.14f)
                lineTo(18.18f, 21.02f)
                lineTo(12f, 17.77f)
                lineTo(5.82f, 21.02f)
                lineTo(7f, 14.14f)
                lineTo(2f, 9.27f)
                lineTo(8.91f, 8.26f)
                close()
            }
        }.build()
    }

    val StarOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "StarOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color(0xFFD1D5DB)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                lineTo(15.09f, 8.26f)
                lineTo(22f, 9.27f)
                lineTo(17f, 14.14f)
                lineTo(18.18f, 21.02f)
                lineTo(12f, 17.77f)
                lineTo(5.82f, 21.02f)
                lineTo(7f, 14.14f)
                lineTo(2f, 9.27f)
                lineTo(8.91f, 8.26f)
                close()
            }
        }.build()
    }

    val XClose: ImageVector by lazy {
        ImageVector.Builder(
            name = "XClose",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }.build()
    }

    val HeartFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "HeartFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFFEF4444))) {
                moveTo(12f, 21.35f)
                lineTo(10.55f, 20.03f)
                curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
                curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
                curveTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
                curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
                curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
                curveTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20.04f)
                lineTo(12f, 21.35f)
                close()
            }
        }.build()
    }

    val Send: ImageVector by lazy {
        ImageVector.Builder(
            name = "Send",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 2f)
                lineTo(11f, 13f)
                moveTo(22f, 2f)
                lineTo(15f, 22f)
                lineTo(11f, 13f)
                lineTo(2f, 9f)
                close()
            }
        }.build()
    }

    val Clock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Clock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, 22f, 12f)
                close()
                moveTo(12f, 6f)
                lineTo(12f, 12f)
                lineTo(16f, 14f)
            }
        }.build()
    }

    val RotateCcw: ImageVector by lazy {
        ImageVector.Builder(
            name = "RotateCcw",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 20.49f, 15f)
                moveTo(3f, 5f)
                lineTo(3f, 12f)
                lineTo(10f, 12f)
            }
        }.build()
    }
}
