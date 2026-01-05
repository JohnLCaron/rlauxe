package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditRatesFromPools
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.TausOA
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.workflow.ClcaSampler
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

// GeneralAdaptiveBetting with OneAudit
// log T_i = ln(1.0 + lamda * (noerror - mui)) * p0  + Sum { ln(1.0 + lamda * (assortValue_k - mui)) * p_k; over error type k } (eq 1)
//          + Sum { ln(1.0 + lamda * (assortValue_pk - mui)) * p_pk; over pools and pool types }              (eq 2)

class TestGeneralAdaptiveBetting2 {

    @Test
    fun makeBet() {
        makeBet(N = 10000, margin = .03, upper = 1.0, maxRisk = .9, poolPct = .15)
    }

    fun makeBet(
        N: Int,
        margin: Double,
        upper: Double,
        maxRisk: Double,
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
        val minCassorter = minAssertion.cassorter as ClcaAssorterOneAudit

        val oaFromPools = OneAuditRatesFromPools(cardPools)
        val oaErrorRates = oaFromPools.oaErrorRates(oaContest, minCassorter)
        val poolAvg = minCassorter.poolAverages.assortAverage[cardPool.poolId]!!
        val taus = TausOA(upper, poolAvg)

        val noerror: Double = 1.0 / (2.0 - margin / upper)

        val assorts = mutableListOf<Double>()
        val pairs = mvrs.zip ( cards)

        val sampling = ClcaSampler(
            oaContest.id,
            pairs, // Pair(mvr, card)
            minCassorter,
            true)

        sampling.reset()
        sampling.forEach { assortValue ->
            assorts.add(assortValue)
        }

        val betFn = GeneralAdaptiveBetting(N, oaErrorRates, d=0,  maxRisk = maxRisk, debug=true)
        val tracker = ClcaErrorTracker(noerror, upper)

        // bet from first half
        assorts.subList(0, N/2).forEach{ tracker.addSample(it) }
        println(tracker.measuredClcaErrorCounts().show())

        val bet = betFn.bet(tracker)
        println("bet = $bet maxRisk = $maxRisk")

        assorts.shuffle(Random)
        findSamplesNeededUsingAssorts2(N, margin, upper, bet, assorts, taus)
    }
}

// see PlotWithAssortValues
fun findSamplesNeededUsingAssorts2(N:Int, margin: Double, upper: Double, lamda: Double, assorts: List<Double>, taus: TausIF, show: Boolean = false) {

    val noerror: Double = 1.0 / (2.0 - margin / upper) // clca assort value when no error
    val tracker = ClcaErrorTracker(noerror, upper)
    var T: Double = 1.0
    var sample = 0

    while (T < 20.0 && sample < assorts.size) {
        val x = assorts[sample]
        tracker.addSample(x)
        val mj = populationMeanIfH0(N = N, withoutReplacement = true, sampleTracker = tracker)
        val ttj = 1.0 + lamda * (x - mj)
        T *= ttj
        sample++
        val name = taus.desc(x/noerror)
        if (name != null) println("  $name $ttj")

        if (show) println("${nfn(tracker.numberOfSamples(), 3)}: ttj=${dfn(ttj, 6)} Tj=${dfn(T, 6)}")
    }
    println("lamda: ${df(lamda)} N=$N, margin=$margin, upper=$upper noerror:${df(noerror)}: stat=$T needed $sample samples" )
}