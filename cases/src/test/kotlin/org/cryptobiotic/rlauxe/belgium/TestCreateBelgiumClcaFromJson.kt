package org.cryptobiotic.rlauxe.belgium


import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.writeSortedCardsExternalSort
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.cli.runRound
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.makeDhondtContest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import kotlin.test.Test
import kotlin.test.fail

class TestCreateBelgiumClcaFromJson {
    val elections = mapOf(
        "Anvers" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription d'Anvers.json",
        "Bruxelles" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Bruxelles-Capitale.json",
        "FlandreWest" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Flandre occidentale.json",
        "FlandreEast" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Flandre orientale.json",
        "Hainaut" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Hainaut.json",
        "Liège" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Liège.json",
        "Limbourg" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Limbourg.json",
        "Luxembourg" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Luxembourg.json",
        "Namur" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription de Namur.json",
        "BrabantFlamant" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription du Brabant flamand.json",
        "BrabantWallon" to "/home/stormy/rla/cases/belgium/2024/2024_chambre-des-représentants_Circonscription du Brabant wallon.json",
    )
    val toptopdir = "/home/stormy/rla/cases/belgium/2024"

    @Test
    fun createBelgiumElection() {
        createBelgiumElection("Anvers")
    }

    @Test
    fun createAllBelgiumElection() {
        val allmvrs = mutableMapOf<String, Pair<Int, Int>>()
        elections.keys.forEach {
            allmvrs[it] =  createBelgiumElection(it)
        }
        allmvrs.forEach {
            val pct = (100.0 * it.value.second) / it.value.first.toDouble()
            println("${sfn(it.key, 15)}: Nc= ${trunc(it.value.first.toString(), 10)} " +
                    " nmvrs= ${trunc(it.value.second.toString(), 6)} pct= ${dfn(pct, 2)} %")
        }
    }

    fun createBelgiumElection(electionName: String): Pair<Int, Int> {
        println("======================================================")
        println("electionName $electionName")
        val filename = elections[electionName]!!
        val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumElectionJson(filename)
        val belgiumElection = if (result is Ok) result.unwrap()
            else throw RuntimeException("Cannot read belgiumElection from ${filename} err = $result")

        // use infoA parties, because they are complete
        val dhondtParties = belgiumElection.ElectionLists.mapIndexed { idx, it ->  DhondtCandidate(it.PartyLabel, idx+1, it.NrOfVotes) }
        val nwinners = belgiumElection.ElectionLists.sumOf { it.NrOfSeats }
        val dcontest = makeDhondtContest(electionName, 1, dhondtParties, nwinners, belgiumElection.NrOfBlankVotes,.05)

        val totalVotes = belgiumElection.NrOfValidVotes + belgiumElection.NrOfBlankVotes
        val contestd = dcontest.createContest(Nc = totalVotes, Ncast = totalVotes)

        val topdir = "$toptopdir/$electionName"
        createBelgiumClca(topdir, dcontest, contestd)

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsExternalSort(topdir, publisher, config.seed)

        val auditdir = "$topdir/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()

        println("============================================================")
        var done = false
        var finalRound: AuditRound? = null
        while (!done) {
            val lastRound = runRound(inputDir = auditdir, useTest = true, quiet = true)
            if (lastRound != null) finalRound = lastRound
            done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5
        }

        return if (finalRound != null) {
            println("$electionName: ${finalRound.show()}")
            Pair(totalVotes, finalRound.nmvrs)
        } else Pair(0, 0)
    }

    @Test
    fun showBelgiumElection() {
        showBelgiumElection("Anvers")
    }

    @Test
    fun showAllBelgiumElection() {
        val allmvrs = mutableMapOf<String, Pair<Int, Int>>()
        elections.keys.forEach {
            allmvrs[it] = showBelgiumElection(it)
        }
        allmvrs.forEach {
            val pct = (100.0 * it.value.second) / it.value.first.toDouble()
            println("${sfn(it.key, 15)}: Nc= ${trunc(it.value.first.toString(), 10)} " +
                    " nmvrs= ${trunc(it.value.second.toString(), 6)} pct= ${dfn(pct, 2)} %")
        }
    }

    fun showBelgiumElection(electionName: String): Pair<Int, Int> {
        println("======================================================")
        println("auditBelgiumElection $electionName")
        val topdir = "$toptopdir/$electionName"
        val auditdir = "$topdir/audit"

        val auditRecord = PersistedWorkflow(auditdir, useTest=true).auditRecord
        val finalRound = auditRecord.rounds.last()
        val Nc = finalRound.contestRounds.first().Nc
        return Pair(Nc, finalRound.nmvrs)
    }
}