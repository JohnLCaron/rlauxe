package org.cryptobiotic.rlauxe.ga

import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.oneaudit.AssortAvgsInPools
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditRatesFromPools
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestPoolAssortRates {

    @Test
    fun testPoolAssortRates() {
        val topdir = "$cases/ga/ga2026"
        val record = AuditRecord.read(topdir) as AuditRecord
        val manager = PersistedMvrManager(record)

        val lastRound = record.rounds.last()
        val contestRounds = lastRound.contestRounds
        val contestsFromRounds = contestRounds.map { it.contestUA }
        println()

        val contests = manager.contestsUA
        val pools = manager.pools()!!

        val contest1 = contests.find { it.id == 1 }!! // rep senate
        val assert1 = contest1.minAssertion()!!  // , plurality
        assertTrue(assert1 is ClcaAssertion)
        assertTrue(assert1.cassorter is OneAuditClcaAssorter)
        println(assert1)

        val contest2 = contests.find { it.id == 2 }!! // dem gov
        val assert2 = contest2.minAssertion()!!  // , threshold
        assertTrue(assert2 is ClcaAssertion)
        assertTrue(assert2.cassorter is OneAuditClcaAssorter)
        println(assert2)
        println()

        val assertPairs = listOf(Pair(contest1, assert1), Pair(contest2, assert2))
        val oneAuditErrorsFromPools = OneAuditRatesFromPools(pools)

        assertPairs.forEach { (contest, assert) ->
            val oaAssorter = assert.cassorter as OneAuditClcaAssorter

            val assortAverages = mutableMapOf<Int, Double>() // poolId -> average assort value
            pools.forEach { cardPool ->
                val tab = cardPool.contestTab(contest.id)!! // Irv not done here
                val poolMargin = assert.assorter.calcMarginFromRegVotes(tab.votes, cardPool.ncards())
                assortAverages[cardPool.poolId] = margin2mean(poolMargin)
            }

            val calcOaAssorter =
                OneAuditClcaAssorter(assert.info, assert.assorter, poolAverages = AssortAvgsInPools(assortAverages))
            calcOaAssorter.oaAssortRates = oneAuditErrorsFromPools.oaErrorRates(contest, calcOaAssorter)
            // assertEquals(oaAssorter.oaAssortRates, calcOaAssorter.oaAssortRates)
            // println("contest ${contest.id} oaAssortRates agree")

            var oaTerm = 0.0
            val bet = 1.0
            val mui = 0.5
            calcOaAssorter.oaAssortRates.rates.forEach { (assortValue: Double, rate: Double) ->
                oaTerm += ln(1.0 + bet * (assortValue - mui)) * rate
            }
            println("contest ${contest.id} oaTerm=$oaTerm")
        }
        println()

        val poolPick = pools.find{ it.poolId == 5579 }!!
        println(poolPick)
        poolPick.contestTabs.forEach {
            println("   $it")
        }
        val usePools = listOf(poolPick)
        assertPairs.forEach { (contest, assert) ->
            var count = 0
            usePools.forEach { pool ->
                val tab = pool.contestTab(contest.id)!! // Irv not done here
                val poolMargin = assert.assorter.calcMarginFromRegVotes(tab.votes, pool.ncards())
                val poolMean = margin2mean(poolMargin)
                println("contest ${contest.id} ${pool.poolId}: poolMargin $poolMargin poolMean=$poolMean")

                val assortAverages = mapOf(pool.poolId to poolMean) // only one pool
                val calcOaAssorter = OneAuditClcaAssorter(assert.info, assert.assorter, poolAverages = AssortAvgsInPools(assortAverages))

                val rates = OneAuditRatesFromPools(listOf(pool))
                calcOaAssorter.oaAssortRates = rates.oaErrorRates(contest, calcOaAssorter)
                println("     oaAssortRates=${calcOaAssorter.oaAssortRates.show()}")

                val oaAssorter = assert.cassorter as OneAuditClcaAssorter

                calcOaAssorter.oaAssortRates.rates.forEach { (assort, rate) ->
                    val inRecordRate = oaAssorter.oaAssortRates.rates[assort]
                    if (!doubleIsClose(rate, inRecordRate ?: 0.0)) {
                        println("*** ${contest.id} ${pool.poolId}: $assort=$rate =? $inRecordRate ${rate == inRecordRate}")
                        print("")
                        count++
                    } else {
                        // println("  ${contest.id} ${pool.poolId}: $assort=$rate =? $inRecordRate")
                    }
                }
            }
            println("\n ${contest.id} $count\n")
        }
    }

}