package com.windrm.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Back to start: a circular arrow that sets off from a dot at the bottom and returns to it. */
val BackToStartIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BackToStart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // The dot the arrow leaves from and comes back to.
        addPath(
            PathParser().parsePathString("M12 17.3a2.1 2.1 0 1 1 0 4.2a2.1 2.1 0 1 1 0 -4.2z").toNodes(),
            fill = SolidColor(Color.Black),
        )
        // Almost the whole circle.
        addPath(
            PathParser().parsePathString("M9.2 17.6A7 7 0 1 1 14.8 17.6").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        )
        // The head, pointing at the dot.
        addPath(
            PathParser().parsePathString("M17.9 20.3l-3.6 -1.1 2.3 -3z").toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}
