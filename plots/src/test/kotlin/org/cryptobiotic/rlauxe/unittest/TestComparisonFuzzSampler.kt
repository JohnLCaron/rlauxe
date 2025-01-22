package org.cryptobiotic.rlauxe.unittest

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.ComparisonFuzzSampler
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.sampling.simulateSampleSizeBetaMart
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.secureRandom
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import org.cryptobiotic.rlauxe.workflow.ClcaErrorRates
import org.cryptobiotic.rlauxe.sampling.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.workflow.PollingConfig
import org.junit.jupiter.api.Test

class TestComparisonFuzzSampler {

    @Test
    fun testFuzzedCvrs() {
        val ncontests = 1
        val test = MultiContestTestData(ncontests, 1, 50000)
        print("contest = ${test.contests.first()}")
        val cvrs = test.makeCvrsFromContests()
        val detail = true
        val ntrials = 1
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = makeFuzzedCvrsFrom(test.contests, cvrs, fuzzPct)
            println(" fuzzPct = $fuzzPct")
            val avgRates = mutableListOf(0.0, 0.0, 0.0, 0.0, 0.0)
            test.contests.forEach { contest ->
                val contestUA = ContestUnderAudit(contest.info, cvrs).makeClcaAssertions(cvrs)
                val minAssert = contestUA.minClcaAssertion()
                if (minAssert != null) repeat(ntrials) {
                    val minAssort = minAssert.cassorter
                    val samples = PrevSamplesWithRates(minAssort.noerror())
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
                        println("  errorCounts = ${samples.errorCounts()}")
                        println("  errorRates =  ${samples.errorRates()}")
                    }
                    samples.errorRates()
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
        val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it) }
        val cvrs = test.makeCvrsFromContests()
        contestsUA.forEach { contest ->
            contest.makeClcaAssertions(cvrs)
        }
        println("total ncvrs = ${cvrs.size}\n")

        val auditConfig = AuditConfig(
            AuditType.CARD_COMPARISON,
            hasStyles = true,
            seed = secureRandom.nextLong(),
            quantile = .50,
            pollingConfig = PollingConfig(fuzzPct = .01)
        )

        contestsUA.forEach { contestUA ->
            val sampleSizes = mutableListOf<Pair<Int, Double>>()
            contestUA.clcaAssertions.map { assertion ->
                val result: RunTestRepeatedResult = runWithComparisonFuzzSampler(auditConfig, contestUA, assertion, cvrs)
                val size = result.findQuantile(auditConfig.quantile)
                assertion.estSampleSize = size
                sampleSizes.add(Pair(size, assertion.assorter.reportedMargin()))
                println(" ${assertion.cassorter.assorter().desc()} margin=${df(assertion.assorter.reportedMargin())} estSize=${size}}")

            }
            // TODO use minAssertion()
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
    assertion: ClcaAssertion,
    cvrs: List<Cvr>, // (mvr, cvr)
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val clcaConfig = auditConfig.clcaConfig
    val assorter = assertion.cassorter
    val sampler = ComparisonFuzzSampler(clcaConfig.fuzzPct!!, cvrs, contestUA.contest as Contest, assorter)
    val optimal = AdaptiveComparison(
        Nc = contestUA.Nc,
        withoutReplacement = true,
        a = assorter.noerror(),
        d1 = clcaConfig.d1,
        d2 = clcaConfig.d2,
        ClcaErrorRates.getErrorRates(contestUA.ncandidates, clcaConfig.fuzzPct),
    )

    return simulateSampleSizeBetaMart(
        auditConfig,
        sampler,
        optimal,
        assorter.assorter().reportedMargin(),
        assorter.noerror(),
        assorter.upperBound(),
        Nc=contestUA.Nc,
        moreParameters=moreParameters,
    )
}