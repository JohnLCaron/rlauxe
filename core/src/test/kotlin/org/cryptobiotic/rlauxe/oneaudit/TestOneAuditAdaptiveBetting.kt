package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.betting.BettingFn
import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting2
import org.cryptobiotic.rlauxe.betting.GeneralOptimalLambda
import org.cryptobiotic.rlauxe.betting.populationMeanIfH0
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.makeDeciles
import org.cryptobiotic.rlauxe.workflow.ClcaSampler
import org.cryptobiotic.rlauxe.workflow.Sampler
import kotlin.test.Test

class TestOneAuditAdaptiveBetting {

    @Test
    fun testOneAuditOptimalLambda() {
        // class ClcaAssorterOneAudit(
        //    info: ContestInfo,
        //    assorter: AssorterIF,   // A(mvr) Use this assorter for the CVRs: plurality or IRV
        //    hasStyle: Boolean = true,
        //    dilutedMargin: Double,
        //    val poolAverages: AssortAvgsInPools,
        //)

        println()

        val cvrFraction = .84
        val (contestUA, mvrs, cards, cardPools) = makeOneAuditTest(
            20000,
            18000,
            cvrFraction = cvrFraction,
            undervoteFraction = .0,
            phantomFraction = .0,
        )
        println("cvrFraction=${cvrFraction}")
        println("contestUA=${contestUA}")
        println("cardPool=${cardPools.first()}")
        val oaCassorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        val oaErrorsFromPools = OneAuditRatesFromPools(cardPools as List<OneAuditPoolIF>)
        val oaErrorRates = oaErrorsFromPools.oaErrorRates(contestUA, oaCassorter)
        println("oaErrorRates=$oaErrorRates pct=${oaErrorRates.rates.values.sum()} ")
        println()

        // no clca errors at first
        val clcaErrors = ClcaErrorCounts(
            emptyMap(),
            totalSamples = 0,
            noerror = oaCassorter.noerror(),
            upper = oaCassorter.assorter.upperBound()
        )
        //val tracker2 = ClcaErrorTracker(noerror, 1.0)

        val mui = .5
        val solver = GeneralOptimalLambda(
            oaCassorter.noerror(),
            clcaErrors.errorRates(),
            oaErrorRates.rates,
            mui,
            2.0,
            debug = true
        )
        val lam = solver.solve()
        println("lamda = $lam")
        println()

        // class ClcaSampling(
        //    val contestId: Int,
        //    val cvrPairs: List<Pair<CardIF, CardIF>>, // Pair(mvr, card)
        //    val cassorter: ClcaAssorter,
        //    val allowReset: Boolean,
        //)
        val cvrPairs = mvrs.zip( cards)
        val sampler = ClcaSampler(contestUA.id, cvrPairs.size, cvrPairs, oaCassorter, true)

        val sampleSizes = mutableListOf<Int>()
        val welford = Welford()
        repeat(100) {
            sampler.reset()
            // data class GeneralAdaptiveBetting2(
            //    val Npop: Int, // population size for this contest
            //    val aprioriCounts: ClcaErrorCounts, // apriori counts not counting phantoms, non-null so we have noerror and upper
            //    val nphantoms: Int, // number of phantoms in the population
            //    val maxLoss: Double, // between 0 and 1; this bounds how close lam can get to 2.0; maxBet = maxLoss / mui
            //
            //    val oaAssortRates: OneAuditAssortValueRates? = null, // non-null for OneAudit
            //    val d: Int = 100,  // trunc weight
            //    val debug: Boolean = false,
            val betFun = GeneralAdaptiveBetting2(
                Npop = contestUA.Npop,
                aprioriCounts = ClcaErrorCounts.empty(oaCassorter.noerror(), oaCassorter.assorter.upperBound()),
                nphantoms = contestUA.contest.Nphantoms(),
                maxLoss = 0.90,
                oaAssortRates = oaErrorRates,
                d = 100,
                debug=true,
            )

            val betFunOld = GeneralAdaptiveBetting(
                contestUA.Npop,
                startingErrors = ClcaErrorCounts.empty(oaCassorter.noerror(), oaCassorter.assorter.upperBound()),
                nphantoms=contestUA.contest.Nphantoms(),
                oaAssortRates = oaErrorRates,
                d = 100,
                maxLoss = 0.90,
                debug = false
            )
            val tracker = ClcaErrorTracker(oaCassorter.noerror(), oaCassorter.assorter.upperBound())
            val nsamples = runSamplesNeeded(contestUA.Npop, betFun, sampler, tracker, show = false)
            sampleSizes.add(nsamples)
            welford.update(nsamples.toDouble())
        }
        println(welford)
        println("nsample dist=${makeDeciles(sampleSizes)}")
    }

}

fun runSamplesNeeded(Npop: Int, betFn: BettingFn, sampler: Sampler, tracker: ClcaErrorTracker, show: Boolean = false): Int {
    var T: Double = 1.0
    var sample = 0

    while (T < 20.0) {
        val lamda = betFn.bet(tracker)
        val assortValue = sampler.next()

        val mj = populationMeanIfH0(Npop, true, tracker)  // approx .5
        val ttj = 1.0 + lamda * (assortValue - mj)
        T *= ttj

        tracker.addSample(assortValue)
        sample++
        if (show) println("runSamplesNeeded lam=${df(lamda)}: x=${df(assortValue)} ttj=${dfn(ttj, 6)} Tj=${dfn(T, 6)}\n")
    }
    println("runSamplesNeeded needed $sample samples" )
    return sample
}