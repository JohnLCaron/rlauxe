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
    val auditRecord: AuditRecord

    init {
        auditRecord = AuditRecord.readFrom(inputDir)
        auditConfig = auditRecord.auditConfig
        auditRounds.addAll(auditRecord.rounds)

        // TODO other auditTypes
        // bcUA = if (auditConfig.auditType == AuditType.POLLING) auditRecord.ballots else auditRecord.cvrs
        bcUA = auditRecord.cvrs
        cvrs = auditRecord.cvrs.map { it.cvr }
        contestsUA = auditRounds.last().contestRounds.map { it.contestUA } // TODO
    }

    fun getLastRound() = auditRounds.last()

    //  return allDone
    override fun runAudit(auditRound: AuditRound, mvrs: List<Cvr>, quiet: Boolean): Boolean  { // return allDone
        return when (auditConfig.auditType) {
            AuditType.CLCA -> runClcaAudit(auditConfig, auditRound.contestRounds, auditRound.sampledIndices, mvrs, cvrs, auditRound.roundIdx, auditor = AuditClcaAssertion())
            AuditType.POLLING -> runPollingAudit(auditConfig, auditRound.contestRounds, mvrs, auditRound.roundIdx, quiet)
            AuditType.ONEAUDIT -> runClcaAudit(auditConfig, auditRound.contestRounds, auditRound.sampledIndices, mvrs, cvrs, auditRound.roundIdx, auditor = OneAuditClcaAssertion())
        }
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestUA(): List<ContestUnderAudit> = contestsUA
    override fun cvrs() = cvrs
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