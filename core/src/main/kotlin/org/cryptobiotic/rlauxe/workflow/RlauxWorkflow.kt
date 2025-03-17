package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.estimate.estimateSampleSizes
import org.cryptobiotic.rlauxe.estimate.sample

// used in ConsistentSampling
interface RlauxWorkflowProxy {
    fun auditConfig() : AuditConfig
    fun ballotCards() : BallotCards
}

interface RlauxWorkflowIF: RlauxWorkflowProxy {
    fun auditRounds(): MutableList<AuditRound>
    fun contestsUA(): List<ContestUnderAudit>

    // start new round and create estimate
    fun startNewRound(quiet: Boolean = true): AuditRound {
        val auditRounds = auditRounds()
        val previousRound = if (auditRounds.isEmpty()) null else auditRounds.last()
        val roundIdx = auditRounds.size + 1

        val auditRound = if (previousRound == null) {
            val contestRounds = contestsUA().map { ContestRound(it, roundIdx) }
            AuditRound(roundIdx, contestRounds = contestRounds, sampleNumbers = emptyList(), sampledBorc = emptyList())
        } else {
            previousRound.createNextRound()
        }
        auditRounds.add(auditRound)

        if (!quiet) println("Estimate round ${roundIdx}")
        estimateSampleSizes(
            auditConfig(),
            auditRound,
        )

        sample(this, auditRound, auditRounds.previousSamples(roundIdx), quiet)
        return auditRound
    }

    // you have to set the mvrs before you run the audit
    fun setMvrsBySampleNumber(sampleNumbers: List<Long>)
    fun runAudit(auditRound: AuditRound, quiet: Boolean = true): Boolean  // return allDone
}