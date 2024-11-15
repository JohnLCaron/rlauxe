package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlaux.core.raire.readRaireCvrs
import org.cryptobiotic.rlauxe.core.AuditType
import org.cryptobiotic.rlauxe.raire.*
import kotlin.test.Test

class TestStylishWorkflow {

    @Test
    fun testWorkflow() {
        val rr = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json")
        val raireResults = rr.import()
        println(raireResults.show())

        // This single contest cvr file is the only real cvr data in SHANGRLA
        // //         'cvr_file':       './data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire',
        val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireCvrs(cvrFile)
        // theres only one contest unfortunately.
        // otherwise we have to match up the raireResults with the cvrs?
        // which begs the question of the "original cvrs"
        val cvrs = raireCvrs.contests.first().cvrs

        // data class AuditParams(val riskLimit: Double, val seed: Long, val auditType: AuditType)
        val auditParams = AuditParams(0.05, seed = 1234567890L, AuditType.CARD_COMPARISON)

        //    contests: List<Contest>, // the contests you want to audit
        //    raireContests: List<RaireContestUnderAudit>, // TODO or call raire from here ??
        //    val auditParams: AuditParams,
        //    val ballotManifest: BallotManifest,
        //    val cvrs: List<Cvr>,
        //    val upperBounds: Map<Int, Int>, // 𝑁_𝑐. Or should this be part of Contest?
        val workflow = StylishWorkflow(emptyList(),  raireResults.contests, auditParams, BallotManifest(), cvrs, mapOf(339 to cvrs.size))

        val indices = workflow.chooseSamples()
        workflow.runAudit(indices, mvrs = emptyList())
    }
}