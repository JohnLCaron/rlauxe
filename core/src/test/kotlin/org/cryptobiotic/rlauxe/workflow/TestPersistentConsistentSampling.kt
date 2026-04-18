package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.previousSamplePrns
import org.cryptobiotic.rlauxe.estimate.consistentSampling
import org.cryptobiotic.rlauxe.persist.AuditRecord
import kotlin.test.Test

class TestPersistentConsistentSampling {

    @Test   //  TODO what the hell are we testing ??
    fun testPersistentConsistentSampling() {
        val auditDir = "../core/src/test/data/testRunCli/polling/audit"
        val auditRecord = AuditRecord.read(auditDir)
        if (auditRecord == null) {
            println("auditRecord not found at $auditDir")
            return
        }
        require(auditRecord is AuditRecord)

        val auditRound = auditRecord.rounds.last()

        println()
        println("auditRound = ${auditRound.roundIdx}")

        repeat(auditRound.roundIdx) {
            val previousSamples = auditRecord.rounds.previousSamplePrns(it+1)
            println("round = ${it+1} previousSample size = ${previousSamples.size}")
        }
        val previousSamples = auditRecord.rounds.previousSamplePrns(auditRound.roundIdx)

        val workflow = PersistedWorkflow(auditRecord, true)
        consistentSampling(auditRound, workflow.mvrManager().sortedManifest(), previousSamples)
        //val actualNewMvrs = auditRound.contestRounds.associate { it.contestUA.id to it.actualNewMvrs }
        //println("last auditRound actualNewMvrs = $actualNewMvrs")
    }
}