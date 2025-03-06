package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.workflow.*
import org.cryptobiotic.rlauxe.estimate.*

/** Created from persistent state. See rla/src/main/kotlin/org/cryptobiotic/rlauxe/cli/RunRlaStartTest.kt */
class PersistentWorkflow(
    val inputDir: String,
): RlauxWorkflowIF {
    private val auditConfig: AuditConfig
    private val bcUA: List<BallotOrCvr>
    private val cvrs: List<Cvr>
    private val contestsUA: List<ContestUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()

    init {
        val auditRecord = AuditRecord.readFrom(inputDir)
        auditConfig = auditRecord.auditConfig
        auditRounds.addAll(auditRecord.rounds)

        // TODO other auditTypes
        // bcUA = if (auditConfig.auditType == AuditType.POLLING) auditRecord.ballots else auditRecord.cvrs
        bcUA = auditRecord.cvrs
        cvrs = auditRecord.cvrs.map { it.cvr }
        contestsUA = auditRounds.last().contests.map { it.contestUA } // TODO
    }

    fun getLastRound() = auditRounds.last()

    override fun startNewRound(quiet: Boolean): AuditRound {
        val previousRound = if (auditRounds.isEmpty()) null else auditRounds.last()
        val roundIdx = auditRounds.size + 1

        val auditRound = if (previousRound == null) {
            val contestRounds = contestsUA.map { ContestRound(it, roundIdx) }
            AuditRound(roundIdx, contests = contestRounds, sampledIndices = emptyList())
        } else {
            previousRound.createNextRound()
        }
        auditRounds.add(auditRound)

        estimateSampleSizes(
            auditConfig,
            auditRound,
            cvrs,
            show=!quiet,
        )

        auditRound.sampledIndices = sample(this, auditRound, auditRounds.previousSamples(roundIdx), quiet)
        return auditRound
    }

    //  return allDone
    override fun runAudit(auditRound: AuditRound, mvrs: List<Cvr>, quiet: Boolean): Boolean  { // return allDone
        return when (auditConfig.auditType) {
            AuditType.CLCA -> runClcaAudit(auditConfig, auditRound.contests, auditRound.sampledIndices, mvrs, cvrs, auditRound.roundIdx, quiet)
            AuditType.POLLING -> runPollingAudit(auditConfig, auditRound.contests, mvrs, auditRound.roundIdx, quiet)
            AuditType.ONEAUDIT -> runOneAudit(auditConfig, auditRound.contests, auditRound.sampledIndices, mvrs, cvrs, auditRound.roundIdx, quiet)
        }
    }

    override fun auditConfig() =  this.auditConfig
    override fun getContests(): List<ContestUnderAudit> = contestsUA
    override fun getBallotsOrCvrs() : List<BallotOrCvr> = bcUA
}

fun RlauxWorkflowIF.showResults(estSampleSize: Int) {
    println("Audit results")
    /* this.getContestRounds().forEach{ contest ->
        val minAssertion = contest.minAssertion()
        if (minAssertion == null) {
            println(" $contest has no assertions; status=${contest.status}")
        } else {
            // if (minAssertion.roundResults.size == 1) {
                print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} done=${contest.done} status=${contest.status} est=${contest.estMvrs} ${minAssertion.auditResult}")
                if (!this.auditConfig().hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
           /* } else {
                print(" ${contest.name} (${contest.id}) Nc=${contest.Nc} done=${contest.done} status=${contest.status} est=${contest.estMvrs}")
                if (!this.auditConfig().hasStyles) println(" estSampleSizeNoStyles=${contest.estSampleSizeNoStyles}") else println()
                minAssertion.roundResults.forEach { rr -> println("   $rr") }
            } */
        }
    }

    var maxBallotsUsed = 0
    this.getContestRounds().forEach { contest ->
        contest.assertions.filter { it.auditResult != null }.forEach { assertion ->
            val lastRound = assertion.auditResult!!
            maxBallotsUsed = max(maxBallotsUsed, lastRound.maxBallotIndexUsed)
        }
    }
    println("$estSampleSize - $maxBallotsUsed = extra ballots = ${estSampleSize - maxBallotsUsed}\n") */
}