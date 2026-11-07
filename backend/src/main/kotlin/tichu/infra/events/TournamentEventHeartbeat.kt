package tichu.infra.events

import org.springframework.scheduling.annotation.Scheduled

/** Keeps idle streams alive through proxies by pinging every open stream. */
class TournamentEventHeartbeat(private val broadcaster: SseTournamentEventBroadcaster) {

    @Scheduled(fixedRateString = "\${app.events.heartbeat-interval:PT20S}")
    fun beat() = broadcaster.sendHeartbeats()
}
