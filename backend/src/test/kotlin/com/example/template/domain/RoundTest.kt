package com.example.template.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Unit tests for the seating lookups on [Round] and [Table]; pure domain, no Spring. */
class RoundTest {

    private val teamA = TeamId("a")
    private val teamB = TeamId("b")
    private val teamC = TeamId("c")
    private val teamD = TeamId("d")

    private val table1 = Table(TableId(1), listOf(teamA, teamB))
    private val table2 = Table(TableId(2), listOf(teamC, teamD))

    private val round = Round(
        id = RoundId("r-1"),
        tournamentId = TournamentId("t-1"),
        number = 1,
        pairing = Pairing(listOf(table1, table2)),
    )

    @Test
    fun `table resolves by table number and is null for an unknown number`() {
        assertEquals(table2, round.table(2))
        assertNull(round.table(9))
    }

    @Test
    fun `tableOf finds the table seating a team`() {
        assertEquals(table1, round.tableOf(teamA))
        assertEquals(table2, round.tableOf(teamD))
        assertNull(round.tableOf(TeamId("bye")))
    }
}
