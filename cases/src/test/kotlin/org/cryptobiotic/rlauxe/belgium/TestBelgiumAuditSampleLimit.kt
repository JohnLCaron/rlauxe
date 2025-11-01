package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ClcaStrategyType
import org.cryptobiotic.rlauxe.audit.writeSortedCardsExternalSort
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.cli.runRound
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.dhondt.ProtoContest
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.DhondtScore
import org.cryptobiotic.rlauxe.dhondt.makeProtoContest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.Welford
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

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
    val belgiumElection = if (result is Ok) result.unwrap()
    else throw RuntimeException("Cannot read belgiumElection from ${filename} err = $result")

    val dhondtParties =
        belgiumElection.ElectionLists.mapIndexed { idx, it -> DhondtCandidate(it.PartyLabel, idx + 1, it.NrOfVotes) }
    val nwinners = belgiumElection.ElectionLists.sumOf { it.NrOfSeats }
    val dcontest = makeProtoContest(electionName, 1, dhondtParties, nwinners, belgiumElection.NrOfBlankVotes, .05)

    val totalVotes = belgiumElection.NrOfValidVotes + belgiumElection.NrOfBlankVotes
    val contestd = dcontest.createContest(Nc = totalVotes, Ncast = totalVotes)

    val auditConfig = AuditConfig(
        AuditType.CLCA,
        hasStyles = true,
        removeCutoffContests = false,
        auditSampleLimit = 1000,
        riskLimit = .05,
        nsimEst = 10,
        minRecountMargin = 0.0,
        clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
    )
    val topdir = "$toptopdir/${electionName}Limited"
    createBelgiumClca(topdir, contestd, auditConfigIn = auditConfig)

    val publisher = Publisher("$topdir/audit")
    val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
    writeSortedCardsExternalSort(topdir, publisher, config.seed)

    // get estimate, set up first round
    runRound(inputDir = "$topdir/audit", useTest = true, quiet = true)
}

fun runAudit(auditdir: String): AuditRound? {
    println("============================================================")
    val lastRound = runRound(inputDir = auditdir, useTest = true, quiet = true)
    return lastRound
}
