package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.simulateSampleSizeClcaAssorter
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.roundUp
import kotlin.test.Test

class TestCorlaEstimateSampleSize {

    @Test
    fun testFindSampleSizePolling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it, isComparison = false, hasStyle = true) } // CORLA does polling?

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
        val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit( it, isComparison = true, hasStyle = true ) }
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
        val auditConfig = AuditConfig(AuditType.CLCA, hasStyles=true, seed = 1234567890L, quantile=.90)

        contestsUA.forEach { contestUA ->
            val cn = contestUA.Nc
            val estSizes = mutableListOf<Int>()
            val sampleSizes = contestUA.clcaAssertions.map { assert ->
                val result = simulateSampleSizeClcaAssorter(auditConfig, contestUA.contest as Contest, assert, cvrs)
                val simSize = result.findQuantile(auditConfig.quantile)
                val estSize = estimateSampleSizeSimple(auditConfig.riskLimit, assert.assorter.reportedMargin(), gamma,
                    oneOver = roundUp(cn*p1), // p1
                    twoOver = roundUp(cn*p2), // p2
                    oneUnder = roundUp(cn*p3), // p3
                    twoUnder = roundUp(cn*p4), // p4
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