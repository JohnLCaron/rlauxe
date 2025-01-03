package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import org.cryptobiotic.rlauxe.util.*

class PollingWorkflow(
        val auditConfig: AuditConfig,
        contests: List<ContestIF>, // the contests you want to audit
        val ballotManifest: BallotManifest,
        val N: Int, // total number of ballots/cards
) {
    val contestsUA: List<ContestUnderAudit> = contests.map { ContestUnderAudit(it, isComparison=false, auditConfig.hasStyles) }
    val ballotsUA: List<BallotUnderAudit>

    init {
        require (auditConfig.auditType == AuditType.POLLING)

        contestsUA.forEach {
            if (it.choiceFunction != SocialChoiceFunction.IRV) {
                checkWinners(it, (it.contest as Contest).votes.entries.sortedByDescending { it.value })
            }
        }

        contestsUA.filter { !it.done }.forEach { contest ->
            contest.makePollingAssertions()
        }

        // must be done once and for all rounds
        val prng = Prng(auditConfig.seed)
        ballotsUA = ballotManifest.ballots.map { BallotUnderAudit(it, prng.next()) }
    }

    fun chooseSamples(prevMvrs: List<Cvr>, roundIdx: Int, show: Boolean = true): List<Int> {
        println("estimateSampleSizes round $roundIdx")
        val maxContestSize = estimateSampleSizes(
            auditConfig,
            contestsUA,
            emptyList(),
            prevMvrs,
            roundIdx,
            show=show,
        )

        // choose indices to sample
        val contestsNotDone = contestsUA.filter{ !it.done }
        if (contestsNotDone.size > 0) {
            return if (auditConfig.hasStyles) {
                println("\nconsistentSampling round $roundIdx")
                val sampleIndices = consistentSampling(contestsNotDone, ballotsUA)
                println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
                sampleIndices
            } else {
                println("\nuniformSampling round $roundIdx")
                val sampleIndices = uniformSampling(contestsNotDone, ballotsUA, auditConfig.samplePctCutoff, N, roundIdx)
                println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
                sampleIndices
            }
        }

        return emptyList()
    }

    fun showResults() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            val minAssertion = contest.minAssertion()
            if (minAssertion == null) {
                println(" $contest has no assertions; status=${contest.status}")
            } else if (auditConfig.hasStyles) {
                println(" $contest round=${minAssertion.round} status=${contest.status}")
                minAssertion.roundResults.forEach { rr ->
                    println("   $rr")
                }
            } else {
                println(" $contest round=${minAssertion.round} status=${contest.status} estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}")
                minAssertion.roundResults.forEach { rr ->
                    println("   $rr")
                }
            }
        }
        println()
    }

    /*
    fun showResults2() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            val minAssertion = contest.minAssertion()
            if (minAssertion == null)
                println(" $contest has no assertions; status=${contest.status}")
            else if (auditConfig.hasStyles)
                println(" $contest samplesUsed=${minAssertion.samplesUsed} round=${minAssertion.round} status=${contest.status}")
            else
                println(" $contest samplesUsed=${minAssertion.samplesUsed} estSampleSize=${contest.estSampleSize}" +
                        " estSampleSizeNoStyles=${contest.estSampleSizeNoStyles} round=${minAssertion.round} status=${contest.status}")
        }
        println()
    }

     */

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
                assertion.status = auditOneAssertion(auditConfig, contestUA.contest as Contest, assertion, mvrs, roundIdx)
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
    contest: Contest,
    assertion: Assertion,
    mvrs: List<Cvr>,
    roundIdx: Int,
): TestH0Status {
    val assorter = assertion.assorter
    val sampler = PollWithoutReplacement(contest, mvrs, assorter, allowReset=false)

    val eta0 = margin2mean(assertion.margin)
    val minsd = 1.0e-6
    val t = 0.5
    val c = (eta0 - t) / 2

    val estimFn = TruncShrinkage(
        N = contest.Nc,
        withoutReplacement = true,
        upperBound = assertion.assorter.upperBound(),
        d = auditConfig.d1,
        eta0 = eta0,
        minsd = minsd,
        c = c,
    )
    val testFn = AlphaMart(
        estimFn = estimFn,
        N = contest.Nc,
        withoutReplacement = true,
        riskLimit = auditConfig.riskLimit,
        upperBound = assorter.upperBound(),
    )

    // do not terminate on null reject, continue to use all available samples
    val testH0Result = testFn.testH0(sampler.maxSamples(), terminateOnNullReject = false) { sampler.sample() }
    if (!testH0Result.status.fail) {
        assertion.proved = true
        assertion.round = roundIdx
    } else {
        println("testH0Result.status = ${testH0Result.status}")
    }

    val roundResult = AuditRoundResult(roundIdx,
        estSampleSize=assertion.estSampleSize,
        samplesNeeded = testH0Result.pvalues.indexOfFirst{ it < auditConfig.riskLimit },
        samplesUsed = testH0Result.sampleCount,
        pvalue = testH0Result.pvalues.last(),
        status = testH0Result.status,
        // calcAssortMargin=assorter.calcAssorterMargin(contest.id, mvrs),
    )
    assertion.roundResults.add(roundResult)

    println(" ${contest.name} $roundResult")
    return testH0Result.status
}
