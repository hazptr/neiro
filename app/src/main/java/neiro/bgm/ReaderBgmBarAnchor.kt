package neiro.bgm

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot

/**
 * Where the reader's top bar actually is, so the music pane can hang from it. The bar slides with
 * its own animation, and on close a copy of that animation in the pane drifts away from it, so the
 * pane follows the measured bottom edge instead. The bar is placed before the pane in the same
 * layout pass and [onPlaced] runs as it is, so the pane reads the bar's position for the frame it
 * is about to draw. This is a plain field rather than state for that reason: a state write reaches
 * its readers a frame late, and onGloballyPositioned runs only after the frame is drawn.
 */
object ReaderBgmBarAnchor {
    /** Bottom edge of the top bar in root coordinates, or negative infinity while it is not shown. */
    @Volatile
    var bottom = Float.NEGATIVE_INFINITY
}

/** Applied to the reader's top bar. */
fun Modifier.readerBgmBarAnchor(): Modifier = onPlaced {
    ReaderBgmBarAnchor.bottom = it.positionInRoot().y + it.size.height
}
