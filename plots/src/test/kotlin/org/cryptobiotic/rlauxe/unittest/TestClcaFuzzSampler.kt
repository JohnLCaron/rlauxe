package org.cryptobiotic.rlauxe.unittest

import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.runRepeatedBettingMart
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.estimate.RunTestRepeatedResult
import org.cryptobiotic.rlauxe.workflow.ClcaFuzzSampler
import org.junit.jupiter.api.Test

// TODO make into a test with asserts ?

class TestClcaFuzzSampler {

    @Test
    fun testComparisonFuzzed() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it).addStandardAssertions() }
        val cvrs = test.makeCvrsFromContests()
        println("total ncvrs = ${cvrs.size}\n")
        val contests = contestsUA.map { ContestRound(it, 1) }

        val auditConfig = AuditConfig(
            AuditType.CLCA,
            hasStyle = true,
            nsimEst=10,
            simFuzzPct = .011
        )

        contests.forEach { contest ->
            val sampleSizes = mutableListOf<Pair<Int, Double>>()
            contest.assertionRounds.map { assertionRound ->
                val result: RunTestRepeatedResult = runWithComparisonFuzzSampler(auditConfig, contest.contestUA, assertionRound, cvrs)
                val size = result.findQuantile(auditConfig.quantile)
                assertionRound.estSampleSize = size
                val assertion = assertionRound.assertion as ClcaAssertion
                sampleSizes.add(Pair(size, assertion.assorter.dilutedMargin()))
                println(" ${assertion.cassorter.assorter().desc()} margin=${df(assertion.assorter.dilutedMargin())} estSize=${size}}")

            }
            // TODO use minAssertion()
            val maxSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.maxOfOrNull { it.first } ?: 0
            val pair = if (sampleSizes.isEmpty()) Pair(0, 0.0) else sampleSizes.find{ it.first == maxSize }!!
            contest.estSampleSize = pair.first
            println("${contest.contestUA.name} estSize=${contest.estSampleSize} margin=${df(pair.second)}")
        }
    }
}

private fun runWithComparisonFuzzSampler(
    auditConfig: AuditConfig,
    contestUA: ContestUnderAudit,
    assertionRound: AssertionRound,
    cvrs: List<Cvr>, // (mvr, cvr)
    moreParameters: Map<String, Double> = emptyMap(),
): RunTestRepeatedResult {
    val cassertion = assertionRound.assertion as ClcaAssertion
    val cassorter = cassertion.cassorter

    val sampler = ClcaFuzzSampler(auditConfig.simFuzzPct!!, cvrs, contestUA.contest as Contest, cassorter)
    val errorCounts = ClcaErrorCounts(emptyMap(), 0, noerror=cassorter.noerror(), upper=cassorter.assorter.upperBound())

    val optimal = GeneralAdaptiveBetting(
        N = contestUA.Npop,
        errorCounts,
        d = 100
    )

    return runRepeatedBettingMart(
        auditConfig,
        sampler,
        optimal,
        // assorter.assorter().reportedMargin(),
        cassorter.noerror(),
        cassorter.upperBound(),
        N=contestUA.Npop,
        moreParameters=moreParameters + mapOf("margin" to cassorter.assorter.dilutedMargin()),
    )
}