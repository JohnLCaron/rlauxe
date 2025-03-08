package org.cryptobiotic.rlauxe.unittest

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.ClcaFuzzSampler
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.simulateSampleSizeBetaMart
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.estimate.RunTestRepeatedResult
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
        val contests = contestsUA.map { ContestRound(it, 1) }

        val auditConfig = AuditConfig(
            AuditType.CLCA,
            hasStyles = true,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.phantoms, simFuzzPct = .011)
        )

        contests.forEach { contest ->
            val sampleSizes = mutableListOf<Pair<Int, Double>>()
            contest.assertionRounds.map { assertionRound ->
                val result: RunTestRepeatedResult = runWithComparisonFuzzSampler(auditConfig, contest.contestUA, assertionRound, cvrs)
                val size = result.findQuantile(auditConfig.quantile)
                assertionRound.estSampleSize = size
                val assertion = assertionRound.assertion as ClcaAssertion
                sampleSizes.add(Pair(size, assertion.assorter.reportedMargin()))
                println(" ${assertion.cassorter.assorter().desc()} margin=${df(assertion.assorter.reportedMargin())} estSize=${size}}")

            }
            // TODO use minAssertion()
            val maxSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.map { it.first }.max() ?: 0
            val pair = if (sampleSizes.isEmpty()) Pair(0, 0.0) else sampleSizes.find{ it.first == maxSize }!!
            contest.estSampleSize = pair.first
            println("${contest.contestUA.name} estSize=${contest.estSampleSize} margin=${df(pair.second)}")
        }
    }
}

fun runWithComparisonFuzzSampler(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    assertionRound: AssertionRound,
    cvrs: List<Cvr>, // (mvr, cvr)
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val clcaConfig = auditConfig.clcaConfig
    val assertion = assertionRound.assertion as ClcaAssertion
    val assorter = assertion.cassorter

    // TODO using fuzzPct as mvrsFuzz
    val sampler = ClcaFuzzSampler(clcaConfig.simFuzzPct!!, cvrs, contestUA.contest as Contest, assorter)
    val optimal = AdaptiveComparison(
        Nc = contestUA.Nc,
        withoutReplacement = true,
        a = assorter.noerror(),
        d = clcaConfig.d,
        ClcaErrorTable.getErrorRates(contestUA.ncandidates, clcaConfig.simFuzzPct),
    )

    return simulateSampleSizeBetaMart(
        1,
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