package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import org.cryptobiotic.rlauxe.util.*

class PollingWithStyle(
    val auditConfig: AuditConfig,
    contests: List<Contest>, // the contests you want to audit
    val ballotManifest: BallotManifest,
    ) {
    val contestsUA: List<ContestUnderAudit> = contests.map { ContestUnderAudit(it, it.Nc) }
    val ballotsUA: List<BallotUnderAudit>

    init {
        // TODO check winners

        contestsUA.forEach {
            if (it.Nc < it.ncvrs) throw RuntimeException(
                "upperBound ${it.Nc} < ncvrs ${it.ncvrs} for contest ${it.contest.id}"
            )
        }

        // TODO polling phantoms
        // phantoms can be CVRs, so dont need CvrIF.
        // val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        val prng = Prng(auditConfig.seed)
        ballotsUA = ballotManifest.ballots.map { BallotUnderAudit( it, prng.next()) }

        contestsUA.forEach { contest ->
            contest.makePollingAssertions()
        }
    }

    fun chooseSamples(prevMvrs: List<CvrIF>, round: Int): List<Int> {
        // need to reset this each round
        contestsUA.forEach {
            it.estSampleSize = 0
        }

        // set contest.sampleSize through simulation.
        // Uses SimContest to simulate a contest with the same vote totals.
        // standard: Uses PollWithoutReplacement, then mvr = cvrs
        // alternative: Uses SimContest to simulate a contest with the same vote totals.
        val sampleSizer = EstimateSampleSize(auditConfig)
        contestsUA.filter{ !it.done }.forEach { contestUA ->
            sampleSizer.simulateSampleSizePollingContest(contestUA, prevMvrs, contestUA.ncvrs, round, show=true)
        }
        val maxContestSize =  contestsUA.map { it.estSampleSize }.max()

        // choose samples
        val sampleIndices = consistentPollingSampling(contestsUA.filter{ !it.done }, ballotsUA, ballotManifest)

        // STYLISH 4 a,b; maybe only works when sampleThreshold is set. In any case, not needed here, since we have sampleIndices
        // val computeSize = finder.computeSampleSizePolling(contestsUA, ballots)
        println(" PollingWithStyle.chooseSamples maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")

        return sampleIndices
    }

/////////////////////////////////////////////////////////////////////////////////

    fun runAudit(mvrs: List<Cvr>): Boolean {
        var allDone = true
        contestsUA.forEach { contestUA ->
            var allAssertionsDone = true
            contestUA.pollingAssertions.forEach { assertion ->
                if (!assertion.proved) {
                    assertion.status = auditOneAssertion(contestUA, assertion, mvrs)
                    allAssertionsDone = allAssertionsDone && (assertion.status != TestH0Status.LimitReached)
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
        contestUA: ContestUnderAudit,
        assertion: Assertion,
        mvrs: List<Cvr>,
    ): TestH0Status {
        val assorter = assertion.assorter
        val sampler = if (auditConfig.fuzzPct == null) {
            PollWithoutReplacement(contestUA, mvrs, assorter)
        } else {
            PollingFuzzSampler(auditConfig.fuzzPct, mvrs, contestUA, assorter)
        }

        // val sampler = PollWithoutReplacement(contestUA, cvrs, assorter)

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

        val testH0Result = testFn.testH0(contestUA.availableInSample, terminateOnNullReject = true) { sampler.sample() }
        if (testH0Result.status == TestH0Status.StatRejectNull) {
            assertion.proved = true
            assertion.samplesNeeded = testH0Result.sampleCount
        } else {
            println("")
        }
        assertion.pvalue = testH0Result.pvalues.last()
        println(" polling audit: $assertion, samplesEst= ${assertion.samplesEst} samplesNeeded= ${testH0Result.sampleCount} status = ${testH0Result.status}")
        return testH0Result.status
    }

}