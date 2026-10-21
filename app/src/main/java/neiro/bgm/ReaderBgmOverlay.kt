package neiro.bgm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.ReaderBgmControls
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.presentation.core.util.collectAsState as collectPreferenceAsState

/** The music controls, part of the reader menu: shown and hidden with it, just below its top bar. */
@Composable
fun BoxScope.ReaderBgmOverlay(
    controller: ReaderBgmController,
    menuVisible: Boolean,
    readerPreferences: ReaderPreferences,
) {
    val state by controller.state.collectAsState()
    AnimatedVisibility(
        visible = state.available && menuVisible,
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
