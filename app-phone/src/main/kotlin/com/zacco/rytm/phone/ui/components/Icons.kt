package com.zacco.rytm.phone.ui.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Ikony zakładek przepisane ze ścieżek SVG makiety (obrys 1,8, zaokrąglone końce). */
object RytmIcons {
    val today = icon("M12 3v2M12 19v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M3 12h2M19 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0")
    val week = icon("M6 5h12a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1 -2.5 2.5h-12a2.5 2.5 0 0 1 -2.5 -2.5v-10a2.5 2.5 0 0 1 2.5 -2.5zM3.5 10h17M8 3v4M16 3v4")
    val body = icon("M4 18l5-6 4 3 7-9M15 6h5v5")
    val habits = icon("M5 12.5l4 4 10-10")
    val evening = icon("M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")

    private fun icon(path: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(path),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
