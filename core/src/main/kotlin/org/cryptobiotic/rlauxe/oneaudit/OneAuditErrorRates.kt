package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.TausIF
import org.cryptobiotic.rlauxe.util.doubleIsClose


// The rates are over the entire population
data class OneAuditErrorRates(val name: String, val rates: Map<Double, Double>, val totalInPools: Int)

// we know exactly the return values and their frequency
class OneAuditErrorsFromPools(val pools: List<OneAuditPoolIF>) {
    // could also contain the ClcaErrorCounts(val errorCounts: Map<Double, Int>, val totalSamples: Int, val noerror: Double, val upper: Double) ??

    fun oaErrorRates(contestUA: ContestWithAssertions, oaCassorter: ClcaAssorterOneAudit): OneAuditErrorRates { // sampleValue -> rate
        val result = mutableListOf<Pair<Double, Double>>()
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
                val dcards = contestUA.Npop.toDouble() // rate is over entire population

                // sampleValue -> rate
                result.add(Pair(taus.tausOA[0].first * oaCassorter.noerror(), loserCounts / dcards))
                result.add(Pair(taus.tausOA[1].first * oaCassorter.noerror(), otherCounts / dcards))
                result.add(Pair(taus.tausOA[2].first * oaCassorter.noerror(), winnerCounts / dcards))
            }
        }
        return OneAuditErrorRates("name", result.toMap().toSortedMap(), totalInPools)  // could also return a string description
    }
}

// Consider a single pool and an assorter a, with upper bound u and avg assort value in the pool = poolAvg.
// poolAvg is used as the cvr_value, so then mvr_assort - mvr_assort has one of 3 possible overstatement values:
//
//    poolAvg - [0, .5, u] = [poolAvg, poolAvg -.5, poolAvg - u] for mvr loser, other and winner
//
// then bassort = (1-o/u)/(2-v/u) in [0, 2] * noerror
//
//    bassort = [1-poolAvg/u, 1 - (poolAvg -.5)/u, 1 - (poolAvg - u)/u] * noerror
//    bassort = [1-poolAvg/u, (u - poolAvg + .5)/u, (2u - poolAvg)/u] * noerror

class TausOA(val upper: Double, val poolAvg: Double): TausIF {
    val tausOA: List<Pair<Double, String>>

    init {
        // bassort = [1-poolAvg/u, (u - poolAvg + .5)/u, (2u - poolAvg)/u] * noerror, for mvr loser, other and winner
        tausOA = mapOf(
            (1 - poolAvg / upper) to "loser",
            (upper - poolAvg + .5) / upper to "other",
            (2 * upper - poolAvg) / upper to "winner"
        ).toList()
    }

    override fun desc(want: Double): String? {
        val pair = tausOA.find { doubleIsClose(it.first, want) }
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

