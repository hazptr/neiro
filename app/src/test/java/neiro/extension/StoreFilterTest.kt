package neiro.extension

import eu.kanade.domain.extension.model.Extensions
import eu.kanade.tachiyomi.extension.model.Extension
import io.kotest.matchers.shouldBe
import mihon.domain.extension.model.ContentWarning
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Test

class StoreFilterTest {

    private fun store(url: String) = ExtensionStore(
        indexUrl = url,
        name = url,
        badgeLabel = url,
        signingKey = "key-$url",
        contact = ExtensionStore.Contact(website = url, discord = null),
        isLegacy = false,
        extensionListUrl = null,
    )

    private val a = store("a")
    private val b = store("b")

    private fun available(name: String, store: ExtensionStore) = Extension.Available(
        name = name, pkgName = name, versionName = "1", versionCode = 1, libVersion = 1.6, lang = "en",
        contentWarning = ContentWarning.SAFE, sources = emptyList(), apkUrl = "", iconUrl = "", store = store,
    )

    private fun loaded(name: String, store: ExtensionStore?, hasUpdate: Boolean = false) = Extension.Loaded(
        name = name, pkgName = name, versionName = "1", versionCode = 1, libVersion = 1.6, lang = "en",
        contentWarning = ContentWarning.SAFE, isShared = true, signatures = emptyList(), pkgFactory = null,
        sources = emptyList(), hasUpdate = hasUpdate, store = store,
    )

    private val update = loaded("update", b, hasUpdate = true)
    private val extensions = Extensions(
        updates = listOf(update),
        loaded = listOf(loaded("fromA", a), loaded("fromB", b), loaded("private", null)),
        available = listOf(available("listedA", a), available("listedB", b)),
        notLoaded = emptyList(),
    )

    @Test
    fun `nothing hidden keeps everything`() {
        StoreFilter.apply(extensions, emptySet()) shouldBe extensions
    }

    @Test
    fun `hidden store drops its listings and installed extensions but keeps updates and storeless ones`() {
        val filtered = StoreFilter.apply(extensions, setOf("b"))
        filtered.updates shouldBe listOf(update)
        filtered.loaded.map { it.name } shouldBe listOf("fromA", "private")
        filtered.available.map { it.name } shouldBe listOf("listedA")
    }
}
