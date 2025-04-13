package org.cryptobiotic.rlauxe.persist

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.writeAuditRoundJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeSamplePrnsJsonFile
import org.cryptobiotic.rlauxe.workflow.*
import java.nio.file.Files
import java.nio.file.Path

/** Created from persistent state. See rla/src/main/kotlin/org/cryptobiotic/rlauxe/cli/RunRlaStartFuzz.kt */
class PersistentAudit(
    val auditDir: String,
    val useTest: Boolean,
): RlauxAuditIF {
    val auditRecord: AuditRecord = AuditRecord.readFrom(auditDir) // TODO need auditConfig, contests in record
    private val auditConfig: AuditConfig = auditRecord.auditConfig
    private val contestsUA: List<ContestUnderAudit> = auditRecord.contests
    private val auditRounds = mutableListOf<AuditRound>()
    private val mvrManager: MvrManager

    init {
        auditRounds.addAll(auditRecord.rounds)
        mvrManager = if (useTest || Files.exists(Path.of("$auditDir/private/testMvrs.csv"))) {
            MvrManagerTestFromRecord(auditRecord.location)
        } else {
            MvrManagerFromRecord(auditRecord.location)
        }
    }

    override fun startNewRound(quiet: Boolean): AuditRound {

        val nextRound = super.startNewRound(quiet)

        if (nextRound.samplePrns.isEmpty()) {
            println("*** FAILED TO GET ANY SAMPLES (PersistentAudit)")
            nextRound.auditIsComplete = true
        } else {
            val publisher = Publisher(auditDir)

            writeAuditRoundJsonFile(nextRound, publisher.auditRoundFile(nextRound.roundIdx))
            println("   writeAuditStateJsonFile ${publisher.auditRoundFile(nextRound.roundIdx)}")

            writeSamplePrnsJsonFile(nextRound.samplePrns, publisher.samplePrnsFile(nextRound.roundIdx))
            println("   writeSampleIndicesJsonFile ${publisher.samplePrnsFile(nextRound.roundIdx)}")
        }

        return nextRound
    }

    // 6. _Run the audit_: For each contest, calculate if the risk limit is satisfied, based on the manual audits.
    //  return complete
    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  { // return complete
        val roundIdx = auditRound.roundIdx

        // TODO
        //   in a real audit, we need to set the real mvrs externally with auditRecord.enterMvrs(mvrs)
        //   in a test audit, the test mvrs are in "private/testMvrs.csv"
        if (mvrManager is MvrManagerTestFromRecord) {
            val sampledMvrs = mvrManager.setMvrsForRoundIdx(roundIdx)
            if (!quiet) println("  added ${sampledMvrs.size} mvrs to mvrManager")
        }

        val complete =  when (auditConfig.auditType) {
            AuditType.CLCA -> runClcaAudit(auditConfig, auditRound.contestRounds, mvrManager as MvrManagerClcaIF, auditRound.roundIdx, auditor = AuditClcaAssertion(quiet))
            AuditType.POLLING -> runPollingAudit(auditConfig, auditRound.contestRounds, mvrManager as MvrManagerPollingIF, auditRound.roundIdx, quiet)
            AuditType.ONEAUDIT -> runClcaAudit(auditConfig, auditRound.contestRounds, mvrManager as MvrManagerClcaIF, auditRound.roundIdx, auditor = OneAuditClcaAssertion(quiet))
        }

        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete

        val publisher = Publisher(auditDir)
        writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(roundIdx))
        if (!quiet) println("    writeAuditRoundJsonFile to '${publisher.auditRoundFile(roundIdx)}'")

        return complete
    }

    override fun auditConfig() =  this.auditConfig
    override fun mvrManager() = mvrManager
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
}