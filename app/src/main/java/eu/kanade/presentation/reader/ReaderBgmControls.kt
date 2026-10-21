package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Repeat
import mihon.icons.materialsymbols.rounded.VolumeOff
import mihon.icons.materialsymbols.rounded.VolumeUp
import neiro.bgm.ReaderBgmColors
import neiro.bgm.readerBgmPane
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val ActiveTint = ReaderBgmColors.Active
private val InactiveTint = ReaderBgmColors.Inactive

/**
 * Background music buttons, stacked. The translucent pane behind them is the caller's, so other
 * rows (the loading spinner) can share it.
 */
@Composable
fun ReaderBgmControls(
    muted: Boolean,
    loop: Boolean,
    onToggleMuted: () -> Unit,
    onToggleLoop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        IconToggleButton(
            checked = muted,
            onCheckedChange = { onToggleMuted() },
        ) {
            Icon(
                imageVector = if (muted) MaterialSymbols.Rounded.VolumeOff else MaterialSymbols.Rounded.VolumeUp,
                contentDescription = stringResource(MR.strings.action_bgm_mute),
                tint = ActiveTint,
            )
        }
        IconToggleButton(
            checked = loop,
            onCheckedChange = { onToggleLoop() },
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Repeat,
                contentDescription = stringResource(MR.strings.pref_bgm_loop),
                tint = if (loop) ActiveTint else InactiveTint,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ReaderBgmControlsPreview() {
    TachiyomiPreviewTheme {
        ReaderBgmControls(
            muted = false,
            loop = true,
            onToggleMuted = {},
            onToggleLoop = {},
            modifier = Modifier.readerBgmPane { 1f },
        )
    }
}
