package org.cryptobiotic.rlauxe.belgium

import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.makeProtoContest
import org.cryptobiotic.rlauxe.util.df
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class TestBelgiumElectionFromSpreadsheet {
    val Afile = "$testdataDir/cases/belgium/2024/CK_K_2024.xlsx"
    val Bfile = "$testdataDir/cases/belgium/2024/CK_CEListes_2024.xlsx"

    @Test
    fun testBelgiumA() {
        val sheetName = "92094"
        val contest = BelgiumElectionFromSpreadsheet(Afile, Bfile, sheetName)
        contest.showSheet(contest.A, sheetName)
        println()

        val info = contest.readA()
        println(info.show())
        info.parties.forEach {
            assertEquals(it.total, it.counts.sum())
        }
    }

    @Test
    fun testBelgiumB() {
        val sheetName = "44021"
        val contest = BelgiumElectionFromSpreadsheet(Afile, Bfile, sheetName)
        contest.showSheet(contest.B, "Seat_C$sheetName")
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
        val sheetName = "44021"
        val contest = BelgiumElectionFromSpreadsheet(Afile, Bfile, sheetName)
        val infoA = contest.readA()
        val infoB = contest.readB()
        val partyBmap = infoB.parties.associateBy { it.num }

        // remove the total infoA party
        infoA.parties.filter{ it.num != 0}.forEach { partyA ->
            val partyB = partyBmap[partyA.num]
            if (partyB == null)
                println("partyB is missing contest ${partyA.num}")
            else
                assertEquals(partyA.total, partyB.total)
        }
    }

    @Test
    fun testBelgiumContest() {
        testBelgiumContest("92094")
    }

    @Test
    fun testAllContests() {
        val contests = listOf("92094", "81001", "71022", "62063", "53053", "31005", "25072", "24062", "21004", "11002", )
        val errors = listOf("44021",  )
        contests.forEach { testBelgiumContest(it) }
    }

    fun testBelgiumContest(sheetName: String) {
        println("======================================================")
        println("Contest $sheetName")
        val contest = BelgiumElectionFromSpreadsheet(Afile, Bfile, sheetName)
        val infoA = contest.readA()
        println(infoA.show())
        val infoB = contest.readB()
        println(infoB.show())

        // use infoA parties, because they are complete
        val dhondtParties = infoA.parties.map { DhondtCandidate(it.name, it.num, it.total) }
        val dcontest = makeProtoContest(infoB.electionName, 1, dhondtParties, infoB.winners.size, 0, .05)
        println("Calculated Winners")
        dcontest.winners.sortedBy { it.winningSeat }.forEach {
            println("  ${it}")
        }
        println()

        val contestd = dcontest.createContest()
        println(contestd.show())

        dcontest.winners.forEachIndexed { idx, winner ->
            val infoWinner = infoB.winners[idx]
            val star = if (testEquals(infoWinner, winner)) "" else "*"
            println("$star$infoWinner")
            println("$star$winner")
            println()
        }

        dcontest.winners.forEachIndexed { idx, winner ->
            val infoWinner = infoB.winners[idx]
            assertEquals(winner.candidate, infoWinner.candidate)
            assertEquals(winner.divisor, infoWinner.divisor)
            assertEquals(winner.winningSeat, infoWinner.winningSeat)
            assertTrue(abs(winner.score - infoWinner.score) <= 1.0)
        }

        testCvrs(dcontest, contestd)
    }
}
