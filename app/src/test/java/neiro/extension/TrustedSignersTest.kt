package neiro.extension

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test

class TrustedSignersTest {

    @Test
    fun `fingerprints are in the form the extension loader computes`() {
        TrustedSigners.fingerprints shouldHaveSize 2
        TrustedSigners.fingerprints.forEach { it shouldMatch Regex("[0-9a-f]{64}") }
    }
}
