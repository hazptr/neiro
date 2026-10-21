package neiro.app

import eu.kanade.tachiyomi.data.cache.ChapterCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat

/** Neiro's version code is Mihon's times this, plus the Neiro release on top of that Mihon version. */
private const val NEIRO_VERSION_SCALE = 100

/** Raised when cached page lists from older builds are known to be wrong; see [clearStalePageLists]. */
private const val PAGE_LIST_EPOCH = 1

/** Neiro's additions to app startup, called from Mihon's App. */
object NeiroStartup {

    /** Mihon's migrations are numbered by Mihon's version codes, which Neiro's multiply by 100. */
    fun toMihonVersionCode(code: Int): Int = if (code >= NEIRO_VERSION_SCALE) code / NEIRO_VERSION_SCALE else code

    /**
     * Drops page lists cached by builds that could not attach source audio to them: a chapter read
     * on one of those would otherwise stay silent until its cache entry expired. Bump
     * [PAGE_LIST_EPOCH] whenever a fix changes what a cached page list should contain.
     */
    fun clearStalePageLists(
        preferenceStore: PreferenceStore,
        chapterCache: () -> ChapterCache,
        scope: CoroutineScope,
        previousVersionCode: Int,
    ) {
        val epoch = preferenceStore.getInt(Preference.appStateKey("neiro_page_list_epoch"), 0)
        if (epoch.get() >= PAGE_LIST_EPOCH) return
        epoch.set(PAGE_LIST_EPOCH)
        if (previousVersionCode == 0) return
        scope.launch(Dispatchers.IO) {
            val deleted = chapterCache().clear()
            logcat { "Cleared $deleted cached chapter files from before page list epoch $PAGE_LIST_EPOCH" }
        }
    }
}
