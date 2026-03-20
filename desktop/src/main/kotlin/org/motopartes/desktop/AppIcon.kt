package org.motopartes.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Gear/cog icon painter — used as window icon and in the banner */
class AppIconPainter(
    private val gearColor: Color = Color(0xFFFFB74D),
    private val bgColor: Color = Color(0xFF2D2D2D),
    private val centerColor: Color = Color(0xFF1A1A1A),
) : Painter() {

    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.width * 0.42f
        val innerR = size.width * 0.30f
        val teeth = 8

        // Background circle
        drawCircle(color = bgColor, radius = size.width * 0.48f, center = Offset(cx, cy))

        // Gear teeth path
        val gearPath = Path().apply {
            val halfTooth = (PI / teeth * 0.6f).toFloat()
            for (i in 0 until teeth) {
                val angle = (2 * PI * i / teeth).toFloat()
                val nextAngle = (2 * PI * (i + 1) / teeth).toFloat()

                val ox1 = cx + outerR * cos(angle - halfTooth)
                val oy1 = cy + outerR * sin(angle - halfTooth)
                val ox2 = cx + outerR * cos(angle + halfTooth)
                val oy2 = cy + outerR * sin(angle + halfTooth)
                val ix1 = cx + innerR * cos(angle + halfTooth)
                val iy1 = cy + innerR * sin(angle + halfTooth)
                val ix2 = cx + innerR * cos(nextAngle - halfTooth)
                val iy2 = cy + innerR * sin(nextAngle - halfTooth)

                if (i == 0) moveTo(ox1, oy1)
                lineTo(ox2, oy2)
                lineTo(ix1, iy1)
                lineTo(ix2, iy2)
            }
            close()
        }
        drawPath(gearPath, color = gearColor)

        // Center hole
        drawCircle(color = centerColor, radius = size.width * 0.14f, center = Offset(cx, cy))
        // Inner ring accent
        drawCircle(color = gearColor, radius = size.width * 0.10f, center = Offset(cx, cy), style = Stroke(width = size.width * 0.03f))
        // Center dot
        drawCircle(color = gearColor, radius = size.width * 0.03f, center = Offset(cx, cy))
    }
}
