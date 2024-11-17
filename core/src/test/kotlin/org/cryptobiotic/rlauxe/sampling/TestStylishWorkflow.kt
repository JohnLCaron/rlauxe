package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.AuditType
import org.cryptobiotic.rlauxe.core.CvrIF
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.raire.*
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.df
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class TestStylishWorkflow {

    @Test
    fun testWorkflow() {
        val stopwatch = Stopwatch()
        val auditParams = AuditParams(0.05, seed = 1234567890L, AuditType.CARD_COMPARISON)

         // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallots(cvrFile)
        val rcontests = raireCvrs.contests
        val cvrs = raireCvrs.cvrs

        // The corresponding assertions file that has already been generated.
        val raireResults = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json").import()

        // check consistencey
        raireResults.contests.forEach { rrc ->
            val rc = rcontests.find { it.contestNumber == rrc.id }
            requireNotNull(rc)
            require(rc.candidates == rrc.candidates)
            rrc.ncvrs = rc.ncvrs
        }

        val workflow = StylishWorkflow(emptyList(), raireResults.contests, auditParams, cvrs, mapOf(339 to cvrs.size))
        println("initialize took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")
        stopwatch.start()

        // form simulated mvrs. TODO kinda dicey because these are intended for a single assorter
        val contestUA = workflow.contestsUA.first()
        val assorter = contestUA.comparisonAssertions.first().assorter
        val sampler = ComparisonSamplerSimulation(workflow.cvrsUA, contestUA, assorter)
        val cvrPairs: List<Pair<CvrIF, CvrUnderAudit>> = sampler.mvrs.zip(sampler.cvrs)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }
        var count = 0
        cvrPairs.forEach { (mvr, cvr) ->
            if (mvr.votes != cvr.votes) {
                count++
            }
        }
        println("diff = $count out of ${cvrPairs.size} = ${df(count.toDouble()/cvrPairs.size)}")

        var done = false
        val sampledMvrs = mutableListOf<CvrIF>()
        var round = 0
        while (!done) {
            // currently overestimating sample size, because estimation uses 80% quantile. Still, seems low.
            val indices = workflow.chooseSamples(sampledMvrs, round)
            println("$round chooseSamples ${indices.size} took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} m")
            // println("$round samples=${indices}")
            stopwatch.start()

            indices.forEach {
                sampledMvrs.add(sampler.mvrs[it])
            }

            done = workflow.runAudit(indices, sampledMvrs)
            println("$round runAudit took ${stopwatch.elapsed(TimeUnit.MILLISECONDS)} ms\n")

            round++
        }
    }
}

// seems suspiciouly higher than the audit

//    quantiles: 0.1000 = 172, 0.2000 = 200, 0.3000 = 227, 0.4000 = 247, 0.5000 = 280, 0.6000 = 322, 0.7000 = 397, 0.8000 = 496, 0.9000 = 679,
//simulateSampleSizes at 80% quantile = ComparisonAssertion for '339' margin=0.0289 samplesEst=496 samplesNeeded=0 proved=false pvalue=0.0 == 496
//   quantiles: 0.1000 = 113, 0.2000 = 123, 0.3000 = 131, 0.4000 = 143, 0.5000 = 161, 0.6000 = 191, 0.7000 = 203, 0.8000 = 241, 0.9000 = 358,
//simulateSampleSizes at 80% quantile = ComparisonAssertion for '339' margin=0.0458 samplesEst=241 samplesNeeded=0 proved=false pvalue=0.0 == 241
//   quantiles: 0.1000 = 89, 0.2000 = 103, 0.3000 = 103, 0.4000 = 113, 0.5000 = 122, 0.6000 = 136, 0.7000 = 150, 0.8000 = 160, 0.9000 = 197,
//simulateSampleSizes at 80% quantile = ComparisonAssertion for '339' margin=0.0581 samplesEst=160 samplesNeeded=0 proved=false pvalue=0.0 == 160
//   quantiles: 0.1000 = 64, 0.2000 = 74, 0.3000 = 74, 0.4000 = 74, 0.5000 = 81, 0.6000 = 88, 0.7000 = 91, 0.8000 = 105, 0.9000 = 115,
//simulateSampleSizes at 80% quantile = ComparisonAssertion for '339' margin=0.0806 samplesEst=105 samplesNeeded=0 proved=false pvalue=0.0 == 105
//   quantiles: 0.1000 = 62, 0.2000 = 70, 0.3000 = 72, 0.4000 = 72, 0.5000 = 79, 0.6000 = 88, 0.7000 = 95, 0.8000 = 111, 0.9000 = 154,
//simulateSampleSizes at 80% quantile = ComparisonAssertion for '339' margin=0.0830 samplesEst=111 samplesNeeded=0 proved=false pvalue=0.0 == 111
//   quantiles: 0.1000 = 47, 0.2000 = 47, 0.3000 = 52, 0.4000 = 54, 0.5000 = 54, 0.6000 = 57, 0.7000 = 67, 0.8000 = 72, 0.9000 = 79,
//simulateSampleSizes at 80% quantile = ComparisonAssertion for '339' margin=0.1095 samplesEst=72 samplesNeeded=0 proved=false pvalue=0.0 == 72
//   quantiles: 0.1000 = 38, 0.2000 = 38, 0.3000 = 44, 0.4000 = 44, 0.5000 = 44, 0.6000 = 45, 0.7000 = 54, 0.8000 = 54, 0.9000 = 58,
//simulateSampleSizes at 80% quantile = ComparisonAssertion for '339' margin=0.1355 samplesEst=54 samplesNeeded=0 proved=false pvalue=0.0 == 54
//   quantiles: 0.1000 = 38, 0.2000 = 42, 0.3000 = 43, 0.4000 = 43, 0.5000 = 43, 0.6000 = 47, 0.7000 = 53, 0.8000 = 53, 0.9000 = 63,
//simulateSampleSizes at 80% quantile = ComparisonAssertion for '339' margin=0.1365 samplesEst=53 samplesNeeded=0 proved=false pvalue=0.0 == 53
//   quantiles: 0.1000 = 33, 0.2000 = 38, 0.3000 = 38, 0.4000 = 38, 0.5000 = 38, 0.6000 = 41, 0.7000 = 46, 0.8000 = 46, 0.9000 = 63,
//simulateSampleSizes at 80% quantile = ComparisonAssertion for '339' margin=0.1563 samplesEst=46 samplesNeeded=0 proved=false pvalue=0.0 == 46
//   quantiles: 0.1000 = 35, 0.2000 = 40, 0.3000 = 40, 0.4000 = 40, 0.5000 = 40, 0.6000 = 44, 0.7000 = 49, 0.8000 = 49, 0.9000 = 49,
//simulateSampleSizes at 80% quantile = ComparisonAssertion for '339' margin=0.1488 samplesEst=49 samplesNeeded=0 proved=false pvalue=0.0 == 49
//   quantiles: 0.1000 = 31, 0.2000 = 31, 0.3000 = 35, 0.4000 = 35, 0.5000 = 35, 0.6000 = 35, 0.7000 = 39, 0.8000 = 43, 0.9000 = 50,
//simulateSampleSizes at 80% quantile = ComparisonAssertion for '339' margin=0.1667 samplesEst=43 samplesNeeded=0 proved=false pvalue=0.0 == 43
// computeSize=497 consistentSamplingSize= 496
//0 chooseSamples 496 took 35440 m
// ComparisonAssertion for '339' margin=0.0289 samplesEst=496 samplesNeeded=239 proved=true pvalue=0.049394950041438866, status = StatRejectNull
// ComparisonAssertion for '339' margin=0.0458 samplesEst=241 samplesNeeded=113 proved=true pvalue=0.04940266120655398, status = StatRejectNull
// ComparisonAssertion for '339' margin=0.0581 samplesEst=160 samplesNeeded=89 proved=true pvalue=0.04923387121229121, status = StatRejectNull
// ComparisonAssertion for '339' margin=0.0806 samplesEst=105 samplesNeeded=57 proved=true pvalue=0.048934165715566885, status = StatRejectNull
// ComparisonAssertion for '339' margin=0.0830 samplesEst=111 samplesNeeded=72 proved=true pvalue=0.048340062983669344, status = StatRejectNull
// ComparisonAssertion for '339' margin=0.1095 samplesEst=72 samplesNeeded=42 proved=true pvalue=0.048323363950808954, status = StatRejectNull
// ComparisonAssertion for '339' margin=0.1355 samplesEst=54 samplesNeeded=42 proved=true pvalue=0.036276752178088005, status = StatRejectNull
// ComparisonAssertion for '339' margin=0.1365 samplesEst=53 samplesNeeded=42 proved=true pvalue=0.03544326160018776, status = StatRejectNull
// ComparisonAssertion for '339' margin=0.1563 samplesEst=46 samplesNeeded=38 proved=true pvalue=0.047301371335245895, status = StatRejectNull
// ComparisonAssertion for '339' margin=0.1488 samplesEst=49 samplesNeeded=40 proved=true pvalue=0.047209926309833915, status = StatRejectNull
// ComparisonAssertion for '339' margin=0.1667 samplesEst=43 samplesNeeded=35 proved=true pvalue=0.04965915093539308, status = StatRejectNull

// translate sample indices to lists for the auditors to find those ballots
fun makeAuditList() {

    // n_sampled_phantoms = np.sum(sampled_cvr_indices > manifest_cards)
    //print(f'The sample includes {n_sampled_phantoms} phantom cards.')

    // cards_to_retrieve, sample_order, cvr_sample, mvr_phantoms_sample = \
    //    Dominion.sample_from_cvrs(cvr_list, manifest, sampled_cvr_indices)

    // # write the sample
    //Dominion.write_cards_sampled(audit.sample_file, cards_to_retrieve, print_phantoms=False)
}