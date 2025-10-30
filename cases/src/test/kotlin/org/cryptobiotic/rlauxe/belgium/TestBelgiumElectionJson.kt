package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.dhondt.ContestDHondt
import org.cryptobiotic.rlauxe.dhondt.ProtoContest
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.DhondtScore
import org.cryptobiotic.rlauxe.dhondt.makeProtoContest
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
        val dcontest = makeProtoContest(electionName, 1, dhondtParties, nwinners, 0,.05)
        println("Calculated Winners")
        dcontest.winners.sortedBy { it.winningSeat }.forEach {
            println("  ${it}")
        }
        println()

        val totalVotes = belgiumElection.NrOfValidVotes + belgiumElection.NrOfBlankVotes
        val contestd = dcontest.createContest(Nc = totalVotes, Ncast = totalVotes)
        println(contestd)
        println(contestd.show())

        testCvrs(dcontest, contestd)
    }
}

fun testCvrs(dcontest: ProtoContest, contestd: ContestDHondt) {

    println("testCvrs2 ----------------------------------------")
    val cvrs = contestd.createSimulatedCvrs()
    assertEquals(contestd.Nc, cvrs.size)

    dcontest.makeAssorters().forEach { assorter ->
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

