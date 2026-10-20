package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Repeat
import mihon.icons.materialsymbols.rounded.VolumeOff
import mihon.icons.materialsymbols.rounded.VolumeUp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val ActiveTint = Color.White.copy(alpha = 0.9f)
private val InactiveTint = Color.White.copy(alpha = 0.4f)

/**
 * Background music controls that float over the pages. Translucent, so they stay out of the way
 * of the art underneath.
 */
@Composable
fun ReaderBgmControls(
    muted: Boolean,
    loop: Boolean,
    onToggleMuted: () -> Unit,
    onToggleLoop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.35f),
    ) {
        Row {
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
}

@PreviewLightDark
@Composable
private fun ReaderBgmControlsPreview() {
    TachiyomiPreviewTheme {
        ReaderBgmControls(muted = false, loop = true, onToggleMuted = {}, onToggleLoop = {})
    }
}
