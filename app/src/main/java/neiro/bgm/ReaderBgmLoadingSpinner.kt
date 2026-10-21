package neiro.bgm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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

/** Stands in for the music controls while a track loads: same translucent disc, one button wide. */
@Composable
fun ReaderBgmLoadingSpinner(modifier: Modifier = Modifier) {
    // Not a Surface: that swallows touches, and this sits over the reader's tap zones
    Box(
        modifier = modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape),
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
