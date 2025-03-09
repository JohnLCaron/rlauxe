package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.estimateSampleSizes
import org.cryptobiotic.rlauxe.estimate.sample

// used in ConsistentSampling
interface RlauxWorkflowProxy {
    fun auditConfig() : AuditConfig
    fun getBallotsOrCvrs() : List<BallotOrCvr>
}

interface RlauxWorkflowIF: RlauxWorkflowProxy {
    fun auditRounds(): MutableList<AuditRound>
    fun contestUA(): List<ContestUnderAudit>
    fun cvrs(): List<Cvr>

    fun startNewRound(quiet: Boolean = true): AuditRound {
        val auditRounds = auditRounds()
        val previousRound = if (auditRounds.isEmpty()) null else auditRounds.last()
        val roundIdx = auditRounds.size + 1

        val auditRound = if (previousRound == null) {
            val contestRounds = contestUA().map { ContestRound(it, roundIdx) }
            AuditRound(roundIdx, contestRounds = contestRounds, sampledIndices = emptyList())
        } else {
            previousRound.createNextRound()
        }
        auditRounds.add(auditRound)

        estimateSampleSizes(
            auditConfig(),
            auditRound,
            cvrs(),
            show=!quiet,
        )

        auditRound.sampledIndices = sample(this, auditRound, auditRounds.previousSamples(roundIdx), quiet)
        return auditRound
    }

    fun runAudit(auditRound: AuditRound, mvrs: List<Cvr>, quiet: Boolean = true): Boolean  // return allDone
}