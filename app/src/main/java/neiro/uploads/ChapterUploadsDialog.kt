package neiro.uploads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.core.metro.metroGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DragHandle
import mihon.icons.materialsymbols.roundedfilled.Visibility
import mihon.icons.materialsymbols.roundedfilled.VisibilityOff
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The title's scanlators in one list, in place of Mihon's scanlator filter. Each row has a visibility toggle
 * (Mihon's excluded scanlators, saved through [onConfirm]); with "One per chapter" on, the rows also drag into
 * the title's preferred order. Until the user changes the mode or order here, the title follows the global settings.
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
    // First appearance in the site's order, to break ties between unranked scanlators. The rows wait for it, so
    // the list doesn't reorder under the user.
    var siteOrder by remember(manga.id) { mutableStateOf(emptyMap<String, Int>()) }
    var loaded by remember(manga.id) { mutableStateOf(false) }
    val rows = remember(manga.id) { mutableListOf<String>().toMutableStateList() }
    LaunchedEffect(manga.id, availableScanlators) {
        siteOrder = withContext(Dispatchers.IO) {
            graph.chapterRepository.getChapterByMangaId(manga.id)
                .sortedBy { it.sourceOrder }
                .mapNotNull { it.scanlator }
                .distinct()
                .withIndex()
                .associate { it.value to it.index }
        }
        rows.replaceWith(sortRows(availableScanlators, preferences.effectiveOrder(manga), siteOrder))
        orderChanged = false
        loaded = true
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        rows.add(to.index, rows.removeAt(from.index))
        orderChanged = true
        useDefaults = false
    }
    val moveUp = stringResource(MR.strings.neiro_uploads_move_up)
    val moveDown = stringResource(MR.strings.neiro_uploads_move_down)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.neiro_uploads_title)) },
        text = {
            Column {
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
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = onePerChapter, onCheckedChange = null)
                }
                val reorderable = onePerChapter && rows.size > 1
                if (reorderable) {
                    Text(
                        text = stringResource(MR.strings.neiro_uploads_order_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.small,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = MaterialTheme.padding.small))
                if (loaded && rows.isEmpty()) {
                    Text(
                        text = stringResource(MR.strings.no_scanlators_found),
                        modifier = Modifier.padding(MaterialTheme.padding.small),
                    )
                }
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    LazyColumn(state = listState) {
                        itemsIndexed(rows, key = { _, scanlator -> scanlator }) { index, scanlator ->
                            ReorderableItem(reorderState, key = scanlator, enabled = reorderable) { isDragging ->
                                val move = { by: Int ->
                                    rows.add(index + by, rows.removeAt(index))
                                    orderChanged = true
                                    useDefaults = false
                                }
                                ScanlatorRow(
                                    scanlator = scanlator,
                                    visible = scanlator !in excluded,
                                    reorderable = reorderable,
                                    isDragging = isDragging,
                                    onToggleVisible = {
                                        if (!excluded.remove(scanlator)) excluded.add(scanlator)
                                    },
                                    modifier = Modifier.semantics {
                                        if (!reorderable) return@semantics
                                        customActions = listOfNotNull(
                                            CustomAccessibilityAction(moveUp) {
                                                move(-1)
                                                true
                                            }
                                                .takeIf { index > 0 },
                                            CustomAccessibilityAction(moveDown) {
                                                move(1)
                                                true
                                            }
                                                .takeIf { index < rows.lastIndex },
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (listState.canScrollForward) {
                        HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
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
                            val global = ChapterUploads.parseGroups(preferences.preferredGroups.get())
                            rows.replaceWith(sortRows(rows, global, siteOrder))
                            scope.launch { listState.scrollToItem(0) }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.neiro_uploads_use_defaults))
                    }
                }
                if (excluded.isNotEmpty()) {
                    TextButton(onClick = excluded::clear) {
                        Text(text = stringResource(MR.strings.neiro_uploads_show_all))
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
                        if (orderChanged) preferences.titleOrder(manga).set(rows.joinToString("\n"))
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

/** By [ranking], then by first upload in the site's order, then by name. */
private fun sortRows(scanlators: Collection<String>, ranking: List<String>, siteOrder: Map<String, Int>): List<String> =
    scanlators.sortedWith(
        compareBy<String> { ChapterUploads.preferenceRank(listOf(it), ranking) }
            .thenBy { siteOrder[it] ?: Int.MAX_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it },
    )

private fun SnapshotStateList<String>.replaceWith(items: List<String>) {
    if (items == this) return
    clear()
    addAll(items)
}

@Composable
private fun ReorderableCollectionItemScope.ScanlatorRow(
    scanlator: String,
    visible: Boolean,
    reorderable: Boolean,
    isDragging: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shadowElevation = if (isDragging) 4.dp else 0.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (reorderable) 0.dp else MaterialTheme.padding.small),
        ) {
            if (reorderable) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .draggableHandle()
                        .minimumInteractiveComponentSize(),
                )
            }
            Text(
                text = scanlator,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (visible) 1f else 0.38f),
            )
            IconToggleButton(checked = visible, onCheckedChange = { onToggleVisible() }) {
                Icon(
                    imageVector = if (visible) {
                        MaterialSymbols.RoundedFilled.Visibility
                    } else {
                        MaterialSymbols.RoundedFilled.VisibilityOff
                    },
                    contentDescription = stringResource(
                        if (visible) MR.strings.neiro_uploads_hide else MR.strings.neiro_uploads_show,
                    ),
                )
            }
        }
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
