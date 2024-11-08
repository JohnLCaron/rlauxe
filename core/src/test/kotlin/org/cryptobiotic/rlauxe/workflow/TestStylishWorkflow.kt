package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlaux.core.raire.readRaireCvrs
import org.cryptobiotic.rlauxe.core.AuditType
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.csv.readDominionBallotManifest
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.sampling.BallotManifest
import org.cryptobiotic.rlauxe.util.mean2margin
import kotlin.test.Test
import kotlin.test.assertEquals

class TestStylishWorkflow {

    @Test
    fun workflow() {
        val rr = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json")
        val raireResults = rr.import()
        println(raireResults.show())

        // This single contest cvr file is the only real cvr data in SHANGRLA
        // //         'cvr_file':       './data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire',
        val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireCvrs(cvrFile)
        val contests = raireCvrs.contests.map { it.toContest() }
        val cvrs = raireCvrs.contests.first().cvrs

        // data class AuditParams(val riskLimit: Double, val seed: Long, val auditType: AuditType)
        val auditParams = AuditParams(0.05, seed = 1234567890L, AuditType.CARD_COMPARISON)

        // val contests: List<Contest>, // the contests you want to audit
        //    val auditParams: AuditParams,
        //    val ballotManifest: BallotManifest,
        //    val cvrs: List<Cvr>,
        //    val upperBounds: Map<Int, Int>
        val workflow = StylishWorkflow(contests, auditParams, BallotManifest(), cvrs, mapOf(339 to cvrs.size))

        workflow.generateAssertions()
    }
}