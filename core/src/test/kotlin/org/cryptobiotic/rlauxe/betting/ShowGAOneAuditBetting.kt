package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditRatesFromPools
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.TausOA
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.workflow.ClcaSampler
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

// GeneralAdaptiveBetting with OneAudit
// log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k; over error type k } (eq 1)
//          + Sum { ln(1.0 + lamda * (assortValue_pk - mui)) * p_pk; over pools and pool types }              (eq 2)

class ShowGAOneAuditBetting {

    @Test
    fun makeBet() {
        makeBet(N = 10000, margin = .03, upper = 1.0, maxLoss = .9, poolPct = .15)
    }

    fun makeBet(
        N: Int,
        margin: Double,
        upper: Double,
        maxLoss: Double,
        poolPct: Double,
    ) {
        // data class ContestMvrCardAndPops(
        //    val contestUA: ContestWithAssertions,
        //    val mvrs: List<Cvr>,
        //    val cards: List<AuditableCard>,
        //    val pools: List<OneAuditPoolIF>,
        //)
        val (oaContest, mvrs, cards, cardPools) =
            makeOneAuditTest(margin, N, cvrFraction = 1 - poolPct, undervoteFraction = 0.0, phantomFraction = 0.0)

        // only one pool, only one contest
        val cardPool = cardPools.first() as OneAuditPoolIF
        assertTrue(cardPool.hasContest(oaContest.id))
        assertFalse(cardPool.hasContest(42))
        assertEquals(1, cardPool.regVotes().size)
        val minAssertion = oaContest.minClcaAssertion()!!
        val minCassorter = minAssertion.cassorter as OneAuditClcaAssorter

        val oaFromPools = OneAuditRatesFromPools(cardPools)
        val oaErrorRates = oaFromPools.oaErrorRates(oaContest, minCassorter)
        val poolAvg = minCassorter.poolAverages.assortAverage[cardPool.poolId]!!
        val taus = TausOA(upper, poolAvg)

        val noerror: Double = 1.0 / (2.0 - margin / upper)

        val assorts = mutableListOf<Double>()
        val pairs = mvrs.zip ( cards)

        val sampling = ClcaSampler(
            oaContest.id,
            pairs.size,
            pairs, // Pair(mvr, card)
            minCassorter,
            true)

        sampling.reset()
        sampling.forEach { assortValue ->
            assorts.add(assortValue)
        }

        // data class GeneralAdaptiveBetting2(
        //    val Npop: Int, // population size for this contest
        //    val aprioriCounts: ClcaErrorCounts, // apriori counts not counting phantoms, non-null so we have noerror and upper
        //    val nphantoms: Int, // number of phantoms in the population
        //    val maxLoss: Double, // between 0 and 1; this bounds how close lam can get to 2.0; maxBet = maxLoss / mui
        //
        //    val oaAssortRates: OneAuditAssortValueRates? = null, // non-null for OneAudit
        //    val d: Int = 100,  // trunc weight
        //    val debug: Boolean = false,
        val betFn = GeneralAdaptiveBetting2(
            Npop = N,
            aprioriCounts = ClcaErrorCounts.empty(noerror, upper),
            nphantoms = oaContest.contest.Nphantoms(),
            maxLoss = .9,
            oaAssortRates=oaErrorRates,
            d = 0,
            debug=true,
        )

        // bet from first half
        val tracker = ClcaErrorTracker(noerror, upper)
        assorts.subList(0, N/2).forEach{ tracker.addSample(it) }
        println(tracker.measuredClcaErrorCounts().show())

        val bet = betFn.bet(tracker)
        println("bet = $bet maxLoss = $maxLoss")

        assorts.shuffle(Random)
        findSamplesNeededUsingAssorts(N, margin, upper, bet, assorts, taus)
    }
}

// see PlotWithAssortValues
private fun findSamplesNeededUsingAssorts(N:Int, margin: Double, upper: Double, lamda: Double, assorts: List<Double>, taus: TausOA, show: Boolean = false) {

    val noerror: Double = 1.0 / (2.0 - margin / upper) // clca assort value when no error
    val tracker = ClcaErrorTracker(noerror, upper)
    var T: Double = 1.0
    var sample = 0

    println()
    println("  ${sfn("assort",7)} ${sfn("ttj", 6)}, ${sfn("T", 6)}")
    while (T < 20.0 && sample < assorts.size) {
        val x = assorts[sample]
        tracker.addSample(x)
        val mj = populationMeanIfH0(N = N, withoutReplacement = true, tracker = tracker)
        val ttj = 1.0 + lamda * (x - mj)
        T *= ttj
        sample++
        val name = taus.desc(x/noerror) ?: "noerror"
        println("  ${sfn(name,7)} ${df(ttj)}, ${df(T)}")

        if (show) println("${nfn(tracker.numberOfSamples(), 3)}: ttj=${dfn(ttj, 6)} Tj=${dfn(T, 6)}")
    }
    println("lamda: ${df(lamda)} N=$N, margin=$margin, upper=$upper noerror:${df(noerror)}: stat=$T needed $sample samples" )
}