package eu.kanade.tachiyomi.data.cache

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.AudioTrack
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.IOException
import java.io.OutputStream

private const val CACHE_DIRECTORY = "bgm_media_cache"
private const val CACHE_SIZE = 64L * 1024 * 1024

/** Tracks are seconds of music, not media files. Anything larger is a broken or hostile origin. */
private const val MAX_TRACK_BYTES = 16L * 1024 * 1024

/**
 * Media3 cache for source-provided audio tracks, shared by playback, preloading and downloads.
 *
 * Bounded and LRU-evicted. Entries are keyed by a hash of the track id rather than by URL: sources
 * hand out signed URLs that change with every page list, and track ids are not safe in a path.
 */
@OptIn(UnstableApi::class)
@Inject
@SingleIn(AppScope::class)
class BgmCache(
    private val context: Context,
    private val networkHelper: NetworkHelper,
) {

    private val cache by lazy {
        SimpleCache(
            File(context.cacheDir, CACHE_DIRECTORY),
            LeastRecentlyUsedCacheEvictor(CACHE_SIZE),
            StandaloneDatabaseProvider(context),
        )
    }

    /** Serialises writers so a track requested from several pages at once is fetched once. */
    private val lock = Mutex()

    fun keyOf(trackId: String): String = DiskUtil.hashKeyForDisk(trackId)

    /**
     * Reads through the cache and fills it from upstream. Network requests go through the source's
     * own client and headers, so expiring media can still be resolved in its interceptors; file and
     * content uris are read directly.
     */
    fun dataSourceFactory(source: HttpSource?): CacheDataSource.Factory {
        val http = OkHttpDataSource.Factory(source?.client ?: networkHelper.client)
            .apply { source?.headers?.toMap()?.let(::setDefaultRequestProperties) }
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, http))
    }

    /** Whether the whole track is stored, so playing it needs no network. */
    fun isCached(trackId: String): Boolean {
        val key = keyOf(trackId)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length != C.LENGTH_UNSET.toLong() && cache.isCached(key, 0, length)
    }

    /**
     * Ensures [track] is fully stored, fetching whatever is missing.
     *
     * Returns false rather than throwing: audio is never worth failing a page load over.
     */
    suspend fun prefetch(track: AudioTrack, source: HttpSource?): Boolean = withContext(Dispatchers.IO) {
        if (isCached(track.id)) return@withContext true
        lock.withLock {
            isCached(track.id) || write(track.id, track.url) { dataSpec ->
                CacheWriter(dataSourceFactory(source).createDataSource(), dataSpec, null, ::checkSize)
            }
        }
    }

    /** Copies [track] to [output] through the cache, fetching it first if needed. */
    suspend fun copyTo(track: AudioTrack, source: HttpSource?, output: OutputStream): Boolean =
        withContext(Dispatchers.IO) {
            if (!prefetch(track, source)) return@withContext false
            val dataSpec = dataSpec(track.id, track.url)
            DataSourceInputStream(dataSourceFactory(source).createDataSource(), dataSpec).use { it.copyTo(output) }
            true
        }

    /**
     * Stores bytes for a track that came from somewhere other than the network, e.g. a downloaded
     * archive. Play it back through [cachedUri].
     */
    suspend fun put(trackId: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_TRACK_BYTES) return@withContext false
        lock.withLock {
            write(trackId, cachedUri(trackId)) { dataSpec ->
                CacheWriter(CacheDataSource(cache, ByteArrayDataSource(bytes)), dataSpec, null, null)
            }
        }
    }

    /** A uri that only resolves from the cache, for tracks stored with [put]. */
    fun cachedUri(trackId: String): String = Uri.Builder()
        .scheme(CACHED_SCHEME)
        .authority(keyOf(trackId))
        .build()
        .toString()

    private fun dataSpec(trackId: String, uri: String): DataSpec = DataSpec.Builder()
        .setUri(uri)
        .setKey(keyOf(trackId))
        .build()

    private suspend fun write(trackId: String, uri: String, writer: (DataSpec) -> CacheWriter): Boolean {
        val cacheWriter = writer(dataSpec(trackId, uri))
        return try {
            currentCoroutineContext().ensureActive()
            cacheWriter.cache()
            true
        } catch (e: Throwable) {
            cacheWriter.cancel()
            // A partial entry would read as cached up to the point it stopped
            cache.removeResource(keyOf(trackId))
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "[BGM] failed to store track $trackId" }
            false
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun checkSize(requestLength: Long, bytesCached: Long, newBytesCached: Long) {
        if (requestLength > MAX_TRACK_BYTES || bytesCached > MAX_TRACK_BYTES) {
            throw IOException("track too large")
        }
    }

    companion object {
        const val CACHED_SCHEME = "neiro-bgm"
    }
}
