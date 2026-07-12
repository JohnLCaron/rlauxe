package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.EstimateAudit
import org.cryptobiotic.rlauxe.util.OnlyTask
import org.cryptobiotic.rlauxe.estimate.removeContestsAndSample
import org.cryptobiotic.rlauxe.strata.Strata
import org.cryptobiotic.rlauxe.util.Stopwatch

// abstract superclass of workflows
 abstract class AuditWorkflow {
    abstract fun config() : Config
    abstract fun mvrManager() : MvrManager
    abstract fun auditRounds(): MutableList<AuditRoundIF>
    abstract fun contestsUA(): List<ContestWithAssertions>

    open fun strata(): List<Strata>? = null

    // start new round and create estimated sample sizes
    open fun startNewRound(quiet: Boolean = true, onlyTask: OnlyTask? = null, auditorMaxNewMvrs: Int? = null): AuditRound {
        val auditRounds = auditRounds()
        val previousRound = if (auditRounds.isEmpty()) null else auditRounds.last() as AuditRound
        val roundIdx = auditRounds.size + 1

        val config = config()
        val auditRound = if (previousRound == null) {
            // first time, create the round
            val contestRounds = contestsUA() // these have been filtered to InProgress only
                .filter{ onlyTask == null || it.id == onlyTask.contestId }
                .map { ContestRound(it, roundIdx) }
            AuditRound(roundIdx, contestRounds = contestRounds, countyStrata=strata(), samplePrns = emptyList())
        } else {
            // next time, create from previous round
            previousRound.createNextRound()
        }
        auditRounds.add(auditRound)

        logger.debug{"Estimate round ${roundIdx}"}
        val previousSamples = auditRounds.previousSamplePrns(roundIdx)
        val stopwatch = Stopwatch()

        val mvrManager = mvrManager()

        // estimate how many samples are needed for each contest, to satisfy the risk function,
        val estimate = EstimateAudit(
            topdir = mvrManager.topdir(),
            config,
            auditRound.roundIdx,
            auditRound.contestRounds,
            mvrManager().pools(),
            mvrManager().styles(),
            mvrManager.sortedManifest()
        )
        val need: Map<Int, List<Int>> = estimate.run()

        logger.debug{"Estimate round ${roundIdx} took ${stopwatch}"}

        // TODO better way to set auditorMaxNewMvrs ?
        // TODO maybe not on the first round ??
        if (auditorMaxNewMvrs != null) // hack-a-shack
            auditRound.auditorMaxNewMvrs = auditorMaxNewMvrs

        // this sets the following fields:
        //    auditRound.nmvrs = sampledCards.size
        //    auditRound.newmvrs = newMvrs
        //    auditRound.samplePrns = sampledCards.map { it.prn }
        //    contestRound.maxSampleAllowed = sampledCards.size
        removeContestsAndSample(
            config.round.sampling,
            mvrManager.samplingCards(),
            auditRound,
            previousSamples = previousSamples,
        )

        return auditRound
    }

    // 5. _Create MVRs_: enter the results of the manual audits (as Manual Vote Records, MVRs) into the system.
    //   fun setMvrsBySampleNumber(sampleNumbers: List<Long>)
    //   fun setMvrsForRound(mvrs: List<AuditableCard>)
    //   AuditRecord.enterMvrs(mvrFile: String)

    // 6. _Run the audit_
    abstract fun runAuditRound(auditRound: AuditRound, onlyTask: OnlyTask? = null, quiet: Boolean = true): Boolean  // return true if audit is complete

    fun writeMvrsForRound(round: Int): Int {
        return mvrManager().writeMvrsForRound(round)
    }

    companion object {
        private val logger = KotlinLogging.logger("AuditWorkflow")
    }
 }