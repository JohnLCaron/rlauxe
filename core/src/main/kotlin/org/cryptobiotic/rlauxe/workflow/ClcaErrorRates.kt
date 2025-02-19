package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.ClcaAssorterIF
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.ErrorRates
import org.cryptobiotic.rlauxe.core.PrevSamplesWithRates

// the idea is that the errorRates are proportional to fuzzPct
// Then p1 = fuzzPct * r1, p2 = fuzzPct * r2, p3 = fuzzPct * r3, p4 = fuzzPct * r4.
// margin doesnt matter (TODO show this)

object ClcaErrorRates {
    val rrates = mutableMapOf<Int, List<Double>>() // errorRates / FuzzPct
    val standard = ErrorRates(.01, 1.0e-4, 0.01, 1.0e-4)

    fun getErrorRates(ncandidates: Int, fuzzPct: Double?): ErrorRates {
        if (fuzzPct == null) return standard

        val useCand = when  {
            ncandidates < 2 -> 2
            ncandidates > 10 -> 10
            else -> ncandidates
        }
        val rr = rrates[useCand]!!.map { it * fuzzPct }
        return ErrorRates(rr[0], rr[1], rr[2], rr[3])
    }

    fun calcErrorRates(contestId: Int,
                       cassorter: ClcaAssorterIF,
                       cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    ) : ErrorRates {
        require(cvrPairs.size > 0)
        val samples = PrevSamplesWithRates(cassorter.noerror()) // accumulate error counts here
        cvrPairs.filter { it.first.hasContest(contestId) }.forEach { samples.addSample(cassorter.bassort(it.first, it.second)) }
        // require( samples.errorCounts().sum() ==  cvrPairs.size)
        return samples.errorRates()
    }

    // given an error rate, what fuzz pct does it corresond to ?
    fun calcFuzzPct(ncandidates: Int, errorRates: ErrorRates ) : List<Double> {
        val useCand = when  {
            ncandidates < 2 -> 2
            ncandidates > 10 -> 10
            else -> ncandidates
        }
        val rr = rrates[useCand]!!
        // p1 = fuzzPct * r1
        // fuzzPct = p1 / r1
        val p2o = errorRates.p2o / rr[0]
        val p1o = errorRates.p1o / rr[1]
        val p1u = errorRates.p1u / rr[2]
        val p2u = errorRates.p2u / rr[3]
        return listOf(p2o, p1o, p1u, p2u)
    }

    init {
        // GenerateClcaErrorTable.generateErrorTable()
        // N=100000 ntrials = 200
        // generated 1/26/2026
        rrates[2] = listOf(0.2623686, 0.2625469, 0.2371862, 0.2370315,)
        rrates[3] = listOf(0.1400744, 0.3492912, 0.3168304, 0.1245060,)
        rrates[4] = listOf(0.1277999, 0.3913025, 0.3519773, 0.1157800,)
        rrates[5] = listOf(0.0692904, 0.3496153, 0.3077332, 0.0600383,)
        rrates[6] = listOf(0.0553841, 0.3398728, 0.2993941, 0.0473467,)
        rrates[7] = listOf(0.0334778, 0.2815991, 0.2397504, 0.0259392,)
        rrates[8] = listOf(0.0351272, 0.3031122, 0.2591883, 0.0280541,)
        rrates[9] = listOf(0.0308620, 0.3042787, 0.2585768, 0.0254916,)
        rrates[10] = listOf(0.0276966, 0.2946918, 0.2517076, 0.0225628,)
    }
}