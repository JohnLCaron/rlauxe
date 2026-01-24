package org.cryptobiotic.rlauxe.oneaudit

import au.org.democracydevelopers.raire.irv.Votes
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.betting.TausIF
import org.cryptobiotic.rlauxe.raire.RaireAssorter
import org.cryptobiotic.rlauxe.util.doubleIsClose
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.ln

// rate :  assort value -> rate over the entire population
data class OneAuditAssortValueRates(val rates: Map<Double, Double>, val totalInPools: Int) {
    fun sumRates() = rates.map{ it.value }.sum()
    fun avgAssort() = rates.map{ (value, rate) -> value * rate }.sum() / sumRates()

    override fun toString() = buildString {
        append("OneAuditAssortValueRates(totalInPools=$totalInPools n=${rates.size} sumOfRates= ${sumRates()} avgAssort= ${avgAssort()})")
    }
    fun sumOneAuditTerm(maxBet:Double): Double {
        var sumOneAuditTerm = 0.0
        rates.forEach { (assortValue: Double, rate: Double) ->
            sumOneAuditTerm += ln(1.0 + maxBet * (assortValue - 0.5)) * rate
        }
        return sumOneAuditTerm
    }
    fun show() = buildString {
        appendLine(this@OneAuditAssortValueRates)
        rates.forEach { appendLine("  $it") }
    }
}

// we know exactly the assort values and their frequency; non-IRV
class OneAuditRatesFromPools(val pools: List<OneAuditPoolIF>) {

    // non-IRV
    fun oaErrorRates(contestUA: ContestWithAssertions, oaCassorter: ClcaAssorterOneAudit): OneAuditAssortValueRates { // sampleValue -> rate
        val pairs = mutableListOf<Pair<Double, Double>>()
        var totalInPools = 0
        pools.filter{ it.hasContest(contestUA.id )}.forEach { pool ->
            val poolAvg = oaCassorter.poolAverages.assortAverage[pool.poolId]
            if (poolAvg != null) {
                val taus = TausOA(oaCassorter.assorter.upperBound(), poolAvg)

                val votes = pool.regVotes()[contestUA.id]!!
                val winnerCounts: Int = votes.votes[oaCassorter.assorter.winner()] ?: 0
                val loserCounts: Int = votes.votes[oaCassorter.assorter.loser()] ?: 0

                val otherCounts = pool.ncards() - winnerCounts - loserCounts
                totalInPools += pool.ncards()
                val dencards = contestUA.Npop.toDouble() // rate is over entire population

                // sampleValue -> rate
                pairs.add(Pair(taus.tausOA[0].first * oaCassorter.noerror(), loserCounts / dencards))  // loser
                pairs.add(Pair(taus.tausOA[1].first * oaCassorter.noerror(), otherCounts / dencards))  // other
                pairs.add(Pair(taus.tausOA[2].first * oaCassorter.noerror(), winnerCounts / dencards))  // winner
            }
        }

        //         var sumOneAuditTerm = 0.0
        //        if (oaErrorRates != null) { // probably dont need the filter
        //            oaErrorRates.filter { it.value != 0.0 }.forEach { (sampleValue: Double, rate: Double) ->
        //                sumOneAuditTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
        //            }
        //        }
        val rates = mutableMapOf<Double, Double>()
        pairs.filter { it.second > 0.0 }.forEach {
            val rate = rates.getOrPut(it.first) { 0.0 }
            rates[it.first] = rate + it.second
        }
        return OneAuditAssortValueRates(rates.toSortedMap(), totalInPools)  // could also return a string description
    }

    fun oaErrorRatesIrv(contestUA: ContestWithAssertions, oaCassorter: ClcaAssorterOneAudit, raire: RaireAssorter): OneAuditAssortValueRates { // sampleValue -> rate
        val pairs = mutableListOf<Pair<Double, Double>>()
        var totalInPools = 0
        pools.filter{ it.hasContest(contestUA.id )}.forEach { pool ->
            val poolAvg = oaCassorter.poolAverages.assortAverage[pool.poolId]
            if (poolAvg != null) {
                val taus = TausOA(oaCassorter.assorter.upperBound(), poolAvg)

                if (pool is OneAuditPoolFromCvrs) {
                    val tab = pool.contestTabs[contestUA.id]!! // assumes that the cardPool has the irvVotes
                    val irvVotes: Votes = tab.irvVotes.makeVotes(contestUA.ncandidates)
                    val winnerLoser = raire.winnerLoserVotes(irvVotes)
                    val winnerCounts: Int = winnerLoser.first
                    val loserCounts: Int = winnerLoser.second

                    val otherCounts = pool.ncards() - winnerCounts - loserCounts
                    totalInPools += pool.ncards()
                    val dencards = contestUA.Npop.toDouble() // rate is over entire population

                    // sampleValue -> rate
                    pairs.add(Pair(taus.tausOA[0].first * oaCassorter.noerror(), loserCounts / dencards))  // loser
                    pairs.add(Pair(taus.tausOA[1].first * oaCassorter.noerror(), otherCounts / dencards))  // other
                    pairs.add(Pair(taus.tausOA[2].first * oaCassorter.noerror(), winnerCounts / dencards))  // winner
                } else {
                    throw RuntimeException("oaErrorRatesIrv needs OneAuditPoolFromCvrs")
                }
            }
        }

        //         var sumOneAuditTerm = 0.0
        //        if (oaErrorRates != null) { // probably dont need the filter
        //            oaErrorRates.filter { it.value != 0.0 }.forEach { (sampleValue: Double, rate: Double) ->
        //                sumOneAuditTerm += ln(1.0 + lam * (sampleValue - mui)) * rate
        //            }
        //        }
        val rates = mutableMapOf<Double, Double>()
        pairs.filter { it.second > 0.0 }.forEach {
            val rate = rates.getOrPut(it.first) { 0.0 }
            rates[it.first] = rate + it.second
        }
        return OneAuditAssortValueRates(rates.toSortedMap(), totalInPools)  // could also return a string description
    }
}

// Consider a single pool and an assorter a, with upper bound u and avg assort value in the pool = poolAvg.
// poolAvg is used as the cvr_value, so then cvr_assort - mvr_assort has one of 3 possible overstatement values:
//
//    poolAvg - [0, .5, u] = [poolAvg, poolAvg -.5, poolAvg - u] for mvr loser, other and winner
//
// then bassort = (1-o/u)/(2-v/u) = (1-o/u) * noerror
//
//    bassort = [1-poolAvg/u, 1 - (poolAvg -.5)/u, 1 - (poolAvg - u)/u] * noerror
//    bassort = [1-poolAvg/u, (u - poolAvg + .5)/u, (2u - poolAvg)/u] * noerror

//    bassort = [1-poolAvg, 1.5 - poolAvg, 2 - poolAvg] * noerror  when u == 1

class TausOA(val upper: Double, val poolAvg: Double): TausIF {
    val tausOA: List<Pair<Double, String>>  // value, desc

    init {
        // bassort = [1-poolAvg/u,
        //            (u - poolAvg + .5)/u,
        //            (2u - poolAvg)/u] * noerror, for mvr loser, other and winner
        // bassort = [1-poolAvg, 1.5 - poolAvg, 2 - poolAvg] * noerror  when u == 1

        tausOA = mapOf(
            (1 - poolAvg / upper) to "loser",
            (upper - poolAvg + .5) / upper to "other",
            (2 * upper - poolAvg) / upper to "winner"
        ).toList()
    }

    override fun desc(tau: Double): String? {
        val pair = tausOA.find { doubleIsClose(it.first, tau) }
        return pair?.second
    }

    override fun values() = tausOA.map { it.first }.toList()

    override fun toString(): String {
        return tausOA.toString()
    }
}

// poolAvg lies betweeen 0 and u
//              loser  other   winner
// poolAvg= 0 :  [1,    1.5/u,   2  ] * noerror
// poolAvg= u :  [0,    .5/u,    1  ] * noerror

