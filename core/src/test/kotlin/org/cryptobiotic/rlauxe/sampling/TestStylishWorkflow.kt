package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.AuditType
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestStylishWorkflow {

    @Test
    fun testWorkflow() {
        val stopwatch = Stopwatch()
        val raireResults = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json").import()
        // println(raireResults.show())

        // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallots(cvrFile)
        // theres only one contest unfortunately.
        // otherwise we have to match up the raireResults with the cvrs?
        // which begs the question of where are the "original cvrs"
        val cvrs = raireCvrs.cvrs

        // data class AuditParams(val riskLimit: Double, val seed: Long, val auditType: AuditType)
        val auditParams = AuditParams(0.05, seed = 1234567890L, AuditType.CARD_COMPARISON)

        //    contests: List<Contest>, // the contests you want to audit
        //    raireContests: List<RaireContestUnderAudit>, // TODO or call raire from here ??
        //    val auditParams: AuditParams,
        //    val ballotManifest: BallotManifest,
        //    val cvrs: List<Cvr>,
        //    val upperBounds: Map<Int, Int>, // 𝑁_𝑐. Or should this be part of Contest?
        val workflow = StylishWorkflow(emptyList(), raireResults.contests, auditParams, cvrs, mapOf(339 to cvrs.size))
        println("initialize took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        var done = false
        val allMvrs = mutableListOf<CvrIF>()
        var round = 0
        while (!done) {
            // currently overestimating sample size, possibly because its raire
            val indices = workflow.chooseSamples(allMvrs, round)
            println("$round chooseSamples took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            stopwatch.start()

            done = workflow.runAudit(indices, allMvrs)
            println("$round runAudit took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
            round++
        }
    }
}

// translate sample indices to lists for the auditors to find those ballots
fun makeAuditList() {

    // n_sampled_phantoms = np.sum(sampled_cvr_indices > manifest_cards)
    //print(f'The sample includes {n_sampled_phantoms} phantom cards.')

    // cards_to_retrieve, sample_order, cvr_sample, mvr_phantoms_sample = \
    //    Dominion.sample_from_cvrs(cvr_list, manifest, sampled_cvr_indices)

    // # write the sample
    //Dominion.write_cards_sampled(audit.sample_file, cards_to_retrieve, print_phantoms=False)
}