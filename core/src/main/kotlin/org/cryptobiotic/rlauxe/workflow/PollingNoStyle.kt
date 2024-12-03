package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import org.cryptobiotic.rlauxe.util.*

// TODO you really need to limit the estimated sample size.
class PollingNoStyle(
        val auditConfig: AuditConfig,
        contests: List<Contest>, // the contests you want to audit
        ballots: List<Ballot>,
        val N: Int, // total number of ballots/cards
        val maxSamplePercent: Double,
    ) {
    val contestsUA: List<ContestUnderAudit> = contests.map { ContestUnderAudit(it, it.Nc) }
    val ballotsUA: List<BallotUnderAudit>

    init {
        require(ballots.size <= N)
        contestsUA.forEach {
            require(it.Nc <= N)
            checkWinners(it, it.contest.votes.entries.sortedByDescending { it.value })
        }

        // TODO polling phantoms
        // phantoms can be CVRs, so dont need CvrIF.
        // val phantomCVRs = makePhantomCvrs(contestsUA, "phantom-", prng)
        val prng = Prng(auditConfig.seed)
        ballotsUA = ballots.map { BallotUnderAudit( it, prng.next()) }

        contestsUA.filter{ !it.done }.forEach { contest ->
            contest.makePollingAssertions()
        }
    }

    fun chooseSamples(prevMvrs: List<CvrIF>, roundIdx: Int): List<Int> {
        println("PollingNoStyle.chooseSamples round $roundIdx")

        // same as with style, depends only on margin
        val sampleSizer = EstimateSampleSize(auditConfig)
        val contestsNotDone = contestsUA.filter{ !it.done }
        contestsNotDone.filter{ !it.done }.forEach { contestUA ->
            sampleSizer.simulateSampleSizePollingContest(contestUA, prevMvrs, contestUA.Nc, roundIdx, show=true)
        }
        val maxContestSize = contestsNotDone.map { it.estSampleSize }.max()

        // choose samples
        println("\nuniformPollingSampling round $roundIdx")
        val sampleIndices = uniformPollingSampling(contestsUA.filter{ !it.done }, ballotsUA, roundIdx)

        println("maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
        return sampleIndices
    }

    fun uniformPollingSampling(
        contests: List<ContestUnderAudit>,
        ballots: List<BallotUnderAudit>, // all the ballots available to sample
        roundIdx: Int,
    ): List<Int> {
        if (ballots.isEmpty()) return emptyList()

        // scale by proportion of ballots that have this contest
        contests.forEach {
            val fac = N / it.Nc.toDouble()
            val est = (it.estSampleSize * fac).toInt()
            val estPct = (it.estSampleSize / it.Nc.toDouble())
            println("  $it: scale=${df(fac)} estTotalNeeded=${est.toInt()}")
            it.estTotalSampleSize = est
            if (estPct > maxSamplePercent) {
                it.done = true
                it.status = TestH0Status.LimitReached
            }
        }

        // get list of ballot indexes sorted by sampleNum
        val sortedCvrIndices = ballots.indices.sortedBy { ballots[it].sampleNum }

        // take the first estSampleSize of the sorted ballots
        val simple = roundIdx * N / 10.0
        //val sampledIndices = sortedCvrIndices.take(estSampleSize.toInt())
        val sampledIndices = sortedCvrIndices.take(simple.toInt())

        return sampledIndices
    }


/////////////////////////////////////////////////////////////////////////////////
// same as PollingWithStyle

    fun runAudit(mvrs: List<Cvr>, roundIdx: Int): Boolean {
        val contestsNotDone = contestsUA.filter{ !it.done }

        println("auditOneAssertion")
        var allDone = true
        contestsNotDone.forEach { contestUA ->
            var allAssertionsDone = true
            contestUA.pollingAssertions.forEach { assertion ->
                if (!assertion.proved) {
                    assertion.status = auditOneAssertion(contestUA, assertion, mvrs, roundIdx)
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
        roundIdx: Int,
    ): TestH0Status {
        val assorter = assertion.assorter
        /* val sampler = if (auditConfig.fuzzPct == null) {
            PollWithoutReplacement(contestUA, mvrs, assorter)
        } else {
            PollingFuzzSampler(auditConfig.fuzzPct, mvrs, contestUA, assorter)
        } */
        val sampler = PollWithoutReplacement(contestUA, mvrs, assorter)

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
        val maxSamples = mvrs.count { it.hasContest(contestUA.id) }
        assertion.samplesUsed = maxSamples
        val testH0Result = testFn.testH0(maxSamples, terminateOnNullReject = false) { sampler.sample() }
        if (!testH0Result.status.fail)  {
            assertion.proved = true
            assertion.round = roundIdx
        } else {
            println("testH0Result.status = ${testH0Result.status}")
        }
        assertion.samplesNeeded = testH0Result.pvalues.indexOfFirst{ it < auditConfig.riskLimit }
        assertion.pvalue = testH0Result.pvalues.last()

        println(" ${contestUA.name} $assertion, samplesNeeded=${assertion.samplesNeeded} samplesUsed=${assertion.samplesUsed} pvalue = ${assertion.pvalue} status = ${testH0Result.status}")
        return testH0Result.status
    }

    fun showResults() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            val minAssertion = contest.minPollingAssertion()!!
            println(" $contest samplesUsed=${minAssertion.samplesUsed} " +
                    "estTotalSampleSize=${contest.estTotalSampleSize} round=${minAssertion.round} status=${contest.status}")
        }
        println()
    }
}