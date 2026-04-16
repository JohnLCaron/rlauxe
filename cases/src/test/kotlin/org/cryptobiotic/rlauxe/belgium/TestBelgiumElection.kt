package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.dhondt.DhondtBuilder
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.DhondtScore
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Welford
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBelgiumElection {

    @Test
    fun testReadBelgiumElection() {
        val filename = belgianElectionMap["Namur"]!!
        val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumElectionJson(filename)
        val belgiumElection = if (result .isOk) result.unwrap()
        else throw RuntimeException("Cannot read belgiumElection from ${filename} err = $result")
        println(belgiumElection)
    }

    @Test
    fun testBelgiumContest() {
        testBelgiumContest("Namur")
    }

    fun testBelgiumContest(electionName: String) {
        println("======================================================")
        println("ElectionName $electionName")
        val filename = belgianElectionMap[electionName]!!
        val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumElectionJson(filename)
        val belgiumElection = if (result .isOk) result.unwrap()
        else throw RuntimeException("Cannot read belgiumElection from ${filename} err = $result")
        println(belgiumElection)

        // use infoA parties, because they are complete
        val dhondtParties = belgiumElection.ElectionLists.mapIndexed { idx, it ->  DhondtCandidate(it.PartyLabel, idx+1, it.NrOfVotes) }
        val nwinners = belgiumElection.ElectionLists.sumOf { it.NrOfSeats }
        val totalVotes = belgiumElection.NrOfValidVotes + belgiumElection.NrOfBlankVotes

        val builder = DhondtBuilder(electionName, 1, dhondtParties, nwinners, totalVotes, 0,.05)
        println("Calculated Winners")
        builder.winnerScores.sortedBy { it.winningSeat }.forEach {
            println("  ${it}")
        }
        println()

        val contestd = builder.build()
        println(contestd)
        println(contestd.show())

        testCvrs(contestd)
    }
}

fun testCvrs(contestd: DHondtContest) {

    println("testCvrs2 ----------------------------------------")
    val cvrs = contestd.createSimulatedCvrs()
    assertEquals(contestd.Nc, cvrs.size)

    contestd.assorters.forEach { assorter ->
        val welford = Welford()
        cvrs.forEach { cvr ->
            welford.update(assorter.assort(cvr, usePhantoms = false))
        }
        println("${assorter.desc()} cvr.mean = ${welford.mean}")
        // assertEquals(welford.mean, havg.mean, doublePrecision)
    }
}

fun testEquals(score1: DhondtScore, score2: DhondtScore): Boolean {
    return (score1.candidate == score2.candidate) &&
            (score1.divisor == score2.divisor) &&
            (score1.winningSeat == score2.winningSeat) &&
            (abs(score1.score - score2.score) <= 1.0)
}

