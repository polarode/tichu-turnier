package tichu.infra.events

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Wiring for the live-event SSE broadcaster (ADR 0007). */
@Configuration
class EventsConfiguration {

    @Bean
    fun tournamentEventBroadcaster(
        @Value("\${app.events.stream-timeout:PT30M}") streamTimeout: Duration,
    ): SseTournamentEventBroadcaster = SseTournamentEventBroadcaster(streamTimeout)

    @Bean
    fun tournamentEventHeartbeat(broadcaster: SseTournamentEventBroadcaster): TournamentEventHeartbeat =
        TournamentEventHeartbeat(broadcaster)
}
