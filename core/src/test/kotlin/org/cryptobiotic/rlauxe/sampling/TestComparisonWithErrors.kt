package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals


class TestComparisonWithErrors {

    @Test
    fun testFuzzTwoPersonContest() {
        val avgCvrAssortValue = .505
        val ncvrs = 10000
        val cvrs = makeCvrsByExactMean(ncvrs, avgCvrAssortValue)
        val contest = makeContestsFromCvrs(cvrs).first()
        val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
        val assort = contestUA.comparisonAssertions.first().cassorter

        val cvrsUI = cvrs.map { CvrUnderAudit(it) }

        // flip
        val mvrsFuzzed = cvrsUI.map { it.flip() }
        var sampler = ComparisonWithoutReplacement(contestUA, mvrsFuzzed.zip(cvrsUI), assort, allowReset = true)
        var samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(0, 0, 5050, 0, 4950), samples.samplingErrors())

        // omit
        val mvrsOmit = cvrsUI.map { it.omit() }
        sampler = ComparisonWithoutReplacement(contestUA, mvrsOmit.zip(cvrsUI), assort, allowReset = true)
        samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(0, 5050, 0, 4950, 0), samples.samplingErrors())
    }

    @Test
    fun testFuzzThreePersonContest() {
        val avgCvrAssortValue = .505
        val ncvrs = 10000
        val cvrs = makeCvrsByExactMean(ncvrs, avgCvrAssortValue)
        val cvrsUI = cvrs.map { CvrUnderAudit(it) }

        val contest = makeContestsFromCvrs(cvrs).first()
        val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
        val assort = contestUA.comparisonAssertions.first().cassorter

        // flip
        val mvrsFlip = cvrsUI.map { it.flip() }
        var sampler = ComparisonWithoutReplacement(contestUA, mvrsFlip.zip(cvrsUI), assort, allowReset = true)
        var samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("flip samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(0, 0, 5050, 0, 4950), samples.samplingErrors())

        // omit
        val mvrsOmit = cvrsUI.map { it.omit() }
        sampler = ComparisonWithoutReplacement(contestUA, mvrsOmit.zip(cvrsUI), assort, allowReset = true)
        samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("omit samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(0, 5050, 0, 4950, 0), samples.samplingErrors())

        // vote other
        val mvrsOther = cvrsUI.map { it.set(2) }
        sampler = ComparisonWithoutReplacement(contestUA, mvrsOther.zip(cvrsUI), assort, allowReset = true)
        samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("mvrsOther samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(0, 5050, 0, 4950, 0), samples.samplingErrors())

        // vote 1
        val mvrs1 = cvrsUI.map { it.set(1) }
        sampler = ComparisonWithoutReplacement(contestUA, mvrs1.zip(cvrsUI), assort, allowReset = true)
        samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("mvrs1 samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(4950, 0, 5050, 0, 0), samples.samplingErrors())

        // vote 0
        val mvrs0 = cvrsUI.map { it.set(0) }
        sampler = ComparisonWithoutReplacement(contestUA, mvrs0.zip(cvrsUI), assort, allowReset = true)
        samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("mvrs0 samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(5050, 0, 0, 0, 4950), samples.samplingErrors())
    }

    fun CvrUnderAudit.flip(): Cvr {
        val nvotes: Map<Int, IntArray> = this.votes.map { (key, value) ->
            val flip = if (value[0] == 0) 1 else 0
            Pair( key, IntArray(1) { flip })
        }.toMap()
        return Cvr(this.id, nvotes)
    }

    fun CvrUnderAudit.omit(): Cvr {
        val nvotes: Map<Int, IntArray> = this.votes.map { (key, value) ->
            Pair( key, IntArray(value.size-1) { value[it-1] })
        }.toMap()
        return Cvr(this.id, nvotes)
    }

    fun CvrUnderAudit.set(votedFor: Int): Cvr {
        val nvotes: Map<Int, IntArray> = this.votes.map { (key, _) ->
            Pair( key, IntArray(1) { votedFor })
        }.toMap()
        return Cvr(this.id, nvotes)
    }
}