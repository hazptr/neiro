package neiro.uploads

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import logcat.LogPriority
import mihon.core.metro.metroGraph
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.model.LibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Library counts (unread, read, bookmarked) of the chapters the series screen shows, for titles in "One per
 * chapter": the database counts every upload, as Mihon's excluded scanlators are the only filter it knows.
 */
object ChapterListCounts {

    /** [library], recounted again whenever the global upload settings change. */
    fun library(library: Flow<List<LibraryManga>>): Flow<List<LibraryManga>> {
        val preferences = ChapterUploadsPreferences.instance
        return combine(
            library,
            preferences.mode.changes(),
            preferences.preferredGroups.changes(),
        ) { items, _, _ -> items }
            .mapLatest(::visible)
    }

    suspend fun visible(library: List<LibraryManga>): List<LibraryManga> {
        return try {
            val preferences = ChapterUploadsPreferences.instance
            val ids = library
                .filter { preferences.effectiveMode(it.manga) == ChapterUploads.Mode.ONE_PER_CHAPTER }
                .map { it.id }
            if (ids.isEmpty()) return library
            val graph = Injekt.get<Application>().metroGraph<ChapterUploadsGraph>()
            val chapters = graph.chapterRepository.getChaptersByMangaIds(ids)
            val excluded = graph.mangaRepository.getExcludedScanlatorsByMangaIds(ids)
            library.map { item ->
                val all = chapters[item.id] ?: return@map item
                val excludedScanlators = excluded[item.id].orEmpty().toHashSet()
                val shown = ChapterListFilter.apply(
                    all.filter { it.scanlator !in excludedScanlators },
                    item.manga,
                ) { it }
                item.copy(
                    totalChapters = shown.size.toLong(),
                    readCount = shown.count { it.read }.toLong(),
                    bookmarkCount = shown.count { it.bookmark }.toLong(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The library must still show when this fails
            logcat(LogPriority.ERROR, e)
            library
        }
    }
}
