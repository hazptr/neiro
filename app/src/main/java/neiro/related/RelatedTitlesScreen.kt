package neiro.related

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import neiro.related.RelatedTitlesViewModel.RowResult
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/** Titles the series' own source relates to it: the site's list, then searches for its title words. */
class RelatedTitlesScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<RelatedTitlesViewModel, RelatedTitlesViewModel.Factory> {
            create(mangaId = mangaId)
        }
        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.action_related_titles),
                    subtitle = state.manga?.title,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            if (!state.done && state.sections.isEmpty()) {
                LoadingScreen(Modifier.padding(paddingValues))
                return@Scaffold
            }
            if (state.isEmpty) {
                EmptyScreen(MR.strings.no_results_found, modifier = Modifier.padding(paddingValues))
                return@Scaffold
            }
            LazyColumn(contentPadding = paddingValues) {
                itemsIndexed(state.sections, key = { index, _ -> index }) { _, section ->
                    val result = section.result
                    // Rows that found nothing new are left out, as global search does
                    if (result is RowResult.Success && result.titles.isEmpty()) return@itemsIndexed
                    val content = @Composable {
                        when (result) {
                            RowResult.Loading -> GlobalSearchLoadingResultItem()
                            is RowResult.Success -> GlobalSearchCardRow(
                                titles = result.titles,
                                getManga = { viewModel.getManga(it) },
                                onClick = { navigator.push(MangaScreen(it.id, true)) },
                                onLongClick = { navigator.push(MangaScreen(it.id, true)) },
                            )
                            is RowResult.Error ->
                                GlobalSearchErrorResultItem(message = result.throwable.message)
                        }
                    }
                    val keyword = section.keyword
                    if (keyword == null) {
                        Column {
                            Text(
                                text = stringResource(MR.strings.related_titles_from_source, state.sourceName),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(
                                    horizontal = MaterialTheme.padding.medium,
                                    vertical = MaterialTheme.padding.small,
                                ),
                            )
                            content()
                        }
                    } else {
                        GlobalSearchResultItem(
                            title = keyword,
                            subtitle = state.sourceName,
                            onClick = { navigator.push(BrowseSourceScreen(state.sourceId, keyword)) },
                            content = content,
                        )
                    }
                }
            }
        }
    }
}
