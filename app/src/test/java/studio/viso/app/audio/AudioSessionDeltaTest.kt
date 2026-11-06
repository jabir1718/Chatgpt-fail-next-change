package studio.viso.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSessionDeltaTest {
    @Test
    fun unchangedSessionsDoNotRestartCapture() {
        assertEquals(
            AudioSessionDelta(emptySet(), emptySet()),
            audioSessionDelta(setOf(4, 8), setOf(8, 4))
        )
    }

    @Test
    fun onlyNewAndRemovedSessionsAreReconciled() {
        assertEquals(
            AudioSessionDelta(removed = setOf(3), added = setOf(9)),
            audioSessionDelta(current = setOf(3, 5), desired = setOf(5, 9))
        )
    }

    @Test
    fun emptyDesiredSetRemovesEveryActiveSession() {
        assertEquals(
            AudioSessionDelta(removed = setOf(1, 2), added = emptySet()),
            audioSessionDelta(current = setOf(1, 2), desired = emptySet())
        )
    }
}
