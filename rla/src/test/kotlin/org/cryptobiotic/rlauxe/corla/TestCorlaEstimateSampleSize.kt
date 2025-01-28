package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.sampling.simulateSampleSizeClcaAssorter
import org.cryptobiotic.rlauxe.util.df
import kotlin.math.ceil
import kotlin.test.Test

class TestCorlaEstimateSampleSize {
    @Test
    fun testFindSampleSizePolling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it, isComparison = false) }

        contestsUA.forEach { contest ->
            println("contest = $contest")
            contest.makePollingAssertions()
            contest.pollingAssertions.forEach {
                println("  polling assertion = ${it}")
            }
        }
    }

    @Test
    fun testFindSampleSize() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit( it ) }
        val cvrs = test.makeCvrsFromContests()
        // val cvrsUAP = cvrs.map { CvrUnderAudit( it) }

        contestsUA.forEach { contest ->
            println("contest = ${contest}")
            contest.makeClcaAssertions(cvrs)
            contest.clcaAssertions.forEach {
                println("  comparison assertion = ${it}")
            }
        }

        //contestsUA.forEach { println("contest = ${it}") }
        println("ncvrs = ${cvrs.size}\n")
        val p1 = .01
        val p2 = .001
        val p3 = .01
        val p4 = .001

        //val computeSize = finder.computeSampleSize(contestsUA, cvrsUAP) // wtf ??
        //println("computeSize = $computeSize")

        val gamma = 1.2
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 1234567890L, quantile=.90)

        contestsUA.forEach { contestUA ->
            val cn = contestUA.Nc
            val estSizes = mutableListOf<Int>()
            val sampleSizes = contestUA.clcaAssertions.map { assert ->
                val result = simulateSampleSizeClcaAssorter(auditConfig, contestUA.contest as Contest, assert, cvrs)
                val simSize = result.findQuantile(auditConfig.quantile)
                val estSize = estimateSampleSizeSimple(auditConfig.riskLimit, assert.assorter.reportedMargin(), gamma,
                    oneOver = ceil(cn*p1).toInt(), // p1
                    twoOver = ceil(cn*p2).toInt(), // p2
                    oneUnder = ceil(cn*p3).toInt(), // p3
                    twoUnder = ceil(cn*p4).toInt(), // p4
                    )
                estSizes.add(estSize)
                println("  ${contestUA.name} margin=${df(assert.assorter.reportedMargin())} est=$estSize sim=$simSize")
                simSize
            }
            val estSize = if (estSizes.isEmpty()) 0 else estSizes.max()
            contestUA.estSampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
            println("${contestUA.name} estSize=$estSize  simSize=${contestUA.estSampleSize}\n")
        }
    }
}