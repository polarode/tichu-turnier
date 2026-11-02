package com.example.template.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Unit tests for [Table]: who sits where vs. who plays which game, at normal and trio tables. */
class TableTest {

    private val teamA = TeamId("a")
    private val teamB = TeamId("b")
    private val teamC = TeamId("c")

    private val normal = Table(TableId(1), listOf(teamA, teamB))
    private val trio = Table(TableId(2), listOf(teamA, teamB, teamC))

    @Test
    fun `a normal table seats two teams who play all four games`() {
        assertFalse(normal.isTrio)
        assertEquals(4, normal.gameCount)
        assertTrue(normal.seats(teamA))
        assertTrue(normal.seats(teamB))
        assertFalse(normal.seats(teamC))
        (1..4).forEach { assertEquals(listOf(teamA, teamB), normal.playingTeams(it)) }
    }

    @Test
    fun `a trio table seats three teams and plays six games in the fixed schedule`() {
        assertTrue(trio.isTrio)
        assertEquals(6, trio.gameCount)
        assertTrue(listOf(teamA, teamB, teamC).all { trio.seats(it) })
        assertEquals(
            listOf(
                listOf(teamA, teamB),
                listOf(teamA, teamC),
                listOf(teamB, teamC),
                listOf(teamA, teamB),
                listOf(teamA, teamC),
                listOf(teamB, teamC),
            ),
            (1..6).map { trio.playingTeams(it) },
        )
    }

    @Test
    fun `a sitting-out team is still seated at the trio table`() {
        assertFalse(teamC in trio.playingTeams(1))
        assertTrue(trio.seats(teamC))
    }

    @Test
    fun `trio schedule meets every pair twice`() {
        val counts = (1..6).map { trio.playingTeams(it).toSet() }.groupingBy { it }.eachCount()

        assertEquals(
            mapOf(setOf(teamA, teamB) to 2, setOf(teamA, teamC) to 2, setOf(teamB, teamC) to 2),
            counts,
        )
    }

    @Test
    fun `every trio team plays four games and sits out two, never twice in a row`() {
        for (team in trio.seatedTeams) {
            val sitsOut = (1..6).map { team !in trio.playingTeams(it) }
            assertEquals(4, sitsOut.count { !it }, "$team plays")
            assertEquals(2, sitsOut.count { it }, "$team sits out")
            assertTrue(sitsOut.zipWithNext().none { (a, b) -> a && b }, "$team sits out twice in a row")
        }
    }

    @Test
    fun `a game number outside the table's games is rejected`() {
        assertFailsWith<IllegalArgumentException> { normal.playingTeams(5) }
        assertFailsWith<IllegalArgumentException> { trio.playingTeams(0) }
        assertFailsWith<IllegalArgumentException> { trio.playingTeams(7) }
    }

    @Test
    fun `a table seats two or three distinct teams`() {
        assertFailsWith<IllegalArgumentException> { Table(TableId(1), listOf(teamA)) }
        assertFailsWith<IllegalArgumentException> { Table(TableId(1), listOf(teamA, teamB, teamC, TeamId("d"))) }
        assertFailsWith<IllegalArgumentException> { Table(TableId(1), listOf(teamA, teamA)) }
    }
}
