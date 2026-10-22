package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.source.model.AudioTrack
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * How long a cue must stay current before it is acted on.
 *
 * Page images cancel instead of debouncing, which works because a cancelled image was never
 * drawn. Starting playback cannot be undone the same way, so flinging past a short cue must
 * not sound it at all. Fetching itself is [BgmCueMachine.Output.load]'s job.
 */
private const val SETTLE_MS = 250L

/**
 * Cue decision logic for source-provided background music: settle debounce, supersede,
 * enable/disable and loop changes. Has no Android dependency, so it can be driven directly from a
 * plain JVM test; [output] does the actual loading and playback of whatever [T] it plays.
 */
class BgmCueMachine<T : Any>(private val scope: CoroutineScope, private val output: Output<T>) {

    /** What this machine decides should happen, carried out by whoever owns the real player. */
    interface Output<T : Any> {
        /** Resolves [track] to something playable. Null means it could not be obtained. */
        suspend fun load(track: AudioTrack, source: HttpSource?): T?

        /** Hands [item] to the real player. Returns whether it actually started. */
        suspend fun start(item: T, id: String, loop: Boolean): Boolean

        /** Applies a loop-preference change to whatever is currently playing. */
        fun setLooping(loop: Boolean)

        /** Fades out and releases whatever is currently playing, if anything. */
        fun fadeOutAndRelease()

        /**
         * Fades out and pauses whatever is playing, keeping its position, or resumes it. Only
         * called when [muted] actually changes.
         */
        fun setMuted(muted: Boolean)
    }

    /** What should be playing once the current cue settles. Compared against to debounce. */
    private var desiredId: String? = null

    /** What is actually loading or playing right now. */
    private var activeId: String? = null

    private var settleJob: Job? = null

    private var loadingId: String? = null

    /** The active track the player has reported audible; reset whenever a new one is committed. */
    private var settledId: String? = null
    private val mutableLoading = MutableStateFlow(false)

    /**
     * Whether the desired track is being obtained (fetched, extracted, prepared) but is not yet
     * audible. Cleared on every exit: ready, failure, supersede, disable, reset.
     */
    val loading: StateFlow<Boolean> = mutableLoading.asStateFlow()

    /** Mirrors the user's preference; see [onPageChanged]. */
    private var loop = true

    /** Mirrors the session's mute; see [onPageChanged]. */
    private var muted = false

    /** A start was held back because of the mute, and is owed when it is lifted. */
    private var deferred = false

    /**
     * [muted] pauses instead of tearing down: the cue is still followed, so unmuting resumes the
     * track where it stopped, or starts the one the reader has since scrolled to. Nothing starts
     * while muted.
     */
    fun onPageChanged(
        track: AudioTrack?,
        source: HttpSource?,
        enabled: Boolean,
        loop: Boolean,
        muted: Boolean = false,
    ) {
        if (loop != this.loop) {
            this.loop = loop
            output.setLooping(loop)
        }
        val unmuting = this.muted && !muted
        if (muted != this.muted) {
            this.muted = muted
            if (muted) setLoading(null)
            // Muting cleared the spinner but not the fetch: if the wanted track is still on its way
            // (not audible yet), unmuting shows it loading again
            if (unmuting && activeId != null && activeId == desiredId && settledId != activeId) {
                setLoading(activeId)
            }
            output.setMuted(muted)
        }

        // Turning the preference off is a deliberate action, not a scroll artifact, so it skips
        // the settle delay. Still fades rather than cutting: an abrupt stop is startling.
        if (!enabled) {
            if (desiredId != null || activeId != null) {
                settleJob?.cancel()
                desiredId = null
                activeId = null
                deferred = false
                setLoading(null)
                output.fadeOutAndRelease()
            }
            return
        }

        // Unmuting is the one time the same cue has to act again: its start was held back
        if (track?.id == desiredId && !(unmuting && deferred)) return

        desiredId = track?.id
        // A track that is no longer wanted stops counting as loading at once, not after the settle.
        if (loadingId != desiredId) setLoading(null)
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(SETTLE_MS)
            if (desiredId != track?.id) return@launch
            if (track == null) {
                activeId = null
                deferred = false
                setLoading(null)
                output.fadeOutAndRelease()
            } else if (this@BgmCueMachine.muted) {
                // Leaving a track for another while muted drops the held one and owes the new one
                if (activeId != track.id) {
                    activeId = null
                    deferred = true
                    setLoading(null)
                    output.fadeOutAndRelease()
                }
            } else {
                deferred = false
                commit(track, source)
            }
        }
    }

    private suspend fun commit(track: AudioTrack, source: HttpSource?) {
        if (activeId == track.id) {
            // Back on a track that was left mid-load: it is still preparing, so loading resumes
            if (settledId != track.id) setLoading(track.id)
            return
        }
        output.fadeOutAndRelease()
        activeId = track.id
        settledId = null
        setLoading(track.id)
        // Tracks whether this call actually handed the track off to the player, so every other
        // exit - cancellation, being superseded, start() failing - clears activeId instead of
        // leaving it pointed at a track that will never play and can never be retried.
        var started = false
        try {
            val item = output.load(track, source) ?: return
            if (desiredId != track.id) return
            if (muted) {
                deferred = true
                return
            }
            started = output.start(item, track.id, loop)
        } finally {
            if (!started && activeId == track.id) activeId = null
            // Still wanted but not playing (muted meanwhile, no audio focus, fetch failed): owed
            // again when unmuted, since the same cue is otherwise never acted on twice
            if (!started && desiredId == track.id) deferred = true
            if (!started) clearLoading(track.id)
        }
    }

    /** Immediate teardown of decision state, for audio-focus loss and reader exit. */
    fun reset() {
        settleJob?.cancel()
        settleJob = null
        desiredId = null
        activeId = null
        deferred = false
        setLoading(null)
    }

    /** The player reached a state where [id] is audible, or never will be: loading is over. */
    fun onTrackSettled(id: String) {
        if (activeId == id) settledId = id
        clearLoading(id)
    }

    private fun clearLoading(id: String) {
        if (loadingId == id) setLoading(null)
    }

    private fun setLoading(id: String?) {
        loadingId = id
        mutableLoading.value = id != null
    }

    /** The active track finished on its own (not looping); lets it be retried if revisited. */
    fun onPlaybackEnded(id: String) {
        if (activeId == id) activeId = null
        clearLoading(id)
    }
}
