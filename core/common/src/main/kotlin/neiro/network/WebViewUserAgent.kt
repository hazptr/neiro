package neiro.network

import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat

/**
 * Keeps the default user agent level with the installed WebView: its Chrome version is the WebView's when that is
 * newer than the app's built-in one, and the client hints carry the WebView's full version, as Chrome's do.
 */
object WebViewUserAgent {

    private val CHROME_VERSION = """Chrome/(\d+)""".toRegex()
    private const val REDUCED_VERSION = ".0.0.0"

    /** The installed WebView's version (`149.0.7445.88`), or null when there is none. */
    private val installedVersion: String? by lazy {
        try {
            WebView.getCurrentWebViewPackage()?.versionName
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    /** [preference], with its default raised to the installed WebView's Chrome version. A value the user set stays. */
    infix fun matching(preference: Preference<String>): Preference<String> = Matching(preference) {
        withChromeVersion(preference.defaultValue(), installedVersion)
    }

    /**
     * The full version for the client hints of a user agent with [majorVersion] and [rest] (`.0.0.0`, or empty
     * when the user agent gives only the major version): the installed WebView's when the user agent names its
     * major version in the reduced form, as Chrome reports the exact version there.
     */
    fun fullVersion(majorVersion: String, rest: String): String = fullVersion(majorVersion, rest, installedVersion)

    internal fun fullVersion(majorVersion: String, rest: String, installed: String?): String {
        if (rest.isNotEmpty() && rest != REDUCED_VERSION) return majorVersion + rest
        return installed?.takeIf { it.substringBefore('.') == majorVersion && it.count { c -> c == '.' } == 3 }
            ?: (majorVersion + REDUCED_VERSION)
    }

    /** [userAgent] with the installed WebView's major Chrome version, when that is newer than its own. */
    internal fun withChromeVersion(userAgent: String, installed: String?): String {
        val webViewMajor = installed?.substringBefore('.')?.toIntOrNull() ?: return userAgent
        val match = CHROME_VERSION.find(userAgent) ?: return userAgent
        val major = match.groups[1]!!
        if (webViewMajor <= major.value.toInt()) return userAgent
        return userAgent.replaceRange(major.range, webViewMajor.toString())
    }

    private class Matching(
        private val preference: Preference<String>,
        computeDefault: () -> String,
    ) : Preference<String> {

        private val default by lazy(computeDefault)

        override fun key() = preference.key()

        override fun get() = if (preference.isSet()) preference.get() else default

        override fun set(value: String) = preference.set(value)

        override fun isSet() = preference.isSet()

        override fun delete() = preference.delete()

        override fun defaultValue() = default

        override fun changes(): Flow<String> = preference.changes().map { if (preference.isSet()) it else default }

        override fun stateIn(scope: CoroutineScope): StateFlow<String> =
            changes().stateIn(scope, SharingStarted.Eagerly, get())
    }
}
