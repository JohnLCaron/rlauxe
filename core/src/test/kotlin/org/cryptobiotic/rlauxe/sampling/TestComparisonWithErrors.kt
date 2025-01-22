package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.df
import kotlin.test.Test
import kotlin.test.assertEquals

class TestComparisonWithErrors {

    @Test
    fun testFuzzTwoPersonContest() {
        val avgCvrAssortValue = .505
        val mvrsFuzzPct = .10
        val ncvrs = 10000
        val testCvrs = makeCvrsByExactMean(ncvrs, avgCvrAssortValue)
        val contest = makeContestsFromCvrs(testCvrs).first()
        val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(testCvrs)
        val assort = contestUA.comparisonAssertions.first().cassorter

        // fuzz
        val testMvrs = makeFuzzedCvrsFrom(listOf(contest), testCvrs, mvrsFuzzPct)
        var sampler = ComparisonWithoutReplacement(contestUA.contest as Contest, testMvrs.zip(testCvrs), assort, allowReset = true)
        var samples = PrevSamplesWithRates( assort.noerror())
        repeat(ncvrs) {
            samples.addSample( sampler.sample())
        }
        println("  errorCounts = ${samples.errorCounts()}")
        println("  errorRates =  ${samples.errorRates()}")
        assertEquals(ncvrs, samples.errorCounts().sum() )

        val changes = samples.errorCounts().subList(1, samples.errorCounts().size).sum()
        val changePct = changes/samples.numberOfSamples().toDouble()
        assertEquals(mvrsFuzzPct, changePct, .01)
    }

}