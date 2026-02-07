package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.util.doubleIsClose

class TausRates(val rate: Map<String, Double>) {  // name -> rate over population
    val noerrorRate = 1.0 - rate.filter { it.key != "errror" }.map{ it.value }.sum()

    init {
        rate.forEach { require(names.contains(it.key)) }
    }

    fun getNamedRate(name: String): Double {
        return if (name == "noerror") noerrorRate
        else rate[name] ?: 0.0
    }

    // o = overstatement = cvr_assort - mvr_assort
    // (u-0),       cvr has vote for winner, mvr has vote for loser : p2o = 2 vote overstatement
    // (u-.5),      cvr has vote for winner, mvr has vote for other : p1o = 1 vote overstatement
    // (.5-0),      cvr has vote for other, mvr has vote for loser : p1o = 1 vote overstatement
    // (cvr==mvr),  no error
    // (.5-u),      cvr has vote for other, mvr has vote for winner : p1u = 1 vote understatement
    // (0-.5),      cvr has vote for loser, mvr has vote for other  : p1u = 1 vote understatement
    // (0-u)        cvr has vote for loser, mvr has vote for winner : p2u = 2 vote understatement

    // bassort = (1-o/u)/(2-v/u) = tau * noerror
    // tau = bassort/noerror = (1-o/u)
    // tau = [0,       1/2u,    1-1/2u,  1,       2-1/2u,  1+1/2u,  2]
    //       [win-los, win-oth, oth-los, noerror, los-oth, oth-win, los-win]
    // tau = [0,   0.5,  1,       1.5, 2]  when u = 1
    //       [p2o, p1o, noerror, p1u, p2u]
    companion object {
        val names = listOf("win-los", "win-oth", "oth-los", "noerror", "los-oth", "oth-win", "los-win")
    }
}

class Taus(upper: Double) {
    private val u12 = 1.0 / (2 * upper)  // (2 * upper) > 1, u12 < 1

    // tau = [0,       1/2u,    1-1/2u,  1,       2-1/2u,  1+1/2u,  2]
    //       [win-los, win-oth, oth-los, noerror, los-oth, oth-win, los-win]
    private val tauValues = listOf(0.0, u12, 1-u12, 1.0,  2-u12, 1+u12, 2.0)
    private val tauDesc = listOf("0", "1/2u", "1-1/2u", "noerror", "2-1/2u", "1+1/2u", "2")

    // cvr-mvr
    fun nameOf(want: Double): String {
        val idx = tauValues.indexOfFirst { doubleIsClose(it, want) }
        return if (idx < 0) "unknown" else TausRates.names[idx]
    }

    fun descOf(want: Double) : String {
        val idx = tauValues.indexOfFirst { doubleIsClose(it, want) }
        return if (idx < 0) "unknown" else tauDesc[idx]
    }

    fun valueOf(wantName: String) : Double {
        val idx = TausRates.names.indexOfFirst { it == wantName }
        return if (idx < 0) 0.0 else tauValues[idx]
    }

    fun values(): List<Double> = tauValues

    fun names(): List<String> = TausRates.names

    fun isClcaError(tau: Double): Boolean {
        val name = nameOf(tau)
        return (name != "noerror") && (name != "unknown")
    }

    override fun toString(): String {
        return TausRates.names.mapIndexed{ idx, name -> Pair(name, tauValues[idx])}.toMap().toString()
    }
}


// "taus rate" is sum of normalizedCount / totalSamples
// normalizedCount is error count / fuzzPct
// so tausRate = error count / fuzzPct / totalSamples
// tau is assort value / noerror; we give each tau a descriptive name
// ncandidates changes the rates (see table below)
// margin doesnt matter, because noerror has been factored out

object TausRateTable {
    val tauRates = mutableMapOf<Int, Map<String, Double>>() // ncands -> tauDesc -> tauRate

    fun makeErrorCounts(ncandidates: Int, fuzzPct: Double, totalSamples: Int, noerror: Double, upper: Double): ClcaErrorCounts {
        val useCand = when  {
            ncandidates < 2 -> 2
            ncandidates > 10 -> 10
            else -> ncandidates
        }
        // find the tauRates for the given ncandidates
        val tauRateForNCand: Map<String, Double> = tauRates[useCand]!!
        val taus = Taus(upper)

        // each tau generates an errorCount
        val errorCounts = taus.names().filter { it != "noerror" }.map { tauName ->
            // errorRate = tausRate * fuzzPct
            val errorRate = (tauRateForNCand[tauName] ?: 0.0) * fuzzPct
            // error count = errorRate * totalSamples
            val errorCount = (totalSamples * errorRate).toInt()
            // tau is assort value / noerror, so assort value = tau * noerror
            val tau = taus.valueOf(tauName)
            Pair(tau * noerror, errorCount)
        }.toMap()

        // data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double)
        return ClcaErrorCounts(errorCounts, totalSamples, noerror, upper)
    }

    // given an error count, what fuzz pct does it corresond to ?
    // inverse of makeErrorRates

    // tausRate = error count / fuzzPct / totalSamples
    // fuzzPct = error count / tausRate / totalSamples
    // fuzzPct = error rate / tausRate

    fun calcFuzzPct(ncandidates: Int, errorCounts: ClcaErrorCounts) : List<Double> {
        val useCand = when  {
            ncandidates < 2 -> 2
            ncandidates > 10 -> 10
            else -> ncandidates
        }
        val tauRateForNCand = tauRates[useCand]!!
        val taus = errorCounts.taus
        val errorRates = errorCounts.errorRates()

        // different fuzzPct for each tau
        val fuzzPcts = errorCounts.errorCounts.map { (assortValue, count) ->
            val tau = assortValue / errorCounts.noerror
            val tauDesc = taus.nameOf(tau)!!
            val tauRate = tauRateForNCand[tauDesc]!!
            val errorRate = errorRates[assortValue] ?: 0.0
            // fuzzPct = error rate / tauRate
            errorRate / tauRate
        }
        return fuzzPcts
    }

    init {
        // TODO this only has tables for upper = 1.0
        // generated 1/1/2026 by GenerateTausErrorTable
        // tauErrorRates N=1000000
        tauRates[2] = mapOf( "win-los" to 0.2515, "oth-los" to 0.27258, "oth-win" to 0.23966, "los-win" to 0.21922,  )
        tauRates[3] = mapOf( "win-los" to 0.12973, "oth-los" to 0.36293, "oth-win" to 0.30457, "los-win" to 0.10201,  )
        tauRates[4] = mapOf( "win-los" to 0.09754, "oth-los" to 0.3847, "oth-win" to 0.22948666666666667, "los-win" to 0.038413333333333334,  )
        tauRates[5] = mapOf( "win-los" to 0.0726, "oth-los" to 0.382905, "oth-win" to 0.223975, "los-win" to 0.03136,  )
        tauRates[6] = mapOf( "win-los" to 0.051956, "oth-los" to 0.359464, "oth-win" to 0.1865, "los-win" to 0.01924,  )
        tauRates[7] = mapOf( "win-los" to 0.04778, "oth-los" to 0.36621333333333334, "oth-win" to 0.16278, "los-win" to 0.013033333333333333,  )
        tauRates[8] = mapOf( "win-los" to 0.03302, "oth-los" to 0.30988571428571426, "oth-win" to 0.14912571428571428, "los-win" to 0.0098,  )
        tauRates[9] = mapOf( "win-los" to 0.031185, "oth-los" to 0.318485, "oth-win" to 0.141475, "los-win" to 0.009395,  )
        tauRates[10] = mapOf( "win-los" to 0.029946666666666667, "oth-los" to 0.3352688888888889, "oth-win" to 0.11130666666666666, "los-win" to 0.005048888888888889,  )    }
}

/*
tauErrorRates N=1000000
rrates[2] = mapOf( "win-los" to 0.2263, "win-oth" to 0.28012, "oth-los" to 0.28012, "noerror" to 0.0, "los-oth" to 0.24746, "oth-win" to 0.24746, "los-win" to 0.22616,  )
rrates[3] = mapOf( "win-los" to 0.12314, "win-oth" to 0.39035, "oth-los" to 0.39035, "noerror" to 0.0, "los-oth" to 0.26711, "oth-win" to 0.26711, "los-win" to 0.07286,  )
rrates[4] = mapOf( "win-los" to 0.08524666666666667, "win-oth" to 0.37255333333333335, "oth-los" to 0.37255333333333335, "noerror" to 0.0, "los-oth" to 0.26398, "oth-win" to 0.26398, "los-win" to 0.04366,  )
rrates[5] = mapOf( "win-los" to 0.0701, "win-oth" to 0.38028, "oth-los" to 0.38028, "noerror" to 0.0, "los-oth" to 0.23144, "oth-win" to 0.23144, "los-win" to 0.029605,  )
rrates[6] = mapOf( "win-los" to 0.04324, "win-oth" to 0.3219, "oth-los" to 0.3219, "noerror" to 0.0, "los-oth" to 0.22698, "oth-win" to 0.22698, "los-win" to 0.02394,  )
rrates[7] = mapOf( "win-los" to 0.04602666666666667, "win-oth" to 0.3632533333333333, "oth-los" to 0.3632533333333333, "noerror" to 0.0, "los-oth" to 0.16333333333333333, "oth-win" to 0.16333333333333333, "los-win" to 0.012153333333333334,  )
rrates[8] = mapOf( "win-los" to 0.0255, "win-oth" to 0.2741485714285714, "oth-los" to 0.2741485714285714, "noerror" to 0.0, "los-oth" to 0.17047714285714285, "oth-win" to 0.17047714285714285, "los-win" to 0.011628571428571429,  )
rrates[9] = mapOf( "win-los" to 0.0234525, "win-oth" to 0.2685925, "oth-los" to 0.2685925, "noerror" to 0.0, "los-oth" to 0.14736, "oth-win" to 0.14736, "los-win" to 0.008075,  )
rrates[10] = mapOf( "win-los" to 0.024777777777777777, "win-oth" to 0.2901088888888889, "oth-los" to 0.2901088888888889, "noerror" to 0.0, "los-oth" to 0.14106888888888888, "oth-win" to 0.14106888888888888, "los-win" to 0.007431111111111111,  )
 */





