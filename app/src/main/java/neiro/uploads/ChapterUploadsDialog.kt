package neiro.uploads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.core.metro.metroGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.CheckBoxOutlineBlank
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowUp
import mihon.icons.materialsymbols.roundedfilled.DisabledByDefault
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Mihon's scanlator filter for [manga] (the same exclusion checkboxes, saved through [onConfirm]), plus the
 * title's own chapter uploads choice: "One per chapter" and the order of its groups. Until the user changes
 * them here, the title follows the global settings.
 */
@Composable
fun ChapterUploadsDialog(
    manga: Manga,
    availableScanlators: Set<String>,
    excludedScanlators: Set<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val graph = remember { context.metroGraph<ChapterUploadsGraph>() }
    val preferences = graph.chapterUploadsPreferences
    val sortedScanlators = remember(availableScanlators) {
        availableScanlators.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    val excluded = remember(excludedScanlators) { excludedScanlators.toMutableStateList() }

    val hasOwnChoice = remember(manga.id) {
        preferences.titleMode(manga).get().isNotEmpty() || preferences.titleOrderList(manga).isNotEmpty()
    }
    var onePerChapter by remember(manga.id) {
        mutableStateOf(preferences.effectiveMode(manga) == ChapterUploads.Mode.ONE_PER_CHAPTER)
    }
    var modeChanged by remember(manga.id) { mutableStateOf(false) }
    var orderChanged by remember(manga.id) { mutableStateOf(false) }
    var useDefaults by remember(manga.id) { mutableStateOf(false) }
    val order = remember(manga.id) { mutableListOf<String>().toMutableStateList() }
    LaunchedEffect(manga.id) {
        val groups = withContext(Dispatchers.IO) {
            graph.chapterRepository.getChapterByMangaId(manga.id)
                .sortedBy { it.sourceOrder }
                .flatMap(ChapterUploads::groupsOf)
                .distinctBy(ChapterUploads::normalized)
        }
        val ranking = preferences.effectiveOrder(manga)
        order.clear()
        order.addAll(groups.sortedBy { ChapterUploads.preferenceRank(listOf(it), ranking) })
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.neiro_uploads_title)) },
        text = {
            Box {
                val state = rememberLazyListState()
                LazyColumn(state = state) {
                    item(key = "one_per_chapter") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    onePerChapter = !onePerChapter
                                    modeChanged = true
                                    useDefaults = false
                                }
                                .minimumInteractiveComponentSize()
                                .fillMaxWidth()
                                .padding(horizontal = MaterialTheme.padding.small),
                        ) {
                            Text(
                                text = stringResource(MR.strings.neiro_uploads_mode_one),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = onePerChapter, onCheckedChange = null)
                        }
                    }
                    if (onePerChapter && order.size > 1) {
                        item(key = "order_header") { SectionHeader(stringResource(MR.strings.neiro_uploads_order)) }
                        itemsIndexed(order, key = { _, group -> "order_$group" }) { index, group ->
                            OrderRow(
                                group = group,
                                canMoveUp = index > 0,
                                canMoveDown = index < order.lastIndex,
                                onMove = { by ->
                                    order.add(index + by, order.removeAt(index))
                                    orderChanged = true
                                    useDefaults = false
                                },
                            )
                        }
                    }
                    item(key = "excluded_header") { SectionHeader(stringResource(MR.strings.exclude_scanlators)) }
                    if (sortedScanlators.isEmpty()) {
                        item(key = "excluded_none") {
                            Text(
                                text = stringResource(MR.strings.no_scanlators_found),
                                modifier = Modifier.padding(horizontal = MaterialTheme.padding.small),
                            )
                        }
                    }
                    sortedScanlators.forEach { scanlator ->
                        item(key = "excluded_$scanlator") {
                            ExclusionRow(scanlator, excluded.contains(scanlator)) {
                                if (excluded.contains(
                                        scanlator,
                                    )
                                ) {
                                    excluded.remove(scanlator)
                                } else {
                                    excluded.add(scanlator)
                                }
                            }
                        }
                    }
                }
                if (state.canScrollBackward) HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                if (state.canScrollForward) HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = true),
        confirmButton = {
            FlowRow {
                if (hasOwnChoice && !useDefaults) {
                    TextButton(
                        onClick = {
                            useDefaults = true
                            modeChanged = false
                            orderChanged = false
                            onePerChapter = preferences.mode.get() == ChapterUploads.Mode.ONE_PER_CHAPTER
                        },
                    ) {
                        Text(text = stringResource(MR.strings.neiro_uploads_use_defaults))
                    }
                } else if (sortedScanlators.isNotEmpty()) {
                    if (excluded.isEmpty()) {
                        TextButton(onClick = { excluded.addAll(availableScanlators) }) {
                            Text(text = stringResource(MR.strings.action_select_all))
                        }
                    } else {
                        TextButton(onClick = excluded::clear) {
                            Text(text = stringResource(MR.strings.action_reset))
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                TextButton(
                    onClick = {
                        if (useDefaults) {
                            preferences.titleMode(manga).delete()
                            preferences.titleOrder(manga).delete()
                        }
                        if (modeChanged) {
                            val mode = ChapterUploads.Mode.ONE_PER_CHAPTER.takeIf { onePerChapter }
                                ?: ChapterUploads.Mode.ALL_UPLOADS
                            preferences.titleMode(manga).set(mode.name)
                        }
                        if (orderChanged) preferences.titleOrder(manga).set(order.joinToString("\n"))
                        if (useDefaults || modeChanged || orderChanged) refreshChapterList(graph, manga)
                        onConfirm(excluded.toSet())
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = MaterialTheme.padding.small,
            top = MaterialTheme.padding.medium,
            bottom = MaterialTheme.padding.extraSmall,
        ),
    )
}

@Composable
private fun OrderRow(group: String, canMoveUp: Boolean, canMoveDown: Boolean, onMove: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MaterialTheme.padding.small),
    ) {
        Text(text = group, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(
                MaterialSymbols.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(MR.strings.neiro_uploads_move_up),
            )
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(
                MaterialSymbols.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(MR.strings.neiro_uploads_move_down),
            )
        }
    }
}

/** Mihon's exclusion row, as in its scanlator filter. */
@Composable
private fun ExclusionRow(scanlator: String, isExcluded: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.small)
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.small),
    ) {
        Icon(
            imageVector = if (isExcluded) {
                MaterialSymbols.RoundedFilled.DisabledByDefault
            } else {
                MaterialSymbols.Rounded.CheckBoxOutlineBlank
            },
            tint = if (isExcluded) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            contentDescription = null,
        )
        Text(
            text = scanlator,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 24.dp),
        )
    }
}

/**
 * The series screen filters its chapter list once per state, so settings saved here only show on its next
 * update. Writing the title's chapter flags back unchanged makes the database announce one.
 */
private fun refreshChapterList(graph: ChapterUploadsGraph, manga: Manga) {
    CoroutineScope(Dispatchers.IO).launch {
        runCatching { graph.mangaRepository.update(MangaUpdate(manga.id) { chapterFlags = manga.chapterFlags }) }
    }
}
