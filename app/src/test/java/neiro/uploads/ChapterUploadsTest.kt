package neiro.uploads

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ChapterUploadsTest {

    private fun chapter(id: Long, number: Double, scanlator: String?, order: Long = id) =
        Chapter.create().copy(id = id, chapterNumber = number, scanlator = scanlator, sourceOrder = order)

    @Test
    fun `an upload's group is its whole scanlator text, as in Mihon's filter`() {
        ChapterUploads.groupsOf(chapter(1, 1.0, " Alpha & Beta ")) shouldBe listOf("Alpha & Beta")
        ChapterUploads.groupsOf(chapter(1, 1.0, "Alpha • Beta, Gamma")) shouldBe listOf("Alpha • Beta, Gamma")
        ChapterUploads.groupsOf(chapter(1, 1.0, " ")) shouldBe emptyList()
        ChapterUploads.groupsOf(chapter(1, 1.0, null)) shouldBe emptyList()
    }

    @Test
    fun `settings lists are trimmed, deduplicated case-insensitively, in order`() {
        ChapterUploads.parseGroups(" Beta , alpha,, ALPHA ,Gamma ") shouldBe listOf("Beta", "alpha", "Gamma")
    }

    @Test
    fun `blocklist matches any of an upload's groups, ignoring case and spaces`() {
        ChapterUploads.isBlocked(listOf("Alpha", "Beta"), listOf(" beta ")) shouldBe true
        ChapterUploads.isBlocked(listOf("Alpha"), listOf("Alph")) shouldBe false
        ChapterUploads.isBlocked(listOf("Alpha"), emptyList()) shouldBe false
    }

    @Test
    fun `seeding excludes blocked scanlator texts once`() {
        val chapters = listOf(
            chapter(1, 1.0, "Beta"),
            chapter(2, 2.0, "Gamma"),
            chapter(3, 3.0, "Beta"),
            chapter(4, 4.0, "Delta"),
            chapter(5, 5.0, null),
            chapter(6, 6.0, "Alpha & Beta"),
        )
        // A joint credit is its own group, as in Mihon's scanlator filter
        ChapterUploads.scanlatorsToSeed(chapters, listOf("beta", "delta"), emptySet()) shouldBe setOf("Beta", "Delta")
        ChapterUploads.scanlatorsToSeed(chapters, listOf("alpha & beta"), emptySet()) shouldBe setOf("Alpha & Beta")
        // Already seeded (the user may have let it back in): not again
        ChapterUploads.scanlatorsToSeed(chapters, listOf("beta", "delta"), setOf("Beta")) shouldBe setOf("Delta")
        ChapterUploads.scanlatorsToSeed(chapters, emptyList(), emptySet()) shouldBe emptySet()
    }

    @Test
    fun `ranking follows the preferred list, unknown groups last`() {
        val preferred = listOf("Beta", "Alpha")
        ChapterUploads.preferenceRank(listOf("alpha"), preferred) shouldBe 1
        ChapterUploads.preferenceRank(listOf("Gamma", "BETA"), preferred) shouldBe 0
        ChapterUploads.preferenceRank(listOf("Gamma"), preferred) shouldBe 2
    }

    @Test
    fun `one per chapter keeps the preferred group, else the site's first, and every unnumbered chapter`() {
        val chapters = listOf(
            chapter(1, 2.0, "Alpha", order = 0),
            chapter(2, 2.0, "Beta", order = 1),
            chapter(3, 1.0, "Gamma", order = 2),
            chapter(4, 1.0, "Alpha", order = 3),
            chapter(5, -1.0, "Alpha", order = 4),
            chapter(6, -1.0, "Beta", order = 5),
        )
        ChapterUploads.onePerChapter(chapters, { it }, listOf("beta")).map { it.id } shouldBe listOf(2L, 3L, 5L, 6L)
        ChapterUploads.onePerChapter(chapters, { it }, emptyList()).map { it.id } shouldBe listOf(1L, 3L, 5L, 6L)
    }

    @Test
    fun `one per chapter keeps the chapter being opened`() {
        val chapters = listOf(chapter(1, 2.0, "Alpha", order = 0), chapter(2, 2.0, "Beta", order = 1))
        ChapterUploads.onePerChapter(chapters, { it }, listOf("alpha"), keepId = 2L).map { it.id } shouldBe listOf(2L)
    }
}
