package neiro.uploads

import tachiyomi.domain.chapter.model.Chapter

/**
 * Which uploads of a chapter the chapter list keeps, when a site lists several (other groups, re-uploads).
 * Matches groups by name, case-insensitive. Pure: the settings come in as arguments.
 */
object ChapterUploads {

    enum class Mode {
        /** Every upload the source lists. */
        ALL_UPLOADS,

        /** One upload per chapter number: the most preferred group's, else the first in the site's order. */
        ONE_PER_CHAPTER,
    }

    fun normalized(group: String): String = group.trim().lowercase()

    /** A comma-separated list of group names as typed in a setting, each once, in order. */
    fun parseGroups(text: String): List<String> = text.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy(::normalized)

    /**
     * The group behind [chapter]: its whole scanlator text, as Mihon's scanlator filter treats it, so a joint
     * credit is a group of its own.
     */
    fun groupsOf(chapter: Chapter): List<String> = listOfNotNull(chapter.scanlator?.trim()?.ifEmpty { null })

    fun isBlocked(groups: List<String>, blocked: Collection<String>): Boolean {
        if (blocked.isEmpty()) return false
        val names = blocked.mapTo(HashSet(), ::normalized)
        return groups.any { normalized(it) in names }
    }

    /**
     * The scanlator texts of [chapters] that a blocked group uploaded and Neiro hasn't excluded before
     * ([alreadySeeded]), so a group the user lets back in stays in.
     */
    fun scanlatorsToSeed(
        chapters: List<Chapter>,
        blocked: Collection<String>,
        alreadySeeded: Set<String>,
    ): Set<String> {
        if (blocked.isEmpty()) return emptySet()
        return chapters.asSequence()
            .filter {
                val scanlator = it.scanlator
                scanlator != null && scanlator.isNotBlank() &&
                    scanlator !in alreadySeeded
            }
            .filter { isBlocked(groupsOf(it), blocked) }
            .mapNotNullTo(HashSet()) { it.scanlator }
    }

    /** 0 for an upload by the first of [preferred], and so on; [preferred]'s size when none of its groups is. */
    fun preferenceRank(groups: List<String>, preferred: List<String>): Int {
        val names = groups.mapTo(HashSet(), ::normalized)
        return preferred.indexOfFirst { normalized(it) in names }.takeIf { it >= 0 } ?: preferred.size
    }

    /**
     * One upload per chapter number, in [items]' order: the one by the most [preferred] group, else the
     * first in the site's order (`sourceOrder`); chapter [keepId] (being opened) always wins its number.
     * Chapters without a recognised number are each their own chapter and always kept.
     */
    fun <T> onePerChapter(
        items: List<T>,
        chapterOf: (T) -> Chapter,
        preferred: List<String>,
        groupsOf: (Chapter) -> List<String> = ::groupsOf,
        keepId: Long? = null,
    ): List<T> {
        val kept = items.withIndex()
            .filter { chapterOf(it.value).isRecognizedNumber }
            .groupBy { chapterOf(it.value).chapterNumber }
            .values
            .mapTo(HashSet()) { same ->
                same.firstOrNull { chapterOf(it.value).id == keepId }?.index
                    ?: same.minWith(
                        compareBy<IndexedValue<T>> { preferenceRank(groupsOf(chapterOf(it.value)), preferred) }
                            .thenBy { chapterOf(it.value).sourceOrder },
                    ).index
            }
        return items.filterIndexed { index, item -> !chapterOf(item).isRecognizedNumber || index in kept }
    }
}
