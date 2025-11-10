package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.cli.enterMvrs
import org.cryptobiotic.rlauxe.cli.runRoundResult
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
import org.cryptobiotic.rlauxe.persist.*
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CvrsWithStylesToCards
import kotlin.test.Test
import kotlin.test.fail

class TestPersistedWorkflow {

    @Test
    fun testPersistedAuditClca() {
        // val topdir = kotlin.io.path.createTempDirectory().toString()
        val topdir = "/home/stormy/rla/persist/testPersistedAuditClca"
        val fuzzMvrPct = .01

        val config = AuditConfig(AuditType.CLCA, hasStyle=true, seed = 12356667890L, nsimEst=10, contestSampleCutoff = 1000)

        val N = 50000
        val testData = MultiContestTestData(11, 4, N, hasStyle=true, marginRange=0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testPersistedAuditClca $testData")

        // Synthetic cvrs for testing reflecting the exact contest votes, already has undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (fuzzMvrPct == 0.0) testCvrs // TODO REDO
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrPct)

        val contestsUA = contests.map { ContestUnderAudit(it, isClca = true, hasStyle = config.hasStyle).addStandardAssertions() }

        val election = CreateElectionFromCvrs(contestsUA, testCvrs, config=config)
        CreateAudit("testPersistedAuditClca", topdir, config, election, clear = true)

        runPersistedAudit(topdir)
    }

    @Test
    fun testPersistedAuditPolling() {
        // val topdir = kotlin.io.path.createTempDirectory().toString()
        val topdir = "/home/stormy/rla/persist/testPersistedAuditPolling"
        val fuzzMvrPct = .01

        val config = AuditConfig(AuditType.POLLING, hasStyle=true, seed = 12356667890L, nsimEst=10)

        val N = 50000
        val testData = MultiContestTestData(11, 4, N, hasStyle=true, marginRange=0.03..0.05)

        val contests: List<Contest> = testData.contests
        println("Start testPersistedAuditPolling $testData")

        // Synthetic cvrs for testing reflecting the exact contest votes, already has undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (fuzzMvrPct == 0.0) testCvrs // TODO REDO
        // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrPct)

        val contestsUA = contests.map { ContestUnderAudit(it, isClca = true, hasStyle = config.hasStyle).addStandardAssertions() }

        val election = CreateElectionFromCvrs(contestsUA, testCvrs, config=config)
        CreateAudit("testPersistedAuditPolling", topdir, config, election, clear = true)

        runPersistedAudit(topdir)
    }

    @Test
    fun testPersistedOneAudit() {
        // val topdir = kotlin.io.path.createTempDirectory().toString()
        val topdir = "/home/stormy/rla/persist/testPersistedOneAudit"
        val fuzzMvrPct = .01

        val config = AuditConfig(
            AuditType.ONEAUDIT, hasStyle = true, contestSampleCutoff = 20000, nsimEst = 10,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )

        val N = 5000
        // Synthetic cvrs for testing reflecting the exact contest votes, already has undervotes and phantoms.
        val (contestOA, cardPools, testCvrs) = makeOneContestUA(
            N + 100,
            N - 100,
            cvrFraction = .95,
            undervoteFraction = .0,
            phantomFraction = .0
        )

        val contestsUA = listOf(contestOA)

        val election = CreateElectionFromCvrs(contestsUA, testCvrs, cardPools, config=config)
        CreateAudit("testPersistedAuditPolling", topdir, config, election, clear = true)

        runPersistedAudit(topdir)

        runPersistedAudit(topdir)
    }
}

class CreateElectionFromCvrs (
    val contestsUA: List<ContestUnderAudit>,
    val cvrs: List<Cvr>,
    val cardPools: List<CardPoolIF>? = null,
    val cardStyles: List<CardStyleIF>? = null,
    val config: AuditConfig,
): CreateElectionIF {

    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA

    override fun cardManifest(): CardLocationManifest {
        val cvrsIterable  = CloseableIterable{ cvrs.iterator() }
        val cardLocations = CvrsWithStylesToCards(cvrsIterable, cardPools ?: cardStyles, null, type=config.auditType, config.hasStyle) // already has phantoms
        return CardLocationManifest(cardLocations, emptyList())
    }
}

fun runPersistedAudit(topdir: String) {
    val auditdir = "$topdir/audit"
    val publisher = Publisher(auditdir)
    val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
    writeSortedCardsExternalSort(topdir, publisher, config.seed)

    val verifyResults = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
    println()
    print(verifyResults)
    if (verifyResults.hasErrors) fail()

    println("============================================================")
    var done = false
    var lastRound: AuditRound? = null

    while (!done) {
        val roundResult = runRoundResult(inputDir = auditdir, useTest = false, quiet = true) // TODO useTest = false ?
        if (roundResult is Err) {
            println("runRoundResult failed ${roundResult.error}")
            fail()
        }
        lastRound = roundResult.unwrap()

        val enterResult = enterMvrs(auditdir, publisher.sortedCardsFile())
        if (enterResult is Err) {
            println("enterMvrs failed ${enterResult.error}")
            fail()
        }

        done = lastRound.auditIsComplete || lastRound.roundIdx > 5
    }

    if (lastRound != null) {
        println("nrounds = ${lastRound.roundIdx} nmvrs = ${lastRound.nmvrs} topdir=$topdir")
    } else {
        println("failed in topdir=$topdir")
        fail()
    }
}