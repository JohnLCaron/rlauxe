package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.persist.PersistentAudit
import org.cryptobiotic.rlauxe.audit.previousSamples
import kotlin.test.Test

class TestPersistentConsistentSampling {

    // @Test TODO failing
    fun testPersistentConsistentSampling() {
        val topdir = "/home/stormy/temp/persist/testRlaClcaFuzz"
        val workflow = PersistentAudit(topdir)
        val auditRecord = workflow.auditRecord
        val auditRound = workflow.auditRounds().last()

        println()
        println("auditRound = ${auditRound.roundIdx}")

//        val contest4 = auditRound.contestRounds.find { it.id == 4 }!!
//        contest4.auditorWantNewMvrs = 777

        repeat(auditRound.roundIdx) {
            val previousSamples = auditRecord.rounds.previousSamples(it+1)
            println("round = ${it+1} previousSamples = ${previousSamples.size}")
        }

        val previousSamples = auditRecord.rounds.previousSamples(auditRound.roundIdx)

        consistentSampling(auditRound, workflow.mvrManager(), previousSamples)
        val actualNewMvrs = auditRound.contestRounds.map { it.actualNewMvrs}
        println("actualNewMvrs = $actualNewMvrs")
    }
}