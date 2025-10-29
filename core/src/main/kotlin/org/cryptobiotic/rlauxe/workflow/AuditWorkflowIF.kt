package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.estimateSampleSizes
import org.cryptobiotic.rlauxe.estimate.sampleCheckLimits
import org.cryptobiotic.rlauxe.util.Stopwatch

private val logger = KotlinLogging.logger("RlauxAuditIF")

// abstraction for running an audit.
interface AuditWorkflowIF {
    fun auditConfig() : AuditConfig
    fun mvrManager() : MvrManager
    fun auditRounds(): MutableList<AuditRound>
    fun contestsUA(): List<ContestUnderAudit>

    // start new round and create estimate
    fun startNewRound(quiet: Boolean = true): AuditRound {
        val auditRounds = auditRounds()
        val previousRound = if (auditRounds.isEmpty()) null else auditRounds.last()
        val roundIdx = auditRounds.size + 1

        val auditRound = if (previousRound == null) {
            // first time, create the rounds
            val contestRounds = contestsUA().filter { !auditConfig().skipContests.contains(it.id) }.map { ContestRound(it, roundIdx) }
            AuditRound(roundIdx, contestRounds = contestRounds, samplePrns = emptyList(), sampledBorc = emptyList())
        } else {
            // next time, create from previous round
            previousRound.createNextRound()
        }
        auditRounds.add(auditRound)

        logger.info{"Estimate round ${roundIdx}"}
        val stopwatch = Stopwatch()

        // 1. _Estimation_: for each contest, estimate how many samples are needed to satisfy the risk function,
        // only need cvrIterator for OneAudit
        val cvrIterator = if (auditConfig().auditType != AuditType.ONEAUDIT) null else mvrManager().sortedCvrs().iterator()

        estimateSampleSizes(
            auditConfig(),
            auditRound,
            cvrIterator = cvrIterator,
            // nthreads=1,
        )
        logger.info{"Estimate round ${roundIdx} took ${stopwatch}"}

        // 2. _Choosing sample sizes_: the Auditor decides which contests and how many samples will be audited.
        // 3. _Random sampling_: The actual ballots to be sampled are selected randomly based on a carefully chosen random seed.
        sampleCheckLimits(
            auditConfig(),
            mvrManager(),
            auditRound,
            auditRounds.previousSamples(roundIdx),
            quiet)

        return auditRound
    }

    // 5. _Create MVRs_: enter the results of the manual audits (as Manual Vote Records, MVRs) into the system.
    // fun setMvrsBySampleNumber(sampleNumbers: List<Long>)
    // fun setMvrsForRound(mvrs: List<AuditableCard>)
    // AuditRecord.enterMvrs(mvrFile: String)

    // 6. _Run the audit_
    fun runAuditRound(auditRound: AuditRound, quiet: Boolean = true): Boolean  // return complete
}