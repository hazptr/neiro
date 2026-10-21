package neiro.bgm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/** A cached track starts in well under this; showing the spinner sooner would only flash it. */
const val LOADING_SHOW_DELAY_MS = 300L

/**
 * Turns the raw "a track is loading" signal into what the spinner shows: it appears only once
 * loading has lasted [showAfterMs] and goes at once when loading ends. There is no time limit:
 * every load ends in a signal (ready, failure, cancellation), bounded by the network timeouts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<Boolean>.spinnerVisible(showAfterMs: Long = LOADING_SHOW_DELAY_MS): Flow<Boolean> =
    distinctUntilChanged()
        .transformLatest { loading ->
            emit(false)
            if (loading) {
                delay(showAfterMs)
                emit(true)
            }
        }
        .distinctUntilChanged()
