package neiro.bgm

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BgmLoadingIndicatorTest {

    @Test
    fun `spinner stays hidden for a load shorter than the delay`() = runTest {
        val loading = MutableStateFlow(false)
        val seen = mutableListOf<Boolean>()
        loading.spinnerVisible(300).onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()

        loading.value = true
        advanceTimeBy(299)
        loading.value = false
        advanceTimeBy(1000)

        seen.contains(true) shouldBe false
    }

    @Test
    fun `spinner shows after the delay and hides when loading ends`() = runTest {
        val loading = MutableStateFlow(false)
        val seen = mutableListOf<Boolean>()
        loading.spinnerVisible(300).onEach { seen += it }.launchIn(backgroundScope)
        runCurrent()

        loading.value = true
        advanceTimeBy(301)
        seen.last() shouldBe true
        loading.value = false
        runCurrent()
        seen.last() shouldBe false
    }

    @Test
    fun `spinner keeps showing through a slow load`() = runTest {
        val loading = MutableStateFlow(true)
        val seen = mutableListOf<Boolean>()
        loading.spinnerVisible(300).onEach { seen += it }.launchIn(backgroundScope)

        advanceTimeBy(130_000)
        seen.last() shouldBe true
        loading.value = false
        runCurrent()
        seen.last() shouldBe false
    }
}
