package org.cryptobiotic.rlauxe.workflow

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.existsOrZip
import org.cryptobiotic.rlauxe.persist.json.writeAuditRoundJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeSamplePrnsJsonFile

private val logger = KotlinLogging.logger("PersistentAudit")

/** AuditWorkflow with persistent state. */
class PersistedWorkflow(
    val auditDir: String,
    val useTest: Boolean,
): AuditWorkflowIF {
    val auditRecord: AuditRecord // TODO need auditConfig, contests in record
    val publisher = Publisher(auditDir)

    private val auditConfig: AuditConfig
    private val contestsUA: List<ContestUnderAudit>
    private val auditRounds = mutableListOf<AuditRound>()
    private val mvrManager: MvrManager

    init {
        val auditRecordResult = AuditRecord.readFromResult(auditDir)
        if (auditRecordResult is Ok) {
            auditRecord = auditRecordResult.unwrap()
        } else {
            logger.error{ auditRecordResult.toString() }
            throw RuntimeException( auditRecordResult.toString() )
        }

        auditConfig = auditRecord.auditConfig
        contestsUA = auditRecord.contests

        auditRounds.addAll(auditRecord.rounds)
        mvrManager = if (useTest || existsOrZip(publisher.sortedMvrsFile())) {
            MvrManagerTestFromRecord(auditRecord.location)
        } else {
            MvrManagerFromRecord(auditRecord.location)
        }
    }

    override fun startNewRound(quiet: Boolean): AuditRound {

        val nextRound = super.startNewRound(quiet)

        if (nextRound.samplePrns.isEmpty()) {
            logger.warn {"*** FAILED TO GET ANY SAMPLES (PersistentAudit)"}
            nextRound.auditIsComplete = true
        } else {
            val publisher = Publisher(auditDir)

            writeAuditRoundJsonFile(nextRound, publisher.auditRoundFile(nextRound.roundIdx))
            logger.info {"   writeAuditStateJsonFile ${publisher.auditRoundFile(nextRound.roundIdx)}"}

            writeSamplePrnsJsonFile(nextRound.samplePrns, publisher.samplePrnsFile(nextRound.roundIdx))
            logger.info {"   writeSampleIndicesJsonFile ${publisher.samplePrnsFile(nextRound.roundIdx)}"}
        }

        return nextRound
    }

    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  { // return complete
        val roundIdx = auditRound.roundIdx

        // TODO
        //   in a real audit, we need to set the real mvrs externally with auditRecord.enterMvrs(mvrs)
        //   in a test audit, the test mvrs are in "private/testMvrs.csv"
        if (mvrManager is MvrManagerTestFromRecord) {
            val sampledMvrs = mvrManager.setMvrsForRoundIdx(roundIdx)
            logger.info {"  added ${sampledMvrs.size} mvrs to mvrManager"}
        }

        val complete =  when (auditConfig.auditType) {
            AuditType.CLCA -> runClcaAuditRound(auditConfig, auditRound.contestRounds, mvrManager as MvrManagerClcaIF, auditRound.roundIdx, auditor = ClcaAssertionAuditor(quiet))
            AuditType.POLLING -> runPollingAuditRound(auditConfig, auditRound.contestRounds, mvrManager as MvrManagerPollingIF, auditRound.roundIdx, quiet)
            AuditType.ONEAUDIT -> runClcaAuditRound(auditConfig, auditRound.contestRounds, mvrManager as MvrManagerClcaIF, auditRound.roundIdx, auditor = OneAuditAssertionAuditor(quiet))
        }

        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete

        val publisher = Publisher(auditDir)
        writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(roundIdx))
        logger.info {"    writeAuditRoundJsonFile to '${publisher.auditRoundFile(roundIdx)}'"}

        return complete
    }

    override fun auditConfig() =  this.auditConfig
    override fun mvrManager() = mvrManager
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA

    override fun toString(): String {
        return "PersistentAudit(auditDir='$auditDir', useTest=$useTest, mvrManager=$mvrManager)"
    }

}