package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test

class TestRaireWorkflowFromJson {

    @Test
    fun testRaireComparisonWithStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 12356667890L, fuzzPct = null, ntrials=10))
    }

    @Test
    fun testRaireComparisonNoStyle() {
        testRaireWorkflow(AuditConfig(AuditType.CARD_COMPARISON, hasStyles=false, seed = 123568667890L, fuzzPct = null, ntrials=10))
    }

    fun testRaireWorkflow(auditConfig: AuditConfig) {
        val stopwatch = Stopwatch()

         // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallots(cvrFile)
        val rcontests = raireCvrs.contests
        val cvrs = raireCvrs.cvrs

        // The corresponding assertions file that has already been generated.
        val ncs = raireCvrs.contests.associate { Pair(it.contestNumber.toString(), it.ncvrs + 2) }
        val nps = raireCvrs.contests.associate { Pair(it.contestNumber.toString(), 2) }
        val raireResults = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json")
            .import(ncs, nps)
        print(raireResults.show())

        // check consistencey
        raireResults.contests.forEach { rrc ->
            val rc = rcontests.find { it.contestNumber == rrc.id }
            requireNotNull(rc)
            require(rc.candidates == rrc.candidates)
            // TODO rrc.ncvrs = rc.ncvrs
            // TODO rrc.Nc = rc.ncvrs
        }

        val nassertions = raireResults.contests.sumOf { it.assertions.size }
        val workflow = ComparisonWorkflow(auditConfig, emptyList(), raireResults.contests, cvrs)
        runComparisonWorkflow(workflow, cvrs, nassertions)
    }

}