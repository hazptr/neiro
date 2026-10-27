package neiro.related

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class RelatedTitlesViewModel(
    @Assisted private val mangaId: Long,
    private val getManga: GetManga,
    private val sourceManager: SourceManager,
    private val networkToLocalManga: NetworkToLocalManga,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): RelatedTitlesViewModel
    }

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { load() }
    }

    private suspend fun load() {
        val manga = getManga.await(mangaId) ?: return mutableState.update { it.copy(done = true) }
        val source = sourceManager.get(manga.source) as? CatalogueSource
            ?: return mutableState.update { it.copy(manga = manga, done = true) }
        val capabilities = RelatedTitlesSupport.capabilities(source)

        val sections = buildList {
            if (capabilities.fromSource) add(Section(keyword = null))
            if (capabilities.bySearch) RelatedTitlesSupport.keywords(manga.title).forEach { add(Section(it)) }
        }
        mutableState.update {
            it.copy(manga = manga, sourceId = source.id, sourceName = source.name, sections = sections)
        }

        // Titles already shown, so each one appears once, under the first section that found it
        val seen = mutableSetOf(manga.url)
        val sManga = manga.toSManga()
        val filters by lazy { source.getFilterList() }
        // One request at a time: this is the site's own catalogue, not a global search
        sections.forEachIndexed { index, section ->
            val result = try {
                val found = if (section.keyword == null) {
                    RelatedTitlesSupport.fetchFromSource(source, sManga)
                } else {
                    source.getSearchManga(1, section.keyword, filters).mangas
                }
                RowResult.Success(toLocal(found, source.id, seen))
            } catch (e: Throwable) {
                // Only this screen closing stops the load; a cancellation from inside the extension (its
                // own timeout) is that row's error
                currentCoroutineContext().ensureActive()
                logcat(LogPriority.WARN, e) { "Related titles failed for ${source.name}" }
                RowResult.Error(e)
            }
            mutableState.update { state ->
                state.copy(sections = state.sections.toMutableList().also { it[index] = section.copy(result = result) })
            }
        }
        mutableState.update { it.copy(done = true) }
    }

    private suspend fun toLocal(found: List<SManga>, sourceId: Long, seen: MutableSet<String>): List<Manga> {
        val fresh = found.map { it.toDomainManga(sourceId) }.distinctBy { it.url }.filter { seen.add(it.url) }
        return networkToLocalManga(fresh)
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collect { value = it }
        }
    }

    @Immutable
    data class State(
        val manga: Manga? = null,
        val sourceId: Long = 0,
        val sourceName: String = "",
        val sections: List<Section> = emptyList(),
        val done: Boolean = false,
    ) {
        val isEmpty: Boolean
            get() = done && sections.all { (it.result as? RowResult.Success)?.titles?.isEmpty() == true }
    }

    /** One row: the site's own list ([keyword] null) or a search for [keyword]. */
    @Immutable
    data class Section(val keyword: String?, val result: RowResult = RowResult.Loading)

    sealed interface RowResult {
        data object Loading : RowResult
        data class Success(val titles: List<Manga>) : RowResult
        data class Error(val throwable: Throwable) : RowResult
    }
}
