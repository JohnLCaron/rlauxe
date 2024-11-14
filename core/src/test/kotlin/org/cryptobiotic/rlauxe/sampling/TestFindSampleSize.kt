package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.util.df
import kotlin.math.ceil
import kotlin.test.Test


class TestFindSampleSize {
    @Test
    fun testFindSampleSize() {
        val (contestsUA, cvrsUAP) = makeTestData(0, true)
        contestsUA.forEach { contest ->
            println("contest = ${contest}")
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
        val riskLimit = .05
        val finder = FindSampleSize(riskLimit, p1=p1, p2=p2, p3=p3, p4=p4, 1000, .90)

        contestsUA.forEach { contestUA ->
            val cn = contestUA.ncvrs
            val estSizes = mutableListOf<Int>()
            val sampleSizes = contestUA.comparisonAssertions.map { assert ->
                val simSize = finder.simulateSampleSize(contestUA, assert.assorter, cvrsUAP,)
                //     riskLimit: Double,
                //    dilutedMargin: Double,
                //    gamma: Double = 1.03,
                //    oneOver: Int = 0,   // p1
                //    twoOver: Int = 0,   // p2
                //    oneUnder: Int = 0,  // p3
                //    twoUnder: Int = 0,  // p4
                val estSize = estimateSampleSizeSimple(riskLimit, assert.assorter.margin, gamma,
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
            contestUA.sampleSize = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
            println("${contestUA.name} estSize=$estSize  simSize=${contestUA.sampleSize}\n")
        }
    }
}