package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.util.*
import java.util.concurrent.TimeUnit

private val showQuantiles = false

class PollingWorkflow(
    val auditConfig: AuditConfig,
    contests: List<Contest>, // the contests you want to audit
    val cvrs: List<CvrIF>,
    val upperBounds: Map<Int, Int>, // 𝑁_𝑐.
) {
    val contestsUA: List<ContestUnderAudit> = contests.map { ContestUnderAudit(it) }
    val cvrsUA: List<CvrUnderAudit>
    val prng = Prng(auditConfig.seed)

    init {
        contestsUA.forEach {
            it.Nc = upperBounds[it.contest.id]!!
            if (it.Nc < it.ncvrs) throw RuntimeException(
                "upperBound ${it.Nc} < ncvrs ${it.ncvrs} for contest ${it.contest.id}"
            )
        }

        val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        cvrsUA = cvrs.map { CvrUnderAudit(it as Cvr, false, prng.next()) } + phantomCVRs

        contestsUA.forEach { contest ->
            contest.makePollingAssertions(cvrsUA)
        }
    }

    /**
     * Choose lists of ballots to sample.
     * @parameter mvrs: use existing mvrs to estimate sample sizes. may be empty.
     */
    fun chooseSamples(prevMvrs: List<CvrIF>, round: Int): List<Int> {
        // set contestUA.sampleSize
        contestsUA.forEach { it.sampleThreshold = 0L } // need to reset this each round
        val maxContestSize = simulateSampleSizes(auditConfig, contestsUA, prevMvrs, round)

        val samples = consistentSampling(contestsUA, cvrsUA)
        println(" maxContestSize=$maxContestSize consistentSamplingSize= ${samples.size}")
        return samples// set contestUA.sampleThreshold
    }

    //   The auditors retrieve the indicated cards, manually read the votes from those cards, and input the MVRs
    fun runAudit(sampleIndices: List<Int>, mvrs: List<CvrIF>): Boolean {

        val sampledCvrs = sampleIndices.map { cvrsUA[it] }
        val useMvrs = if (mvrs.isEmpty()) sampledCvrs else mvrs

        // prove that sampledCvrs correspond to mvrs
        require(sampledCvrs.size == useMvrs.size)
        val cvrPairs: List<Pair<CvrIF, CvrUnderAudit>> = useMvrs.zip(sampledCvrs)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

        // TODO could parellelize across assertions
        var allDone = true
        contestsUA.forEach { contestUA ->
            contestUA.pollingAssertions.forEach { assertion ->
                if (!assertion.proved) {
                    val done = runOneAssertion(contestUA, assertion, mvrs)
                    allDone = allDone && done
                    // simulateSampleSize(auditConfig, contestUA, assertion, cvrPairs)
                    // println()
                }
            }
        }
        return allDone
    }

    // TODO somehow est is much higher than actual
    // TODO what forces this to a higher count on subsequent rounds ?? the overstatements in the mvrs ??
    fun simulateSampleSizes(
        auditConfig: AuditConfig,
        contestsUA: List<ContestUnderAudit>,
        prevMvrs: List<CvrIF>,
        round: Int,
    ): Int {
        val stopwatch = Stopwatch()
        // TODO could parellelize
        val finder = FindSampleSize(auditConfig)
        contestsUA.forEach { contestUA ->
            val sampleSizes = mutableListOf<Int>()
            contestUA.comparisonAssertions.map { assert ->
                if (!assert.proved) {
                    val result = finder.simulateSampleSize(contestUA, assert.assorter, cvrs)
                    if (showQuantiles) {
                        print("   quantiles: ")
                        repeat(9) {
                            val quantile = .1 * (1 + it)
                            print("${df(quantile)} = ${result.findQuantile(quantile)}, ")
                        }
                        println()
                    }
                    val size = result.findQuantile(auditConfig.quantile)
                    assert.samplesEst = size + round * 100  // TODO how to increase sample size ??
                    sampleSizes.add(assert.samplesEst)
                    println(
                        "simulateSampleSizes at ${100 * auditConfig.quantile}% quantile: ${assert} took ${
                            stopwatch.elapsed(
                                TimeUnit.MILLISECONDS
                            )
                        } ms"
                    )
                    // println("  errorRates % = [${result.errorRates()}]")
                }
            }
            contestUA.sampleSize = sampleSizes.max()
        }

        // AFAICT, the calculation of the total_size using the probabilities as described in 4.b) is when you just want the
        // total_size estimate, but not do the consistent sampling.
        //val computeSize = finder.computeSampleSize(contestsUA, cvrs)
        //println(" computeSize=$computeSize consistentSamplingSize= ${samples.size}")

        return contestsUA.map { it.sampleSize }.max()
    }

/////////////////////////////////////////////////////////////////////////////////
// run audit for one assertion; could be parallel

    fun runOneAssertion(
        contestUA: ContestUnderAudit,
        assertion: Assertion,
        cvrs: List<CvrIF>,
    ): Boolean {
        val assorter = assertion.assorter
        val sampler: SampleFn = PollWithoutReplacement(cvrs, assorter)

        val eta0 = assertion.avgCvrAssortValue
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
            upperBound = assorter.upperBound(),
            withoutReplacement = true
        )

        val testH0Result = testFn.testH0(contestUA.sampleSize, terminateOnNullReject = true) { sampler.sample() }
        if (testH0Result.status == TestH0Status.StatRejectNull) {
            assertion.proved = true
            assertion.samplesNeeded = testH0Result.sampleCount
        }
        println("runOneAssertionAudit: $assertion, status = ${testH0Result.status}")
        return (testH0Result.status == TestH0Status.StatRejectNull)
    }

}