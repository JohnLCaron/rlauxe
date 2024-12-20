package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.corla.estimateSampleSizeSimple
import org.cryptobiotic.rlauxe.util.df
import kotlin.math.ceil
import kotlin.test.Test

class TestEstimateSampleSize {
    @Test
    fun testFindSampleSizePolling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.makeContests().map { ContestUnderAudit(it, isComparison = false) }

        contestsUA.forEach { contest ->
            println("contest = ${contest}")
            contest.makePollingAssertions()
            contest.pollingAssertions.forEach {
                println("  polling assertion = ${it}")
            }
        }
    }

    @Test
    fun testFindSampleSize() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.makeContests().map { ContestUnderAudit( it ) }
        val cvrs = test.makeCvrsFromContests()
        val cvrsUAP = cvrs.map { CvrUnderAudit( it) }

        contestsUA.forEach { contest ->
            println("contest = ${contest}")
            contest.makeComparisonAssertions(cvrsUAP)
            contest.comparisonAssertions.forEach {
                println("  comparison assertion = ${it}")
            }
        }

        //contestsUA.forEach { println("contest = ${it}") }
        println("ncvrs = ${cvrsUAP.size}\n")
        val p1 = .01
        val p2 = .001
        val p3 = .01
        val p4 = .001

        //val computeSize = finder.computeSampleSize(contestsUA, cvrsUAP) // wtf ??
        //println("computeSize = $computeSize")

        val gamma = 1.2
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 1234567890L, fuzzPct = null, quantile=.90)

        contestsUA.forEach { contestUA ->
            val cn = contestUA.Nc
            val estSizes = mutableListOf<Int>()
            val sampleSizes = contestUA.comparisonAssertions.map { assert ->
                val result = simulateSampleSizeComparisonAssorter(auditConfig, contestUA, assert.cassorter, cvrs)
                val simSize = result.findQuantile(auditConfig.quantile)
                val estSize = estimateSampleSizeSimple(auditConfig.riskLimit, assert.cassorter.margin, gamma,
                    oneOver = ceil(cn*p1).toInt(), // p1
                    twoOver = ceil(cn*p2).toInt(), // p2
                    oneUnder = ceil(cn*p3).toInt(), // p3
                    twoUnder = ceil(cn*p4).toInt(), // p4
                    )
                estSizes.add(estSize)
                println("  ${contestUA.name} margin=${df(assert.cassorter.margin)} est=$estSize sim=$simSize")
                simSize
            }
            val estSize = if (estSizes.isEmpty()) 0 else estSizes.max()
            contestUA.estSampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
            println("${contestUA.name} estSize=$estSize  simSize=${contestUA.estSampleSize}\n")
        }
    }
}

// fun simulateSampleSizeComparisonAssorter(
//    auditConfig: AuditConfig,
//    contestUA: ContestUnderAudit,
//    cassorter: ComparisonAssorter,
//    cvrs: List<Cvr>,
//    maxSamples: Int,
//    startingTestStatistic: Double = 1.0,
//): RunTestRepeatedResult {