package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.oneaudit.OneAuditAssortValueRates
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals

class TestEstimatedErrorRates {
    val margin = .02
    val upper = 1.0
    val noerror: Double = 1.0 / (2.0 - margin / upper)
    val taus = Taus(upper)

    val Npop: Int = 10000 // population size for this contest
    val maxLoss = 0.9

    val oaAssortRates: OneAuditAssortValueRates? = null // non-null for OneAudit
    val d: Int = 1  // trunc weight

    //// current design

    // lots going on in here:
    // 1. scaled to make room for oa rates
    // 2. val sampleNumber = startingErrors.totalSamples + (trackerErrors?.totalSamples ?: 0)
    // 2. val allCount = (startCount + trackCount) / ( )
    // 3. shrinkTruncEstimateRate : ease the first d samples in slowly: est = (d * apriori + errorCount) / (d + sampleNum - 1)
    // 4. phantoms, add or max?
    fun estimatedErrorRates(startingErrors: ClcaErrorCounts, nphantoms: Int, trackerErrors: ClcaErrorCounts? = null): Map<Double, Double> { // bassort -> rate
        val scaled = if (oaAssortRates == null) 1.0 else (Npop - oaAssortRates.totalInPools) / Npop.toDouble()
        val sampleNumber = startingErrors.totalSamples + (trackerErrors?.totalSamples ?: 0)

        val estRates = taus.namesNoErrors().map { name ->
            val tauValue = taus.valueOf(name)
            val bassort = tauValue * noerror
            val startCount = (startingErrors.errorCounts()[bassort] ?: 0)
            val trackCount = (trackerErrors?.errorCounts()[bassort] ?: 0)
            val allCount = startCount + trackCount
            var rate = scaled * shrinkTruncEstimateRate(
                apriori = 0.0,   // TODO shouldnt the startingErrors be the apriori ?? right now you are just doing an average
                errorCount = allCount,
                sampleNum = sampleNumber,
            )
            // rate of phantoms is the minimum "oth-los" rate
            if (startingErrors.isPhantom(bassort) && nphantoms > 0) {
                val prate = nphantoms / Npop.toDouble()
                rate = max(rate, prate) // TODO max or sum ?
            }
            Pair(bassort, rate)
        }.toMap()
        return estRates
    }

    fun shrinkTruncEstimateRate(
        apriori: Double,
        errorCount: Int,
        sampleNum: Int,
    ): Double {
        if (sampleNum == 0) return 0.0
        if (errorCount == 0) return 0.0 // experiment
        val used = max(d, 1)
        val est = (used * apriori + errorCount) / (used + sampleNum - 1)
        // println("")
        return est
    }

    // new design
    //  first round: use apriori and nphantoms for initial rate estimate, dont need starting errorCount, just apriori starting rates.
    //  estimation will jigger the tracker in gaBetting.bet(tracker) to continue on from previous round
    //  audit always starts from beginning
    //  bets will do shrinktrunc of starting and measured.

    // apriori: apriori rates not counting phantoms
    // phantomRate: nphantoms / Npop
    // return full map (all taus except noerrors)
    fun startingErrorRates(apriori: TausRates, noerror: Double, phantomRate: Double): Map<Double, Double> { // bassort -> rate
        val startingRates = mutableMapOf<Double, Double>()

        taus.namesNoErrors().forEach { name ->
            val tauValue = taus.valueOf(name)
            val bassort = tauValue * noerror
            val aprioriRate = apriori.getNamedRate(name) // may be null
            val phantom = if (taus.isPhantom(tauValue) && phantomRate > 0.0) phantomRate else 0.0
            startingRates[bassort] = (aprioriRate?: 0.0) + phantom
        }
        return startingRates
    }

    fun estimatedErrorRates2(startingRates: Map<Double, Double>, trackerErrors: ClcaErrorCounts? = null): Map<Double, Double> { // bassort -> rate
        if (trackerErrors == null || trackerErrors.errorCounts.isEmpty()) return startingRates

        val errorRates = trackerErrors.errorRates()
        val scaled = if (oaAssortRates == null) 1.0 else (Npop - oaAssortRates.totalInPools) / Npop.toDouble()

        val estRates = taus.namesNoErrors().map { name ->
            val tauValue = taus.valueOf(name)
            val bassort = tauValue * noerror
            val aprioriRate = startingRates[bassort] ?: 0.0
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
    // COBRA does this as a rate:
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
        //if (sampleNum == 0) return 0.0
        //if (errorCount == 0) return 0.0 // experiment
        val d1 = if (d<1) 1 else d
        val est = (d1 * aprioriRate + sampleNum * measuredRate) / (d1 + sampleNum - 1)
        return est
    }

    @Test
    fun testPhantoms() {
        val r1 = makeRates(0)
        println()
        val r2 = makeRates(30)
    }

    fun makeRates(
        nphantoms: Int
    ): Map<Double, Double> {
        println("----------------- nphantoms = $nphantoms")
        val startingErrors = ClcaErrorCounts.empty(noerror, upper)
        println("bassort values = ${startingErrors.bassortValues()}")

        val tracker = ClcaErrorTracker(noerror, upper)
        repeat(1000) { tracker.addSample(noerror) }

        repeat(1) { tracker.addSample(0.0 * noerror) }
        repeat(10) { tracker.addSample(0.5 * noerror) }
        repeat(10) { tracker.addSample(1.5 * noerror) }
        repeat(1) { tracker.addSample(2.0 * noerror) }

        val trackerCounts = tracker.measuredClcaErrorCounts()
        println("tracker totalSamples = ${trackerCounts.totalSamples}")
        println("trackerCounts = ${trackerCounts.show()}")
        println("trackerRates = ${trackerCounts.errorRates()}")
        assertEquals(listOf(1, 10, 10, 1), trackerCounts.errorCounts().map { it.value })

        // the old way has p1o = max(tracker rate, phantom rate)
        val rates = estimatedErrorRates(startingErrors, nphantoms, trackerCounts)
        println("estimatedErrorRates = $rates")
        println()

        // fun estimatedErrorRates2(startingRates: Map<Double, Double>, trackerErrors: ClcaErrorCounts? = null): Map<Double, Double> { // bassort -> rate
        val apriori = TausRates(emptyMap())
        val phantomRate = nphantoms / Npop.toDouble()
        println("phantomRate = $phantomRate")
        val startingRates =  startingErrorRates(apriori, noerror = noerror, phantomRate = phantomRate)
        println("startingRates = $startingRates")

        // the new way has p1o = tracker rate + phantom rate, but diluted by the trunc shrink
        val rates2 = estimatedErrorRates2(startingRates, trackerCounts)
        println("estimatedErrorRates2 = $rates2")

        rates.forEach { bassort, rate ->
            val rate2 = rates2[bassort]!!
            if (trackerCounts.isPhantom(bassort)) {
                println("rate = $rate rate2 = $rate2 diff = ${rate-rate2}")
            } else {
                assertEquals(rate, rate2)
            }
        }
        return rates2

        /* get optimal bet
        val gaBetting = GeneralAdaptiveBetting(
            Npop,
            startingErrors = ClcaErrorCounts.empty(noerror, upper),
            nphantoms = nphantoms,
            oaAssortRates = null, d = 0, maxLoss = maxLoss, debug = false
        )
        val bet = gaBetting.bet(tracker)
        println("optimal bet = $bet")
        return bet */
    }

}