package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditErrorsFromPools
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.makeDeciles
import org.cryptobiotic.rlauxe.workflow.ClcaSampling
import org.cryptobiotic.rlauxe.workflow.Sampling
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
        val oaCassorter = contestUA.minClcaAssertion()!!.cassorter as ClcaAssorterOneAudit
        val oaErrorsFromPools = OneAuditErrorsFromPools(cardPools)
        val oaErrorRates = oaErrorsFromPools.oaErrorRates(contestUA, oaCassorter)
        println("oaErrorRates=$oaErrorRates pct=${oaErrorRates.rates.values.sum()} ")
        println()

        // no clca errors at first
        val clcaErrors = ClcaErrorCounts(emptyMap(), totalSamples = 0, noerror = oaCassorter.noerror(), upper = oaCassorter.assorter.upperBound())
        //val tracker2 = ClcaErrorTracker(noerror, 1.0)

        val mui = .5
        val solver = OneAuditOptimalLambda(oaCassorter.noerror(), clcaErrors.errorRates(), oaErrorRates.rates, mui, debug=true)
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
        val sampler = ClcaSampling(contestUA.id, cvrPairs, oaCassorter, true)

        val sampleSizes = mutableListOf<Int>()
        val welford = Welford()
        repeat(100) {
            sampler.reset()
            val betFun = GeneralAdaptiveBetting(contestUA.Npop, oaErrorRates = oaErrorRates, d=100, maxRisk=0.90, debug = false)
            val tracker = ClcaErrorTracker(oaCassorter.noerror(), oaCassorter.assorter.upperBound())
            val nsamples = runSamplesNeeded(contestUA.Npop, betFun, sampler, tracker, show = false)
            sampleSizes.add(nsamples)
            welford.update(nsamples.toDouble())
        }
        println(welford)
        println("nsample dist=${makeDeciles(sampleSizes)}")
    }

}

fun runSamplesNeeded(Npop: Int, betFn: BettingFn, sampler: Sampling, tracker: ClcaErrorTracker, show: Boolean = false): Int {
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