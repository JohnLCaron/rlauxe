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
        contestsUA.forEach {
            if (it.Nc < it.ncvrs) throw RuntimeException(
                "upperBound ${it.Nc} < ncvrs ${it.ncvrs} for contest ${it.contest.id}"
            )
            checkWinners(it, it.contest.votes.entries.sortedByDescending { it.value })
        }

        // TODO polling phantoms
        // phantoms can be CVRs, so dont need CvrIF.
        // val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        val prng = Prng(auditConfig.seed)
        ballotsUA = ballotManifest.ballots.map { BallotUnderAudit( it, prng.next()) }

        contestsUA.filter{ !it.done }.forEach { contest ->
            contest.makePollingAssertions()
        }
    }

    fun chooseSamples(prevMvrs: List<CvrIF>, roundIdx: Int): List<Int> {
        println("EstimateSampleSize.simulateSampleSizePollingContest round $roundIdx")

        // set contest.sampleSize through simulation.
        // Uses SimContest to simulate a contest with the same vote totals.
        // standard: Uses PollWithoutReplacement, then mvr = cvrs
        // alternative: Uses SimContest to simulate a contest with the same vote totals.
        val sampleSizer = EstimateSampleSize(auditConfig)
        val contestsNotDone = contestsUA.filter{ !it.done }

        contestsNotDone.filter{ !it.done }.forEach { contestUA ->
            sampleSizer.simulateSampleSizePollingContest(contestUA, prevMvrs, contestUA.ncvrs, roundIdx, show=true)
        }
        val maxContestSize = contestsNotDone.map { it.estSampleSize }.max()

        // choose samples
        println("\nconsistentPollingSampling round $roundIdx")
        val sampleIndices = consistentPollingSampling(contestsUA.filter{ !it.done }, ballotsUA, ballotManifest)
        println(" PollingWithStyle.chooseSamples maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")

        return sampleIndices
    }

/////////////////////////////////////////////////////////////////////////////////

    fun runAudit(mvrs: List<Cvr>): Boolean {
        val contestsNotDone = contestsUA.filter{ !it.done }

        println("auditOneAssertion")
        var allDone = true
        contestsNotDone.forEach { contestUA ->
            var allAssertionsDone = true
            contestUA.pollingAssertions.forEach { assertion ->
                if (!assertion.proved) {
                    assertion.status = auditOneAssertion(contestUA, assertion, mvrs)
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

        // do not terminate on null retject, continue to use all samples
        val testH0Result = testFn.testH0(contestUA.availableInSample, terminateOnNullReject = false) { sampler.sample() }
        if (!testH0Result.status.fail)  {
            assertion.proved = true
        } else {
            println("testH0Result.status = ${testH0Result.status}")
        }
        assertion.samplesNeeded = testH0Result.pvalues.indexOfFirst{ it < auditConfig.riskLimit }
        assertion.samplesUsed = testH0Result.sampleCount
        assertion.pvalue = testH0Result.pvalues.last()

        println(" ${contestUA.name} $assertion, samplesNeeded=${assertion.samplesNeeded} samplesUsed=${assertion.samplesUsed} pvalue = ${assertion.pvalue} status = ${testH0Result.status}")
        return testH0Result.status
    }

    fun showResults() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            println(" $contest minMargin=${df(contest.minPollingAssertion()?.margin ?: 0.0)} status=${contest.status}")
        }
        println()
    }
}