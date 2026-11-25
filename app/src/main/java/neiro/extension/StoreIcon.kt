package neiro.extension

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import mihon.domain.extension.model.ExtensionStore

/**
 * The icon [store] names in its index, [size] square. [fallback] when it names none or the icon doesn't load, so
 * stores without one look as they always did.
 */
@Composable
fun StoreIcon(
    store: ExtensionStore,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    fallback: @Composable () -> Unit = {},
) {
    val iconUrl = store.iconUrl
    var failed by remember(iconUrl) { mutableStateOf(false) }
    if (iconUrl == null || failed) {
        fallback()
        return
    }
    AsyncImage(
        model = iconUrl,
        contentDescription = null,
        onError = { failed = true },
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 6)),
    )
}

/** [content] (the store's name) with the store's icon in front when it has one. */
@Composable
fun StoreLabel(store: ExtensionStore, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StoreIcon(store, Modifier.padding(end = 4.dp), size = 14.dp)
        content()
    }
}
