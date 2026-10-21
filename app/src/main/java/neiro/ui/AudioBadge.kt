package neiro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.MusicNote
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * [content] with a music note over its bottom-right corner, marking an extension whose sources can
 * play background music. Translucent, so the icon stays readable.
 */
@Composable
fun AudioBadgeBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier) {
        content()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxSize(0.42f)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) {
            Icon(
                imageVector = MaterialSymbols.RoundedFilled.MusicNote,
                contentDescription = stringResource(MR.strings.extension_has_audio),
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxSize(0.7f),
            )
        }
    }
}
