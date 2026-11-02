package com.example.template.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Unit tests for the derived Table Status (ADR 0006) and round completion; pure domain, no Spring. */
class TableConfirmationsTest {

    private val teamA = TeamId("a")
    private val teamB = TeamId("b")
    private val teamC = TeamId("c")
    private val teamD = TeamId("d")

    private val normal = Table(TableId(1), listOf(teamA, teamB))
    private val trio = Table(TableId(2), listOf(teamB, teamC, teamD))

    private fun statusOf(table: Table, confirmed: Set<TeamId> = emptySet()) =
        TableConfirmations(table, confirmed).status

    @Test
    fun `a normal table is playing, confirming, then completed with 0, 1 and 2 confirmations`() {
        assertEquals(TableStatus.PLAYING, statusOf(normal))
        assertEquals(TableStatus.CONFIRMING, statusOf(normal, setOf(teamB)))
        assertEquals(TableStatus.COMPLETED, statusOf(normal, setOf(teamA, teamB)))
    }

    @Test
    fun `a trio table completes only once all three seated teams confirmed`() {
        assertEquals(TableStatus.PLAYING, statusOf(trio))
        assertEquals(TableStatus.CONFIRMING, statusOf(trio, setOf(teamC)))
        assertEquals(TableStatus.CONFIRMING, statusOf(trio, setOf(teamC, teamB)))
        assertEquals(TableStatus.COMPLETED, statusOf(trio, setOf(teamC, teamB, teamD)))
    }

    @Test
    fun `confirmed team ids are listed in seat order`() {
        assertEquals(listOf(teamB, teamD), TableConfirmations(trio, setOf(teamD, teamB)).confirmedInSeatOrder)
    }

    @Test
    fun `a round is complete only when every table is completed`() {
        val round = Round(RoundId("r-1"), TournamentId("t-1"), 1, Pairing(listOf(normal, trio)))

        assertFalse(round.isComplete(emptyMap()))
        assertFalse(round.isComplete(mapOf(normal.id to setOf(teamA, teamB), trio.id to setOf(teamB, teamC))))
        assertFalse(round.isComplete(mapOf(normal.id to setOf(teamA, teamB))))
        assertTrue(round.isComplete(mapOf(normal.id to setOf(teamA, teamB), trio.id to setOf(teamB, teamC, teamD))))
    }
}
