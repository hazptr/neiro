package neiro.network

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WebViewUserAgentTest {

    private val builtIn =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"

    @Test
    fun `a newer WebView raises the Chrome version`() {
        WebViewUserAgent.withChromeVersion(builtIn, "151.0.7520.12") shouldBe builtIn.replace("149", "151")
    }

    @Test
    fun `an older or missing WebView keeps the built-in version`() {
        WebViewUserAgent.withChromeVersion(builtIn, "120.0.6099.230") shouldBe builtIn
        WebViewUserAgent.withChromeVersion(builtIn, null) shouldBe builtIn
        WebViewUserAgent.withChromeVersion(builtIn, "weird") shouldBe builtIn
    }

    @Test
    fun `client hints carry the WebView's full version for its own major`() {
        WebViewUserAgent.fullVersion("149", ".0.0.0", "149.0.7445.88") shouldBe "149.0.7445.88"
        WebViewUserAgent.fullVersion("149", "", "149.0.7445.88") shouldBe "149.0.7445.88"
    }

    @Test
    fun `client hints keep the user agent's version otherwise`() {
        WebViewUserAgent.fullVersion("149", ".0.0.0", "120.0.6099.230") shouldBe "149.0.0.0"
        WebViewUserAgent.fullVersion("149", ".0.7000.1", "149.0.7445.88") shouldBe "149.0.7000.1"
        WebViewUserAgent.fullVersion("149", "", null) shouldBe "149.0.0.0"
    }
}
