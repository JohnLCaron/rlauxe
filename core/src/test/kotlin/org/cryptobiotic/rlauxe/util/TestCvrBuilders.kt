package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCvrBuilders {

    @Test
    fun testConvertCvrsRoundtrip() {
        val test = MultiContestTestData(20, 11, 20000)
        val contests: List<Contest> = test.contests
        val cvrs = test.makeCvrsFromContests()

        // turn the cvrs into mutable builders
        val cvrsbs = CvrBuilders()
        cvrsbs.addContests( contests.map { it.info } )
        cvrs.forEach { CvrBuilder.fromCvr(cvrsbs, it) }

        // convert back to Cvr
        val roundtrip: List<Cvr> = cvrsbs.build().map { it }
        // same order
        roundtrip.forEachIndexed { idx, it ->
            val cvr2 = cvrs[idx]
            if (it != cvr2)
                println("cvr")
            assertEquals(cvrs[idx], it)
        }

        cvrsbs.show()
    }

    @Test
    fun testFuzzedCvrs() {
        val ncontests = 20
        val test = MultiContestTestData(ncontests, 11, 50000)
        val contests: List<Contest> = test.contests
        val cvrs = test.makeCvrsFromContests()
        val detail = false
        val ntrials = 1
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = makeFuzzedCvrsFrom(contests, cvrs, fuzzPct)
            println("fuzzPct = $fuzzPct")
            val allErrorRates = mutableListOf<PluralityErrorRates>()
            contests.forEach { contest ->
                val contestUA = makeContestUAfromCvrs(contest.info, cvrs)
                val minAssert = contestUA.minClcaAssertion().first
                if (minAssert != null) repeat(ntrials) {
                    val minAssort = minAssert.cassorter
                    val samples = PluralityErrorTracker(minAssort.noerror())
                    var ccount = 0
                    var count = 0
                    fcvrs.forEachIndexed { idx, fcvr ->
                        if (fcvr.hasContest(contest.id)) {
                            samples.addSample(minAssort.bassort(fcvr, cvrs[idx]))
                            ccount++
                            if (cvrs[idx] != fcvr) count++
                        }
                    }
                    val fuzz = count.toDouble() / ccount
                    println("$it ${contest.name} changed = $count out of ${ccount} = ${df(fuzz)}")
                    if (detail) {
                        println("  errorCounts = ${samples.pluralityErrorCounts()}")
                        println("  errorRates =  ${samples.errorRates()}")
                    }
                    allErrorRates.add(samples.pluralityErrorRates())
                }
            }
            val total = ntrials * ncontests
            val avgRates = PluralityErrorRates(
                allErrorRates.sumOf { it.p2o } / total,
                allErrorRates.sumOf { it.p1o } / total,
                allErrorRates.sumOf { it.p1u } / total,
                allErrorRates.sumOf { it.p2u } / total,
            )
            println("  avgRates = $avgRates")
            // println("  error% = ${avgRates.map { it / (total * fuzzPct) }}")
        }
    }
}