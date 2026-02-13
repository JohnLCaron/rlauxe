package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.writeSortedCardsExternalSort
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.makeProtoContest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.ErrorMessages
import kotlin.test.Test

class TestBelgiumAuditSampleLimit {
    val electionName = "Hainaut"

    @Test
    fun createBelgiumElectionLimited() {
        println("createBelgiumElectionLimited with electionName $electionName")
        createBelgiumElectionLimited(electionName)
    }

    @Test
    fun runAuditLimited() {
        val auditdir = "$toptopdir/${electionName}Limited/audit"
        println("runAuditLimited in $auditdir")
        runAudit(auditdir)
    }
}

fun createBelgiumElectionLimited(electionName: String)  {
    println("======================================================")

    val filename = belgianElectionMap[electionName]!!
    val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumElectionJson(filename)
    val belgiumElection = if (result .isOk) result.unwrap()
    else throw RuntimeException("Cannot read belgiumElection from ${filename} err = $result")

    val dhondtParties =
        belgiumElection.ElectionLists.mapIndexed { idx, it -> DhondtCandidate(it.PartyLabel, idx + 1, it.NrOfVotes) }
    val nwinners = belgiumElection.ElectionLists.sumOf { it.NrOfSeats }
    val dcontest = makeProtoContest(electionName, 1, dhondtParties, nwinners, belgiumElection.NrOfBlankVotes, .05)

    val totalVotes = belgiumElection.NrOfValidVotes + belgiumElection.NrOfBlankVotes
    val contestd = dcontest.createContest(Nc = totalVotes, Ncast = totalVotes)

    val config = AuditConfig(
        AuditType.CLCA,
        removeCutoffContests = false,
        auditSampleLimit = 1000,
        riskLimit = .05,
        nsimEst = 10,
        minRecountMargin = 0.0,
    )
    val topdir = "$toptopdir/${electionName}Limited"
    val auditdir = "$topdir/audit"
    createBelgiumClca(auditdir, contestd, auditConfigIn = config)

    val publisher = Publisher(auditdir)
    writeSortedCardsExternalSort(topdir, publisher, config.seed)

    // get estimate, set up first round
    runRound(inputDir = auditdir)
}

fun runAudit(auditdir: String): AuditRoundIF? {
    println("============================================================")
    val lastRound = runRound(inputDir = auditdir)
    return lastRound
}
