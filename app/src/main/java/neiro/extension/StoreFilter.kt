package neiro.extension

import android.app.Application
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.extension.model.Extensions
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import mihon.core.metro.metroGraph
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Which extension stores the Extensions tab shows. */
@Inject
@SingleIn(AppScope::class)
class StoreFilterPreferences(preferenceStore: PreferenceStore) {

    /** Index URLs of the stores whose extensions the Extensions tab hides. */
    val hiddenStores = preferenceStore.getStringSet("pref_neiro_hidden_extension_stores", emptySet())

    fun toggle(store: ExtensionStore) {
        hiddenStores.getAndSet { hidden ->
            if (store.indexUrl in hidden) hidden - store.indexUrl else hidden + store.indexUrl
        }
    }

    companion object {
        /** For the Extensions tab's view model, which Mihon builds without Neiro's classes. */
        val instance: StoreFilterPreferences by lazy {
            Injekt.get<Application>().metroGraph<StoreFilterGraph>().storeFilterPreferences
        }
    }
}

@ContributesTo(AppScope::class)
interface StoreFilterGraph {
    val storeFilterPreferences: StoreFilterPreferences
    val getExtensionStores: GetExtensionStores
}

object StoreFilter {

    /**
     * Leaves out the extensions of hidden stores: their listings, and installed extensions whose store is one of
     * them. Pending updates stay, so the list matches the tab's update count. Extensions without a store stay.
     */
    fun apply(extensions: Extensions, hidden: Set<String>): Extensions {
        if (hidden.isEmpty()) return extensions
        fun Extension.Installed.shown() = store?.indexUrl !in hidden
        return extensions.copy(
            loaded = extensions.loaded.filter { it.shown() },
            notLoaded = extensions.notLoaded.filter { it.shown() },
            available = extensions.available.filter { it.store.indexUrl !in hidden },
        )
    }

    /** [apply] with the current setting, updating when it changes. */
    fun filterHiddenStores(extensions: Flow<Extensions>): Flow<Extensions> =
        extensions.combine(StoreFilterPreferences.instance.hiddenStores.changes(), ::apply)
}

@Immutable
data class StoreFilterState(
    val stores: List<ExtensionStore>,
    val hidden: Set<String>,
    val onToggle: (ExtensionStore) -> Unit,
)

@Composable
fun rememberStoreFilterState(): StoreFilterState {
    val context = LocalContext.current
    val graph = remember { context.metroGraph<StoreFilterGraph>() }
    val stores by remember { graph.getExtensionStores.subscribe() }.collectAsStateWithLifecycle(emptyList())
    val hidden by remember { graph.storeFilterPreferences.hiddenStores.changes() }
        .collectAsStateWithLifecycle(graph.storeFilterPreferences.hiddenStores.get())
    return StoreFilterState(
        stores = stores.sortedBy { it.name.lowercase() },
        hidden = hidden,
        onToggle = graph.storeFilterPreferences::toggle,
    )
}

/**
 * A switch per store above the language switches, under their own headers. Nothing with one store or none, as
 * there's nothing to choose between.
 */
fun storeFilterItems(list: LazyListScope, state: StoreFilterState): Unit = with(list) {
    if (state.stores.size < 2) return
    item(key = "neiro_stores_header") {
        PreferenceGroupHeader(title = stringResource(MR.strings.neiro_extension_filter_stores))
    }
    items(state.stores, key = { "neiro_store_${it.indexUrl}" }) { store ->
        SwitchPreferenceWidget(
            modifier = Modifier.animateItem(),
            title = store.name,
            checked = store.indexUrl !in state.hidden,
            onCheckedChanged = { state.onToggle(store) },
        )
    }
    item(key = "neiro_languages_header") {
        PreferenceGroupHeader(title = stringResource(MR.strings.neiro_extension_filter_languages))
    }
}
