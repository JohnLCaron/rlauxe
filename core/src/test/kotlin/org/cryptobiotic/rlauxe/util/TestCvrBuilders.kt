package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestCvrBuilders {

    @Test
    fun testConvertCvrsRoundtrip() {
        val test = MultiContestTestData(20, 11, 20000)
        val contests: List<Contest> = test.makeContests()
        val cvrs = test.makeCvrsFromContests()

        // turn the cvrs into mutable builders
        val cvrsbs = CvrBuilders()
        cvrsbs.addContests( contests.map { it.info} )
        cvrs.forEach { CvrBuilder.fromCvr(cvrsbs, it) }

        // convert back to Cvr
        val roundtrip: List<Cvr>  = cvrsbs.build().map { it as Cvr}
        // same order
        roundtrip.forEachIndexed { idx, it ->
            assertEquals( cvrs[idx], it)
        }
    }

    @Test
    fun testFuzzedCvrs() {
        val ncontests = 20
        val test = MultiContestTestData(ncontests, 11, 50000)
        val contests: List<Contest> = test.makeContests()
        val cvrs = test.makeCvrsFromContests()
        val detail = false
        val ntrials = 100
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = test.makeFuzzedCvrsFrom(contests, cvrs, fuzzPct)
            println("fuzzPct = $fuzzPct")
            val avgRates = mutableListOf(0.0, 0.0, 0.0, 0.0, 0.0)
            contests.forEach { contest ->
                repeat(ntrials) {
                    val contestUA = ContestUnderAudit(contest.info, cvrs).makeComparisonAssertions(cvrs)
                    val minMargin = contestUA.comparisonAssertions.map { it.margin }.min()
                    val minAssort = contestUA.comparisonAssertions.find { it.margin == minMargin }!!.assorter
                    val samples = PrevSamplesWithRates(minAssort.noerror)
                    var ccount = 0
                    var count = 0
                    fcvrs.forEachIndexed { idx, it ->
                        if (it.hasContest(contest.id)) {
                            samples.addSample(minAssort.bassort(it, cvrs[idx]))
                            ccount++
                            if (cvrs[idx] != it) count++
                        }
                    }
                    val fuzz = count.toDouble() / ccount
                    if (detail) {
                        println(" ${contest.name} changed = $count out of ${ccount} = ${df(fuzz)}")
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