package neiro.related

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * The related titles API that extensions built against the Keiyoushi extensions-lib carry
 * (`supportsRelatedMangas`, `disableRelatedMangasBySearch`, `fetchRelatedMangaList`). Mihon's
 * source-api doesn't declare it, so it's looked up on the extension's own class: Mihon's files stay
 * untouched, and an extension that never overrides it simply has no related titles.
 */
object RelatedTitlesSupport {

    /** What a source offers. [fromSource]: the site's own list; [bySearch]: searching its title words. */
    data class Capabilities(val fromSource: Boolean, val bySearch: Boolean) {
        val any: Boolean get() = fromSource || bySearch
    }

    private val NONE = Capabilities(fromSource = false, bySearch = false)

    // By source id: the answer can differ between instances of one class. Weak on the class, so an
    // updated or removed extension's class loader isn't kept alive by this cache.
    private val capabilities = ConcurrentHashMap<Long, Pair<WeakReference<Class<*>>, Capabilities>>()

    fun capabilities(source: Source?): Capabilities {
        if (source == null) return NONE
        capabilities[source.id]?.let { (clazz, cached) -> if (clazz.get() === source.javaClass) return cached }
        // LinkageError as well: the extension may reference lib types the app doesn't have.
        // A failure isn't cached, so a getter that throws once is asked again next time.
        val read = try {
            read(source)
        } catch (_: Throwable) {
            return NONE
        }
        capabilities[source.id] = WeakReference<Class<*>>(source.javaClass) to read
        return read
    }

    private fun read(source: Source): Capabilities {
        if (bool(source, "getDisableRelatedMangas") == true) return NONE
        return Capabilities(
            fromSource = bool(source, "getSupportsRelatedMangas") == true && fetchMethod(source.javaClass) != null,
            bySearch = bool(source, "getDisableRelatedMangasBySearch") == false,
        )
    }

    private fun bool(source: Source, getter: String): Boolean? {
        val method = source.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
        return method?.invoke(source) as? Boolean
    }

    private fun fetchMethod(clazz: Class<*>): Method? = clazz.methods.firstOrNull {
        it.name == "fetchRelatedMangaList" &&
            it.parameterTypes.contentEquals(arrayOf(SManga::class.java, Continuation::class.java))
    }

    /** The site's own related titles for [manga], as the extension provides them. */
    suspend fun fetchFromSource(source: Source, manga: SManga): List<SManga> {
        val method = fetchMethod(source.javaClass) ?: return emptyList()
        val result = suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
            try {
                method.invoke(source, manga, continuation)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
        return (result as? List<*>).orEmpty().filterIsInstance<SManga>()
    }

    /**
     * The searches to run for [title]: the whole title, then its longest words, at most [MAX_SEARCHES]
     * in all and run one at a time, so a long title doesn't turn into a burst of requests to the site.
     */
    fun keywords(title: String): List<String> {
        val whole = title.replace(SEPARATORS, " ").trim().replace(WHITESPACE, " ")
        val words = whole
            .split(' ')
            .map { it.lowercase() }
            .filter { it.length > 2 && !it.all(Char::isDigit) && it !in STOP_WORDS }
            .distinct()
            .filter { it != whole.lowercase() }
            .sortedByDescending { it.length }
        return (listOf(whole) + words).filter { it.isNotEmpty() }.take(MAX_SEARCHES)
    }

    private const val MAX_SEARCHES = 4

    private val WHITESPACE = Regex("\\s+")

    // Words that match half a catalogue on their own
    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "from", "into", "you", "your", "his", "her", "its", "who", "how", "was", "are",
        "not", "but", "all", "one", "after", "when", "this", "that", "than", "out", "off", "over", "manga", "manhwa",
        "manhua", "comic", "webtoon", "novel", "season", "part", "volume", "chapter",
    )

    private val SEPARATORS =
        Regex("([!~#$%^&*+_|/\\\\,?:;'“”‘’\"<>(){}\\[\\]。・～：—！？、―«»《》〘〙【】「」｜]|\\s-|-\\s|\\s\\.|\\.\\s)")
}
