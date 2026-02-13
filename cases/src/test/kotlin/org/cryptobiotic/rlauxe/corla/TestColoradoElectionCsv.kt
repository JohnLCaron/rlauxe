package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting2
import org.cryptobiotic.rlauxe.betting.populationMeanIfH0
import org.cryptobiotic.rlauxe.core.sampleSize
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.test.Test

class TestColoradoElectionCsv {

    @Test
    fun testColoradoElectionSummary() {
        val filename = "src/test/data/corla/2024election/summary.csv"
        val contests = readColoradoElectionSummaryCsv(filename)
        contests.forEach { it.complete() }
        println("--------------------------------------------------------------")
        println("contest sort by reversed underVote percentage\n")
        contests.sortedBy { it.underPct }.reversed().forEach { println(it) }
        println("--------------------------------------------------------------")
        println("contest sort by dilutedMargin percentage\n")
        contests.filter{ it.dilutedMargin != 0.0 }.sortedBy { it.dilutedMargin }.forEach { print(it.show()) }
        println("--------------------------------------------------------------")
    }

    @Test
    fun testColoradoContestRoundCsv() {
        val filename = "src/test/data/corla/2024audit/round1/contest.csv"
        val contests = readColoradoContestRoundCsv(filename)
        println("read ${contests.size} contests from $filename")
        contests.forEach { it.showEstimation() }
    }
}

fun CorlaContestRoundCsv.showEstimation() {
    // TODO they use ballotCardCount instead of contestBallotCardCount for some reason
    val dilutedMargin = minMargin.toDouble() / ballotCardCount
    if (dilutedMargin > 0) {
        val est = optimistic(riskLimit, dilutedMargin, gamma)
        val (bet, payoff, samples) = betPayoffSamples(ballotCardCount, risk=riskLimit, assorterMargin=dilutedMargin, 0.0)

        // println("dilutedMargin = $dilutedMargin estSamples = ${est.toInt()} corlaEst=$optimisticSamplesToAudit rauxEst=$samples")
        require(optimisticSamplesToAudit == est.toInt())
        // println("   rauxe bet = $bet payoff = $payoff rauxeEst=$samples")
    }
}

// Compare Corla estimate with ours.
// this assumes you get the same bet each time, which is not true because mui is changing.
// Also eps (lower bound on the estimated rate) turns out to be important.
fun betPayoffSamples(N: Int, risk: Double, assorterMargin: Double, error: Double): Triple<Double, Double, Int> {
    val avgCvrAssortValue = margin2mean(assorterMargin)
    val assorterMargin2 = 2.0 * avgCvrAssortValue - 1.0 // reported assorter margin, not clca margin
    // val noerror = 1.0 / (2.0 - assorterMargin / assorter.upperBound())
    val noerror = 1 / (2 - assorterMargin2)

    // assumes upperBound = 1.0
    // class GeneralAdaptiveBetting(
    //    val Npop: Int, // population size for this contest
    //    // val accumErrorCounts: ClcaErrorCounts, // propable illegal to do (cant use prior knowlege of the sample)
    //    val oaErrorRates: OneAuditErrorRates?,
    //    val d: Int = 100,  // trunc weight
    //    val maxRisk: Double, // this bounds how close lam gets to 2.0; TODO study effects of this
    //    val withoutReplacement: Boolean = true,
    //    val debug: Boolean = false,
    //
    // data class GeneralAdaptiveBetting2(
    //    val Npop: Int, // population size for this contest
    //    val aprioriCounts: ClcaErrorCounts, // apriori counts not counting phantoms, non-null so we have noerror and upper
    //    val nphantoms: Int, // number of phantoms in the population
    //    val maxLoss: Double, // between 0 and 1; this bounds how close lam can get to 2.0; maxBet = maxLoss / mui
    //
    //    val oaAssortRates: OneAuditAssortValueRates? = null, // non-null for OneAudit
    //    val d: Int = 100,  // trunc weight
    //    val debug: Boolean = false,
    val bettingFn = GeneralAdaptiveBetting2(
        Npop = N,
        aprioriCounts = ClcaErrorCounts.empty(noerror, 1.0),
        nphantoms = 0,
        oaAssortRates = null,
        d = 100,
        maxLoss = .99,
    )

    val samples = ClcaErrorTracker(noerror, 1.0)
    repeat(10) { samples.addSample(noerror) }
    val bet = bettingFn.bet(samples)
    val mj = populationMeanIfH0(N=N, true, samples)

    val payoff = 1.0 + bet * (noerror - mj)
    val samplesSize = sampleSize(risk, payoff)
    return Triple(bet, payoff, roundUp(samplesSize))
}