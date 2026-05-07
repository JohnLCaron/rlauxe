package org.cryptobiotic.rlauxe.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.OnlyTask
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.AuditRecordIF
import org.cryptobiotic.rlauxe.persist.CompositeRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.writeAuditRoundConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditRoundJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeSamplePrnsJsonFile

/** AuditWorkflow with persistent state. */
class PersistedWorkflow(
    val auditRecord: AuditRecordIF,
    val mvrWrite: Boolean = true, // skip writing when doing RunRoundAgain
): AuditWorkflow() {
    val auditDir = auditRecord.location
    val publisher = Publisher(auditDir)

    private val config: Config
    private val auditContests: List<ContestWithAssertions>
    private val auditRounds = mutableListOf<AuditRoundIF>()
    private val mvrManager: MvrManager
    private val mvrSource: MvrSource

    init {
        config = auditRecord.config
        mvrSource = config.election.mvrSource
        // skip contests that have been removed
        auditContests = auditRecord.contests.filter { it.preAuditStatus == TestH0Status.InProgress }
        auditRounds.addAll(auditRecord.rounds)

        mvrManager = when {
            (auditRecord is CompositeRecord) -> CompositeMvrManager(auditRecord, config, auditContests)
            (mvrSource == MvrSource.testClcaSimulated) -> PersistedMvrManagerTest(auditRecord as AuditRecord)
            else -> PersistedMvrManager(auditRecord as AuditRecord, mvrWrite=mvrWrite)
        }
    }

    override fun config() =  this.config
    override fun mvrManager() = mvrManager
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestWithAssertions> = auditContests

    override fun startNewRound(quiet: Boolean, onlyTask: OnlyTask?, auditorWantNewMvrs: Int?): AuditRound {

        val nextRound = super.startNewRound(quiet, onlyTask, auditorWantNewMvrs)

        if (nextRound.samplePrns.isEmpty()) {
            logger.warn {"*** FAILED TO GET ANY SAMPLES (PersistentAudit)"}
            nextRound.auditIsComplete = true
        } else {
            // heres where we limit the number of samples we are willing to audit
            val riskMeasuringSampleLimit = config.creation.riskMeasuringSampleLimit
            if (riskMeasuringSampleLimit != null && nextRound.samplePrns.size > riskMeasuringSampleLimit) {
                nextRound.samplePrns = nextRound.samplePrns.subList(0, riskMeasuringSampleLimit)
            }

            val auditRoundConfig = config.round
            writeAuditRoundConfigJsonFile(auditRoundConfig, publisher.auditRoundConfigFile(nextRound.roundIdx))
            logger.info {"startNewRound writeAuditRoundConfig to ${publisher.auditRoundConfigFile(nextRound.roundIdx)}"}

            writeAuditRoundJsonFile(nextRound, publisher.auditEstFile(nextRound.roundIdx))
            logger.info {"startNewRound writeAuditEstimation to ${publisher.auditEstFile(nextRound.roundIdx)}"}

            writeSamplePrnsJsonFile(nextRound.samplePrns, publisher.samplePrnsFile(nextRound.roundIdx))
            logger.info {"startNewRound ${nextRound.samplePrns.size} samplePrns written to ${publisher.samplePrnsFile(nextRound.roundIdx)}"}
        }

        return nextRound
    }

    override fun runAuditRound(auditRound: AuditRound, onlyTask: OnlyTask?, quiet: Boolean): Boolean  { // return complete
        val roundIdx = auditRound.roundIdx

        //   in a real audit, need to set the real mvrs externally with EnterMvrsCli, which calls auditRecord.enterMvrs(mvrs)
        //   in a test audit, the test mvrs are generated from the cardManifest, with optional fuzzing
        if (mvrManager is PersistedMvrManagerTest) {
            val sampledMvrs = mvrManager.setMvrsForRoundIdx(roundIdx)
            logger.info {"  added ${sampledMvrs.size} mvrs to mvrManager"}
        }

        val complete =  when (config.auditType) {
            AuditType.CLCA -> runClcaAuditRound(config, auditRound, mvrManager, auditRound.roundIdx,
                auditor = ClcaAssertionAuditor(quiet), onlyTask=onlyTask)
            AuditType.ONEAUDIT -> runClcaAuditRound(config, auditRound, mvrManager, auditRound.roundIdx,
                auditor = OneAuditAssertionAuditor(mvrManager().pools()!!, quiet), onlyTask=onlyTask, )
            AuditType.POLLING -> runPollingAuditRound(config, auditRound, mvrManager, auditRound.roundIdx)
        }

        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete

        writeAuditRoundJsonFile(auditRound, publisher.auditFile(roundIdx))
        logger.info {"runAuditRound writeAuditState to '${publisher.auditFile(roundIdx)}'"}

        return complete
    }

    override fun toString(): String {
        return "PersistentWorkflow(auditDir='$auditDir', mode=$mvrSource, mvrManager=${mvrManager.javaClass.simpleName})"
    }

    companion object {
        private val logger = KotlinLogging.logger("PersistedWorkflow")

        fun readFrom(location: String): PersistedWorkflow? {
            val auditRecord = AuditRecord.read(location)
            return if (auditRecord == null) null
            else PersistedWorkflow(auditRecord as AuditRecord) // TODO CompositeRecord
        }
    }
}
