package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.persist.json.writeAuditRoundJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeSampleNumbersJsonFile

/** Created from persistent state. See rla/src/main/kotlin/org/cryptobiotic/rlauxe/cli/RunRlaStartFuzz.kt */
class PersistentAudit(
    val inputDir: String,
): RlauxAuditIF {
    val auditRecord: AuditRecord = AuditRecord.readFrom(inputDir)
    private val auditConfig: AuditConfig = auditRecord.auditConfig
    private val contestsUA: List<ContestUnderAudit> = auditRecord.contests
    private val auditRounds = mutableListOf<AuditRound>()
    private val mvrManager: MvrManager by lazy { makeMvrManager(auditRecord.location, auditConfig) }

    init {
        auditRounds.addAll(auditRecord.rounds)
    }

    override fun startNewRound(quiet: Boolean): AuditRound {
        val nextRound = super.startNewRound(quiet)

        if (nextRound.sampleNumbers.isEmpty()) {
            println("*** FAILED TO GET ANY SAMPLES (PersistentAudit)")
            nextRound.auditIsComplete = true
        } else {
            val publisher = Publisher(inputDir)

            writeAuditRoundJsonFile(nextRound, publisher.auditRoundFile(nextRound.roundIdx))
            println("   writeAuditStateJsonFile ${publisher.auditRoundFile(nextRound.roundIdx)}")

            writeSampleNumbersJsonFile(nextRound.sampleNumbers, publisher.sampleNumbersFile(nextRound.roundIdx))
            println("   writeSampleIndicesJsonFile ${publisher.sampleNumbersFile(nextRound.roundIdx)}")
        }

        return nextRound
    }

    // 6. _Run the audit_: For each contest, calculate if the risk limit is satisfied, based on the manual audits.
    //  return complete
    override fun runAuditRound(auditRound: AuditRound, quiet: Boolean): Boolean  { // return complete
        val roundIdx = auditRound.roundIdx

        // TODO
        //   in a real audit, we need to set the real mvrs
        //   val enterMvrsOk = workflow.auditRecord.enterMvrs(mvrFile)
        //   instead, we assume its a test for now
        val sampledMvrs = (mvrManager as MvrManagerTest).setMvrsForRoundIdx(roundIdx)
        if (!quiet) println("  added ${sampledMvrs.size} mvrs to mvrManager")

        val complete =  when (auditConfig.auditType) {
            AuditType.CLCA -> runClcaAudit(auditConfig, auditRound.contestRounds, mvrManager() as MvrManagerClca, auditRound.roundIdx, auditor = AuditClcaAssertion(quiet))
            AuditType.POLLING -> runPollingAudit(auditConfig, auditRound.contestRounds, mvrManager() as MvrManagerPolling, auditRound.roundIdx, quiet)
            AuditType.ONEAUDIT -> runClcaAudit(auditConfig, auditRound.contestRounds, mvrManager() as MvrManagerClca, auditRound.roundIdx, auditor = OneAuditClcaAssertion(quiet))
        }

        auditRound.auditWasDone = true
        auditRound.auditIsComplete = complete

        // overwriting state with audit info, a bit messy TODO separate estimation and audit?
        val publisher = Publisher(inputDir)

        writeAuditRoundJsonFile(auditRound, publisher.auditRoundFile(roundIdx))
        if (!quiet) println("    writeAuditRoundJsonFile to '${publisher.auditRoundFile(roundIdx)}'")

        writeAuditableCardCsvFile(sampledMvrs , publisher.sampleMvrsFile(roundIdx)) // TODO
        if (!quiet) println("    write sampledMvrs to '${publisher.sampleMvrsFile(roundIdx)}'")

        return complete
    }

    override fun auditConfig() =  this.auditConfig
    override fun mvrManager() = mvrManager
    override fun auditRounds() = auditRounds
    override fun contestsUA(): List<ContestUnderAudit> = contestsUA
}