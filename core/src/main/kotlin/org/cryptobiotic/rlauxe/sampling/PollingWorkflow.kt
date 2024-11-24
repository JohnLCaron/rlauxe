package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.util.*

// Assume we have BallotStyles, which is equivilent to styles = true.
// TODO what happens if we dont?
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
        // need to reset this each round
        contestsUA.forEach {
            it.estSampleSize = 0
            it.sampleThreshold = 0L // TODO needed?
        }

        // set contest.sampleSize through simulation. Uses SimContest to simulate a contest with the same vote totals.
        val finder = FindSampleSize(auditConfig)
        contestsUA.forEach { contestUA -> finder.simulateSampleSizePollingContest(contestUA, prevMvrs, contestUA.ncvrs, round) }
        val maxContestSize =  contestsUA.map { it.estSampleSize }.max()

        // choose samples, not setting contestUA.sampleThreshold
        val sampleIndices = consistentPollingSampling(contestsUA, ballots)

        // STYLISH 4 a,b; maybe only works when sampleThreshold is set. In any case, not needed here, since we have sampleIndices
        val computeSize = finder.computeSampleSizePolling(contestsUA, ballots)
        println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size} computeSize=$computeSize")

        return sampleIndices
    }

/////////////////////////////////////////////////////////////////////////////////

    fun runAudit(mvrs: List<CvrIF>): Boolean {
        // TODO could parellelize across assertions
        var allDone = true
        contestsUA.forEach { contestUA ->
            contestUA.pollingAssertions.forEach { assertion ->
                if (!assertion.proved) {
                    val done = auditOneAssertion(contestUA, assertion, mvrs)
                    allDone = allDone && done
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

        val testH0Result = testFn.testH0(contestUA.estSampleSize, terminateOnNullReject = true) { sampler.sample() }
        if (testH0Result.status == TestH0Status.StatRejectNull) {
            assertion.proved = true
            assertion.samplesNeeded = testH0Result.sampleCount
        }
        println("runOneAssertionAudit: $assertion, status = ${testH0Result.status}")
        return (testH0Result.status == TestH0Status.StatRejectNull)
    }

}