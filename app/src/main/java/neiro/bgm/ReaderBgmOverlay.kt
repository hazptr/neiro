package neiro.bgm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.ReaderBgmControls
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.presentation.core.util.collectAsState as collectPreferenceAsState

/**
 * The music overlay, one translucent pane in the top corner. With the reader menu open it holds the
 * mute and repeat buttons, always, and a loading spinner slides in below them while a track loads;
 * the pane grows and shrinks around its rows. With the menu hidden only the spinner shows, alone in
 * the corner. Opening the menu eases a 64 dp spacer in above the pane (the app bar) and expands the
 * buttons above the spinner, so a spinner already showing travels down with them in one motion.
 *
 * The pane is a plain background, not a Surface, and the spinner a plain Box: neither consumes
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
    val enter = fadeIn(tween(FADE_MS)) + expandVertically(tween(SLIDE_MS), Alignment.Top)
    val exit = fadeOut(tween(FADE_MS)) + shrinkVertically(tween(SLIDE_MS), Alignment.Top)
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(8.dp),
    ) {
        AnimatedVisibility(
            visible = state.available && menuVisible,
            enter = expandVertically(tween(SLIDE_MS), Alignment.Top),
            exit = shrinkVertically(tween(SLIDE_MS), Alignment.Top),
        ) {
            Spacer(Modifier.height(64.dp))
        }
        Column(
            modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(visible = state.available && menuVisible, enter = enter, exit = exit) {
                val loop by readerPreferences.bgmLoop.collectPreferenceAsState()
                ReaderBgmControls(
                    muted = state.muted,
                    loop = loop,
                    onToggleMuted = controller::toggleMuted,
                    onToggleLoop = controller::toggleLoop,
                )
            }
            AnimatedVisibility(visible = state.available && spinner, enter = enter, exit = exit) {
                ReaderBgmLoadingSpinner()
            }
        }
    }
}

// Mihon's reader app bars: 200 ms slide, 150 ms fade. The spacer has to move in step with them.
private const val SLIDE_MS = 200
private const val FADE_MS = 150
