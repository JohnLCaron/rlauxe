package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.simulateSampleSizeClcaAssorter
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.workflow.ContestRound
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
        val contestsUAs: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit( it, isComparison = true, hasStyle = true ) }
        val cvrs = test.makeCvrsFromContests()
        // val cvrsUAP = cvrs.map { CvrUnderAudit( it) }

        contestsUAs.forEach { contest ->
            println("contest = ${contest}")
            contest.makeClcaAssertions(cvrs)
            contest.clcaAssertions.forEach {
                println("  comparison assertion = ${it}")
            }
        }
        val contestRounds = contestsUAs.map{ contest -> ContestRound(contest, 1) }

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

        contestRounds.forEach { contest ->
            val cn = contest.Nc
            val estSizes = mutableListOf<Int>()
            val sampleSizes = contest.assertions.map { assertRound ->
                val result = simulateSampleSizeClcaAssorter(1, auditConfig, contest.contestUA.contest as Contest, assertRound, cvrs)
                val simSize = result.findQuantile(auditConfig.quantile)
                val estSize = estimateSampleSizeSimple(auditConfig.riskLimit, assertRound.assertion.assorter.reportedMargin(), gamma,
                    oneOver = roundUp(cn*p1), // p1
                    twoOver = roundUp(cn*p2), // p2
                    oneUnder = roundUp(cn*p3), // p3
                    twoUnder = roundUp(cn*p4), // p4
                    )
                estSizes.add(estSize)
                println("  ${contest.name} margin=${df(assertRound.assertion.assorter.reportedMargin())} est=$estSize sim=$simSize")
                simSize
            }
            val estSize = if (estSizes.isEmpty()) 0 else estSizes.max()
            contest.estSampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
            println("${contest.name} estSize=$estSize  simSize=${contest.estSampleSize}\n")
        }
    }
}