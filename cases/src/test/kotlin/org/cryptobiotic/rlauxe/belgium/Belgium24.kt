package org.cryptobiotic.rlauxe.belgium

import org.cryptobiotic.rlauxe.dhondt.DhondtParty
import org.cryptobiotic.rlauxe.dhondt.makeDhondtElection
import org.cryptobiotic.rlauxe.util.df
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class Belgium24 {

    @Test
    fun testBelgiumA() {
        val name = "92094"
        val contest = BelgiumContest(name)
        contest.showSheet(contest.A, name)
        println()

        val info = contest.readA()
        println(info.show())
        info.parties.forEach {
            assertEquals(it.total, it.counts.sum())
        }
    }

    @Test
    fun testBelgiumB() {
        val name = "44021"
        val contest = BelgiumContest(name)
        contest.showSheet(contest.B, "Seat_C$name")
        println()

        val info = contest.readB()
        println(info.show())

        val allTotals = info.parties.sumOf{ it.total}.toDouble()
        info.parties.forEach {
            println("party ${it.num} pct=${df(it.total / allTotals)}")
        }
    }

    @Test
    fun testBelgiumAB() {
        val contest = BelgiumContest("44021")
        val infoA = contest.readA()
        val partyAmap = infoA.parties.associateBy { it.num }
        val infoB = contest.readB()

        infoB.parties.forEach { it ->
            val partyB = partyAmap[it.num]!!
            assertEquals(it.total, partyB.total)
        }
    }

    @Test
    fun testBelgiumContest() {
        testBelgiumContest("44021")
    }

    @Test
    fun testAllContests() {
        val contests = listOf("92094", "81001", "71022", "62063", "53053", "31005", "25072", "24062", "21004", "11002", )
        val errors = listOf("44021", "31005", "25072", "24062", "21004", "11002", )
        contests.forEach { testBelgiumContest(it) }
    }

    fun testBelgiumContest(name: String) {
        println("======================================================")
        println("Contest $name")
        val contest = BelgiumContest(name)
        val infoB = contest.readB()
        println(infoB.show())

        val dhondtParties = infoB.parties.map { DhondtParty(it.num, it.total) }
        val result = makeDhondtElection(dhondtParties, infoB.winners.size, .05)
        result.winners.sortedBy { it.winningSeat }.forEach {
            println(it)
        }
        println()

        result.winners.forEachIndexed { idx, winner ->
            val infoWinner = infoB.winners[idx]
            println("$infoWinner")
            println("$winner")
            println()
        }

        result.winners.forEachIndexed { idx, winner ->
            val infoWinner = infoB.winners[idx]
            assertEquals(winner.partyId, infoWinner.partyId)
            assertEquals(winner.seatno, infoWinner.seatno)
            assertEquals(winner.winningSeat, infoWinner.winningSeat)
            assertTrue(abs(winner.avg - infoWinner.avg) <= 1.0)
        }
    }
}