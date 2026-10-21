package neiro.bgm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.ReaderBgmControls
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlin.math.max
import kotlin.math.roundToInt
import tachiyomi.presentation.core.util.collectAsState as collectPreferenceAsState

/**
 * The music overlay, one translucent pane in the top corner. With the reader menu open it holds the
 * mute and repeat buttons, always, and a loading spinner slides in below them while a track loads;
 * the pane grows and shrinks around its rows. With the menu hidden only the spinner shows, alone in
 * the corner, and it just fades in and out as a circle.
 *
 * Opening the menu moves the whole pane down so it hangs [GAP] below the reader's top bar. The move
 * is an offset in the layout phase, driven by the same 200 ms tween and easing as the bar's slide and
 * computed from the bar's own geometry (status bar + [BAR_HEIGHT]), so the gap holds through the
 * animation; a spinner already showing travels down with the pane. The buttons expand above the
 * spinner inside the pane.
 *
 * The pane is drawn behind its rows, not a Surface, and the spinner a plain Box: neither consumes
 * touches, so the reader's tap zones keep working under a spinner with the menu hidden. Only the
 * buttons take input, and they exist only while the menu is open.
 */
@Composable
fun BoxScope.ReaderBgmOverlay(
    controller: ReaderBgmController,
    menuVisible: Boolean,
    readerPreferences: ReaderPreferences,
) {
    val state by controller.state.collectAsState()
    val spinner by controller.loadingSpinner.collectAsState()
    val controlsShown = state.available && menuVisible
    val spinnerShown = state.available && spinner

    // Same spec (and so the same default easing) as the bar's slideInVertically/slideOutVertically. A
    // transition, like AnimatedVisibility's, starts in the same frame as the bar; animateFloatAsState
    // would start a frame later and trail it.
    val barProgress by updateTransition(controlsShown, label = "bgmBar").animateFloat(
        transitionSpec = { tween(SLIDE_MS) },
        label = "bgmBarProgress",
    ) { if (it) 1f else 0f }
    val paneAlpha by animateFloatAsState(
        if (controlsShown || spinnerShown) 1f else 0f,
        tween(FADE_MS),
        label = "bgmPane",
    )
    // Joining or leaving the buttons' pane grows and shrinks; a lone spinner only fades
    val spinnerEnter = if (controlsShown) enter else fadeIn(tween(FADE_MS))
    val spinnerExit = if (controlsShown) exit else fadeOut(tween(FADE_MS))
    val density = LocalDensity.current
    val shownInset = WindowInsets.systemBars
    val barInset = WindowInsets.systemBarsIgnoringVisibility
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            // Only the sides: the top inset is part of the offset below
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .offset {
                // The bar's bottom edge is (status bar + height) * progress. Opening counts the status bar at
                // full size before it has finished showing, closing as it is; the pane rests at the shown inset
                val inset = if (controlsShown) barInset.getTop(density) else shownInset.getTop(density)
                val barBottom = (inset + BAR_HEIGHT.roundToPx()) * barProgress
                IntOffset(0, max(shownInset.getTop(density).toFloat(), barBottom).roundToInt() + GAP.roundToPx())
            }
            .padding(end = GAP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.readerBgmPane { paneAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(visible = controlsShown, enter = enter, exit = exit) {
                val loop by readerPreferences.bgmLoop.collectPreferenceAsState()
                ReaderBgmControls(
                    muted = state.muted,
                    loop = loop,
                    onToggleMuted = controller::toggleMuted,
                    onToggleLoop = controller::toggleLoop,
                )
            }
            AnimatedVisibility(visible = spinnerShown, enter = spinnerEnter, exit = spinnerExit) {
                ReaderBgmLoadingSpinner()
            }
        }
    }
}

private val enter = fadeIn(tween(FADE_MS)) + expandVertically(tween(SLIDE_MS), Alignment.Top)
private val exit = fadeOut(tween(FADE_MS)) + shrinkVertically(tween(SLIDE_MS), Alignment.Top)

// Mihon's reader app bars: 200 ms slide, 150 ms fade, and a 64 dp top app bar under the status bar.
private const val SLIDE_MS = 200
private const val FADE_MS = 150
private val BAR_HEIGHT = 64.dp

// Gap between the bar and the pane, and between the pane and the side edge: Material's 8 dp step, small
// enough to read as attached to the bar and large enough to stay clear of its edge.
private val GAP = 8.dp
