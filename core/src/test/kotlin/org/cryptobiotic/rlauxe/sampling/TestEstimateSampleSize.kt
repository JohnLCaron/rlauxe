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
    fun testFindSampleSize() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestUnderAudit> = test.makeContests().map { ContestUnderAudit( it, it.Nc) }
        val cvrs = test.makeCvrsFromContests()
        val cvrsUAP = cvrs.map { CvrUnderAudit( it) }

        contestsUA.forEach { contest ->
            println("contest = ${contest}")

            contest.makePollingAssertions()
            contest.pollingAssertions.forEach {
                println("  polling assertion = ${it}")
            }
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
        val auditConfig = AuditConfig(AuditType.CARD_COMPARISON, hasStyles=true, seed = 1234567890L, fuzzPct = null, quantile=.50)
        val finder = EstimateSampleSize(auditConfig)

        contestsUA.forEach { contestUA ->
            val cn = contestUA.ncvrs
            val estSizes = mutableListOf<Int>()
            val sampleSizes = contestUA.comparisonAssertions.map { assert ->
                //         contestsUA.forEach { contestUA -> finder.simulateSampleSizeComparisonContest(contestUA, cvrsUA, prevMvrs, round) }
                val result = finder.simulateSampleSizeAssorter(contestUA, assert.assorter, cvrs,)
                //     riskLimit: Double,
                //    dilutedMargin: Double,
                //    gamma: Double = 1.03,
                //    oneOver: Int = 0,   // p1
                //    twoOver: Int = 0,   // p2
                //    oneUnder: Int = 0,  // p3
                //    twoUnder: Int = 0,  // p4
                val simSize = result.findQuantile(.90)
                val estSize = estimateSampleSizeSimple(auditConfig.riskLimit, assert.assorter.margin, gamma,
                    oneOver = ceil(cn*p1).toInt(),
                    twoOver = ceil(cn*p2).toInt(),
                    oneUnder = ceil(cn*p3).toInt(),
                    twoUnder = ceil(cn*p4).toInt(),
                    )
                estSizes.add(estSize)
                println("  ${contestUA.name} margin=${df(assert.assorter.margin)} est=$estSize sim=$simSize")
                simSize
            }
            val estSize = if (estSizes.isEmpty()) 0 else estSizes.max()
            contestUA.estSampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
            println("${contestUA.name} estSize=$estSize  simSize=${contestUA.estSampleSize}\n")
        }
    }
}