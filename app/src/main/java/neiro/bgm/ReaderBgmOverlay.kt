package neiro.bgm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.ReaderBgmControls
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.presentation.core.util.collectAsState as collectPreferenceAsState

/**
 * The music controls, part of the reader menu: shown and hidden with it, just below its top bar.
 * While a track loads, a spinner takes their place whether or not the menu is open: below the top
 * bar with the menu, in the top corner without it.
 */
@Composable
fun BoxScope.ReaderBgmOverlay(
    controller: ReaderBgmController,
    menuVisible: Boolean,
    readerPreferences: ReaderPreferences,
) {
    val state by controller.state.collectAsState()
    val spinner by controller.loadingSpinner.collectAsState()
    val spinnerTop by animateDpAsState(if (menuVisible) 64.dp else 0.dp, label = "bgmSpinnerTop")
    AnimatedVisibility(
        visible = state.available && spinner,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .displayCutoutPadding()
            .offset { IntOffset(0, spinnerTop.roundToPx()) }
            .padding(8.dp),
    ) {
        ReaderBgmLoadingSpinner()
    }
    AnimatedVisibility(
        visible = state.available && menuVisible && !spinner,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(top = 64.dp)
            .padding(8.dp),
    ) {
        val loop by readerPreferences.bgmLoop.collectPreferenceAsState()
        ReaderBgmControls(
            muted = state.muted,
            loop = loop,
            onToggleMuted = controller::toggleMuted,
            onToggleLoop = controller::toggleLoop,
        )
    }
}
