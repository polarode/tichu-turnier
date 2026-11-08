package tichu.infra.events

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Wire payload of a live event: a thin signal with raw identifiers only (ADR 0007). */
data class TournamentEventPayload(
    val type: String,
    val tournamentId: String,
    val roundId: String?,
    val tableId: Int?,
)

/**
 * In-memory SSE adapter for [TournamentEvents]. Events published inside a transaction
 * are sent only after it commits; a failing stream is dropped and never fails the caller.
 */
class SseTournamentEventBroadcaster(
    private val streamTimeout: Duration,
    private val emitterFactory: (Long) -> SseEmitter = ::SseEmitter,
) : TournamentEvents {

    private val log = LoggerFactory.getLogger(SseTournamentEventBroadcaster::class.java)
    private val streams = ConcurrentHashMap<TournamentId, MutableSet<SseEmitter>>()
    private val filters = ConcurrentHashMap<SseEmitter, Set<TournamentEventType>>()
    private val eventIds = AtomicLong()

    fun subscribe(
        tournamentId: TournamentId,
        types: Set<TournamentEventType> = TournamentEventType.entries.toSet(),
    ): SseEmitter {
        val emitter = emitterFactory(streamTimeout.toMillis())
        filters[emitter] = types
        streams.compute(tournamentId) { _, set -> (set ?: ConcurrentHashMap.newKeySet()).apply { add(emitter) } }
        emitter.onCompletion { release(tournamentId, emitter) }
        emitter.onTimeout {
            release(tournamentId, emitter)
            emitter.complete()
        }
        emitter.onError {
            release(tournamentId, emitter)
            emitter.complete()
        }
        // The response only starts with its first bytes; send them now so the browser sees the stream open at once.
        send(tournamentId, emitter, heartbeat())
        return emitter
    }

    override fun publish(event: TournamentEvent) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = deliver(event)
                },
            )
        } else {
            deliver(event)
        }
    }

    fun sendHeartbeats() {
        streams.forEach { (tournamentId, emitters) ->
            emitters.toList().forEach { send(tournamentId, it, heartbeat()) }
        }
    }

    fun openStreamCount(tournamentId: TournamentId): Int = streams[tournamentId]?.size ?: 0

    fun openStreamCount(): Int = streams.values.sumOf { it.size }

    private fun heartbeat(): SseEmitter.SseEventBuilder = SseEmitter.event().comment("heartbeat")

    private fun deliver(event: TournamentEvent) {
        try {
            val payload = TournamentEventPayload(
                type = event.type.name,
                tournamentId = event.tournamentId.value,
                roundId = event.roundId?.value,
                tableId = event.tableId?.value,
            )
            val emitters = streams[event.tournamentId]?.toList() ?: return
            emitters.filter { event.type in filters[it].orEmpty() }.forEach {
                send(
                    event.tournamentId,
                    it,
                    SseEmitter.event()
                        .id(eventIds.incrementAndGet().toString())
                        .name(event.type.name)
                        .data(payload, MediaType.APPLICATION_JSON),
                )
            }
        } catch (e: RuntimeException) {
            log.debug("Failed to deliver live event {}", event, e)
        }
    }

    private fun send(tournamentId: TournamentId, emitter: SseEmitter, builder: SseEmitter.SseEventBuilder) {
        try {
            emitter.send(builder)
        } catch (e: IOException) {
            drop(tournamentId, emitter, e)
        } catch (e: IllegalStateException) {
            drop(tournamentId, emitter, e)
        }
    }

    private fun drop(tournamentId: TournamentId, emitter: SseEmitter, cause: Exception) {
        log.debug("Dropping live stream for tournament {}", tournamentId.value, cause)
        release(tournamentId, emitter)
        try {
            emitter.completeWithError(cause)
        } catch (_: RuntimeException) {
            // already completed
        }
    }

    private fun release(tournamentId: TournamentId, emitter: SseEmitter) {
        filters.remove(emitter)
        streams.computeIfPresent(tournamentId) { _, set ->
            set.remove(emitter)
            if (set.isEmpty()) null else set
        }
    }
}
