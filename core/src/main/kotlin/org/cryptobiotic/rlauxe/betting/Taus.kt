package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.betting.TausRates.Companion.alias
import org.cryptobiotic.rlauxe.util.doubleIsClose

// may have 5 or 7 values, depending if upper=1 or not..
// use7override means use7 in any case
class Taus(upper: Double, use7override: Boolean = false) {
    private val u12 = 1.0 / (2 * upper)  // (2 * upper) > 1, u12 < 1, because upper > .5
    private val tauValues: List<Double>
    private val tauNames: List<String>

    init {
        if (upper == 1.0 && !use7override) {
            // tau = [0,   0.5,  1,      1.5, 2]  when u = 1
            //       [p2o, p1o, noerror, p1u, p2u]
            tauValues = listOf(0.0, 0.5, 1.0, 1.5, 2.0)
            tauNames = listOf("p2o", "p1o", "noerror", "p1u", "p2u")
        } else {
            // tau = [0,       1/2u,    1-1/2u,  1,       2-1/2u,  1+1/2u,  2]
            //       [win-los, win-oth, oth-los, noerror, los-oth, oth-win, los-win]
            tauValues = listOf(0.0, u12, 1-u12, 1.0, 2-u12, 1+u12, 2.0)
            tauNames = TausRates.names7
        }
    }

    // cvr-mvr
    fun nameOf(want: Double): String {
        val idx = tauValues.indexOfFirst { doubleIsClose(it, want) }
        return if (idx < 0) "unknown" else tauNames[idx]
    }

    fun valueOf(wantName: String) : Double {
        val idx = tauNames.indexOfFirst { it == wantName }
        return if (idx < 0) 0.0 else tauValues[idx]
    }

    // can use 5 names for 7, but not 7 names for 5.
    fun getNamedValue(name: String): Double? {
        var idx = tauNames.indexOfFirst { it == name }
        return if (idx >= 0) tauValues[idx] else {
            idx = tauNames.indexOfFirst { it == alias[name] }
            if (idx < 0) null else tauValues[idx]
        }
    }

    fun values(): List<Double> = tauValues

    fun names(): List<String> = tauNames
    // list of names that does not include "error"
    fun namesNoErrors(): List<String> = tauNames.filter { it != "noerror"}

    fun isClcaError(tau: Double): Boolean {
        val name = nameOf(tau)
        return (name != "noerror") && (name != "unknown")
    }

    fun isPhantom(tausValue: Double): Boolean {
        val name = nameOf(tausValue)
        return name == "oth-los" || name == "p1o"
    }

    override fun toString(): String {
        return tauNames.mapIndexed{ idx, name -> Pair(name, tauValues[idx])}.toMap().toString()
    }
}

/*
from ClcaErrors.md

| cvr - mvr       | value  | name     | SHANGRLA name |
|-----------------|--------|----------|---------------|
| winner-loser    | 0      | win-los  | p2o           |
| winner-phantom  | 0      | win-los  |               |
| winner-other    | 1/2u   | win-oth  | p1o           |
| other-loser     | 1-1/2u | oth-los  | p1o           |
| other-phantom   | 1-1/2u | oth-los  |               |
| phantom-loser   | 1-1/2u | oth-los  |               |
| phantom-phantom | 1-1/2u | oth-los  |               |
| winner-winner   | 1/2    | noerror  |               |
| other-other     | 1/2    | noerror  |               |
| loser-loser     | 1/2    | noerror  |               |
| loser-phantom   | 1/2    | noerror  |               |
| phantom-other   | 1/2    | noerror  |               |
| loser-other     | 1+1/2u | los-oth  | p1u           |
| other-winner    | 2-1/2u | oth-win  | p1u           |
| phantom-winner  | 2-1/2u | oth-win  |               |
| loser-winner    | 2      | los-win  | p2u           |

 */

// uses all 7 names, but may have only a subset. Use getNamedRate() to look for name aliases.
// the rate must be multiplied by noerror to get bassort values.
data class TausRates(val rates: Map<String, Double>) {  // name -> rate over population
    val noerrorRate = 1.0 - rates.filter { it.key != "errror" }.map{ it.value }.sum()

    init {
        rates.forEach { require(names7.contains(it.key)) }
    }

    // convert to ClcaErrorCounts by multiplying by totalSamples, and noerror.
    fun makeErrorCounts(totalSamples: Int, noerror: Double, upper: Double): ClcaErrorCounts {
        val taus = Taus(upper)

        // each tau generates an errorCount
        val errorCounts = mutableMapOf<Double, Int>()
        taus.names().filter { it != "noerror" }.forEach { tauName ->
            val errorRate = getNamedRate(tauName)
            if (errorRate != null) {
                // error count = errorRate * totalSamples
                val errorCount = (totalSamples * errorRate).toInt()
                // tau is assort value / noerror, so assort value = tau * noerror
                val tau = taus.valueOf(tauName)
                errorCounts[tau * noerror] = errorCount
            }
        }

        // data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double)
        return ClcaErrorCounts(errorCounts, totalSamples, noerror, upper)
    }

    // will look for both 7 names and 5 names.
    fun getNamedRate(name: String): Double? {
        if (name == "noerror") return noerrorRate
        var tauRate =  rates[name]
        if (tauRate == null) {
            tauRate = rates[alias[name]]
        }
        return tauRate
    }

    // o = overstatement = cvr_assort - mvr_assort
    // (u-0),       cvr has vote for winner, mvr has vote for loser : p2o = 2 vote overstatement
    // (u-.5),      cvr has vote for winner, mvr has vote for other : p1o = 1 vote overstatement
    // (.5-0),      cvr has vote for other, mvr has vote for loser : p1o = 1 vote overstatement
    // (cvr==mvr),  no error
    // (.5-u),      cvr has vote for other, mvr has vote for winner : p1u = 1 vote understatement
    // (0-.5),      cvr has vote for loser, mvr has vote for other  : p1u = 1 vote understatement
    // (0-u)        cvr has vote for loser, mvr has vote for winner : p2u = 2 vote understatement

    // bassort = (1-o/u) * noerror = tau * noerror
    // tau = bassort/noerror = (1-o/u)
    // tau = [0,       1/2u,    1-1/2u,  1,       1+1/2u,  1+1/2u,  2]
    //       [win-los, win-oth, oth-los, noerror, los-oth, oth-win, los-win]
    // tau = [0,   0.5,  1,       1.5, 2]  when u = 1
    //       [p2o, p1o, noerror, p1u, p2u]
    companion object {
        val names7 = listOf("win-los", "win-oth", "oth-los", "noerror", "oth-win", "los-oth", "los-win")
        val alias = mapOf("p2o" to "win-los", "p1o" to "win-oth", "p1u" to "los-oth", "p2u" to "los-win")
    }
}

// "taus rate" is sum of normalizedCount / totalSamples
// normalizedCount is error count / fuzzPct
// so tausRate = error count / fuzzPct / totalSamples
// tau is assort value / noerror; we give each tau a descriptive name
// ncandidates changes the rates (see table below)
// margin doesnt matter, because noerror has been factored out

object TausRateTable {
    private val normalizedRates = mutableMapOf<Int, TausRates>() // ncands -> tauDesc -> tauRate

    // return ClcaErrorCounts by multiplying by fuzzPct, totalSamples, and noerror.
    fun makeErrorCounts(ncandidates: Int, fuzzPct: Double, totalSamples: Int, noerror: Double, upper: Double): ClcaErrorCounts {
        val useCand = when  {
            ncandidates < 2 -> 2
            ncandidates > 10 -> 10
            else -> ncandidates
        }
        // find the tauRates for the given ncandidates
        val tauRateForNCand: TausRates = normalizedRates[useCand]!!
        val taus = Taus(upper)

        // each tau generates an errorCount
        val errorCounts = taus.names().filter { it != "noerror" }.map { tauName ->
            // errorRate = tausRate * fuzzPct
            val errorRate = tauRateForNCand.getNamedRate(tauName)!! * fuzzPct
            // error count = errorRate * totalSamples
            val errorCount = (totalSamples * errorRate).toInt()
            // tau is assort value / noerror, so assort value = tau * noerror
            val tau = taus.valueOf(tauName)
            Pair(tau * noerror, errorCount)
        }.toMap()

        // data class ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double)
        return ClcaErrorCounts(errorCounts, totalSamples, noerror, upper)
    }

    // Experimental - do not use
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
        val tauRateForNCand = normalizedRates[useCand]!!
        val taus = errorCounts.taus
        val errorRates = errorCounts.errorRates()

        // different fuzzPct for each tau
        val fuzzPcts = errorCounts.errorCounts.map { (assortValue, count) ->
            val tau = assortValue / errorCounts.noerror
            val name = taus.nameOf(tau)
            val tauRate = tauRateForNCand.getNamedRate(name) ?: throw RuntimeException("calcFuzzPct cant find tauRateForNCand $name ncand=$ncandidates")
            val errorRate = errorRates[assortValue] ?: 0.0
            // fuzzPct = error rate / tauRate
            errorRate / tauRate
        }
        return fuzzPcts
    }

    init {
        // 2/7/26
        // tauErrorRates N=1000000
        normalizedRates[2] = TausRates(mapOf( "win-los" to 0.21339818181818182, "win-oth" to 0.2664109090909091, "oth-los" to 0.2664109090909091, "los-oth" to 0.2591127272727273, "oth-win" to 0.2591127272727273, "los-win" to 0.20853636363636363))
        normalizedRates[3] = TausRates(mapOf( "win-los" to 0.13128727272727272, "win-oth" to 0.3697027272727273, "oth-los" to 0.3697027272727273, "los-oth" to 0.26815272727272726, "oth-win" to 0.26815272727272726, "los-win" to 0.0780790909090909))
        normalizedRates[4] = TausRates(mapOf( "win-los" to 0.08808363636363636, "win-oth" to 0.3794666666666667, "oth-los" to 0.3794666666666667, "los-oth" to 0.23896242424242425, "oth-win" to 0.23896242424242425, "los-win" to 0.04194484848484849))
        normalizedRates[5] = TausRates(mapOf( "win-los" to 0.062468636363636365, "win-oth" to 0.3578081818181818, "oth-los" to 0.3578081818181818, "los-oth" to 0.20814772727272726, "oth-win" to 0.20814772727272726, "los-win" to 0.025486818181818183))
        normalizedRates[6] = TausRates(mapOf( "win-los" to 0.048581454545454546, "win-oth" to 0.3369643636363636, "oth-los" to 0.3369643636363636, "los-oth" to 0.19450036363636364, "oth-win" to 0.19450036363636364, "los-win" to 0.02000618181818182))
        normalizedRates[7] = TausRates(mapOf( "win-los" to 0.039082121212121214, "win-oth" to 0.32060333333333335, "oth-los" to 0.32060333333333335, "los-oth" to 0.16815060606060606, "oth-win" to 0.16815060606060606, "los-win" to 0.013656969696969697))
        normalizedRates[8] = TausRates(mapOf( "win-los" to 0.03302077922077922, "win-oth" to 0.30717766233766236, "oth-los" to 0.30717766233766236, "los-oth" to 0.15172103896103897, "oth-win" to 0.15172103896103897, "los-win" to 0.010150649350649351))
        normalizedRates[9] = TausRates(mapOf( "win-los" to 0.028155909090909093, "win-oth" to 0.29654977272727273, "oth-los" to 0.29654977272727273, "los-oth" to 0.14481227272727273, "oth-win" to 0.14481227272727273, "los-win" to 0.008987727272727272))
        normalizedRates[10] = TausRates(mapOf( "win-los" to 0.02313131313131313, "win-oth" to 0.2760222222222222, "oth-los" to 0.2760222222222222, "los-oth" to 0.1349258585858586, "oth-win" to 0.1349258585858586, "los-win" to 0.007379595959595959))
    }
}

// probably not needed

// B(bi, ci) = (1-o/u)/(2-v/u) = (1-o/u) * noerror, where
//                o is the overstatement = (cvr_assort - mvr_assort)
//                u is the upper bound on the value the assorter assigns to any ballot
//                v is the assorter margin
//                noerror = 1/(2-v/u) == B(bi, ci) when o = 0 (no error)

// assort in [0, .5, u], u > .5, so overstatementError in
//      [-1, -.5, 0, .5, 1] (plurality)
//      [-u, -.5, .5-u, 0, u-.5, .5, u] (SM, u in [.5, 1])
//      [-u, .5-u, -.5, 0, .5, u-.5, u] (SM, u > 1)

// so bassort in
// [1+(u-l)/u, 1+(.5-l)/u, 1+(u-.5)/u,  1, 1-(.5-l)/u, 1-(u-.5)/u, 1-(u-l)/u] * noerror
// [2, 1+1/2u, 2-1/2u,  1, 1-1/2u, 1/2u, 0] * noerror (l==0) (we will assume this)
// [2, 1.5,  1, .5, 0] * noerror (l==0, u==1)

// o = cvr_assort - mvr_assort when l = 0:
// [0, .5, u] - [0, .5, u] = 0, -.5, -u
//                         = .5,  0, .5-u
//                         = u, u-.5, 0
// u, u-.5, .5,  0, .5-u, -.5, -u = (cvr - mvr) = (u-0), (u-.5), (.5-0), (cvr==mvr), (.5-u), (0-.5), (0-u)

// (u-0),       cvr has vote for winner, mvr has vote for loser : p2o = 2 vote overstatement
// (u-.5),      cvr has vote for winner, mvr has vote for other : p1o = 1 vote overstatement
// (.5-0),      cvr has vote for other, mvr has vote for loser : p1o = 1 vote overstatement
// (cvr==mvr),  no error
// (.5-u),      cvr has vote for other, mvr has vote for winner : p1u = 1 vote understatement
// (0-.5),      cvr has vote for loser, mvr has vote for other  : p1u = 1 vote understatement
// (0-u)        cvr has vote for loser, mvr has vote for winner : p2u = 2 vote understatement

//      winner-loser expect 0.0000 actual 0.0000 tau='    0' (p2o)
//     winner-other expect 0.2857 actual 0.2857 tau='  1/2u' (p1o)
//      other-loser expect 0.7143 actual 0.7143 tau='1-1/2u' (p1o)
//    winner-winner expect 1.0000 actual 1.0000 tau='     1' (noerror)
//      other-other expect 1.0000 actual 1.0000 tau='     1' (noerror)
//      loser-loser expect 1.0000 actual 1.0000 tau='     1' (noerror)
//      loser-other expect 1.2857 actual 1.2857 tau='1+1/2u' (p1u)
//     other-winner expect 1.7143 actual 1.7143 tau='2-1/2u' (p1u)
//     loser-winner expect 2.0000 actual 2.0000 tau='     2' (p2u)

//  tau = (1.0 - overstatement / this.assorter.upperBound()) // τi eq (6)
//  assort = tau * noerror   // Bi eq (7)

// tj = 1 + λ_j * (x_i − µ_j)
//tj = 1 + λ_j * (tau * noerror − 1/2)
//
//when u = 1, tau = [0, .5, 1, 1,5, 2]
//
//0.0: tj = 1 - λ/2
//0.5: tj = 1 + λ/2 (noerror - 1)
//1.0: tj = 1 + λ   (noerror - 1/2)
//1.5: tj = 1 + λ   (3 * noerror - 1)/2
//2.0: tj = 1 + λ   (2 * noerror - 1/2)
//
//
//noerror = 1/(2-v/u)   u in (1/2 ... inf)
//u = 1, noerror = 1/(2-v), v in 0..1 so noerror in (1/2 to 1)
//u > 1/2, noerror = 1/(2-2v), v in (0..1) so noerror in (1/2 to inf)
//u > 1, noerror = 1/(2-v/u), v in (0..1) so v/u ??
//u=inf, noerror = 1/(2-v/u) > 1/2
//
//
//in order to gain, 1 + λ_j * (x_i − µ_j) must be > 1
//
//1 - λ/2 always loses
//1 + λ/2 (noerror - 1) wins if noerror > 1 otherwise loses; when u = 1,  noerror in (1/2 to 1), so always loses
//
//
//if you bet < 1, are you betting that you will lose ??
//
//
//tau = [0,       1/2u,    1-1/2u,  1,       1+1/2u,  1+1/2u,  2]
//
//tj = 1 + λ_j * (tau * noerror − 1/2)
//
//0: 1 - λ/2
//1/2u: 1 + λ * (noerror/2u − 1/2)
//1-1/2u: 1 + λ * ((1-1/2u) * noerror − 1/2)
//1: 1 + λ * (noerror − 1/2)
//1+1/2u: 1 + λ * ((1+1/2u)noerror − 1/2)
//1+1/2u: 1 + λ * ((1+1/2u)noerror − 1/2)
//2: 1 + λ * (2*noerror − 1/2)





