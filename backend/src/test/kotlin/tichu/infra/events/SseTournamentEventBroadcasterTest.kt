package tichu.infra.events

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.http.MediaType
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionSynchronizationUtils
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tichu.domain.RoundId
import tichu.domain.TableId
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentId
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SseTournamentEventBroadcasterTest {

    private class RecordingEmitter(timeout: Long, var failure: Exception? = null) : SseEmitter(timeout) {
        val sent = mutableListOf<String>()
        val events get() = sent.filterNot { it.contains("heartbeat") }
        val payloads = mutableListOf<Any?>()
        var completion: Runnable? = null
        var timeoutCallback: Runnable? = null
        var errorCallback: java.util.function.Consumer<Throwable>? = null

        override fun send(builder: SseEventBuilder) {
            failure?.let { throw it }
            val parts = builder.build()
            sent += parts.joinToString("") { it.data.toString() }
            payloads.addAll(parts.filter { it.mediaType == MediaType.APPLICATION_JSON }.map { it.data })
        }

        override fun onCompletion(callback: Runnable) {
            completion = callback
            super.onCompletion(callback)
        }

        override fun onTimeout(callback: Runnable) {
            timeoutCallback = callback
            super.onTimeout(callback)
        }

        override fun onError(callback: java.util.function.Consumer<Throwable>) {
            errorCallback = callback
            super.onError(callback)
        }
    }

    private val created = mutableListOf<RecordingEmitter>()
    private val broadcaster = SseTournamentEventBroadcaster(Duration.ofMinutes(5)) { timeout ->
        RecordingEmitter(timeout).also { created += it }
    }
    private val t1 = TournamentId("t1")
    private val t2 = TournamentId("t2")
    private val event = TournamentEvent(TournamentEventType.GAME_RECORDED, t1, RoundId("r1"), TableId(3))

    @Test
    fun `publish reaches every stream of that tournament only`() {
        broadcaster.subscribe(t1)
        broadcaster.subscribe(t1)
        broadcaster.subscribe(t2)

        broadcaster.publish(event)

        assertEquals(1, created[0].events.size)
        assertEquals(1, created[1].events.size)
        assertEquals(0, created[2].events.size)
        assertTrue(created[0].events[0].contains("tournamentId=t1"))
        assertTrue(created[0].events[0].contains("tableId=3"))
    }

    @ParameterizedTest
    @EnumSource(TournamentEventType::class)
    fun `every event type is sent as a thin payload of exactly the ids`(type: TournamentEventType) {
        broadcaster.subscribe(t1)

        broadcaster.publish(TournamentEvent(type, t1, RoundId("r1"), TableId(3)))

        val mapper = ObjectMapper()
        val json = mapper.readTree(mapper.writeValueAsString(created[0].payloads.single()))
        // A thin signal: exactly these ids, no scores, standings or other results (ADR 0007).
        assertEquals(setOf("type", "tournamentId", "roundId", "tableId"), json.propertyNames().toSet())
    }

    @Test
    fun `publish inside a transaction waits for commit`() {
        broadcaster.subscribe(t1)
        TransactionSynchronizationManager.initSynchronization()
        try {
            broadcaster.publish(event)
            assertEquals(0, created[0].events.size)
            TransactionSynchronizationUtils.triggerAfterCommit()
            assertEquals(1, created[0].events.size)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `publish inside a rolled back transaction sends nothing`() {
        broadcaster.subscribe(t1)
        TransactionSynchronizationManager.initSynchronization()
        try {
            broadcaster.publish(event)
            val syncs = TransactionSynchronizationManager.getSynchronizations()
            TransactionSynchronizationUtils.invokeAfterCompletion(syncs, TransactionSynchronization.STATUS_ROLLED_BACK)
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
        assertEquals(0, created[0].events.size)
    }

    @Test
    fun `streams are released on completion, timeout and error`() {
        broadcaster.subscribe(t1)
        broadcaster.subscribe(t1)
        broadcaster.subscribe(t1)
        assertEquals(3, broadcaster.openStreamCount(t1))

        created[0].completion!!.run()
        created[1].timeoutCallback!!.run()
        created[2].errorCallback!!.accept(RuntimeException("boom"))

        assertEquals(0, broadcaster.openStreamCount(t1))
        assertEquals(0, broadcaster.openStreamCount())
        broadcaster.publish(event)
        created.forEach { assertEquals(0, it.events.size) }
    }

    @Test
    fun `a stream whose send throws is released and publish does not propagate`() {
        broadcaster.subscribe(t1)
        broadcaster.subscribe(t1)
        created[0].failure = IOException("broken pipe")

        broadcaster.publish(event)

        assertEquals(1, broadcaster.openStreamCount(t1))
        assertEquals(1, created[1].events.size)
        broadcaster.publish(event)
        assertEquals(2, created[1].events.size)
    }

    @Test
    fun `a failing send inside afterCommit does not propagate`() {
        broadcaster.subscribe(t1)
        created[0].failure = IllegalStateException("closed")
        TransactionSynchronizationManager.initSynchronization()
        try {
            broadcaster.publish(event)
            TransactionSynchronizationUtils.triggerAfterCommit()
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
        assertEquals(0, broadcaster.openStreamCount())
    }

    @Test
    fun `a type-filtered stream receives only the allowed lifecycle events`() {
        val lifecycle = setOf(
            TournamentEventType.ROUND_STARTED,
            TournamentEventType.TOURNAMENT_FINISHED,
            TournamentEventType.RESULTS_PUBLISHED,
        )
        broadcaster.subscribe(t1, lifecycle)

        listOf(
            TournamentEventType.GAME_RECORDED,
            TournamentEventType.TABLE_CONFIRMED,
            TournamentEventType.CONFIRMATION_RETRACTED,
            TournamentEventType.ROUND_STARTED,
            TournamentEventType.TOURNAMENT_FINISHED,
            TournamentEventType.RESULTS_PUBLISHED,
        ).forEach { broadcaster.publish(TournamentEvent(it, t1)) }

        val received = created[0].payloads.map { (it as TournamentEventPayload).type }
        assertEquals(
            listOf("ROUND_STARTED", "TOURNAMENT_FINISHED", "RESULTS_PUBLISHED"),
            received,
        )
    }

    @Test
    fun `a new stream gets a heartbeat right away`() {
        broadcaster.subscribe(t1)

        assertEquals(1, created[0].sent.size)
        assertTrue(created[0].sent[0].contains("heartbeat"))
    }

    @Test
    fun `heartbeats go to every open stream as a comment`() {
        broadcaster.subscribe(t1)
        broadcaster.subscribe(t2)

        broadcaster.sendHeartbeats()

        // One heartbeat on subscribe, one from the scheduled sweep.
        created.forEach {
            assertEquals(2, it.sent.size)
            assertTrue(it.sent.all { sent -> sent.contains("heartbeat") })
        }
    }
}
