package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.dhondt.ContestDHondt
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.DhondtScore
import org.cryptobiotic.rlauxe.dhondt.makeDhondtContest
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBelgiumElectionJson {
    val elections = mapOf(
        "Namur" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Namur.json"
    )

    @Test
    fun testReadBelgiumElectionJson() {
        val filename = elections["Namur"]!!
        val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumElectionJson(filename)
        val belgiumElection = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read belgiumElection from ${filename} err = $result")
        println(belgiumElection)
    }

    @Test
    fun testBelgiumContest() {
        testBelgiumContest("Namur")
    }

    @Test
    fun testAllContests() {
        val contests = listOf("92094", "81001", "71022", "62063", "53053", "31005", "25072", "24062", "21004", "11002", )
        val errors = listOf("44021",  )
        contests.forEach { testBelgiumContest(it) }
    }

    fun testBelgiumContest(electionName: String) {
        println("======================================================")
        println("ElectionName $electionName")
        val filename = elections[electionName]!!
        val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumElectionJson(filename)
        val belgiumElection = if (result is Ok) result.unwrap()
        else throw RuntimeException("Cannot read belgiumElection from ${filename} err = $result")
        println(belgiumElection)

        // use infoA parties, because they are complete
        val dhondtParties = belgiumElection.ElectionLists.mapIndexed { idx, it ->  DhondtCandidate(it.PartyLabel, idx+1, it.NrOfVotes) }
        val nwinners = belgiumElection.ElectionLists.sumOf { it.NrOfSeats }
        val dcontest = makeDhondtContest(electionName, 1, dhondtParties, nwinners, 0,.05)
        println("Calculated Winners")
        dcontest.winners.sortedBy { it.winningSeat }.forEach {
            println("  ${it}")
        }
        println()

        val totalVotes = belgiumElection.NrOfValidVotes + belgiumElection.NrOfBlankVotes
        val contestd = dcontest.createContest(Nc = totalVotes, Ncast = totalVotes)
        println(contestd)
        println(contestd.show())

        testCvrs1(dcontest)
        testCvrs2(dcontest, contestd)
    }
}


fun testCvrs1(dcontest: DHondtContest) {
    println("testCvrs1 ----------------------------------------")
    println(dcontest.show())

    val cvrs = dcontest.createSimulatedCvrs()

    dcontest.assorters.forEach { assorter ->
        val welford = Welford()
        cvrs.forEach { cvr ->
            welford.update(assorter.assort(cvr, usePhantoms = false))
        }
        val (gavg, havg) = assorter.getAssortAvg(0)
        print(assorter.show())
        println("             gavg=${gavg.show2()}")
        println("             havg=${havg.show2()}")
        println("             assort mean = ${df(welford.mean)}")
        assertEquals(welford.mean, havg.mean, doublePrecision)
    }
}

fun testCvrs2(dcontest: DHondtContest, contestd: ContestDHondt) {
    println("testCvrs2 ----------------------------------------")
    val cvrs = contestd.createSimulatedCvrs()
    assertEquals(contestd.Nc, cvrs.size)

    dcontest.assorters.forEach { assorter ->
        val welford = Welford()
        cvrs.forEach { cvr ->
            welford.update(assorter.assort(cvr, usePhantoms = false))
        }
        val (gavg, havg) = assorter.getAssortAvg(contestd.undervotes)
        print(assorter.show())
        println("             gavg=${gavg.show2()}")
        println("             havg=${havg.show2()}")
        println("             assort mean = ${df(welford.mean)}")
       assertEquals(welford.mean, havg.mean, doublePrecision)
    }
}

fun testEquals(score1: DhondtScore, score2: DhondtScore): Boolean {
    return (score1.candidate == score2.candidate) &&
            (score1.divisor == score2.divisor) &&
            (score1.winningSeat == score2.winningSeat) &&
            (abs(score1.score - score2.score) <= 1.0)
}

