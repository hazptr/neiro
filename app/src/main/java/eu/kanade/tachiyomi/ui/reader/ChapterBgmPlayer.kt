package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import eu.kanade.tachiyomi.data.cache.BgmCache
import eu.kanade.tachiyomi.source.model.AudioTrack
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

private const val FADE_IN_MS = 900L
private const val FADE_OUT_MS = 600L
private const val FADE_STEPS = 24

/**
 * ExoPlayer output for source-provided background music: audio focus and crossfades. Cue
 * decisions - settle debounce, supersede, enable/disable, loop changes - live in [BgmCueMachine];
 * this class only carries out what it decides.
 *
 * Each track gets its own player, so a swap crossfades: the outgoing player is detached from
 * [player] before its fade-out starts. Tracks stream through [BgmCache], so playback starts before
 * a track has finished downloading and a replay costs no network.
 */
@OptIn(UnstableApi::class)
class ChapterBgmPlayer(
    private val scope: CoroutineScope,
    private val context: Context,
    private val bgmCache: BgmCache,
) : BgmCueMachine.Output<MediaSource> {

    private val audioManager = context.getSystemService<AudioManager>()

    private val cueMachine = BgmCueMachine(scope, this)

    private var player: ExoPlayer? = null

    /** Intent to be paused, independent of whether a player currently exists or is playing. */
    private var pausedByLifecycle = false

    /**
     * Requested by hand rather than through ExoPlayer, which takes full focus for music and so
     * pauses whatever else is playing. Background music should duck other audio, not stop it.
     */
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> stop()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
                    AudioManager.AUDIOFOCUS_GAIN -> if (!pausedByLifecycle) player?.play()
                }
            }
            .build()
    }

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    fun onPageChanged(track: AudioTrack?, source: HttpSource?, enabled: Boolean, loop: Boolean) =
        cueMachine.onPageChanged(track, source, enabled, loop)

    /**
     * Downloaded tracks are plain files or document uris and are read directly. Everything else
     * reads through the cache: remote tracks stream into it, and tracks extracted from an archive
     * already sit in it.
     */
    override suspend fun load(track: AudioTrack, source: HttpSource?): MediaSource {
        val isLocal = Uri.parse(track.url).scheme in setOf("file", "content")
        val dataSourceFactory = if (isLocal) {
            DefaultDataSource.Factory(context)
        } else {
            bgmCache.dataSourceFactory(source)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(track.url)
            .setCustomCacheKey(bgmCache.keyOf(track.id))
            .build()
        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }

    override suspend fun start(item: MediaSource, id: String, loop: Boolean): Boolean =
        withContext(Dispatchers.Main) {
            if (!requestFocus()) {
                logcat(LogPriority.WARN) { "[BGM] audio focus denied for $id" }
                return@withContext false
            }
            val exo = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
                .setHandleAudioBecomingNoisy(true)
                .build()
            exo.volume = 0f
            exo.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            exo.addListener(TrackListener(exo, id))
            exo.setMediaSource(item)
            exo.playWhenReady = !pausedByLifecycle
            exo.prepare()
            player = exo
            true
        }

    private inner class TrackListener(private val exo: ExoPlayer, private val id: String) : Player.Listener {

        private var fadedIn = false

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (player !== exo) return
            when (playbackState) {
                Player.STATE_READY -> if (!fadedIn) {
                    fadedIn = true
                    scope.launch(Dispatchers.Main) { fade(exo, from = 0f, to = 1f, millis = FADE_IN_MS) }
                }
                // Only reached when not looping. A track that has run out otherwise holds a player
                // and audio focus for the rest of its cue, which can be most of a scene. Clearing
                // the active track also lets it play again if the reader leaves the cue and comes
                // back; staying put does not restart it.
                Player.STATE_ENDED -> {
                    player = null
                    cueMachine.onPlaybackEnded(id)
                    exo.release()
                    abandonFocus()
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Headphones unplugged: ExoPlayer has paused, and music should not resume on its own
            if (player === exo && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) stop()
        }

        override fun onPlayerError(error: PlaybackException) {
            logcat(LogPriority.WARN) { "[BGM] playback error ${error.errorCodeName} for $id" }
            if (player === exo) stop()
        }
    }

    override fun setLooping(loop: Boolean) {
        player?.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /**
     * Fades the current track out and releases it. Detaches [player] first so a track starting
     * during the fade crossfades with it instead of the two fighting over one reference.
     */
    override fun fadeOutAndRelease() {
        val exo = player ?: return
        player = null
        scope.launch(Dispatchers.Main) {
            try {
                fade(exo, from = exo.volume, to = 0f, millis = FADE_OUT_MS)
            } finally {
                exo.release()
                // Only give up focus if nothing else claimed it while fading out.
                if (player == null) abandonFocus()
            }
        }
    }

    /**
     * Ramps [exo] between two volumes. Cancelling the caller simply abandons the ramp, which is why
     * a fade holds its own player reference rather than reading [player]. Setting the volume only
     * posts to the playback thread, so stepping on the main thread costs nothing mid-scroll.
     */
    private suspend fun fade(exo: ExoPlayer, from: Float, to: Float, millis: Long) {
        val stepDelay = millis / FADE_STEPS
        for (i in 1..FADE_STEPS) {
            // A fade-in yields to the fade-out of a player that has since been superseded
            if (to > from && player !== exo) return
            exo.volume = from + (to - from) * (i / FADE_STEPS.toFloat())
            delay(stepDelay)
        }
    }

    fun pause() {
        pausedByLifecycle = true
        player?.pause()
    }

    fun resume() {
        if (!pausedByLifecycle) return
        pausedByLifecycle = false
        if (requestFocus()) player?.play()
    }

    /** Immediate teardown, for audio-focus loss and reader exit. No fade: the scope may be dying. */
    fun stop() {
        cueMachine.reset()
        player?.release()
        player = null
        pausedByLifecycle = false
        abandonFocus()
    }

    private fun requestFocus(): Boolean {
        val manager = audioManager ?: return true
        return manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }
}
