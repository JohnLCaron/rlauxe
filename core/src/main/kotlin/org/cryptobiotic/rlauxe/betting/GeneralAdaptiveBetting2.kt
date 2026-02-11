package org.cryptobiotic.rlauxe.betting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.oneaudit.OneAuditAssortValueRates

private val logger = KotlinLogging.logger("GeneralAdaptiveBetting")

// new design
//  first round: use apriori and nphantoms for initial rate estimate
//  estimation will jigger the tracker in gaBetting.bet(tracker) to continue on from previous round
//  audit always starts from beginning
//  bets will do shrinkTrunc of starting and measured.

data class GeneralAdaptiveBetting2(
    val Npop: Int, // population size for this contest
    val aprioriCounts: ClcaErrorCounts, // apriori counts not counting phantoms, non-null so we have noerror and upper
    val nphantoms: Int, // number of phantoms in the population
    val maxLoss: Double, // between 0 and 1; this bounds how close lam can get to 2.0; maxBet = maxLoss / mui

    val oaAssortRates: OneAuditAssortValueRates? = null, // non-null for OneAudit
    val d: Int = 100,  // trunc weight
    val debug: Boolean = false,
) : BettingFn {
    val noerror = aprioriCounts.noerror
    val upper = aprioriCounts.upper
    val taus = Taus(upper)
    val aprioriRates: Map<Double, Double>  // bassort -> rate

    init {
        aprioriRates = makeAprioriErrorRates(aprioriCounts, nphantoms/Npop.toDouble())
    }

    fun estimatedErrorRates2(trackerErrors: ClcaErrorCounts? = null): Map<Double, Double> { // bassort -> rate
        if (trackerErrors == null || trackerErrors.errorCounts.isEmpty()) return aprioriRates

        val errorRates = trackerErrors.errorRates()
        val scaled = if (oaAssortRates == null) 1.0 else (Npop - oaAssortRates.totalInPools) / Npop.toDouble()

        val estRates = taus.namesNoErrors().map { name ->
            val tauValue = taus.valueOf(name)
            val bassort = tauValue * noerror
            val aprioriRate = aprioriRates[bassort] ?: 0.0
            val rate = scaled * shrinkTruncEstimateRate2(
                aprioriRate = aprioriRate,
                measuredRate = errorRates[bassort] ?: 0.0,
                sampleNum = trackerErrors.totalSamples,
            )
            Pair(bassort, rate)
        }.toMap()
        return estRates
    }

    // ease the first d samples in gradually
    // For k ∈ {1, 2} we set a value d_k ≥ 0, capturing the degree of shrinkage to the a priori estimate p̃_k ,
    // and a truncation factor eps_k ≥ 0, enforcing a lower bound on the estimated rate.
    // Let p̂_ki be the sample rates at time i, e.g., p̂_2i = Sum(1{Xj = 0})/i , j=1..i
    // Then the shrink-trunc estimate is:
    //   p_̃ki := (d_k * p̃_k + i * p̂_k(i−1)) / (d_k + i − 1) ∨ epsk  ; COBRA eq (4)

    fun shrinkTruncEstimateRate2(
        aprioriRate: Double,
        measuredRate: Double,
        sampleNum: Int,
    ): Double {
        if (sampleNum == 0) return aprioriRate
        val d1 = if (d<1) 1 else d
        val est = (d1 * aprioriRate + sampleNum * measuredRate) / (d1 + sampleNum - 1)
        return est
    }

    override fun bet(prevSamples: Tracker): Double {
        val errorTracker = prevSamples as ErrorTracker
        val trackerErrors = errorTracker.measuredClcaErrorCounts()

        val estRates = estimatedErrorRates2(trackerErrors)
        val mui = populationMeanIfH0(Npop, withoutReplacement=true, prevSamples)

        val maxBet = maxLoss / mui
        val kelly = GeneralOptimalLambda(errorTracker.noerror(), estRates, oaAssortRates?.rates, mui=mui, maxBet=maxBet, debug = debug)
        val bet = kelly.solve()

        return bet
    }
}

// apriori: apriori rates not counting phantoms
// phantomRate: nphantoms / Npop
// return full errorRates (all taus except noerrors)
fun makeAprioriErrorRates(apriori: ClcaErrorCounts, phantomRate: Double): Map<Double, Double> { // bassort -> rate
    val startingRates = mutableMapOf<Double, Double>()

    val noerror = apriori.noerror
    val upper = apriori.upper
    val taus = Taus(upper)

    taus.namesNoErrors().forEach { name ->
        val tauValue = taus.valueOf(name)
        val bassort = tauValue * noerror
        val aprioriRate = apriori.getNamedRate(name) // may be null
        val phantom = if (apriori.isPhantom(tauValue) && phantomRate > 0.0) phantomRate else 0.0
        startingRates[bassort] = (aprioriRate?: 0.0) + phantom
    }
    return startingRates
}