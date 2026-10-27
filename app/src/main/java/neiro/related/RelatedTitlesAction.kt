package neiro.related

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.manga.MangaViewModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.reflect.KClass

/**
 * Neiro's entries in the series screen's overflow menu. Called from Mihon's MangaToolbar, inside the
 * series screen, so the screen's own [MangaViewModel] is already in the ViewModel store: it's read
 * from there instead of passing the series down through Mihon's screen and toolbar.
 */
@Composable
fun neiroMangaOverflowActions(): List<AppBar.OverflowAction> {
    val navigator = LocalNavigator.current ?: return emptyList()
    val owner = LocalViewModelStoreOwner.current ?: return emptyList()
    val mangaViewModel = remember(owner) {
        runCatching { ViewModelProvider.create(owner, ExistingOnly)[MangaViewModel::class] }.getOrNull()
    } ?: return emptyList()
    val state by mangaViewModel.state.collectAsStateWithLifecycle()
    val success = state as? MangaViewModel.State.Success ?: return emptyList()
    if (!RelatedTitlesSupport.capabilities(success.source).any) return emptyList()

    return listOf(
        AppBar.OverflowAction(
            title = stringResource(MR.strings.action_related_titles),
            onClick = { navigator.push(RelatedTitlesScreen(success.manga.id)) },
        ),
    )
}

/** Hands back the series screen's existing ViewModel; never creates one. */
private object ExistingOnly : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
        throw IllegalStateException("No ${modelClass.simpleName} in this screen")
}
