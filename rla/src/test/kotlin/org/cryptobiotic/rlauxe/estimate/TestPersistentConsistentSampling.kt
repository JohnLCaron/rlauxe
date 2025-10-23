package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.workflow.PersistedWorkflow
import org.cryptobiotic.rlauxe.audit.previousSamples
import kotlin.test.Test

class TestPersistentConsistentSampling {

    @Test //  TODO what can we assert ?
    fun testPersistentConsistentSampling() {
        val auditDir = "../core/src/test/data/workflow/testCliRoundPolling"
        val workflow = PersistedWorkflow(auditDir, true)
        val auditRecord = workflow.auditRecord
        val auditRound = workflow.auditRounds().last()

        println()
        println("auditRound = ${auditRound.roundIdx}")

//        val contest4 = auditRound.contestRounds.find { it.id == 4 }!!
//        contest4.auditorWantNewMvrs = 777

        repeat(auditRound.roundIdx) {
            val previousSamples = auditRecord.rounds.previousSamples(it+1)
            println("round = ${it+1} previousSample size = ${previousSamples.size}")
        }

        val previousSamples = auditRecord.rounds.previousSamples(auditRound.roundIdx)

        consistentSampling(auditRound, workflow.mvrManager(), previousSamples)
        val actualNewMvrs = auditRound.contestRounds.associate { it.contestUA.id to it.actualNewMvrs }
        println("last auditRound actualNewMvrs = $actualNewMvrs")
    }
}