package com.livetvpro.app.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PlaybackIcon: ImageVector
    get() {
        if (_playbackIcon != null) {
            return _playbackIcon!!
        }
        _playbackIcon = ImageVector.Builder(
            name = "PlaybackIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(16.2f, 21.9f)
                quadTo(14.6f, 21.65f, 13.46f, 20.5f)
                reflectiveQuadTo(12.08f, 17.75f)
                horizontalLineToRelative(1.47f)
                quadToRelative(0.22f, 1f, 0.94f, 1.71f)
                quadToRelative(0.71f, 0.71f, 1.71f, 0.94f)
                verticalLineToRelative(1.5f)
                close()
                moveToRelative(1.5f, 0.03f)
                verticalLineTo(20.4f)
                quadToRelative(1.2f, -0.27f, 1.98f, -1.2f)
                reflectiveQuadTo(20.45f, 17f)
                reflectiveQuadTo(19.68f, 14.8f)
                quadTo(18.9f, 13.88f, 17.7f, 13.6f)
                verticalLineTo(12.08f)
                quadToRelative(1.8f, 0.28f, 3.03f, 1.66f)
                quadToRelative(1.23f, 1.39f, 1.23f, 3.26f)
                reflectiveQuadToRelative(-1.23f, 3.26f)
                reflectiveQuadTo(17.7f, 21.93f)
                close()
                moveTo(12.08f, 16.25f)
                quadToRelative(0.25f, -1.6f, 1.39f, -2.75f)
                reflectiveQuadTo(16.2f, 12.1f)
                verticalLineToRelative(1.5f)
                quadToRelative(-1f, 0.22f, -1.71f, 0.94f)
                reflectiveQuadToRelative(-0.94f, 1.71f)
                horizontalLineTo(12.08f)
                close()
                moveTo(15.95f, 19f)
                verticalLineTo(15f)
                lineToRelative(3.1f, 2f)
                lineToRelative(-3.1f, 2f)
                close()
                moveTo(9.25f, 22f)
                lineTo(8.85f, 18.8f)
                quadTo(8.53f, 18.68f, 8.24f, 18.5f)
                reflectiveQuadTo(7.68f, 18.13f)
                lineTo(4.7f, 19.38f)
                lineTo(1.95f, 14.63f)
                lineTo(4.53f, 12.68f)
                quadTo(4.5f, 12.5f, 4.5f, 12.34f)
                quadToRelative(0f, -0.16f, 0f, -0.34f)
                reflectiveQuadToRelative(0f, -0.34f)
                reflectiveQuadTo(4.53f, 11.33f)
                lineTo(1.95f, 9.38f)
                lineTo(4.7f, 4.63f)
                lineTo(7.68f, 5.88f)
                quadTo(7.95f, 5.68f, 8.25f, 5.5f)
                reflectiveQuadTo(8.85f, 5.2f)
                lineTo(9.25f, 2f)
                horizontalLineToRelative(5.5f)
                lineToRelative(0.4f, 3.2f)
                quadToRelative(0.33f, 0.13f, 0.61f, 0.3f)
                reflectiveQuadToRelative(0.56f, 0.38f)
                lineTo(19.3f, 4.63f)
                lineToRelative(2.75f, 4.75f)
                lineToRelative(-1.85f, 1.4f)
                quadTo(19.65f, 10.5f, 19.08f, 10.31f)
                reflectiveQuadTo(17.85f, 10.05f)
                lineToRelative(1.57f, -1.2f)
                lineTo(18.45f, 7.15f)
                lineTo(15.98f, 8.2f)
                quadTo(15.43f, 7.63f, 14.76f, 7.24f)
                reflectiveQuadTo(13.33f, 6.65f)
                lineTo(13f, 4f)
                horizontalLineTo(11.03f)
                lineTo(10.68f, 6.65f)
                quadTo(9.9f, 6.85f, 9.24f, 7.24f)
                reflectiveQuadTo(8.03f, 8.17f)
                lineTo(5.55f, 7.15f)
                lineTo(4.58f, 8.85f)
                lineToRelative(2.15f, 1.6f)
                quadTo(6.6f, 10.83f, 6.55f, 11.2f)
                reflectiveQuadTo(6.5f, 12f)
                quadToRelative(0f, 0.4f, 0.05f, 0.77f)
                reflectiveQuadToRelative(0.17f, 0.75f)
                lineTo(4.58f, 15.15f)
                lineToRelative(0.98f, 1.7f)
                lineTo(8.03f, 15.8f)
                quadToRelative(0.43f, 0.43f, 0.91f, 0.76f)
                quadTo(9.43f, 16.9f, 10f, 17.13f)
                quadToRelative(0.03f, 1.43f, 0.59f, 2.68f)
                reflectiveQuadTo(12.1f, 22f)
                horizontalLineTo(9.25f)
                close()
                moveToRelative(1.03f, -6.98f)
                quadToRelative(0.15f, -0.5f, 0.36f, -0.96f)
                reflectiveQuadToRelative(0.49f, -0.89f)
                quadTo(10.85f, 12.98f, 10.7f, 12.66f)
                reflectiveQuadTo(10.55f, 12f)
                quadToRelative(0f, -0.63f, 0.44f, -1.06f)
                reflectiveQuadTo(12.05f, 10.5f)
                quadToRelative(0.35f, 0f, 0.68f, 0.16f)
                reflectiveQuadToRelative(0.52f, 0.44f)
                quadToRelative(0.43f, -0.28f, 0.88f, -0.49f)
                reflectiveQuadToRelative(0.95f, -0.34f)
                quadTo(14.63f, 9.48f, 13.83f, 8.99f)
                reflectiveQuadTo(12.05f, 8.5f)
                quadToRelative(-1.47f, 0f, -2.49f, 1.02f)
                reflectiveQuadTo(8.55f, 12f)
                quadToRelative(0f, 0.95f, 0.46f, 1.76f)
                reflectiveQuadToRelative(1.26f, 1.26f)
                close()
            }
        }.build()
        return _playbackIcon!!
    }

private var _playbackIcon: ImageVector? = null

