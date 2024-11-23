package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.util.*


class PollingWorkflow(
        val auditConfig: AuditConfig,
        contests: List<Contest>, // the contests you want to audit
        val ballots: List<BallotUnderAudit>,
    ) {
    val contestsUA: List<ContestUnderAudit> = contests.map { ContestUnderAudit(it, it.Nc) }

    init {
        contestsUA.forEach {
            if (it.Nc < it.ncvrs) throw RuntimeException(
                "upperBound ${it.Nc} < ncvrs ${it.ncvrs} for contest ${it.contest.id}"
            )
        }

        // TODO polling phantoms
        // val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        val prng = Prng(auditConfig.seed)
        ballots.forEach { it.sampleNum = prng.next() }

        contestsUA.forEach { contest ->
            contest.makePollingAssertions()
        }
    }

    fun chooseSamples(prevMvrs: List<CvrIF>, round: Int): List<Int> {
        // set contestUA.sampleSize
        contestsUA.forEach {
            it.sampleSize = 0
            it.sampleThreshold = 0L // TODO needed?
        } // need to reset this each round
        val maxContestSize = simulateSampleSizes(auditConfig, contestsUA, prevMvrs, round) // set contest.sampleSize

        val samples = consistentPollingSampling(contestsUA, ballots)

        val computeSize = FindSampleSize(auditConfig).computeSampleSizePolling(contestsUA, ballots)
        println(" maxContestSize=$maxContestSize consistentSamplingSize= ${samples.size} computeSize=$computeSize")
        return samples// set contestUA.sampleThreshold
    }

    fun simulateSampleSizes(
        auditConfig: AuditConfig,
        contestsUA: List<ContestUnderAudit>,
        prevMvrs: List<CvrIF>, // TODO should be used for subsequent round estimation
        round: Int,
    ): Int {
        contestsUA.forEach { contestUA ->
            val sampleSizes = mutableListOf<Int>()
            contestUA.pollingAssertions.map { assert ->
                if (!assert.proved) {
                    val result = simulateSampleSizePolling(contestUA, assert.assorter)
                    val size = result.findQuantile(auditConfig.quantile)
                    assert.samplesEst = size + round * 100  // TODO how to increase sample size ??
                    sampleSizes.add(assert.samplesEst)
                    println(" simulateSampleSizes ${assert} est=$size")
                }
            }
            contestUA.sampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
            println("simulateSampleSizes at ${100 * auditConfig.quantile}% quantile: contest ${contestUA.name} est=${contestUA.sampleSize}")
        }
      return contestsUA.map { it.sampleSize }.max()
    }

    fun simulateSampleSizePolling(
        contestUA: ContestUnderAudit,
        assorter: AssorterFunction,
    ): RunTestRepeatedResult {
        val margin = assorter.reportedMargin()
        val simContest = SimContest(contestUA.contest, assorter, true)
        val cvrs = simContest.makeCvrs()
        require(cvrs.size == contestUA.ncvrs)
        val sampler = PollWithoutReplacement(contestUA, cvrs, assorter)
        // TODO fuzz data from the reported mean
        // Isnt this number fixed by the margin ??

        val eta0 = margin2mean(margin)
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
            upperBound = assorter.upperBound(),
            withoutReplacement = true
        )

        // TODO use coroutines
        val result: RunTestRepeatedResult = runTestRepeated(
            drawSample = sampler,
            maxSamples = contestUA.ncvrs, // not set yet contestUA.actualAvailable,
            ntrials = auditConfig.ntrials,
            testFn = testFn,
            testParameters = mapOf("margin" to margin),
            showDetails = false,
        )
        return result
    }

/////////////////////////////////////////////////////////////////////////////////

    fun runAudit(sampleIndices: List<Int>, mvrs: List<CvrIF>): Boolean {
        // TODO could parellelize across assertions
        var allDone = true
        contestsUA.forEach { contestUA ->
            contestUA.pollingAssertions.forEach { assertion ->
                if (!assertion.proved) {
                    val done = auditOneAssertion(contestUA, assertion, mvrs)
                    allDone = allDone && done
                    // simulateSampleSize(auditConfig, contestUA, assertion, cvrPairs)
                    // println()
                }
            }
        }
        return allDone
    }

    fun auditOneAssertion(
        contestUA: ContestUnderAudit,
        assertion: Assertion,
        cvrs: List<CvrIF>,
    ): Boolean {
        val assorter = assertion.assorter
        val sampler: SampleFn = PollWithoutReplacement(contestUA, cvrs, assorter)

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