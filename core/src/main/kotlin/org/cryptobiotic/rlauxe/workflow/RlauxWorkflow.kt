package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.sampling.*
import kotlin.math.max

// created from persistent state
class RlauxWorkflow(
    val auditConfig: AuditConfig,
    val contestsUA: List<ContestUnderAudit>,
    ballotsUA: List<BallotUnderAudit>,  // one
    cvrsUA: List<CvrUnderAudit>,        // or the other
    val quiet: Boolean = false,
): RlauxWorkflowIF {
    val cvrs = cvrsUA.map { it.cvr }    // may be empty
    val bcUA = if (auditConfig.auditType == AuditType.POLLING) ballotsUA else cvrsUA

    // debugging and plots
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

        return sample(this, roundIdx, quiet)
    }

    override fun runAudit(sampleIndices: List<Int>, mvrs: List<Cvr>, roundIdx: Int): Boolean {
        return if (auditConfig.auditType == AuditType.POLLING) {
            runPollingAudit(auditConfig, contestsUA, mvrs, roundIdx, quiet)
        } else {
            runClcaAudit(auditConfig, contestsUA, sampleIndices, mvrs, cvrs, roundIdx, quiet)
        }
    }

    override fun showResultsOld(estSampleSize: Int) {
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

    override fun auditConfig() =  this.auditConfig
    override fun getContests(): List<ContestUnderAudit> = contestsUA
    override fun getBallotsOrCvrs() : List<BallotOrCvr> = bcUA
}

fun RlauxWorkflowIF.showResults(estSampleSize: Int) {
    println("Audit results")
    this.getContests().forEach{ contest ->
        val minAssertion = contest.minAssertion()
        if (minAssertion == null) {
            println(" $contest has no assertions; status=${contest.status}")
        } else {
            if (minAssertion.roundResults.size == 1) {
                print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} done=${contest.done} status=${contest.status} est=${contest.estSampleSize} ${minAssertion.roundResults[0]}")
                if (!this.auditConfig().hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
            } else {
                print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} done=${contest.done} status=${contest.status} est=${contest.estSampleSize}")
                if (!this.auditConfig().hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                minAssertion.roundResults.forEach { rr -> println("   $rr") }
            }
        }
    }

    var maxBallotsUsed = 0
    this.getContests().forEach { contest ->
        contest.assertions().filter { it.roundResults.isNotEmpty() }.forEach { assertion ->
            val lastRound = assertion.roundResults.last()
            maxBallotsUsed = max(maxBallotsUsed, lastRound.maxBallotsUsed)
        }
    }
    println("$estSampleSize - $maxBallotsUsed = extra ballots = ${estSampleSize - maxBallotsUsed}\n")
}