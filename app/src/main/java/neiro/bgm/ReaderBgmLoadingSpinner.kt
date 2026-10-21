package neiro.bgm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.MusicNote
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** A button-sized spinner row for the music pane while a track loads. No background of its own. */
@Composable
fun ReaderBgmLoadingSpinner(modifier: Modifier = Modifier) {
    val description = stringResource(MR.strings.bgm_loading)
    // A plain Box, no Surface or clickable: it never consumes touches. The description sits on the Box
    // itself, merged over its children, so it is one node whichever pane the Box is in.
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = ReaderBgmColors.Active,
            trackColor = Color.Transparent,
            strokeWidth = 2.dp,
        )
        Icon(
            imageVector = MaterialSymbols.RoundedFilled.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = ReaderBgmColors.Active,
        )
    }
}
