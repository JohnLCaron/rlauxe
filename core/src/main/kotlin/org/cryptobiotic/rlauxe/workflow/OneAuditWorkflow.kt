package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import org.cryptobiotic.rlauxe.util.*

class OneAuditWorkflow(
    val auditConfig: AuditConfig,
    contests: List<OneAuditContest>, // the contests you want to audit
    val cvrs: List<Cvr>, // includes undervotes and phantoms.
) {
    val contestsUA: List<ContestUnderAudit>
    val cvrsUA: List<CvrUnderAudit>
    init {
        require (auditConfig.auditType == AuditType.ONEAUDIT)

        contestsUA = contests.map { it.makeContestUnderAudit(cvrs) }

        // must be done once and for all rounds
        val prng = Prng(auditConfig.seed)
        cvrsUA = cvrs.map { CvrUnderAudit(it, prng.next()) }
    }

    /**
     * Choose lists of ballots to sample.
     * @parameter prevMvrs: use existing mvrs to estimate samples. may be empty.
     */
    fun chooseSamples(prevMvrs: List<Cvr>, roundIdx: Int, show: Boolean = false): List<Int> {
        println("estimateSampleSizes round $roundIdx")

        val maxContestSize = estimateSampleSizes(
            auditConfig,
            contestsUA,
            cvrs,
            prevMvrs,
            roundIdx,
            show=show,
        )

        // TODO how to control the round's sampleSize?

        //	4.c) Choose thresholds {𝑡_𝑐} 𝑐 ∈ C so that 𝑆_𝑐 ballot cards containing contest 𝑐 have a sample number 𝑢_𝑖 less than or equal to 𝑡_𝑐 .
        // draws random ballots and returns their locations to the auditors.
        val contestsNotDone = contestsUA.filter{ !it.done }
        if (contestsNotDone.size > 0) {
            return if (auditConfig.hasStyles) {
                println(" consistentSampling round $roundIdx")
                val sampleIndices = consistentSampling(contestsNotDone, cvrsUA)
                println("  maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
                sampleIndices
            } else {
                println(" uniformSampling round $roundIdx")
                val sampleIndices = uniformSampling(contestsNotDone, cvrsUA, auditConfig.samplePctCutoff, cvrs.size, roundIdx)
                println("  maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
                sampleIndices
            }
        }
        return emptyList()
    }

    //   The auditors retrieve the indicated cards, manually read the votes from those cards, and input the MVRs
    fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>, roundIdx: Int): Boolean {
        val contestsNotDone = contestsUA.filter{ !it.done }
        val sampledCvrs = sampleIndices.map { cvrs[it] }

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == mvrs.size)
        val cvrPairs: List<Pair<Cvr, Cvr>> = mvrs.zip(sampledCvrs)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

        println("runAudit round $roundIdx")
        var allDone = true
        contestsNotDone.forEach { contestUA ->
            var allAssertionsDone = true
            contestUA.comparisonAssertions.forEach { assertion ->
                if (!assertion.proved) {
                    // assertion.status = runOneAuditAssertionBet(auditConfig, contestUA, assertion, cvrPairs, roundIdx)
                    assertion.status = runOneAuditAssertionAlpha(auditConfig, contestUA, assertion, cvrPairs, roundIdx)
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

    fun showResults() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            val minAssertion = contest.minComparisonAssertion()
            if (minAssertion == null) {
                println(" $contest has no assertions; status=${contest.status}")
            } else {
                if (minAssertion.roundResults.size == 1) {
                    print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} Np=${contest.Np} minMargin=${df(contest.minMargin())} ${minAssertion.roundResults[0]}")
                    if (!auditConfig.hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                } else {
                    print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} minMargin=${df(contest.minMargin())} est=${contest.estSampleSize} round=${minAssertion.round} status=${contest.status}")
                    if (!auditConfig.hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                    minAssertion.roundResults.forEach { rr -> println("   $rr") }
                }
            }
        }
        println()
    }
}

fun runOneAuditAssertionBet(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    cassertion: ComparisonAssertion,
    cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    roundIdx: Int,
): TestH0Status {
    val cassorter = cassertion.cassorter
    val sampler = ComparisonWithoutReplacement(contestUA.contest, cvrPairs, cassorter, allowReset = false, trackStratum = false)

    val errorRates = auditConfig.errorRates ?: ComparisonErrorRates.getErrorRates(contestUA.ncandidates, auditConfig.fuzzPct)
    val optimal = AdaptiveComparison(
        Nc = contestUA.Nc,
        withoutReplacement = true,
        a = cassorter.noerror(),
        d1 = auditConfig.d1,
        d2 = auditConfig.d2,
        p2o = errorRates[0],
        p1o = errorRates[1],
        p1u = errorRates[2],
        p2u = errorRates[3],
    )
    val testFn = BettingMart(
        bettingFn = optimal,
        Nc = contestUA.Nc,
        noerror = cassorter.noerror(),
        upperBound = cassorter.upperBound(),
        riskLimit = auditConfig.riskLimit,
        withoutReplacement = true
    )

    // do not terminate on null reject, continue to use all samples
    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = false) { sampler.sample() }
    if (!testH0Result.status.fail) {
        cassertion.proved = true
        cassertion.round = roundIdx
    } else {
        println("testH0Result.status = ${testH0Result.status}")
    }

    val roundResult = AuditRoundResult(roundIdx,
        estSampleSize=cassertion.estSampleSize,
        samplesNeeded = testH0Result.pvalues.indexOfFirst{ it < auditConfig.riskLimit },
        samplesUsed = testH0Result.sampleCount,
        pvalue = testH0Result.pvalues.last(),
        status = testH0Result.status,
        )
    cassertion.roundResults.add(roundResult)

    println(" ${contestUA.name} $roundResult")
    return testH0Result.status
}

fun runOneAuditAssertionAlpha(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    cassertion: ComparisonAssertion,
    cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    roundIdx: Int,
): TestH0Status {
    val assorter = cassertion.cassorter as OneAuditComparisonAssorter
    val sampler = ComparisonWithoutReplacement(contestUA.contest, cvrPairs, cassertion.cassorter, allowReset = false, trackStratum = true)

    val eta0 = margin2mean(assorter.clcaMargin)
    val minsd = 1.0e-6
    val t = 0.5
    val c = (eta0 - t) / 2

    val estimFn = TruncShrinkage(
        N = contestUA.Nc,
        withoutReplacement = true,
        upperBound = assorter.upperBound(),
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

    // do not terminate on null reject, continue to use all available samples
    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = false) { sampler.sample() }
    if (!testH0Result.status.fail) {
        cassertion.proved = true
        cassertion.round = roundIdx
    } else {
        println("testH0Result.status = ${testH0Result.status}")
    }

    val roundResult = AuditRoundResult(roundIdx,
        estSampleSize=cassertion.estSampleSize,
        samplesNeeded = testH0Result.pvalues.indexOfFirst{ it < auditConfig.riskLimit },
        samplesUsed = testH0Result.sampleCount,
        pvalue = testH0Result.pvalues.last(),
        status = testH0Result.status,
    )
    cassertion.roundResults.add(roundResult)

    println(" ${contestUA.name} $roundResult")
    return testH0Result.status
}