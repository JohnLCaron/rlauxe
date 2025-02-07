package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import kotlin.math.max

class RlauxWorkflow(
    val auditConfig: AuditConfig,
    val contestsUA: List<ContestUnderAudit>,
    ballotsUA: List<BallotUnderAudit>,  // one
    cvrsUA: List<CvrUnderAudit>,        // or the other
    val quiet: Boolean = false,
): RlauxWorkflowIF {
    val cvrs = cvrsUA.map { it.cvr }    // may be empty
    val bcUA = if (auditConfig.auditType == AuditType.POLLING) ballotsUA else cvrsUA

    // debugging
    fun estimateSampleSizes(roundIdx: Int, show: Boolean): List<EstimationResult> {
        return estimateSampleSizes(
            auditConfig,
            contestsUA,
            cvrs,
            roundIdx,
            show = show,
        )
    }

    override fun chooseSamples(roundIdx: Int, show: Boolean): List<Int> {
        if (!quiet) println("----------estimateSampleSizes round $roundIdx")

        estimateSampleSizes(
            auditConfig,
            contestsUA,
            cvrs,
            roundIdx,
            show=show,
        )
        val maxContestSize = contestsUA.filter { !it.done }.maxOfOrNull { it.estSampleSize }

        val contestsNotDone = contestsUA.filter{ !it.done }
        if (contestsNotDone.size > 0) {
            return if (auditConfig.hasStyles) {
                if (!quiet) println("\nconsistentSampling round $roundIdx")
                val sampleIndices = consistentSampling(contestsNotDone, bcUA)
                if (!quiet) println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
                sampleIndices
            } else {
                if (!quiet) println("\nuniformSampling round $roundIdx")
                val sampleIndices = uniformSampling(contestsNotDone, bcUA, auditConfig.samplePctCutoff, cvrs.size, roundIdx)
                if (!quiet) println(" maxContestSize=$maxContestSize consistentSamplingSize= ${sampleIndices.size}")
                sampleIndices
            }
        }
        return emptyList()
    }

    override fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>, roundIdx: Int): Boolean {
        return if (auditConfig.auditType == AuditType.POLLING) {
            runPollingAudit(auditConfig, contestsUA, mvrs, roundIdx, quiet)
        } else {
            runClcaAudit(auditConfig, contestsUA, sampleIndices, mvrs, cvrs, roundIdx, quiet)
        }
    }

    override fun showResults(estSampleSize: Int) {
        println("Audit results")
        contestsUA.forEach{ contest ->
            val minAssertion = contest.minAssertion()
            if (minAssertion == null) {
                println(" $contest has no assertions; status=${contest.status}")
            } else {
                if (minAssertion.roundResults.size == 1) {
                    print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} done=${contest.done} status=${contest.status} est=${contest.estSampleSize} ${minAssertion.roundResults[0]}")
                    if (!auditConfig.hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                } else {
                    print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} done=${contest.done} status=${contest.status} est=${contest.estSampleSize}")
                    if (!auditConfig.hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                    minAssertion.roundResults.forEach { rr -> println("   $rr") }
                }
            }
        }

        var maxBallotsUsed = 0
        contestsUA.forEach { contest ->
            contest.assertions().filter { it.roundResults.isNotEmpty() }.forEach { assertion ->
                val lastRound = assertion.roundResults.last()
                maxBallotsUsed = max(maxBallotsUsed, lastRound.maxBallotsUsed)
            }
        }
        println("$estSampleSize - $maxBallotsUsed = extra ballots = ${estSampleSize - maxBallotsUsed}\n")
    }

    override fun getContests(): List<ContestUnderAudit> = contestsUA
    override fun getBallotsOrCvrs() : List<BallotOrCvr> = bcUA

}