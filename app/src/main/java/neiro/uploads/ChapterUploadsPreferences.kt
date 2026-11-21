package neiro.uploads

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.presentation.more.settings.Preference
import logcat.LogPriority
import mihon.core.metro.metroGraph
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The chapter upload settings: global defaults, and each title's own order and mode.
 *
 * Everything is in the app's preference store, so it goes into backups with the app settings. A title's keys
 * are its source id and URL, not its database id, so they still match after a restore into another install.
 */
@Inject
@SingleIn(AppScope::class)
class ChapterUploadsPreferences(private val preferenceStore: PreferenceStore) {

    /** Groups whose uploads are excluded when a title first lists them, comma-separated. */
    val blockedGroups = preferenceStore.getString("pref_neiro_uploads_blocked_groups", "")

    /** Groups "One per chapter" prefers, most preferred first, comma-separated. */
    val preferredGroups = preferenceStore.getString("pref_neiro_uploads_preferred_groups", "")

    /** The mode for titles without their own. */
    val mode = preferenceStore.getEnum("pref_neiro_uploads_mode", ChapterUploads.Mode.ALL_UPLOADS)

    /** The title's own group order, one name per line; empty for the global preferred groups. */
    fun titleOrder(manga: Manga) = preferenceStore.getString("neiro_uploads_order_${manga.key}", "")

    /** The title's own mode's name, or empty for the global mode. */
    fun titleMode(manga: Manga) = preferenceStore.getString("neiro_uploads_mode_${manga.key}", "")

    /** Scanlator texts Neiro excluded for the title from the blocklist, so they're never excluded again. */
    fun seeded(manga: Manga) = preferenceStore.getStringSet("neiro_uploads_seeded_${manga.key}", emptySet())

    fun effectiveMode(manga: Manga): ChapterUploads.Mode =
        ChapterUploads.Mode.entries.firstOrNull { it.name == titleMode(manga).get() } ?: mode.get()

    /** The title's own order when it has one, else the global preferred groups. */
    fun effectiveOrder(manga: Manga): List<String> =
        titleOrderList(manga).ifEmpty { ChapterUploads.parseGroups(preferredGroups.get()) }

    fun titleOrderList(manga: Manga): List<String> = titleOrder(manga).get().lines().filter { it.isNotBlank() }

    private val Manga.key get() = "${source}_$url"

    companion object {
        /** For code Mihon calls without a context (chapter filters, chapter sync). */
        val instance: ChapterUploadsPreferences by lazy {
            Injekt.get<Application>().metroGraph<ChapterUploadsGraph>().chapterUploadsPreferences
        }
    }
}

@ContributesTo(AppScope::class)
interface ChapterUploadsGraph {
    val chapterUploadsPreferences: ChapterUploadsPreferences
    val mangaRepository: MangaRepository
    val chapterRepository: ChapterRepository
}

/**
 * Mihon's chapter filters, plus Neiro's: one upload per chapter in that mode. Reads the settings once per call.
 * [keepId] (a chapter being opened) is always kept.
 */
object ChapterListFilter {

    fun <T> apply(items: List<T>, manga: Manga, keepId: Long? = null, chapterOf: (T) -> Chapter): List<T> {
        val uploads = ChapterUploadsPreferences.instance
        if (uploads.effectiveMode(manga) != ChapterUploads.Mode.ONE_PER_CHAPTER) return items
        return ChapterUploads.onePerChapter(items, chapterOf, uploads.effectiveOrder(manga), keepId = keepId)
    }

    fun <T> apply(items: Sequence<T>, manga: Manga, chapterOf: (T) -> Chapter): Sequence<T> =
        apply(items.toList(), manga, chapterOf = chapterOf).asSequence()
}

/** Excludes, per title, the scanlator texts of blocked groups the first time the title lists them. */
object ChapterUploadsSeeder {

    suspend fun seed(manga: Manga, chapters: List<Chapter>) {
        try {
            val preferences = ChapterUploadsPreferences.instance
            val blocked = ChapterUploads.parseGroups(preferences.blockedGroups.get())
            if (blocked.isEmpty()) return
            val seeded = preferences.seeded(manga)
            val toSeed = ChapterUploads.scanlatorsToSeed(chapters, blocked, seeded.get())
            if (toSeed.isEmpty()) return
            val repository = Injekt.get<Application>().metroGraph<ChapterUploadsGraph>().mangaRepository
            repository.setExcludedScanlators(manga.id, repository.getExcludedScanlators(manga.id) + toSeed)
            seeded.set(seeded.get() + toSeed)
        } catch (e: Exception) {
            // The chapter list must still update when this fails
            logcat(LogPriority.ERROR, e)
        }
    }
}

/** The global rows: chapter uploads mode, preferred groups, blocked groups. */
@Composable
fun chapterUploadsPreferenceItems(): Array<Preference.PreferenceItem<out Any, out Any>> {
    val context = LocalContext.current
    val preferences = remember { context.metroGraph<ChapterUploadsGraph>().chapterUploadsPreferences }
    return arrayOf(
        Preference.PreferenceItem.ListPreference(
            preference = preferences.mode,
            entries = mapOf(
                ChapterUploads.Mode.ALL_UPLOADS to stringResource(MR.strings.neiro_uploads_mode_all),
                ChapterUploads.Mode.ONE_PER_CHAPTER to stringResource(MR.strings.neiro_uploads_mode_one),
            ),
            title = stringResource(MR.strings.neiro_uploads_mode),
        ),
        Preference.PreferenceItem.EditTextPreference(
            preference = preferences.preferredGroups,
            title = stringResource(MR.strings.neiro_uploads_preferred),
            subtitle = stringResource(MR.strings.neiro_uploads_preferred_summary),
        ),
        Preference.PreferenceItem.EditTextPreference(
            preference = preferences.blockedGroups,
            title = stringResource(MR.strings.neiro_uploads_blocked),
            subtitle = stringResource(MR.strings.neiro_uploads_blocked_summary),
        ),
    )
}
