package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.source.model.AudioTrack
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.File

private const val SETTLE_MS = 250L

class BgmCueMachineTest {

    private val trackA = AudioTrack("a", "https://example.test/a")
    private val trackB = AudioTrack("b", "https://example.test/b")

    @Test
    fun `a cue that stays current for the settle window commits exactly once`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)

        output.startCalls shouldBe listOf("a")
    }

    @Test
    fun `a cue superseded before the settle window never plays at all`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS / 2)
        machine.onPageChanged(trackB, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)

        output.loadCalls shouldBe listOf("b")
        output.startCalls shouldBe listOf("b")
    }

    @Test
    fun `scrolling across five cues quickly commits only the last`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)
        val tracks = listOf("a", "b", "c", "d", "e").map { AudioTrack(it, "https://example.test/$it") }

        tracks.forEach { track ->
            machine.onPageChanged(track, source = null, enabled = true, loop = true)
            advanceTimeBy(SETTLE_MS / 2)
        }
        advanceTimeBy(SETTLE_MS)

        output.startCalls shouldBe listOf("e")
    }

    @Test
    fun `a cue that leaves and returns while its load is in flight can still play afterwards`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)
        val pendingLoad = CompletableDeferred<File?>()
        output.pendingLoads["a"] = pendingLoad

        // Cue A settles and starts fetching, but the fetch never resolves on its own.
        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        runCurrent()
        output.loadCalls shouldBe listOf("a")

        // Scrolling away cancels the in-flight commit before its fetch ever completes.
        machine.onPageChanged(trackB, source = null, enabled = true, loop = true)
        runCurrent()

        // Scrolling straight back to A must not be blocked by a leftover activeId.
        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        runCurrent()
        output.loadCalls shouldBe listOf("a", "a")

        pendingLoad.complete(File("/a"))
        advanceUntilIdle()

        output.startCalls shouldBe listOf("a")
    }

    @Test
    fun `toggling enabled=false stops immediately without waiting for the settle`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        val fadeOutsBeforeDisable = output.fadeOutCalls

        machine.onPageChanged(trackA, source = null, enabled = false, loop = true)

        output.fadeOutCalls shouldBe fadeOutsBeforeDisable + 1
    }

    @Test
    fun `toggling enabled back on re-commits the current cue`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.onPageChanged(trackA, source = null, enabled = false, loop = true)

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)

        output.startCalls shouldBe listOf("a", "a")
    }

    @Test
    fun `the same cue reported repeatedly does not restart playback`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)

        output.startCalls shouldBe listOf("a")
    }

    @Test
    fun `a null cue stops`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        val fadeOutsBeforeNull = output.fadeOutCalls

        machine.onPageChanged(null, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)

        output.fadeOutCalls shouldBe fadeOutsBeforeNull + 1
    }

    @Test
    fun `loop preference is forwarded only when it actually changes`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)

        // Machine starts with loop = true, matching the default preference.
        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        machine.onPageChanged(trackA, source = null, enabled = true, loop = false)
        machine.onPageChanged(trackA, source = null, enabled = true, loop = false)

        output.loopCalls shouldBe listOf(false)
    }

    @Test
    fun `loading spans fetch and preparation and ends when the player reports ready`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)
        val gate = CompletableDeferred<File?>()
        output.pendingLoads["a"] = gate

        machine.loading.value shouldBe false
        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.loading.value shouldBe true
        gate.complete(File("/a"))
        runCurrent()
        machine.loading.value shouldBe true
        machine.onTrackSettled("a")
        machine.loading.value shouldBe false
    }

    @Test
    fun `a failed load or start clears loading`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)
        output.startResult = false

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.loading.value shouldBe false
    }

    @Test
    fun `leaving the cue, muting or resetting clears loading`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)
        output.pendingLoads["a"] = CompletableDeferred()

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.loading.value shouldBe true
        machine.onPageChanged(null, source = null, enabled = true, loop = true)
        machine.loading.value shouldBe false

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.loading.value shouldBe true
        machine.onPageChanged(trackA, source = null, enabled = false, loop = true)
        machine.loading.value shouldBe false

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.loading.value shouldBe true
        machine.reset()
        machine.loading.value shouldBe false
    }

    @Test
    fun `a second track takes over loading and a stale ready does not clear it`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)
        val gate = CompletableDeferred<File?>()
        output.pendingLoads["b"] = gate

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.onPageChanged(trackB, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.onTrackSettled("a")
        machine.loading.value shouldBe true
        gate.complete(File("/b"))
        runCurrent()
        machine.onTrackSettled("b")
        machine.loading.value shouldBe false
    }

    @Test
    fun `flicking away and back to a track still preparing resumes loading`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.loading.value shouldBe true // started, player still preparing
        machine.onPageChanged(trackB, source = null, enabled = true, loop = true)
        machine.loading.value shouldBe false
        advanceTimeBy(SETTLE_MS / 2)
        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.loading.value shouldBe true
        machine.onTrackSettled("a")
        machine.loading.value shouldBe false

        // Once audible, flicking away and back does not claim to be loading
        machine.onPageChanged(trackB, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS / 2)
        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS + 1)
        machine.loading.value shouldBe false
    }

    @Test
    fun `flinging past a short cue never starts loading`() = runTest {
        val output = FakeOutput()
        val machine = BgmCueMachine(this, output)

        machine.onPageChanged(trackA, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS - 1)
        machine.onPageChanged(null, source = null, enabled = true, loop = true)
        advanceTimeBy(SETTLE_MS * 2)
        machine.loading.value shouldBe false
        output.loadCalls.size shouldBe 0
    }

    /** Records every command the machine emits; fetches resolve immediately unless queued. */
    private class FakeOutput : BgmCueMachine.Output<File> {

        val loadCalls = mutableListOf<String>()
        val startCalls = mutableListOf<String>()
        val loopCalls = mutableListOf<Boolean>()
        val pendingLoads = mutableMapOf<String, CompletableDeferred<File?>>()
        var fadeOutCalls = 0
        var startResult = true

        override suspend fun load(track: AudioTrack, source: HttpSource?): File? {
            loadCalls += track.id
            return pendingLoads[track.id]?.await() ?: File("/${track.id}")
        }

        override suspend fun start(item: File, id: String, loop: Boolean): Boolean {
            startCalls += id
            return startResult
        }

        override fun setLooping(loop: Boolean) {
            loopCalls += loop
        }

        override fun fadeOutAndRelease() {
            fadeOutCalls++
        }
    }
}
