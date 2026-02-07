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
            val tauDesc = taus.nameOf(tau)
            val tauRate = tauRateForNCand[tauDesc]
            if (tauRate == null) {
                print("")
            }
            val errorRate = errorRates[assortValue] ?: 0.0
            // fuzzPct = error rate / tauRate
            errorRate / tauRate!!
        }
        return fuzzPcts
    }

    init {
        // 2/7/26
        // tauErrorRates N=1000000
        tauRates[2] = mapOf( "win-los" to 0.21339818181818182, "win-oth" to 0.2664109090909091, "oth-los" to 0.2664109090909091, "los-oth" to 0.2591127272727273, "oth-win" to 0.2591127272727273, "los-win" to 0.20853636363636363,  )
        tauRates[3] = mapOf( "win-los" to 0.13128727272727272, "win-oth" to 0.3697027272727273, "oth-los" to 0.3697027272727273, "los-oth" to 0.26815272727272726, "oth-win" to 0.26815272727272726, "los-win" to 0.0780790909090909,  )
        tauRates[4] = mapOf( "win-los" to 0.08808363636363636, "win-oth" to 0.3794666666666667, "oth-los" to 0.3794666666666667, "los-oth" to 0.23896242424242425, "oth-win" to 0.23896242424242425, "los-win" to 0.04194484848484849,  )
        tauRates[5] = mapOf( "win-los" to 0.062468636363636365, "win-oth" to 0.3578081818181818, "oth-los" to 0.3578081818181818, "los-oth" to 0.20814772727272726, "oth-win" to 0.20814772727272726, "los-win" to 0.025486818181818183,  )
        tauRates[6] = mapOf( "win-los" to 0.048581454545454546, "win-oth" to 0.3369643636363636, "oth-los" to 0.3369643636363636, "los-oth" to 0.19450036363636364, "oth-win" to 0.19450036363636364, "los-win" to 0.02000618181818182,  )
        tauRates[7] = mapOf( "win-los" to 0.039082121212121214, "win-oth" to 0.32060333333333335, "oth-los" to 0.32060333333333335, "los-oth" to 0.16815060606060606, "oth-win" to 0.16815060606060606, "los-win" to 0.013656969696969697,  )
        tauRates[8] = mapOf( "win-los" to 0.03302077922077922, "win-oth" to 0.30717766233766236, "oth-los" to 0.30717766233766236, "los-oth" to 0.15172103896103897, "oth-win" to 0.15172103896103897, "los-win" to 0.010150649350649351,  )
        tauRates[9] = mapOf( "win-los" to 0.028155909090909093, "win-oth" to 0.29654977272727273, "oth-los" to 0.29654977272727273, "los-oth" to 0.14481227272727273, "oth-win" to 0.14481227272727273, "los-win" to 0.008987727272727272,  )
        tauRates[10] = mapOf( "win-los" to 0.02313131313131313, "win-oth" to 0.2760222222222222, "oth-los" to 0.2760222222222222, "los-oth" to 0.1349258585858586, "oth-win" to 0.1349258585858586, "los-win" to 0.007379595959595959,  )
    }
}








