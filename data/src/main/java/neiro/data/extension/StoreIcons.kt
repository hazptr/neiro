package neiro.data.extension

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * The icon each extension store names in its index (`meta.icon` in a `repo.json`), kept beside Mihon's
 * store table so that table stays Mihon's. Refreshed with the store, so it is app state, not backed up.
 */
@Inject
@SingleIn(AppScope::class)
class StoreIcons(private val preferenceStore: PreferenceStore) {

    operator fun get(indexUrl: String): String? = preference(indexUrl).get().ifEmpty { null }

    operator fun set(indexUrl: String, iconUrl: String?) {
        if (iconUrl == null) preference(indexUrl).delete() else preference(indexUrl).set(iconUrl)
    }

    private fun preference(indexUrl: String) =
        preferenceStore.getString(Preference.appStateKey("neiro_store_icon_$indexUrl"), "")

    companion object {
        /** [icon] as an absolute http(s) URL, relative ones against the index; null when missing or unusable. */
        fun resolve(icon: String?, indexUrl: String): String? {
            if (icon.isNullOrBlank()) return null
            val url = indexUrl.toHttpUrlOrNull()?.resolve(icon.trim()) ?: return null
            return url.toString()
        }
    }
}
