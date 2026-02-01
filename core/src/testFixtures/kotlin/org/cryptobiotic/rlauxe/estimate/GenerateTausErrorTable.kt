package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.betting.ClcaErrorCounts
import org.cryptobiotic.rlauxe.betting.ClcaSamplerErrorTracker
import org.cryptobiotic.rlauxe.betting.Taus
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import kotlin.test.Test

class GenerateTausErrorTable {
    val showRates = false

    @Test
    fun makeTauErrorRates() {
        val ncontests = 9
        val phantomPct = 0.00
        val totalBallots = 1_000_000

        // make 1 contest with ncandidates = 2..11
        // make seperate rates for each ncandidates
        // for now, u = 1

        val test = MultiContestTestData(ncontests, 1, totalBallots=totalBallots, phantomPctRange=phantomPct..phantomPct, seqCands = true)
        val contestsUA = test.contests.map {
            println(" contest ${it.id} ncands ${it.ncandidates}")
            ContestWithAssertions(it).addStandardAssertions()
        }
        val cards = test.makeCardsFromContests()
        val fuzzPcts = listOf(0.0001, 0.0005, 0.001, .005,  .01, )

        val tauErrorRatesList = mutableMapOf<Int, MutableList<TauErrorRatesCumul>>() // avg across assertions
        val tauErrorRates = mutableMapOf<Int, TauErrorRatesCumul>() // avg across assertions

        fuzzPcts.forEach { fuzzPct ->

            val fcards = makeFuzzedCardsForClca(contestsUA.map { it.contest.info() }, cards, fuzzPct)
            val testPairs = fcards.zip(cards)

            contestsUA.forEach { contestUA ->
                val terc = TauErrorRatesCumul() // avg across assertions
                contestUA.clcaAssertions.forEach { cassertion ->
                    val cassorter = cassertion.cassorter
                    val sampler: ClcaSamplerErrorTracker  = ClcaSamplerErrorTracker(contestUA.id, cassorter, testPairs)
                    while (sampler.hasNext()) {
                        sampler.next()
                    }
                    terc.add(sampler.measuredClcaErrorCounts(), fuzzPct)
                }
                val tauList = tauErrorRatesList.getOrPut(contestUA.ncandidates) { mutableListOf() }
                tauList.add(terc)
                // println("ncands ${contestUA.ncandidates}: fuzzPct= $fuzzPct totalSamples=${terc.totalSamples}: ${terc.rates()}")

                // avg across assertions and fuzzPcts
                val tercAll = tauErrorRates.getOrPut(contestUA.ncandidates) { TauErrorRatesCumul() } // avg across assertions and fuzzPcts
                tercAll.add(terc)
            }
        }
        println("all N=$totalBallots")
        tauErrorRates.forEach { (ncands, ter) ->
            val tauList = tauErrorRatesList[ncands]!!
            println("ncands $ncands")
            tauList.forEach {
                println("    rates=${it.rates()}  totalSamples=${it.totalSamples}")
            }
            println("avg rates=${ter.rates()}  totalSamples=${ter.totalSamples}")
        }

        println("\ntauErrorRates N=$totalBallots")
        // cut and paste into TausErrorTable
        tauErrorRates.forEach { (ncands, ter) ->
            println("rrates[$ncands] = mapOf( ${ter.ratesString()} )")
        }
    }
}

class TauErrorRatesCumul {
    val taus = Taus(1.0)
    val tauCounts = mutableMapOf<Double, Double>()  // tau, normalizedCount
    var totalSamples = 0

    fun add(cec: ClcaErrorCounts, fuzzPct: Double) {
        // convert assort values to taus
        cec.errorCounts.forEach { (assort, count) ->
            val tau = assort / cec.noerror // will depend on upper, for now upper == 1
            val tc = tauCounts.getOrPut(tau) { 0.0 }
            val normalizedCount = if (fuzzPct == 0.0) count.toDouble() else count / fuzzPct
            tauCounts[tau] = tc + normalizedCount
        }
        totalSamples += cec.totalSamples
    }

    fun add(terc: TauErrorRatesCumul) {
        // convert assort values to taus
        terc.tauCounts.forEach { (tau, ncount) ->
            val tc = tauCounts.getOrPut(tau) { 0.0 }
            tauCounts[tau] = tc + ncount
        }
        totalSamples += terc.totalSamples
    }

    fun rates(): Map<String, Double> {
        return taus.taus.map { (tau, desc) ->
            val ncount = tauCounts[tau] ?: 0.0
            Pair(desc, ncount/totalSamples)
        }.toMap()
        // tauCounts.toSortedMap().map { it.value / totalSamples.toDouble() }
    }

    fun ratesString() = buildString {
        taus.taus.forEach { (tau, desc) ->
            val ncount = tauCounts[tau] ?: 0.0
            append("\"$desc\" to ${ncount/totalSamples}, ")
        }
    }
}

