package org.cryptobiotic.rlauxe.unittest

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.ClcaFuzzSampler
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.simulateSampleSizeBetaMart
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.sampling.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.workflow.*
import org.junit.jupiter.api.Test

// TODO make into a test with asserts ?

class TestClcaFuzzSampler {

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
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous, simFuzzPct = .011)
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

fun runWithComparisonFuzzSampler(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    assertion: ClcaAssertion,
    cvrs: List<Cvr>, // (mvr, cvr)
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val clcaConfig = auditConfig.clcaConfig
    val assorter = assertion.cassorter

    // TODO using fuzzPct as mvrsFuzz
    val sampler = ClcaFuzzSampler(clcaConfig.simFuzzPct!!, cvrs, contestUA.contest as Contest, assorter)
    val optimal = AdaptiveComparison(
        Nc = contestUA.Nc,
        withoutReplacement = true,
        a = assorter.noerror(),
        d = clcaConfig.d,
        ClcaErrorRates.getErrorRates(contestUA.ncandidates, clcaConfig.simFuzzPct),
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