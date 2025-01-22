package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.ComparisonAssertion
import org.cryptobiotic.rlauxe.core.ComparisonAssorterIF
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.PrevSamplesWithRates

object ClcaErrorRates {
    val errorRatios = mutableMapOf<Int, List<Double>>()
    val standard = listOf(.01, 1.0e-4, 0.01, 1.0e-4)

    fun getErrorRates(ncandidates: Int, fuzzPct: Double?): List<Double> {
        if (fuzzPct == null) return standard

        val useCand = when  {
            ncandidates < 2 -> 2
            ncandidates > 10 -> 10
            else -> ncandidates
        }
        return errorRatios[useCand]!!.map { it * fuzzPct }
    }

    fun calcErrorRates(contestId: Int, cassorter: ComparisonAssorterIF,
                       cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    ) : List<Double> {
        require(cvrPairs.size > 0)
        val samples = PrevSamplesWithRates(cassorter.noerror()) // accumulate error counts here
        cvrPairs.filter { it.first.hasContest(contestId) }.forEach { samples.addSample(cassorter.bassort(it.first, it.second)) }
        // require( samples.errorCounts().sum() ==  cvrPairs.size)
        return samples.errorRates()
    }

    // TODO REDO?
    init {
        // GenerateComparisonErrorTable.generateErrorTable()
        // N=100000 ntrials = 1000
        // generated 12/12024
        errorRatios[2] = listOf(0.2535, 0.2524, 0.2474, 0.2480)
        errorRatios[3] = listOf(0.3367, 0.1673, 0.3300, 0.1646)
        errorRatios[4] = listOf(0.3357, 0.0835, 0.3282, 0.0811)
        errorRatios[5] = listOf(0.3363, 0.0672, 0.3288, 0.0651)
        errorRatios[6] = listOf(0.3401, 0.0575, 0.3323, 0.0557)
        errorRatios[7] = listOf(0.3240, 0.0450, 0.3158, 0.0434)
        errorRatios[8] = listOf(0.2886, 0.0326, 0.2797, 0.0314)
        errorRatios[9] = listOf(0.3026, 0.0318, 0.2938, 0.0306)
        errorRatios[10] = listOf(0.2727, 0.0244, 0.2624, 0.0233)
    }

}