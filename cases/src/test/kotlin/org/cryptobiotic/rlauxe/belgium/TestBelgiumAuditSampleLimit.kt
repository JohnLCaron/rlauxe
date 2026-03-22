package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.makeProtoContest
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.ErrorMessages
import kotlin.test.Test

class TestBelgiumAuditSampleLimit {
    val topdirLimited = "$testdataDir/cases/belgium/2024limited"

    @Test
    fun createBelgiumElectionLimited() {
        val electionName = "Hainaut"
        val topdir = "$topdirLimited/${electionName}"
        println("createBelgiumElectionLimited with electionName $electionName")
        createBelgiumElectionLimited(electionName, topdir, 5)
    }

    @Test
    fun createAllBelgiumElectionsLimited() {
        belgianElectionMap.keys.forEachIndexed { idx, electionName ->
            val topdir = "$topdirLimited/${electionName}"
           createBelgiumElectionLimited(electionName, topdir, idx+1)
        }
    }
}

fun createBelgiumElectionLimited(electionName: String, topdir: String, contestId: Int)  {
    println("======================================================")
    println("createBelgiumElectionLimited with electionName $electionName")

    val filename = belgianElectionMap[electionName]!!
    val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumElectionJson(filename)
    val belgiumElection = if (result .isOk) result.unwrap()
    else throw RuntimeException("Cannot read belgiumElection from ${filename} err = $result")

    val dhondtParties =
        belgiumElection.ElectionLists.mapIndexed { idx, it -> DhondtCandidate(it.PartyLabel, idx + 1, it.NrOfVotes) }
    val nwinners = belgiumElection.ElectionLists.sumOf { it.NrOfSeats }
    val dcontest = makeProtoContest(electionName, contestId, dhondtParties, nwinners, belgiumElection.NrOfBlankVotes, .05)

    val totalVotes = belgiumElection.NrOfValidVotes + belgiumElection.NrOfBlankVotes
    val contestd = dcontest.createContest(Nc = totalVotes, Ncast = totalVotes)

    val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.05, riskMeasuringSampleLimit=1000)
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 1),
        ContestSampleControl.NONE,
        ClcaConfig(fuzzMvrs=0.0), null)

    createBelgiumClca(topdir, contestd, creation, round)

    // get estimate, set up first round
    runRound(inputDir = "$topdir/audit")
}

fun runAudit(auditdir: String): AuditRoundIF? {
    println("============================================================")
    val lastRound = runRound(inputDir = auditdir)
    return lastRound
}
