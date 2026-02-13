package org.cryptobiotic.rlauxe.betting

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCardsForClca
import kotlin.test.Test


// simulate multiple contests with different numbers of candidates. Fuzz the cvrs and measure the error counts.
// convert the counts to tauErrorRates (divide out the noerror factor) and then normalize by fuzzPct.
// now we have tauErrorRates that can be multiplied by the fuzzPct and noerror to simulate the error rates:
//      tauErrorRate =
// copy those into TausErrorTable (in ClcaErrorCounts) so we can estimate the ClcaErrorCounts with
//       fun makeErrorRates(ncandidates: Int, fuzzPct: Double, totalSamples: Int, noerror: Double, upper: Double): ClcaErrorCounts
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

        val tauErrorRatesList = mutableMapOf<Int, MutableList<TauErrorRatesCumul>>() // avg across assertions
        val tauErrorRates = mutableMapOf<Int, TauErrorRatesCumul>() // avg across assertions

        repeat (11) {
            val test = MultiContestTestData(
                ncontests,
                1,
                totalBallots = totalBallots,
                phantomPctRange = phantomPct..phantomPct,
                seqCands = true
            )
            val contestsUA = test.contests.map {
                println(" contest ${it.id} ncands ${it.ncandidates}")
                ContestWithAssertions(it).addStandardAssertions()
            }
            val cards = test.makeCardsFromContests()
            val fuzzPcts = listOf(0.0001, 0.0005, 0.001, .005, .01)


            fuzzPcts.forEach { fuzzPct ->
                val fcards = makeFuzzedCardsForClca(contestsUA.map { it.contest.info() }, cards, fuzzPct)
                val testPairs = fcards.zip(cards)

                contestsUA.forEach { contestUA ->
                    val terc = TauErrorRatesCumul() // avg across assertions
                    contestUA.clcaAssertions.forEach { cassertion ->
                        val cassorter = cassertion.cassorter
                        val sampler: ClcaSamplerErrorTracker =
                            ClcaSamplerErrorTracker(contestUA.id, cassorter, testPairs)
                        while (sampler.hasNext()) {
                            sampler.next()
                        }
                        terc.add(sampler.measuredClcaErrorCounts(), fuzzPct)
                    }
                    val tauList = tauErrorRatesList.getOrPut(contestUA.ncandidates) { mutableListOf() }
                    tauList.add(terc)
                    // println("ncands ${contestUA.ncandidates}: fuzzPct= $fuzzPct totalSamples=${terc.totalSamples}: ${terc.rates()}")

                    // avg across assertions and fuzzPcts
                    val tercAll =
                        tauErrorRates.getOrPut(contestUA.ncandidates) { TauErrorRatesCumul() } // avg across assertions and fuzzPcts
                    tercAll.add(terc)
                }
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

        //// cut and paste this output into TausErrorTable
        // tauRates[2] = Taus7Rates(mapOf( "win-los" to 0.21339818181818182, "win-oth" to 0.2664109090909091, "oth-los" to 0.2664109090909091, "los-oth" to 0.2591127272727273, "oth-win" to 0.2591127272727273, "los-win" to 0.20853636363636363))
        println("\ntauErrorRates N=$totalBallots")
        tauErrorRates.forEach { (ncands, ter) ->
            println("tauRates[$ncands] = Taus7Rates(mapOf( ${ter.ratesString()}))")
        }
    }
}

class TauErrorRatesCumul {
    val taus = Taus(1.0, use7override = true)
    val tauCounts = mutableMapOf<Double, Double>()  // tau, normalizedCount
    var totalSamples = 0

    fun add(cec: ClcaErrorCounts, fuzzPct: Double) {
        // convert assort values to taus by dividing by noerror
        cec.errorCounts.forEach { (assortValue, count) ->
            val tau = assortValue / cec.noerror // the tau value; will depend on upper, for now upper == 1
            val tc = tauCounts.getOrPut(tau) { 0.0 }
            // convert counts to normalizedCount by dividing by fuzzPct
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

    // rate is sum of normalizedCount / totalSamples
    fun rates(): Map<String, Double> {
        return taus.names().map { name ->
            val tau = taus.valueOf(name)
            val ncount = tauCounts[tau] ?: 0.0
            Pair(name, ncount/totalSamples)
        }.toMap()
        // tauCounts.toSortedMap().map { it.value / totalSamples.toDouble() }
    }

    fun ratesString() = buildString {
        taus.names().forEach { name ->
            val tau = taus.valueOf(name)
            val ncount = tauCounts[tau] ?: 0.0
            append("\"$name\" to ${ncount/totalSamples}, ")
        }
    }
}

