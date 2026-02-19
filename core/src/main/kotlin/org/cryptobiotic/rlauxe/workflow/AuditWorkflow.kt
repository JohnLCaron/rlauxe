package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.estimateSampleSizes
import org.cryptobiotic.rlauxe.estimate.sampleWithContestCutoff
import org.cryptobiotic.rlauxe.util.Stopwatch

private val logger = KotlinLogging.logger("RlauxAuditIF")

// abstract superclass of workflows
 abstract class AuditWorkflow {
    abstract fun auditConfig() : AuditConfig
    abstract fun mvrManager() : MvrManager
    abstract fun auditRounds(): MutableList<AuditRoundIF>
    abstract fun contestsUA(): List<ContestWithAssertions>

    // start new round and create estimated sample sizes
    open fun startNewRound(quiet: Boolean = true, onlyTask: String? = null): AuditRound {
        val auditRounds = auditRounds()
        val previousRound = if (auditRounds.isEmpty()) null else auditRounds.last()
        val roundIdx = auditRounds.size + 1

        val auditConfig = auditConfig()
        val auditRound = if (previousRound == null) {
            // first time, create the round
            val contestRounds = contestsUA()
                .filter { !auditConfig.skipContests.contains(it.id) }
                .map { ContestRound(it, roundIdx) }
            AuditRound(roundIdx, contestRounds = contestRounds, samplePrns = emptyList())
        } else {
            // next time, create from previous round
            previousRound.createNextRound()
        }
        auditRounds.add(auditRound)

        logger.debug{"Estimate round ${roundIdx}"}
        val previousSamples = auditRounds.previousSamples(roundIdx)
        val stopwatch = Stopwatch()

        val mvrManager = mvrManager()
        val cardManifest = mvrManager.cardManifest()

        // for each contest, estimate how many samples are needed to satisfy the risk function,
        estimateSampleSizes(
            auditConfig,
            auditRound,
            cardManifest = cardManifest,
            cardPools = mvrManager().oapools(),
            previousSamples,
            // nthreads=1,
            onlyTask = onlyTask,
        )
        logger.debug{"Estimate round ${roundIdx} took ${stopwatch}"}

        // this sets the following fields:
        //    auditRound.nmvrs = sampledCards.size
        //    auditRound.newmvrs = newMvrs
        //    auditRound.samplePrns = sampledCards.map { it.prn }
        //    contestRound.maxSampleAllowed = sampledCards.size
        sampleWithContestCutoff(
            auditConfig,
            cardManifest,
            auditRound,
            previousSamples = previousSamples,
            quiet)

        return auditRound
    }

    // 5. _Create MVRs_: enter the results of the manual audits (as Manual Vote Records, MVRs) into the system.
    //   fun setMvrsBySampleNumber(sampleNumbers: List<Long>)
    //   fun setMvrsForRound(mvrs: List<AuditableCard>)
    //   AuditRecord.enterMvrs(mvrFile: String)

    // 6. _Run the audit_
    abstract fun runAuditRound(auditRound: AuditRound, onlyTask: String? = null, quiet: Boolean = true): Boolean  // return true if audit is complete
}