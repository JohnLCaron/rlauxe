package org.cryptobiotic.rlauxe.estimate.corla

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.estimateClcaAssertionRound
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import kotlin.test.Test

class TestCorlaEstimateSampleSize {

    @Test
    fun testFindSampleSizePolling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit(it, isClca = false, hasStyle = true).addStandardAssertions() } // CORLA does polling?

        contestsUA.forEach { contest ->
            println("contest = $contest")
            contest.pollingAssertions.forEach {
                println("  polling assertion = ${it}")
            }
        }
    }

    @Test
    fun testFindSampleSize() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUAs: List<ContestUnderAudit> = test.contests.map { ContestUnderAudit( it, isClca = true, hasStyle = true).addStandardAssertions() }
        val cvrs = test.makeCvrsFromContests()

        contestsUAs.forEach { contest ->
            println("contest = ${contest}")
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
        val config = AuditConfig(AuditType.CLCA, hasStyle=true, seed = 1234567890L, quantile=.90)

        contestRounds.forEach { contestRound ->
            val cn = contestRound.Nb
            val estSizes = mutableListOf<Int>()
            val cvrs = ContestSimulation.simulateContestCvrsWithLimits(contestRound.contestUA.contest as Contest, config).makeCvrs()
            val cards = cvrs.map { AuditableCard.fromCvr(it, 0, 0L)}
            val sampleSizes = contestRound.assertionRounds.map { assertRound ->
                val result = estimateClcaAssertionRound(1, config, contestRound, cards, assertRound)
                val simSize = result.findQuantile(config.quantile)
                val estSize = estimateSampleSizeSimple(config.riskLimit, assertRound.assertion.assorter.reportedMargin(), gamma,
                    oneOver = roundUp(cn*p1), // p1
                    twoOver = roundUp(cn*p2), // p2
                    oneUnder = roundUp(cn*p3), // p3
                    twoUnder = roundUp(cn*p4), // p4
                    )
                estSizes.add(estSize)
                println("  ${contestRound.name} margin=${df(assertRound.assertion.assorter.reportedMargin())} est=$estSize sim=$simSize")
                simSize
            }
            val estSize = if (estSizes.isEmpty()) 0 else estSizes.max()
            contestRound.estSampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
            println("${contestRound.name} estSize=$estSize  simSize=${contestRound.estSampleSize}\n")
        }
    }
}