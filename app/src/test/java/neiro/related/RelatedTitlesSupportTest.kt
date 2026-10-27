package neiro.related

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RelatedTitlesSupportTest {

    @Test
    fun `whole title first, then its longest words, stop words left out`() {
        RelatedTitlesSupport.keywords("The Lantern After the Rain") shouldBe
            listOf("The Lantern After the Rain", "lantern", "rain")
    }

    @Test
    fun `punctuation becomes spaces in the title query`() {
        RelatedTitlesSupport.keywords("Paper Kite: Homeward") shouldBe
            listOf("Paper Kite Homeward", "homeward", "paper", "kite")
    }

    @Test
    fun `at most four searches`() {
        RelatedTitlesSupport.keywords("Seventh Lighthouse Keeper Remembers Distant Winters").size shouldBe 4
    }

    @Test
    fun `a one word title is searched once`() {
        RelatedTitlesSupport.keywords("Driftwood") shouldBe listOf("Driftwood")
    }

    @Test
    fun `numbers and short words are not searched alone`() {
        RelatedTitlesSupport.keywords("Floor 99 of a Tower") shouldBe listOf("Floor 99 of a Tower", "floor", "tower")
    }
}
