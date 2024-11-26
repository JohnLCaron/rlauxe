package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.makeContestsFromCvrs
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.secureRandom
import kotlin.test.Test
import kotlin.test.assertEquals


class TestComparisonFuzzed {

    @Test
    fun testFuzzTwoPersonContest() {
        val avgCvrAssortValue = .505
        val ncvrs = 10000
        val cvrs = makeCvrsByExactMean(ncvrs, avgCvrAssortValue)
        val contest = makeContestsFromCvrs(cvrs).first()
        val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
        val assort = contestUA.comparisonAssertions.first().assorter

        val cvrsUI = cvrs.map { CvrUnderAudit(it as Cvr, false) }

        // flip
        val mvrsFuzzed = cvrsUI.map { it.flip() }
        var sampler = ComparisonSamplerGen(mvrsFuzzed.zip(cvrsUI), contestUA, assort)
        var samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(0, 0, 5050, 0, 4950), samples.samplingErrors())

        // omit
        val mvrsOmit = cvrsUI.map { it.omit() }
        sampler = ComparisonSamplerGen(mvrsOmit.zip(cvrsUI), contestUA, assort)
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
        val cvrsUI = cvrs.map { CvrUnderAudit(it as Cvr, false) }

        val contest = makeContestsFromCvrs(cvrs).first()
        val contestUA = ContestUnderAudit(contest).makeComparisonAssertions(cvrs)
        val assort = contestUA.comparisonAssertions.first().assorter

        // flip
        val mvrsFlip = cvrsUI.map { it.flip() }
        var sampler = ComparisonSamplerGen(mvrsFlip.zip(cvrsUI), contestUA, assort)
        var samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("flip samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(0, 0, 5050, 0, 4950), samples.samplingErrors())

        // omit
        val mvrsOmit = cvrsUI.map { it.omit() }
        sampler = ComparisonSamplerGen(mvrsOmit.zip(cvrsUI), contestUA, assort)
        samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("omit samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(0, 5050, 0, 4950, 0), samples.samplingErrors())

        // vote other
        val mvrsOther = cvrsUI.map { it.set(2) }
        sampler = ComparisonSamplerGen(mvrsOther.zip(cvrsUI), contestUA, assort)
        samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("mvrsOther samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(0, 5050, 0, 4950, 0), samples.samplingErrors())

        // vote 1
        val mvrs1 = cvrsUI.map { it.set(1) }
        sampler = ComparisonSamplerGen(mvrs1.zip(cvrsUI), contestUA, assort)
        samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("mvrs1 samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(4950, 0, 5050, 0, 0), samples.samplingErrors())

        // vote 0
        val mvrs0 = cvrsUI.map { it.set(0) }
        sampler = ComparisonSamplerGen(mvrs0.zip(cvrsUI), contestUA, assort)
        samples = PrevSamplesWithRates( assort.noerror)
        repeat(ncvrs) { samples.addSample( sampler.sample())}
        println("mvrs0 samplingErrors = ${ samples.samplingErrors() }")
        assertEquals(listOf(5050, 0, 0, 0, 4950), samples.samplingErrors())
    }

    fun CvrUnderAudit.flip(): CvrUnderAudit {
        val nvotes: Map<Int, IntArray> = this.votes.map { (key, value) ->
            val flip = if (value[0] == 0) 1 else 0
            Pair( key, IntArray(1) { flip })
        }.toMap()
        return CvrUnderAudit( Cvr(this.id, nvotes), false)
    }

    fun CvrUnderAudit.omit(): CvrUnderAudit {
        val nvotes: Map<Int, IntArray> = this.votes.map { (key, value) ->
            Pair( key, IntArray(value.size-1) { value[it-1] })
        }.toMap()
        return CvrUnderAudit( Cvr(this.id, nvotes), false)
    }

    fun CvrUnderAudit.set(votedFor: Int): CvrUnderAudit {
        val nvotes: Map<Int, IntArray> = this.votes.map { (key, value) ->
            Pair( key, IntArray(1) { votedFor })
        }.toMap()
        return CvrUnderAudit( Cvr(this.id, nvotes), false)
    }

    @Test
    fun testComparisonFuzzed() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.makeContests().map { ContestUnderAudit(it, it.Nc) }
        val cvrsUAP = test.makeCvrsFromContests().map { CvrUnderAudit.fromCvrIF( it, false) }
        contestsUA.forEach { contest ->
            println("contest = ${contest}")
            contest.makeComparisonAssertions(cvrsUAP)
            contest.comparisonAssertions.forEach {
                println("  comparison assertion = ${it}")
            }
        }
        println("total ncvrs = ${cvrsUAP.size}\n")

        val mvrsFuzzed = cvrsUAP.map { it.fuzzed() }
        val cvrPairs: List<Pair<CvrIF, CvrUnderAudit>> = mvrsFuzzed.zip(cvrsUAP)
        cvrPairs.forEach { (mvr, cvr) -> require(mvr.id == cvr.id) }

        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, riskLimit=0.05, seed = secureRandom.nextLong(), quantile=.50,
            p1=0.0, p2=0.0, p3=0.0, p4=0.0, )

        contestsUA.forEach { contestUA ->
            val sampleSizes = mutableListOf<Int>()
            contestUA.comparisonAssertions.map { assertion ->
                val result: RunTestRepeatedResult = runRepeatedAudit(auditConfig, contestUA, assertion, cvrPairs)
                val size = result.findQuantile(auditConfig.quantile)
                assertion.samplesEst = size
                sampleSizes.add(assertion.samplesEst)
            }
            contestUA.estSampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
            println("${contestUA.name} estSize=${contestUA.estSampleSize}")
        }
    }
}

fun CvrUnderAudit.fuzzed(): CvrUnderAudit {
    return this
}

private fun runRepeatedAudit(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    assertion: ComparisonAssertion,
    cvrPairs: List<Pair<CvrIF, CvrUnderAudit>>, // (mvr, cvr)
): RunTestRepeatedResult {
    val assorter = assertion.assorter
    val sampler = ComparisonSamplerGen(cvrPairs, contestUA, assorter)

    val optimal = AdaptiveComparison(
        Nc = contestUA.Nc,
        withoutReplacement = true,
        a = assorter.noerror,
        d1 = auditConfig.d1,
        d2 = auditConfig.d2,
        p1 = auditConfig.p1,
        p2 = auditConfig.p2,
        p3 = auditConfig.p3,
        p4 = auditConfig.p4,
    )
    val testFn = BettingMart(
        bettingFn = optimal,
        Nc = contestUA.Nc,
        noerror = assorter.noerror,
        upperBound = assorter.upperBound,
        withoutReplacement = false
    )

    val result: RunTestRepeatedResult = runTestRepeated(
        drawSample = sampler,
        maxSamples = contestUA.ncvrs,
        ntrials = auditConfig.ntrials,
        testFn = testFn,
        testParameters = mapOf("p1" to optimal.p1, "p2" to optimal.p2, "p3" to optimal.p3, "p4" to optimal.p4, "margin" to assorter.margin),
        showDetails = false,
        margin = assorter.margin,
    )
    return result
}