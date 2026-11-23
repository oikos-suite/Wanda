package com.wander.android

import com.wander.android.data.replay.ReplayServerTooOld
import com.wander.android.data.sources.agro.AgroRejected
import com.wander.android.data.sources.agro.AgroReplayApi
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a failed recap means "update Agro" (oikos-suite/Agro#43), and when it does not.
 *
 * An Agro from before Replay refuses the recap query outright, because it has never heard of
 * `utcOffsetMinutes` or `topHourLocal`; that refusal is what used to surface as "That year could
 * not be read" with nothing to say why.
 */
class ReplayServerTooOldTest {
    private val oldServerRefusal =
        AgroRejected("Unknown argument \"utcOffsetMinutes\" on field \"Query.agroWrapped\".")

    @Test
    fun aRefusalFromAServerThatDoesNotAdvertiseReplayIsAnOldServer() {
        val explained = ReplayServerTooOld.explain(oldServerRefusal, serverAdvertisesReplay = false)

        assertTrue(explained is ReplayServerTooOld)
        assertSame(oldServerRefusal, explained.cause)
    }

    @Test
    fun aRefusalFromAServerThatHasReplayIsShownAsItIs() {
        assertSame(oldServerRefusal, ReplayServerTooOld.explain(oldServerRefusal, serverAdvertisesReplay = true))
    }

    /** Offline, or a timeout: no answer at all says nothing about how old the server is. */
    @Test
    fun aFailureThatIsNotARefusalIsNeverBlamedOnTheServersAge() {
        val dropped = IOException("timeout")

        assertSame(dropped, ReplayServerTooOld.explain(dropped, serverAdvertisesReplay = false))
    }

    /** The refusal still reads the way it always did, so existing logs and checks keep matching. */
    @Test
    fun aRefusalKeepsTheMessageItAlwaysHad() {
        assertEquals(
            "Agro rejected the request: Unknown argument \"utcOffsetMinutes\" on field \"Query.agroWrapped\".",
            oldServerRefusal.message
        )
    }

    /** The name the server advertises, which `server_capabilities` in Agro must keep. */
    @Test
    fun theCapabilityNameMatchesWhatAgroAdvertises() {
        assertEquals("replay.wrapped", AgroReplayApi.CAPABILITY)
    }
}
