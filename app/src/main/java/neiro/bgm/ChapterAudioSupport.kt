package neiro.bgm

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.cache.BgmCache
import eu.kanade.tachiyomi.data.download.BGM_FILE_PREFIX
import eu.kanade.tachiyomi.data.download.BGM_INFO_FILE
import eu.kanade.tachiyomi.data.download.BgmInfo
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.model.AudioCue
import eu.kanade.tachiyomi.source.model.AudioTrack
import eu.kanade.tachiyomi.source.model.ChapterAudio
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.DiskUtil.NOMEDIA_FILE
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.core.archive.ArchiveReader
import mihon.core.archive.archiveReader
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.domain.download.service.DownloadPreferences
import java.io.InputStream

/**
 * Everything background music needs from Mihon's page loaders and downloader, in one Neiro-owned
 * place: those classes only call in here, so a new Mihon version touching them merges with a few
 * hook lines instead of all of this.
 */
@Inject
@SingleIn(AppScope::class)
class ChapterAudioSupport(
    private val context: Context,
    val bgmCache: BgmCache,
    private val readerPreferences: ReaderPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val json: Json,
) {

    /**
     * Fetches the track covering [page], if the reader is going to play it.
     *
     * Hung off page loading rather than scheduled separately so it inherits the preload window,
     * priority and cancellation the reader already applies to images: by the time a cue is
     * scrolled into, its track was fetched alongside the pages around it.
     */
    suspend fun preload(page: ReaderPage, source: HttpSource) {
        if (!readerPreferences.bgmEnabled.get()) return
        val audio = page.chapter.pages?.firstNotNullOfOrNull { it.chapterAudio } ?: return
        val track = audio.trackAt(page.index) ?: return
        bgmCache.prefetch(track, source)
    }

    /** Pages of a downloaded chapter, with the music its sidecar recorded attached again. */
    suspend fun attachOffline(pages: List<ReaderPage>, chapterPath: UniFile?): List<ReaderPage> =
        attachBgm(pages, chapterPath)

    /** Stores the chapter's music next to its downloaded images. */
    suspend fun download(tmpDir: UniFile, download: Download, pageList: List<Page>) =
        downloadChapterAudio(tmpDir, download, pageList)

    /** Music files and their sidecar, which must not count as downloaded pages. */
    fun isAudioFile(fileName: String): Boolean = fileName == BGM_INFO_FILE || fileName.startsWith(BGM_FILE_PREFIX)

    /**
     * Re-attaches the background music a [BgmInfo] sidecar recorded for this chapter.
     *
     * Almost no chapters carry a sidecar, so a miss here must be silent and cheap. Past that
     * point, a warning is worth it: a sidecar that won't parse or a track file that has gone
     * missing means a chapter that should have music will just play silently instead, with
     * nothing in the UI to say why.
     */
    private suspend fun attachBgm(pages: List<ReaderPage>, chapterPath: UniFile?): List<ReaderPage> {
        // Checked first, before any archive or network I/O: a user with audio off should never
        // pay for a sidecar read or a track fetch just for opening a chapter.
        if (!readerPreferences.bgmEnabled.get()) return pages
        chapterPath ?: return pages
        val isArchive = chapterPath.isFile
        val info = readBgmInfo(chapterPath, isArchive) ?: return pages
        if (info.cues.isEmpty()) return pages

        // Resolve each cued track once rather than once per page.
        val cuedTrackIds = info.cues.values.toSet()
        val tracks = cuedTrackIds.mapNotNull { trackId ->
            val filename = info.files[trackId] ?: return@mapNotNull null
            val url = if (isArchive) {
                extractTrackFromArchive(chapterPath, filename, trackId)
            } else {
                findTrackInDirectory(chapterPath, filename)
            } ?: return@mapNotNull null
            AudioTrack(trackId, url)
        }
        if (tracks.isEmpty()) return pages
        val resolvedIds = tracks.map { it.id }.toSet()

        // Run-length encode the per-file cues into ranges over the reader's own page index,
        // which is what ChapterAudio.trackAt is keyed on.
        val cues = mutableListOf<AudioCue>()
        var i = 0
        while (i < pages.size) {
            val trackId = info.cues[pages[i].name]?.takeIf { it in resolvedIds }
            if (trackId == null) {
                i++
                continue
            }
            var j = i + 1
            while (j < pages.size && info.cues[pages[j].name] == trackId) j++
            cues += AudioCue(trackId, i, j)
            i = j
        }
        if (cues.isEmpty()) return pages

        // Attached to a single page; the reader reads it back with firstNotNullOfOrNull.
        pages.firstOrNull()?.chapterAudio = ChapterAudio(tracks, cues)
        return pages
    }

    private fun readBgmInfo(chapterPath: UniFile, isArchive: Boolean): BgmInfo? {
        val raw = try {
            if (isArchive) {
                chapterPath.archiveReader(context).use { reader ->
                    reader.getEntryStream(BGM_INFO_FILE)?.use { it.bufferedReader().readText() }
                }
            } else {
                chapterPath.findFile(BGM_INFO_FILE)?.openInputStream()?.use { it.bufferedReader().readText() }
            } ?: return null
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "[BGM] failed to read $BGM_INFO_FILE" }
            return null
        }

        val info = try {
            json.decodeFromString(BgmInfo.serializer(), raw)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "[BGM] malformed $BGM_INFO_FILE" }
            return null
        }

        // A newer sidecar is a format this build would read wrong rather than not at all.
        if (info.version > BgmInfo.VERSION) {
            logcat(LogPriority.WARN) {
                "[BGM] sidecar version ${info.version} is newer than supported (${BgmInfo.VERSION})"
            }
            return null
        }
        return info
    }

    /** Directory case: the track is already a plain file, so the player can open it by uri. */
    private fun findTrackInDirectory(chapterPath: UniFile, filename: String): String? {
        val file = chapterPath.findFile(filename)
        if (file == null) {
            logcat(LogPriority.WARN) { "[BGM] track file $filename missing from $chapterPath" }
            return null
        }
        return file.uri.toString()
    }

    /**
     * CBZ case: an archive entry has no file uri of its own, so pull its bytes out once into
     * [BgmCache], the same store a remote track would be fetched into. That makes this a cache
     * hit for the player instead of a second copy living outside it.
     */
    private suspend fun extractTrackFromArchive(chapterFile: UniFile, filename: String, trackId: String): String? {
        val uri = bgmCache.cachedUri(trackId)
        if (bgmCache.isCached(trackId)) return uri

        logcat { "[BGM] extracting $filename from the downloaded archive" }
        val bytes = try {
            chapterFile.archiveReader(context).use { reader ->
                val input = reader.getEntryStream(filename) ?: error("track entry $filename missing from archive")
                input.use { it.readBytes() }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "[BGM] failed to extract $filename from archive" }
            return null
        }
        return uri.takeIf { bgmCache.put(trackId, bytes) }
    }

    /**
     * Downloads the chapter's background music, if the source attached any, and writes a
     * [BgmInfo] sidecar mapping each downloaded image file to the track that plays over it.
     *
     * A track that fails to download is logged and skipped rather than propagated: music is
     * secondary to the pages, and losing it must never fail an otherwise-complete chapter.
     *
     * @param tmpDir the temporary directory of the download.
     * @param download the download the pages belong to.
     * @param pageList the chapter's page list.
     */
    private suspend fun downloadChapterAudio(tmpDir: UniFile, download: Download, pageList: List<Page>) {
        // Checked first, before any network I/O: a user with audio off must not pay for track
        // downloads they will never hear.
        // Two separate questions: whether the reader plays audio at all, and whether it is
        // worth the extra bytes in a download. Tracks add roughly a fifth to a chapter.
        if (!readerPreferences.bgmEnabled.get() || !downloadPreferences.downloadBgm.get()) return

        val chapterAudio = pageList.firstNotNullOfOrNull { it.chapterAudio } ?: return
        if (chapterAudio.tracks.isEmpty()) return

        val source = download.source
        val files = mutableMapOf<String, String>()
        for (track in chapterAudio.tracks) {
            // Content-addressed rather than positional: download.pages is null after a restart
            // and gets re-fetched, so track order is not stable across retries. Naming by ordinal
            // would relabel a file that is already on disk out from under a sidecar that still
            // points at it under its old name.
            val filename = BGM_FILE_PREFIX + DiskUtil.hashKeyForDisk(track.id)

            // tmpDir is reused across retries; a track already stored from a previous attempt
            // shouldn't be re-fetched
            if (tmpDir.findFile(filename) != null) {
                files[track.id] = filename
                continue
            }

            // Fetched through the shared cache, not a bespoke network call, so a track the reader
            // already previewed online is not downloaded a second time here.
            try {
                val tmpFile = tmpDir.createFile("$filename.tmp")!!
                val stored = try {
                    tmpFile.openOutputStream().use { output -> bgmCache.copyTo(track, source, output) }
                } catch (e: Exception) {
                    tmpFile.delete()
                    throw e
                }
                if (!stored) {
                    tmpFile.delete()
                    continue
                }
                tmpFile.renameTo(filename)
                files[track.id] = filename
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                // Best-effort: log and move on, a dead track must not sink the whole chapter
                logcat(LogPriority.WARN, e) { "[BGM] failed to store track ${track.id}" }
            }
        }

        // Nothing made it to disk, so there's nothing worth describing
        if (files.isEmpty()) return

        // Guarded end to end: downloadChapter turns any escaping throwable into Download.State.ERROR,
        // and a chapter with every page already on disk must not be marked failed just because its
        // sidecar could not be built or written.
        try {
            // Excluded up front rather than left for isImage() to reject: ComicInfo.xml and the
            // track files above are always present by now, and isImage() opens a stream to sniff
            // the header, which used to throw for at least one of them and marked an otherwise
            // fully downloaded chapter as errored.
            val knownNonImages = setOf(COMIC_INFO_FILE, BGM_INFO_FILE, NOMEDIA_FILE) + files.values
            val imageFiles = tmpDir.listFiles().orEmpty()
                .filter { file ->
                    val name = file.name.orEmpty()
                    file.isFile && name !in knownNonImages && !name.endsWith(".tmp") &&
                        ImageUtil.isImage(name) { file.openInputStream() }
                }

            // Cued by the source page each file was produced from, not by position: a tall image
            // splits into several files sharing one numeric prefix, and all of them inherit that
            // page's track.
            val cues = imageFiles.mapNotNull { file ->
                val name = file.name.orEmpty()
                val pageIndex = name.takeWhile(Char::isDigit).toIntOrNull()?.minus(1)
                    ?: return@mapNotNull null
                val trackId = chapterAudio.trackAt(pageIndex)?.id?.takeIf { it in files }
                    ?: return@mapNotNull null
                name to trackId
            }.toMap()

            if (cues.isEmpty()) return

            tmpDir.createFile(BGM_INFO_FILE)!!.openOutputStream().use {
                it.write(json.encodeToString(BgmInfo.serializer(), BgmInfo(files = files, cues = cues)).toByteArray())
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "[BGM] failed to write $BGM_INFO_FILE" }
        }
    }
}

/**
 * [ArchiveReader.getInputStream] matches on the full entry name, which misses whenever the cbz
 * wraps its pages in a top-level folder. Widen to a basename match on that miss.
 */
private fun ArchiveReader.getEntryStream(name: String): InputStream? {
    getInputStream(name)?.let { return it }
    val fallback = useEntries { entries ->
        entries.firstOrNull { it.isFile && it.name.substringAfterLast('/') == name }
    }
    return fallback?.let { getInputStream(it.name) }
}
