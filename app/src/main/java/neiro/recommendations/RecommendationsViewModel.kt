package neiro.recommendations

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.CookieJar
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks

@AssistedInject
class RecommendationsViewModel(
    @Assisted private val mangaId: Long,
    private val getManga: GetManga,
    private val getTracks: GetTracks,
    trackerManager: TrackerManager,
    network: NetworkHelper,
    json: Json,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): RecommendationsViewModel
    }

    // Without WebView's cookies: a login on these sites in WebView stays out of these requests
    private val client = network.client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()

    private val services = listOf(
        AniListRecommendations(client, json, trackerManager.aniList.id),
        MangaUpdatesRecommendations(client, json, trackerManager.mangaUpdates.id),
        MyAnimeListRecommendations(client, json, trackerManager.myAnimeList.id),
    )

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val manga = getManga.await(mangaId) ?: return@launch mutableState.update { it.copy(done = true) }
            val rows = services.flatMap { service -> service.kinds.map { Row(service.name, it) } }
            mutableState.update { it.copy(title = manga.title, rows = rows) }

            val tracks = getTracks.await(mangaId)
            // One lookup per service, each on its own host, so they run side by side
            services.map { service ->
                launch { load(service, tracks.find { it.trackerId == service.trackerId }?.remoteId, manga.title) }
            }.forEach { it.join() }
            mutableState.update { it.copy(done = true) }
        }
    }

    private suspend fun load(service: RecommendationService, remoteId: Long?, title: String) {
        val results = try {
            val found = if (remoteId != null && remoteId > 0) service.byId(remoteId) else service.byTitle(title)
            service.kinds.associateWith { kind -> RowResult.Success(found[kind].orEmpty().toMangas()) }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logcat(LogPriority.WARN, e) { "Recommendations failed for ${service.name}" }
            service.kinds.associateWith { RowResult.Error(e) }
        }
        mutableState.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    if (row.service == service.name) results[row.kind]?.let { row.copy(result = it) } ?: row else row
                },
            )
        }
    }

    /** Cards for the result rows. Not saved: the series isn't on any source until it's searched for. */
    private fun List<Recommendation>.toMangas(): List<Manga> = distinctBy { it.url }.map {
        Manga.create().copy(url = it.url, title = it.title, thumbnailUrl = it.thumbnailUrl, initialized = true)
    }

    @Immutable
    data class State(
        val title: String? = null,
        val rows: List<Row> = emptyList(),
        val done: Boolean = false,
    ) {
        val isEmpty: Boolean
            get() = done && rows.all { (it.result as? RowResult.Success)?.titles?.isEmpty() == true }
    }

    @Immutable
    data class Row(val service: String, val kind: RecommendationKind, val result: RowResult = RowResult.Loading)

    sealed interface RowResult {
        data object Loading : RowResult
        data class Success(val titles: List<Manga>) : RowResult
        data class Error(val throwable: Throwable) : RowResult
    }
}
