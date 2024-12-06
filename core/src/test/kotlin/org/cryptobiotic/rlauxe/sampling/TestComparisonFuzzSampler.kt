package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.*
import kotlin.test.Test


class TestComparisonFuzzSampler {

    @Test
    fun testFuzzedCvrs() {
        val ncontests = 1
        val test = MultiContestTestData(ncontests, 1, 50000)
        val contests: List<Contest> = test.makeContests()
        print("contest = ${contests.first()}")
        val cvrs = test.makeCvrsFromContests()
        val detail = false
        val ntrials = 1
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = makeFuzzedCvrsFrom(contests, cvrs, fuzzPct)
            println(" fuzzPct = $fuzzPct")
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

    @Test
    fun testComparisonFuzzed() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.makeContests().map { ContestUnderAudit(it, it.Nc) }
        val cvrs = test.makeCvrsFromContests()
        contestsUA.forEach { contest ->
            contest.makeComparisonAssertions(cvrs)
        }
        println("total ncvrs = ${cvrs.size}\n")

        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = secureRandom.nextLong(), quantile=.50, fuzzPct = .01)

        contestsUA.forEach { contestUA ->
            val sampleSizes = mutableListOf<Pair<Int, Double>>()
            contestUA.comparisonAssertions.map { assertion ->
                val result: RunTestRepeatedResult = runWithComparisonFuzzSampler(auditConfig, contestUA, assertion, cvrs)
                val size = result.findQuantile(auditConfig.quantile)
                assertion.estSampleSize = size
                sampleSizes.add(Pair(size, assertion.cmargin))
                println(" ${assertion.cassorter.assorter.desc()} margin=${df(assertion.cassorter.margin)} estSize=${size}}")

            }
            val maxSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.map { it.first }.max() ?: 0
            val pair = if (sampleSizes.isEmpty()) Pair(0, 0.0) else sampleSizes.find{ it.first == maxSize }!!
            contestUA.estSampleSize = pair.first
            println("${contestUA.name} estSize=${contestUA.estSampleSize} margin=${df(pair.second)}")
        }
    }
}

private fun runWithComparisonFuzzSampler(
        auditConfig: AuditConfig,
        contestUA: ContestUnderAudit,
        assertion: ComparisonAssertion,
        cvrs: List<Cvr>, // (mvr, cvr)
        moreParameters: Map<String, Double> = emptyMap(),
    ): RunTestRepeatedResult {

    val assorter = assertion.cassorter
    val sampler = ComparisonFuzzSampler(auditConfig.fuzzPct!!, cvrs, contestUA, assorter)

    return simulateSampleSizeBetaMart(
        auditConfig,
        sampler,
        assorter.margin,
        assorter.noerror,
        assorter.upperBound(),
        Nc=contestUA.Nc,
        ComparisonErrorRates.getErrorRates(contestUA.ncandidates, auditConfig.fuzzPct),
        maxSamples=contestUA.ncvrs,
        moreParameters=moreParameters,
    )
}