package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.betting.ClcaErrorRates
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.betting.populationMeanIfH0
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.estimate.makeUndervoteForContest
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.TausOA
import org.cryptobiotic.rlauxe.oneaudit.setPoolAssorterAverages
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRunoffOA {

    val f = 0.50
    val info = ContestInfo(
        name = "ABC",
        id = 0,
        choiceFunction = SocialChoiceFunction.RUNOFF,
        candidateNames = listToMap("A", "B", "C", "D"),
        minFraction = f,
        nwinners = 2,
        voteForN = 1,
    )
    val contest = Contest(info, mapOf(1 to 15555, 2 to 14444, 3 to 14000), Nc = 44400, Ncast = 44400)
    val infos = mapOf(0 to info)
    // now we have 3 pools with avg >,=,< total average
    val cardPool1 = CardPool(
        "same", 1, false, infos,
        mapOf(0 to ContestTabulation(info, mapOf(1 to 5100, 2 to 4900, 3 to 4800), 14800)), 14800
    )
    val cardPool2 = CardPool(
        "low", 2, false, infos,
        mapOf(0 to ContestTabulation(info, mapOf(1 to 4890, 2 to 5300, 3 to 5000), 15000)), 15000
    )
    val cardPool3 = CardPool(
        "hi", 3, false, infos,
        // constructor(info: ContestInfo, votes: Map<Int, Int>, ncards: Int)
        mapOf(0 to ContestTabulation(info, mapOf(1 to 5565, 2 to 4244, 3 to 4200), 14200)), 14200
    )
    val pools = listOf(cardPool1, cardPool2, cardPool3)

    val contestOA = ContestWithAssertions(contest, isClca = true, hasStyle = true).addStandardAssertions()
    val oaAssorter: OneAuditClcaAssorter

    init {
        // Its the OA assorters that make this a OneAudit contest
        setPoolAssorterAverages(listOf(contestOA), pools)
        oaAssorter = contestOA.clcaAssertions.first().cassorter as OneAuditClcaAssorter
    }

    @Test
    fun showBelowThresholdOA() {
        println(contest)
        val sumTabs = mutableMapOf<Int, ContestTabulation>()
        pools.forEach {
            sumTabs.sumContestTabulations(it.contestTabs)
            println("pool ${it.poolId} = ${it.contestTabs}")
        }
        println("sumTabs = ${sumTabs}")

        println("\nassertions")
        contestOA.assertions.forEach {
            val assorter = it.assorter
            println(assorter)
            println("  assorter margin ${assorter.reportedMargin()}")
            println("  assorter mean ${assorter.reportedMean()}")

            println("  vote for winner = ${assorter.assort(makeCvr(1))}")
            println("  vote not for Winner = ${assorter.assort(makeCvr(0))}")
            println("  undervote           = ${assorter.assort(makeUndervoteForContest(1))}")
            println("  no contest on ballot = ${assorter.assort(makeUndervoteForContest(99))}")
        }

        println("\nclcaAsertions")
        contestOA.clcaAssertions.filter{it.assorter is BelowThreshold }.forEach {
            val assorter = it.assorter
            println(assorter)
            //assertTrue(assorter is BelowThreshold)
            //assertEquals(1, assorter.winner())
            //assertEquals(-1, assorter.loser())

            val cassorter = it.cassorter
            println("  threshold cassorter upper ${cassorter.upperBound()}")
            println("  threshold cassorter noerror ${cassorter.noerror}")

            println("  vote for winner = ${assorter.assort(makeCvr(1))}")
            println("  vote not for Winner = ${assorter.assort(makeCvr(0))}")
            println("  undervote           = ${assorter.assort(makeUndervoteForContest(1))}")
            println("  no contest on ballot = ${assorter.assort(makeUndervoteForContest(99))}")
            println()

            val taus1 = TausOA(assorter.upperBound(), oaAssorter.poolAverage(1)!!)
            val taus2 = TausOA(assorter.upperBound(), oaAssorter.poolAverage(2)!!)
            val taus3 = TausOA(assorter.upperBound(), oaAssorter.poolAverage(3)!!)

            val oaAssorter = it.cassorter as OneAuditClcaAssorter
            println("pool1 avg = ${oaAssorter.poolAverage(1)} TausOA = $taus1")
            println("pool2 avg = ${oaAssorter.poolAverage(2)} TausOA = $taus2")
            println("pool3 avg = ${oaAssorter.poolAverage(3)} TausOA = $taus3")

            // "The loser term here is always larger than the winner term in absolute value" ??
            // is it true that payoff(loser) > payoff(winner) in absolute value ??
            // xs = taus * noerror
            // payoff(bet:Double, xs:Double,) = 1.0 + bet * (xs - mui)
            // 1.0 + bet * (tausl * noerror - mui) >? 1.0 + bet * (tausw * noerror - mui)
            // 1.0 + bet * (tausl * noerror - mui) >? 1.0 + bet * (tausw * noerror - mui)
            // ln (1 + x) = x - x^2/2 + x^3/3 - ...
            // bet * (tausl * noerror - mui) >? bet * (tausw * noerror - mui)
            // abs(tausl * noerror - 1/2) >? abs(tausw * noerror - 1/2)
            // tausl = (1-poolAvg), tausw = (2-poolAvg), poolAvg in [0,1], noerror > 1/2
            // tausl*noerror = [1..0], tausw = [2..1], poolAvg in [0,1]

            // when is (tausl * noerror - mui) < 0 ? (tausl * noerror < mui)
            // (1-poolAvg) *


            var sumCards = 0
            var wAvg = 0.0
            pools.forEach {
                val ncards = it.ncards()
                val avg = oaAssorter.poolAverage(it.poolId)!!
                sumCards += ncards
                wAvg += ncards * avg
            }
            val poolAvg = wAvg / sumCards
            println("weighted average over pools = ${poolAvg}")
            // TODO assertEquals(assorter.reportedMean(),poolAvg)
        }

        // val terms = listOf("loser", "winner", "nuetral")
        println("\nbassort")
            pools.forEach { pool ->
                repeat(3) { cand ->
                val mvr = AuditableCardBuilder(
                    "cvrId", null, 42, 0, phantom = false, styleId = pool.poolId, poolId = pool.poolId,
                    votesIn = if (cand == 2) mapOf(0 to intArrayOf()) else mapOf(0 to intArrayOf(cand))
                ).build()
                val cvr = AuditableCard.removeVotesReplaceStyle(mvr, 1)
                println("  pool ${pool.poolId} mvr vote for cand ${cand} = ${oaAssorter.bassort(mvr, cvr)}")
            }
        }

        println("\noaAssortRates")
        oaAssorter.oaAssortRates.rates.forEach { println("   $it") }
    }

    @Test
    fun runAudit() {
        println("\naudit")
        val mvrs = makeMvrsFromPools(pools)
        val cvrs = makeCvrsFromMvrs(mvrs)
        val pairs = mvrs.zip(cvrs)

        val welford = Welford()
        pairs.forEach { (mvr, cvr) ->
            welford.update(oaAssorter.bassort(mvr, cvr))
        }
        println("average bassort over cards = ${welford.mean()}")

        // use bettingFun
        val aprioriErrorRates = ClcaErrorRates.empty(oaAssorter.noerror, oaAssorter.assorter.upperBound())
        val errorTracker = ClcaErrorTracker(oaAssorter.noerror, oaAssorter.assorter.upperBound())
        val oaAssortRates = oaAssorter.oaAssortRates

        // use the same betting function as the real audit
        val bettingFun = GeneralAdaptiveBetting(
            contestOA.Npop, // population size for this contest
            aprioriErrorRates = aprioriErrorRates, // apriori rates not counting phantoms; non-null so we always have noerror and upper
            contest.Nphantoms(),
            ClcaConfig().maxLoss,
            oaAssortRates=oaAssortRates,
        )

        // why not just use BettingMart ??
        var countUsed = 0
        var stat: TestH0Status? = null
        var testStatistic = 1.0
        var endingTestStatistic = 20.0

        for ((mvr, cvr) in pairs) {

            val assortValue = oaAssorter.bassort(mvr, cvr)

            val mui = populationMeanIfH0(contestOA.Npop, true, errorTracker)
            if (mui > oaAssorter.upperBound) { // 1  # true mean is certainly less than 1/2
                stat = TestH0Status.AcceptNull
            }
            if (mui < 0.0) { // 5 # true mean certainly greater than 1/2
                stat = TestH0Status.SampleSumRejectNull
            }

            val bet = bettingFun.bet(errorTracker)

            val payoff = (1 + bet * (assortValue - mui))
            testStatistic *= payoff
            if (testStatistic > endingTestStatistic) {
                stat = TestH0Status.StatRejectNull
            }

            val wantId = 1
            if (wantId >= 0) {
                val locWidth = 11
                if (countUsed == 0) {
                    println("idx, xs,              bet,      payoff,       Tj, ${sfn("location", locWidth-3)}, mvr votes, card votes")
                }
                bettingFun.bet(errorTracker, show = true) // debugging

                val mvrVotes = mvr?.votes(wantId)?.contentToString() ?: "missing"
                val cardVotes = cvr.votes(wantId)?.contentToString() ?: "N/A"
                print("$countUsed, ${dfn(assortValue, 8)}, ${dfn(bet, 8)}, ${dfn(payoff, 8)}, ${dfn(testStatistic, 8)}, " +
                        "${sfn(cvr.location(), locWidth)}, ${mvrVotes}")
                if (cvr.poolId() != null) print(", pool=${cvr.poolId()}, poolAvg=${df(oaAssorter?.poolAverage(cvr.poolId()))}")
                else print(", votes=${cardVotes}")
                println()
            }
            errorTracker.addSample(assortValue, cvr.poolId() == null)

            countUsed++
            if (countUsed % 100 == 0)
                print("")
            if (stat != null) break
        }
        println("$countUsed testStatistic = ${testStatistic} stat = $stat")
    }

}