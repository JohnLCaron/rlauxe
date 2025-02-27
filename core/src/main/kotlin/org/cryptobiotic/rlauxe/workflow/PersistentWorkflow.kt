package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.estimate.*
import kotlin.math.max

/** Created from persistent state. See rla/src/main/kotlin/org/cryptobiotic/rlauxe/cli/RunRlaStartTest.kt */
class PersistentWorkflow(
    val auditConfig: AuditConfig,
    val contestsUA: List<ContestUnderAudit>,
    ballotsUA: List<BallotUnderAudit>,  // one
    cvrsUA: List<CvrUnderAudit>,        // or the other
    val quiet: Boolean = false,
): RlauxWorkflowIF {
    val cvrs = cvrsUA.map { it.cvr }    // may be empty
    val bcUA = if (auditConfig.auditType == AuditType.POLLING) ballotsUA else cvrsUA

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
        return when (auditConfig.auditType) {
            AuditType.CLCA -> runClcaAudit(auditConfig, contestsUA, sampleIndices, mvrs, cvrs, roundIdx, quiet)
            AuditType.POLLING -> runPollingAudit(auditConfig, contestsUA, mvrs, roundIdx, quiet)
            AuditType.ONEAUDIT -> runOneAudit(auditConfig, contestsUA, sampleIndices, mvrs, cvrs, roundIdx, quiet)
        }
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
                print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} done=${contest.done} status=${contest.status} est=${contest.estMvrs} ${minAssertion.roundResults[0]}")
                if (!this.auditConfig().hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
            } else {
                print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} done=${contest.done} status=${contest.status} est=${contest.estMvrs}")
                if (!this.auditConfig().hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                minAssertion.roundResults.forEach { rr -> println("   $rr") }
            }
        }
    }

    var maxBallotsUsed = 0
    this.getContests().forEach { contest ->
        contest.assertions().filter { it.roundResults.isNotEmpty() }.forEach { assertion ->
            val lastRound = assertion.roundResults.last()
            maxBallotsUsed = max(maxBallotsUsed, lastRound.maxBallotIndexUsed)
        }
    }
    println("$estSampleSize - $maxBallotsUsed = extra ballots = ${estSampleSize - maxBallotsUsed}\n")
}