package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
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
            val avgRates = mutableListOf(0.0, 0.0, 0.0, 0.0, 0.0)
            contests.forEach { contest ->
                val contestUA = ContestUnderAudit(contest.info, cvrs).makeComparisonAssertions(cvrs)
                val minAssert = contestUA.minComparisonAssertion()
                if (minAssert != null) repeat(ntrials) {
                    val minAssort = minAssert.cassorter
                    val samples = PrevSamplesWithRates(minAssort.noerror)
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
                        println("  errors = ${samples.samplingErrors()}")
                        println("  rates =  ${samples.samplingErrors(ccount.toDouble())}")
                        println("  error% = ${samples.samplingErrors(ccount * fuzz)}")
                    }
                    samples.samplingErrors()
                        .forEachIndexed { idx, it -> avgRates[idx] = avgRates[idx] + it / ccount.toDouble() }
                }
            }
            val total = ntrials * ncontests
            println("  avgRates = ${avgRates.map { it / total }}")
            println("  error% = ${avgRates.map { it / (total * fuzzPct) }}")
        }
    }
}