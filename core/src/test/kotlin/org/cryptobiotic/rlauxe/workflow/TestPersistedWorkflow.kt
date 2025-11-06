package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.cli.EnterMvrsCli
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.cli.enterMvrs
import org.cryptobiotic.rlauxe.cli.runRound
import org.cryptobiotic.rlauxe.cli.runRoundResult
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
import org.cryptobiotic.rlauxe.persist.*
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.CvrToCardAdapter
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.tabulateCvrs
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

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (fuzzMvrPct == 0.0) testCvrs
            // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
            else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrPct)

        val contestsUA = contests.map { ContestUnderAudit(it, isClca = true, hasStyle = config.hasStyle).addStandardAssertions() }

        val election = PersistedAudit(contestsUA, testCvrs, testMvrs)
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

        // Synthetic cvrs for testing reflecting the exact contest votes, plus undervotes and phantoms.
        val testCvrs = testData.makeCvrsFromContests()
        val testMvrs = if (fuzzMvrPct == 0.0) testCvrs
        // fuzzPct of the Mvrs have their votes randomly changed ("fuzzed")
        else makeFuzzedCvrsFrom(contests, testCvrs, fuzzMvrPct)

        val contestsUA = contests.map { ContestUnderAudit(it, isClca = true, hasStyle = config.hasStyle).addStandardAssertions() }

        val election = PersistedAudit(contestsUA, testCvrs, testMvrs)
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
        val (contestOA, cardPools, testCvrs) = makeOneContestUA(
            N + 100,
            N - 100,
            cvrFraction = .95,
            undervoteFraction = .0,
            phantomFraction = .0
        )

        val contestsUA = listOf(contestOA)
        val infos = mapOf(contestOA.contest.info().id to contestOA.contest.info())

        val phantoms = makePhantomCvrs(contestsUA.map { it.contest } )
        val allCvrs = testCvrs + phantoms

        val cvrTabs = tabulateCvrs(allCvrs.iterator(), infos)
        println("allCvrs = ${cvrTabs}")

        val allContests = contestsUA.map { it.contest }
        println("contests")
        allContests.forEach { println("  $it") }
        println()

        val testMvrs = if (fuzzMvrPct == 0.0) allCvrs
            else makeFuzzedCvrsFrom(allContests, allCvrs, fuzzMvrPct)
        println("nmvrs = ${testMvrs.size} fuzzed at ${fuzzMvrPct}")

        val election = PersistedAudit(contestsUA, allCvrs, testMvrs, cardPools)
        CreateAudit("testPersistedOneAudit", topdir = topdir, config, election, clear = true)

        runPersistedAudit(topdir)
    }
}

class PersistedAudit (
    val contestsUA: List<ContestUnderAudit>,
    val cvrs: List<Cvr>,
    val mvrs: List<Cvr>,
    val cardPools: List<CardPoolIF>? = null,
): CreateElectionIF {

    override fun cardPools() = cardPools
    override fun contestsUA() = contestsUA

    override fun allCvrs(): Pair<CloseableIterator<AuditableCard>?, CloseableIterator<AuditableCard>?> {
        val cvrIter = CvrToCardAdapter(Closer(cvrs.iterator()))
        val mvrIter = CvrToCardAdapter(Closer(mvrs.iterator()))
        return Pair(cvrIter, mvrIter)
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
        val roundResult = runRoundResult(inputDir = auditdir, useTest = false, quiet = true)
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