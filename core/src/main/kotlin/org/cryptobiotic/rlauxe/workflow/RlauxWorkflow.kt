package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import org.cryptobiotic.rlauxe.util.*

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

        val maxContestSize = estimateSampleSizes(
            auditConfig,
            contestsUA,
            cvrs,
            roundIdx,
            show=show,
        )
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

    override fun showResults() {
        println("Audit results")
        contestsUA.forEach{ contest ->
            val minAssertion = contest.minAssertion()
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

        val minAssertion = getContests().first().minAssertion()!!
        if (minAssertion.roundResults.isNotEmpty()) {
            val lastRound = minAssertion.roundResults.last()
            println("extra = ${lastRound.estSampleSize - lastRound.samplesNeeded}")
        }
        println()
    }

    override fun getContests(): List<ContestUnderAudit> = contestsUA
}