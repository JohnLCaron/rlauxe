package org.cryptobiotic.rlauxe.estimate.corla

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.estimateClcaAssertionRound
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.math.abs
import kotlin.test.Test

class TestCorlaEstimateSampleSize {

    /*
    @Test
    fun testFindSampleSizePolling() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUA: List<ContestWithAssertions> = test.contests.map { ContestWithAssertions(it, isClca = false).addStandardAssertions() } // CORLA does polling?

        contestsUA.forEach { contest ->
            println("contest = $contest")
            contest.assertions.forEach {
                println("  polling assertion = ${it}")
            }
        }
    }

    @Test
    fun testFindSampleSize() {
        val test = MultiContestTestData(20, 11, 20000)
        val contestsUAs: List<ContestWithAssertions> = test.contests.map { ContestWithAssertions( it, isClca = true).addStandardAssertions() }
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
        val config = AuditConfig(AuditType.CLCA, seed = 1234567890L, quantile=.90)

        contestRounds.forEach { contestRound ->
            val cn = contestRound.Npop
            val estSizes = mutableListOf<Int>()
            val cvrs = simulateContestCvrsWithLimits(contestRound.contestUA.contest as Contest, config).makeCvrs()
            val cards = cvrs.map { AuditableCard.fromCvr(it, 0, 0L)}
            val sampleSizes = contestRound.assertionRounds.map { assertRound ->
                val result = estimateClcaAssertionRound(1, config,cards, contestRound, assertRound)
                val simSize = result.findQuantile(config.quantile)
                val estSize = estimateSampleSizeSimple(config.riskLimit, assertRound.assertion.assorter.dilutedMargin(), gamma,
                    oneOver = roundUp(cn*p1), // p1
                    twoOver = roundUp(cn*p2), // p2
                    oneUnder = roundUp(cn*p3), // p3
                    twoUnder = roundUp(cn*p4), // p4
                    )
                estSizes.add(estSize)
                println("  ${contestRound.name} margin=${df(assertRound.assertion.assorter.dilutedMargin())} est=$estSize sim=$simSize")
                simSize
            }
            val estSize = if (estSizes.isEmpty()) 0 else estSizes.max()
            contestRound.estMvrs = if (sampleSizes.isEmpty()) 0 else sampleSizes.max()
            println("${contestRound.name} estSize=$estSize  simSize=${contestRound.estMvrs}\n")
        }
    }
    */
}


fun simulateContestCvrsWithLimits(contest: Contest, config: AuditConfig): ContestSimulation {
    val limit = config.contestSampleCutoff
    if (limit == null || contest.Nc <= limit) return ContestSimulation(contest, contest.Nc)

    // otherwise scale everything
    val sNc = limit / contest.Nc.toDouble()
    val sNp = roundToClosest(sNc * contest.Nphantoms())
    val sNu = roundToClosest(sNc * contest.Nundervotes())
    val orgVoteCount = contest.votes.map { it.value }.sum() // V_c
    val svotes = contest.votes.map { (id, nvotes) -> id to roundToClosest(sNc * nvotes) }.toMap()
    val voteCount = svotes.map { it.value }.sum() // V_c

    if (abs(voteCount - limit) > 10) {
        println("simulateContestCvrsWithLimits limit wanted = ${limit} scaled = ${voteCount}")
    }

    val contest = Contest(
        contest.info,
        svotes,
        Nc = voteCount + sNu + sNp,
        Ncast = voteCount + sNu,
    )

    return ContestSimulation(contest, contest.Nc)
}