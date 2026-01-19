package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.AuditRecordIF
import org.cryptobiotic.rlauxe.persist.CompositeRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.writeAuditRoundJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeSamplePrnsJsonFile

private val logger = KotlinLogging.logger("PersistedWorkflow")

enum class PersistedWorkflowMode {
    real,           // use PersistedMvrManager;  sampleMvrs$round.csv must be written from external program.
    testSimulated,  // use PersistedMvrManagerTest which fuzzes the cvrs on the fly
    testPrivateMvrs  // use PersistedMvrManager; use private/sortedMvrs.csv to write sampleMvrs$round.csv
}

/** AuditWorkflow with persistent state. */
class PersistedWorkflow(
    val auditRecord: AuditRecordIF,
    val mvrWrite: Boolean = true,
): AuditWorkflow() {
    val auditDir = auditRecord.location
    val publisher = Publisher(auditDir)

    private val config: AuditConfig
    private val auditContests: List<ContestWithAssertions>
    private val auditRounds = mutableListOf<AuditRoundIF>()
    private val mvrManager: MvrManager
    private val mode: PersistedWorkflowMode

    init {
        config = auditRecord.config
        mode = config.persistedWorkflowMode
        // skip contests that have been removed
        auditContests = auditRecord.contests.filter { it.preAuditStatus == TestH0Status.InProgress }
        auditRounds.addAll(auditRecord.rounds)

        mvrManager = when {
            (auditRecord is CompositeRecord) ->
                // TODO mode
                CompositeMvrManager(auditRecord, config, auditContests)
            (mode == PersistedWorkflowMode.testSimulated) ->
                PersistedMvrManagerTest(auditRecord.location, config, auditContests)
            else ->
                PersistedMvrManager(auditRecord.location, config, auditContests, mvrWrite=mvrWrite)
        }
    }

    override fun auditConfig() =  this.config
    override fun mvrManager() = mvrManager
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestWithAssertions> = auditContests

    override fun startNewRound(quiet: Boolean, onlyTask: String?): AuditRoundIF {

        val nextRound = super.startNewRound(quiet, onlyTask)

        if (nextRound.samplePrns.isEmpty()) {
            logger.warn {"*** FAILED TO GET ANY SAMPLES (PersistentAudit)"}
            nextRound.auditIsComplete = true
        } else {
            // heres where we limit the number of samples we are willing to audit
            if (config.auditSampleLimit != null ) {
                nextRound.samplePrns = nextRound.samplePrns.subList(0, config.auditSampleLimit)
            }

            writeAuditRoundJsonFile(nextRound, publisher.auditStateFile(nextRound.roundIdx))
            logger.info {"startNewRound writeAuditState ${publisher.auditStateFile(nextRound.roundIdx)}"}

            writeSamplePrnsJsonFile(nextRound.samplePrns, publisher.samplePrnsFile(nextRound.roundIdx))
            logger.info {"startNewRound writeSamplePrns ${publisher.samplePrnsFile(nextRound.roundIdx)}"}
        }

        return nextRound
    }

    override fun runAuditRound(auditRound: AuditRoundIF, quiet: Boolean): Boolean  { // return complete
        val roundIdx = auditRound.roundIdx

        //   in a real audit, need to set the real mvrs externally with EnterMvrsCli, which calls auditRecord.enterMvrs(mvrs)
        //   in a test audit, the test mvrs are generated from the cardManifest, with optional fuzzing
        if (mvrManager is PersistedMvrManagerTest) {
            val sampledMvrs = mvrManager.setMvrsForRoundIdx(roundIdx)
            logger.info {"  added ${sampledMvrs.size} mvrs to mvrManager"}
        }

        val complete =  when (config.auditType) {
            AuditType.CLCA -> runClcaAuditRound(config, auditRound.contestRounds, mvrManager, auditRound.roundIdx, auditor = ClcaAssertionAuditor(quiet))
            AuditType.POLLING -> runPollingAuditRound(config, auditRound.contestRounds, mvrManager, auditRound.roundIdx, quiet)
            AuditType.ONEAUDIT -> runClcaAuditRound(config, auditRound.contestRounds, mvrManager, auditRound.roundIdx,
                auditor = OneAuditAssertionAuditor(mvrManager().oapools()!!, quiet))
        }

        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete

        writeAuditRoundJsonFile(auditRound, publisher.auditStateFile(roundIdx)) // replace auditState
        logger.info {"runAuditRound writeAuditState to '${publisher.auditStateFile(roundIdx)}'"}

        return complete
    }

    override fun toString(): String {
        return "PersistentWorkflow(auditDir='$auditDir', mode=$mode, mvrManager=${mvrManager.javaClass.simpleName})"
    }

    companion object {
        fun readFrom(location: String): PersistedWorkflow? {
            val auditRecord = AuditRecord.readFrom(location)
            return if (auditRecord == null) null
            else PersistedWorkflow(auditRecord)
        }
    }
}
