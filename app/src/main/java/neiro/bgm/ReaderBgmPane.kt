package neiro.bgm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Colours of the music pane. The scrim is dark enough for white icons to read over a white page
 * (about 7:1 for an active icon, 4.5:1 for the repeat button when off), and a thin light outline keeps
 * the pane's edge visible over a black one.
 */
object ReaderBgmColors {
    val Scrim = Color.Black.copy(alpha = 0.65f)
    val Outline = Color.White.copy(alpha = 0.35f)
    val Active = Color.White
    val Inactive = Color.White.copy(alpha = 0.72f)
}

/**
 * The pane behind the music rows: a scrim and outline in a pill, drawn at [alpha] so it fades with a
 * lone spinner instead of popping in around it. Drawing only, it takes no input.
 */
fun Modifier.readerBgmPane(alpha: () -> Float): Modifier = drawBehind {
    val a = alpha()
    if (a <= 0f) return@drawBehind
    val outline = CircleShape.createOutline(size, layoutDirection, this)
    drawOutline(outline, ReaderBgmColors.Scrim, alpha = a)
    val width = 1.dp.toPx()
    drawOutline(outline, ReaderBgmColors.Outline, alpha = a, style = Stroke(width))
}
