package com.wander.android.data.replay

import com.wander.android.data.sources.agro.AgroRejected
import java.io.IOException

/**
 * The paired Agro predates Replay, so it refused the recap query outright.
 *
 * Replay asks `agroWrapped` for `utcOffsetMinutes` and fields like `topHourLocal`, which an Agro
 * from before Replay has never heard of. GraphQL then rejects the whole query, and all the screen
 * could say was "That year could not be read", with nothing to tell a server that cannot from one
 * that failed this once (oikos-suite/Agro#43).
 */
internal class ReplayServerTooOld(cause: AgroRejected) :
    IOException("This Agro server predates Replay", cause) {

    companion object {
        /**
         * [cause] as the screen should explain it.
         *
         * Only a refusal counts — a server that answered and said no — and only from a server that
         * does not advertise Replay. A dropped connection is not a sign of age, and a server that
         * advertises Replay but refused has some other problem worth showing as it is. A server
         * not advertising Replay is not proof by itself either: Agro had Replay for a while before
         * it named it, so the name's absence explains a failure but never prevents a request.
         */
        fun explain(cause: Throwable, serverAdvertisesReplay: Boolean): Throwable =
            if (cause is AgroRejected && !serverAdvertisesReplay) ReplayServerTooOld(cause) else cause
    }
}
