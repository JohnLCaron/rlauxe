package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import org.cryptobiotic.rlauxe.util.*

class PollingWorkflow(
        val auditConfig: AuditConfig,
        contests: List<Contest>, // the contests you want to audit
        val ballotManifest: BallotManifest,
        val N: Int, // total number of ballots/cards
) {
    val contestsUA: List<ContestUnderAudit> = contests.map { ContestUnderAudit(it, it.Nc) }
    val ballotsUA: List<BallotUnderAudit>

    init {
        contestsUA.forEach {
            checkWinners(it, it.contest.votes.entries.sortedByDescending { it.value })
        }

        // TODO polling phantoms
        // phantoms can be CVRs, so dont need CvrIF.
        // val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        val prng = Prng(auditConfig.seed)
        ballotsUA = ballotManifest.ballots.map { BallotUnderAudit(it, prng.next()) }

        contestsUA.filter { !it.done }.forEach { contest ->
            contest.makePollingAssertions()
        }
    }

    fun chooseSamples(prevMvrs: List<CvrIF>, roundIdx: Int): List<Int> {
        println("EstimateSampleSize.simulateSampleSizeContest round $roundIdx")

        // set contest.sampleSize through simulation.
        // Uses SimContest to simulate a contest with the same vote totals.
        // standard: Uses PollWithoutReplacement, then mvr = cvrs
        // alternative: Uses SimContest to simulate a contest with the same vote totals.
        val sampleSizer = EstimateSampleSize(auditConfig)
        val contestsNotDone = contestsUA.filter { !it.done }

        contestsNotDone.filter { !it.done }.forEach { contestUA ->
            sampleSizer.simulateSampleSizeContest(contestUA, emptyList(), prevMvrs, roundIdx, show = true)
        }
        val maxContestSize = contestsNotDone.map { it.estSampleSize }.max()

        // choose samples
        val result = if (auditConfig.hasStyles) { // maybe should be in AuditConfig?
            println("\nconsistentPollingSampling round $roundIdx")
            val sampleIndices = consistentPollingSampling(contestsUA.filter { !it.done }, ballotsUA, ballotManifest)
            println(" PollingWithStyle.chooseSamples maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
            sampleIndices
        } else {
            println("\nuniformPollingSampling round $roundIdx")
            val sampleIndices = uniformPollingSampling(contestsUA.filter { !it.done }, ballotsUA, auditConfig.samplePctCutoff, N, roundIdx)
            println("maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
            sampleIndices
        }

        return result
    }

    fun showResults() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            val minAssertion = contest.minPollingAssertion()
            if (minAssertion == null)
                println(" $contest has no assertions; status=${contest.status}")
            else if (auditConfig.hasStyles)
                println(" $contest samplesUsed=${minAssertion.samplesUsed} round=${minAssertion.round} status=${contest.status}")
            else
                println(" $contest samplesUsed=${minAssertion.samplesUsed} " +
                        "estTotalSampleSize=${contest.estTotalSampleSize} round=${minAssertion.round} status=${contest.status}")
        }
        println()
    }

    fun runAudit(mvrs: List<Cvr>, roundIdx: Int): Boolean {
        return runAudit(auditConfig, contestsUA, mvrs, roundIdx)
    }
}

fun runAudit(
    auditConfig: AuditConfig,
    contestsUA: List<ContestUnderAudit>,
    mvrs: List<Cvr>,
    roundIdx: Int,
): Boolean {
    val contestsNotDone = contestsUA.filter { !it.done }
    if (contestsNotDone.isEmpty()) {
        println("all done")
        return true
    }

    println("auditOneAssertion")
    var allDone = true
    contestsNotDone.forEach { contestUA ->
        var allAssertionsDone = true
        contestUA.pollingAssertions.forEach { assertion ->
            if (!assertion.proved) {
                assertion.status = auditOneAssertion(auditConfig, contestUA, assertion, mvrs, roundIdx)
                allAssertionsDone = allAssertionsDone && (!assertion.status.fail)
            }
        }
        if (allAssertionsDone) {
            contestUA.done = true
            contestUA.status = TestH0Status.StatRejectNull
        }
        allDone = allDone && contestUA.done
    }
    return allDone
}

fun auditOneAssertion(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    assertion: Assertion,
    mvrs: List<Cvr>,
    roundIdx: Int,
): TestH0Status {
    val assorter = assertion.assorter
    val sampler = if (auditConfig.fuzzPct == null) {
        PollWithoutReplacement(contestUA, mvrs, assorter)
    } else {
        PollingFuzzSampler(auditConfig.fuzzPct, mvrs, contestUA, assorter)
    }

    val eta0 = margin2mean(assertion.margin)
    val minsd = 1.0e-6
    val t = 0.5
    val c = (eta0 - t) / 2

    val estimFn = TruncShrinkage(
        N = contestUA.Nc,
        withoutReplacement = true,
        upperBound = assertion.assorter.upperBound(),
        d = auditConfig.d1,
        eta0 = eta0,
        minsd = minsd,
        c = c,
    )
    val testFn = AlphaMart(
        estimFn = estimFn,
        N = contestUA.Nc,
        withoutReplacement = true,
        riskLimit = auditConfig.riskLimit,
        upperBound = assorter.upperBound(),
    )

    val maxSamples = mvrs.count { it.hasContest(contestUA.id) }
    assertion.samplesUsed = maxSamples

    // do not terminate on null reject, continue to use all available samples
    val testH0Result = testFn.testH0(maxSamples, terminateOnNullReject = false) { sampler.sample() }
    if (!testH0Result.status.fail) {
        assertion.proved = true
        assertion.round = roundIdx
    } else {
        println("testH0Result.status = ${testH0Result.status}")
    }
    assertion.samplesNeeded = testH0Result.pvalues.indexOfFirst { it < auditConfig.riskLimit }
    assertion.pvalue = testH0Result.pvalues.last()

    println(" ${contestUA.name} $assertion, samplesNeeded=${assertion.samplesNeeded} samplesUsed=${assertion.samplesUsed} pvalue = ${assertion.pvalue} status = ${testH0Result.status}")
    return testH0Result.status
}