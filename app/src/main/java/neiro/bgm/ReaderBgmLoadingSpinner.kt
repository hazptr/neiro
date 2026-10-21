package neiro.bgm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.MusicNote
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** A button-sized spinner row for the music pane while a track loads. No background of its own. */
@Composable
fun ReaderBgmLoadingSpinner(modifier: Modifier = Modifier) {
    // A plain Box, no Surface or clickable: it never consumes touches
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = Color.White.copy(alpha = 0.9f),
            trackColor = Color.Transparent,
            strokeWidth = 2.dp,
        )
        Icon(
            imageVector = MaterialSymbols.RoundedFilled.MusicNote,
            contentDescription = stringResource(MR.strings.bgm_loading),
            modifier = Modifier.size(16.dp),
            tint = Color.White.copy(alpha = 0.9f),
        )
    }
}
