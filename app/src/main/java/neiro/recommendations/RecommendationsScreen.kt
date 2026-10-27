package neiro.recommendations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import neiro.recommendations.RecommendationsViewModel.RowResult
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * What AniList, MangaUpdates and MyAnimeList readers recommend alongside a series. A card opens a
 * global search for that title, since the services don't say which source has it; a long press
 * opens its page on the service.
 */
class RecommendationsScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = assistedMetroViewModel<RecommendationsViewModel, RecommendationsViewModel.Factory> {
            create(mangaId = mangaId)
        }
        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.action_recommendations),
                    subtitle = state.title,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            if (state.rows.isEmpty() && !state.done) {
                LoadingScreen(Modifier.padding(paddingValues))
                return@Scaffold
            }
            if (state.rows.isEmpty() || state.isEmpty) {
                EmptyScreen(MR.strings.no_results_found, modifier = Modifier.padding(paddingValues))
                return@Scaffold
            }
            LazyColumn(contentPadding = paddingValues) {
                items(state.rows, key = { "${it.service}-${it.kind}" }) { row ->
                    val result = row.result
                    // Rows that found nothing are left out, as global search does
                    if (result is RowResult.Success && result.titles.isEmpty()) return@items
                    Column {
                        // Global search's row header without its arrow: a row has nothing more to open
                        Column(
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.small,
                            ),
                        ) {
                            Text(text = row.service, style = MaterialTheme.typography.titleMedium)
                            Text(text = stringResource(row.kind.label))
                        }
                        when (result) {
                            RowResult.Loading -> GlobalSearchLoadingResultItem()
                            is RowResult.Success -> GlobalSearchCardRow(
                                titles = result.titles,
                                getManga = { manga: Manga -> remember(manga) { mutableStateOf(manga) } },
                                onClick = { navigator.push(GlobalSearchScreen(it.title)) },
                                onLongClick = {
                                    context.startActivity(WebViewActivity.newIntent(context, it.url, title = it.title))
                                },
                            )
                            is RowResult.Error -> GlobalSearchErrorResultItem(
                                message = with(context) { result.throwable.formattedMessage },
                            )
                        }
                    }
                }
            }
        }
    }
}
