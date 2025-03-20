package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.workflow.*

/** Created from persistent state. See rla/src/main/kotlin/org/cryptobiotic/rlauxe/cli/RunRlaStartFuzz.kt */
class PersistentWorkflow(
    inputDir: String,
): RlauxWorkflowIF {

    private val auditConfig: AuditConfig
    private val contestsUA: List<ContestUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()
    val auditRecord: AuditRecord
    val ballotCards: BallotCards

    init {
        auditRecord = AuditRecord.readFrom(inputDir)
        auditConfig = auditRecord.auditConfig
        auditRounds.addAll(auditRecord.rounds)
        contestsUA = auditRounds.last().contestRounds.map { it.contestUA }

        ballotCards = auditRecord.ballotCards()
        println()
    }

    fun getLastRound() = auditRounds.last()

    //  return allDone
    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  { // return allDone
        return when (auditConfig.auditType) {
            AuditType.CLCA -> runClcaAudit(auditConfig, auditRound.contestRounds, ballotCards as BallotCardsClca, auditRound.roundIdx, auditor = AuditClcaAssertion())
            AuditType.POLLING -> runPollingAudit(auditConfig, auditRound.contestRounds, ballotCards as BallotCardsPolling, auditRound.roundIdx, quiet)
            AuditType.ONEAUDIT -> runClcaAudit(auditConfig, auditRound.contestRounds, ballotCards as BallotCardsClca, auditRound.roundIdx, auditor = OneAuditClcaAssertion())
        }
    }

    override fun auditConfig() =  this.auditConfig
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
    override fun setMvrsBySampleNumber(sampleNumbers: List<Long>) {
        ballotCards.setMvrsBySampleNumber(sampleNumbers)
    }

    override fun ballotCards() = ballotCards
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