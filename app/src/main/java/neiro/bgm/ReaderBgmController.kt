package neiro.bgm

import android.content.Context
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.ChapterBgmPlayer
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.system.logcat

/**
 * Background music for one reader session: which track the current page calls for, the session's
 * mute, and the player. Owned by the reader's view model, which only forwards page changes and
 * lifecycle events here.
 */
class ReaderBgmController(
    scope: CoroutineScope,
    context: Context,
    support: ChapterAudioSupport,
    private val readerPreferences: ReaderPreferences,
    private val source: () -> HttpSource?,
) {

    data class State(
        /** Whether the current chapter has background music the reader would play. */
        val available: Boolean = false,
        /** Silences background music for this reading session only. */
        val muted: Boolean = false,
    )

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    private val player = ChapterBgmPlayer(scope, context, support.bgmCache)

    /** Whether the loading spinner should show: music is loading and has been for a moment. */
    val loadingSpinner = player.loading.spinnerVisible().stateIn(scope, SharingStarted.Eagerly, false)

    /** Mirrored so the webtoon viewer's scroll listener can skip its audio work without reading a preference. */
    @Volatile
    var enabled = readerPreferences.bgmEnabled.get()
        private set

    /**
     * Last page reported, so flipping a preference or the mute can take effect where the reader
     * already is instead of waiting for the next page change.
     */
    private var lastPage: ReaderPage? = null
    private var loggedAudioForChapter: Long? = null

    init {
        readerPreferences.bgmEnabled.changes()
            .onEach {
                enabled = it
                lastPage?.let(::onPageChanged)
            }
            .launchIn(scope)
        readerPreferences.bgmLoop.changes()
            .drop(1)
            .onEach { lastPage?.let(::onPageChanged) }
            .launchIn(scope)
    }

    /**
     * Called with the page whose audio should currently be playing.
     *
     * Deliberately separate from the reader's page selection: reading progress tracks the
     * bottom-most visible page, but a source's audio cues are anchored to the image at the top of
     * the viewport - which is how the site itself triggers them. On a tall webtoon strip those are
     * over a page apart, and using the wrong one makes every cue fire early.
     */
    fun onPageChanged(page: ReaderPage) {
        lastPage = page
        logChapterAudio(page)
        val enabled = readerPreferences.bgmEnabled.get()
        val audio = page.chapter.pages?.firstNotNullOfOrNull { it.chapterAudio }
        mutableState.update { it.copy(available = enabled && audio != null) }
        // Muting goes through the same path as turning audio off, so a muted reader holds no
        // player and no audio focus, and unmuting starts the track that covers the current page.
        val playing = enabled && !state.value.muted
        val track = if (playing) audio?.trackAt(page.index) else null
        player.onPageChanged(track, source(), playing, readerPreferences.bgmLoop.get())
    }

    /** From the webtoon viewer's scroll listener, which reports the same top page many times over. */
    fun onTopPageChanged(page: ReaderPage) {
        if (page !== lastPage) onPageChanged(page)
    }

    fun toggleMuted() {
        mutableState.update { it.copy(muted = !it.muted) }
        lastPage?.let(::onPageChanged)
    }

    fun toggleLoop() {
        readerPreferences.bgmLoop.toggle()
    }

    fun pause() = player.pause()

    fun resume() = player.resume()

    fun stop() = player.stop()

    /** One line per chapter: without it, a chapter that simply has no cues is indistinguishable
     *  from a chapter whose audio failed to resolve. */
    private fun logChapterAudio(page: ReaderPage) {
        val chapterId = page.chapter.chapter.id
        if (chapterId == loggedAudioForChapter) return
        loggedAudioForChapter = chapterId
        val audio = page.chapter.pages?.firstNotNullOfOrNull { it.chapterAudio }
        logcat {
            if (audio == null) {
                "[BGM] no audio for ${page.chapter.chapter.name}"
            } else {
                "[BGM] ${page.chapter.chapter.name}: ${audio.tracks.size} tracks, ${audio.cues.size} cues"
            }
        }
    }
}
