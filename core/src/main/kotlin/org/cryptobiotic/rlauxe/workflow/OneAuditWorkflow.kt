package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditComparisonAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContest
import org.cryptobiotic.rlauxe.sampling.*
import org.cryptobiotic.rlauxe.util.*

class OneAuditWorkflow(
    val auditConfig: AuditConfig,
    val contestsToAudit: List<OneAuditContest>, // the contests you want to audit
    val cvrs: List<Cvr>, // includes undervotes and phantoms.
    val quiet: Boolean = false,
): RlauxWorkflowIF {
    val contestsUA: List<ContestUnderAudit>
    val cvrsUA: List<CvrUnderAudit>
    init {
        require (auditConfig.auditType == AuditType.ONEAUDIT)
        contestsUA = contestsToAudit.map { it.makeContestUnderAudit(cvrs) }

        // check contests well formed etc
        check(auditConfig, contestsUA)

        // must be done once and for all rounds
        val prng = Prng(auditConfig.seed)
        cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) }
    }

    /**
     * Choose lists of ballots to sample.
     * @parameter prevMvrs: use existing mvrs to estimate samples. may be empty.
     */
    override fun chooseSamples(roundIdx: Int, show: Boolean): List<Int> {
        if (!quiet) println("estimateSampleSizes round $roundIdx")

        estimateSampleSizes(
            auditConfig,
            contestsUA,
            cvrs,
            roundIdx,
            show=show,
        )

        return sample(this, roundIdx, quiet)
    }

    //   The auditors retrieve the indicated cards, manually read the votes from those cards, and input the MVRs
    override fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>, roundIdx: Int): Boolean {
        val contestsNotDone = contestsUA.filter{ !it.done }
        val sampledCvrs = sampleIndices.map { cvrs[it] }

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrs.size)
        val cvrPairs: List<Pair<Cvr, Cvr>> = mvrs.zip(sampledCvrs)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

        if (!quiet) println("runAudit round $roundIdx")
        var allDone = true
        contestsNotDone.forEach { contestUA ->
            var allAssertionsDone = true
            contestUA.clcaAssertions.forEach { assertion ->
                  if (!assertion.status.complete) {
                    val testH0Result = runOneAuditAssertionAlpha(auditConfig, contestUA, assertion, cvrPairs, roundIdx, quiet=quiet)
                    assertion.status = testH0Result.status
                    assertion.round = roundIdx
                    allAssertionsDone = allAssertionsDone && assertion.status.complete
                }
            }
            if (allAssertionsDone) {
                contestUA.done = true
                contestUA.status = TestH0Status.StatRejectNull // TODO
            }
            allDone = allDone && contestUA.done

        }
        return allDone
    }

    override fun auditConfig() =  this.auditConfig
    override fun getContests(): List<ContestUnderAudit> = contestsUA
    override fun getBallotsOrCvrs() : List<BallotOrCvr> = cvrsUA
}

fun runOneAuditAssertionAlpha(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    cassertion: ClcaAssertion,
    cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    roundIdx: Int,
    quiet: Boolean = false,
): TestH0Result{
    val assorter = cassertion.cassorter as OneAuditComparisonAssorter
    val sampler = ClcaWithoutReplacement(
        contestUA.contest,
        cvrPairs,
        cassertion.cassorter,
        allowReset = false,
        trackStratum = false
    )

    val eta0 = margin2mean(assorter.clcaMargin)
    val c = (eta0 - 0.5) / 2

    // TODO is this right, no special processing for the "hasCvr" strata?
    val estimFn = if (auditConfig.oaConfig.strategy == OneAuditStrategyType.max99) {
        FixedEstimFn(.99 * assorter.upperBound())
    } else {
        TruncShrinkage(
            N = contestUA.Nc,
            withoutReplacement = true,
            upperBound = assorter.upperBound(),
            d = auditConfig.pollingConfig.d,
            eta0 = eta0,
            c = c,
        )
    }

    val testFn = AlphaMart(
        estimFn = estimFn,
        N = contestUA.Nc,
        withoutReplacement = true,
        riskLimit = auditConfig.riskLimit,
        upperBound = assorter.upperBound(),
    )

    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject=true) { sampler.sample() }

    val roundResult = AuditRoundResult(roundIdx,
        estSampleSize=cassertion.estSampleSize,
        maxBallotsUsed = sampler.maxSamplesUsed(),
        pvalue = testH0Result.pvalueLast,
        samplesNeeded = testH0Result.sampleFirstUnderLimit, // one based
        samplesUsed = testH0Result.sampleCount,
        status = testH0Result.status,
        errorRates = testH0Result.tracker.errorRates()
    )
    cassertion.roundResults.add(roundResult)

    if (!quiet) println(" ${contestUA.name} $roundResult")
    return testH0Result
}