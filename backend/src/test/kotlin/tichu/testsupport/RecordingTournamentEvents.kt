package tichu.testsupport

import tichu.domain.TournamentEvent
import tichu.domain.TournamentEvents

/** Test double that records every published event, in order. */
class RecordingTournamentEvents : TournamentEvents {
    val published = mutableListOf<TournamentEvent>()

    override fun publish(event: TournamentEvent) {
        published += event
    }
}
