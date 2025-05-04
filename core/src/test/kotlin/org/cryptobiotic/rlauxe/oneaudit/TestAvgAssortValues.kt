package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.tabulateVotesWithUndervotes
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.*
import org.junit.jupiter.api.Assertions.assertNotNull
import kotlin.test.Test
import kotlin.test.assertEquals

// when does (winner - loser) / Nc agree with AvgAssortValue?
class TestAvgAssortValues {

    @Test
    fun testAvgAssortValuesSmall() {
        val oaContest1 = makeContestOA(
            11, 6, cvrPercent = .66,
            undervotePercent = .0, phantomPercent = .0, skewPct = .03, contestId = 1
        )
        println()
        print("oaContest1 = $oaContest1")
        val mvrs1 = oaContest1.makeTestMvrs("oaContest1")

        //println("  mvrs1.size=${mvrs1.size}")
        //mvrs1.forEach{ println("   $it")}
        println("\noaContest1 with mvrs")
        show(oaContest1, mvrs1)

        val oaContest2 = makeContestOA(
            5, 3, cvrPercent = .50,
            undervotePercent = .0, phantomPercent = .0, skewPct = .01, contestId = 2
        )
        val mvrs2 = oaContest2.makeTestMvrs("oaContest2")
        //println("  mvrs2.size=${mvrs2.size}")
        //mvrs2.forEach{ println("   $it")}

        val allCvrs = merge(mvrs1, mvrs2)
        //println("  allCvrs.size=${allCvrs.size}")
        //allCvrs.forEach{ println("   $it")}

        val Nc1 = allCvrs.filter { it.hasContest(1) }.count()
        println("oaContest1.Nc=${oaContest1.Nc} count=$Nc1")

        println("\noaContest1 with merged")
        show(oaContest1, allCvrs, show = true)

        println("=========================================")
        //println("  mvrs2.size=${mvrs2.size}")
        //mvrs2.forEach{ println("   $it")}
        println("oaContest2 = $oaContest2")
        println("oaContest2 with mvrs")
        show(oaContest2, mvrs2)

        val Nc2 = allCvrs.filter { it.hasContest(2) }.count()
        println("\noaContest2.Nc=${oaContest2.Nc} count=$Nc2")

        println("oaContest2 with merged")
        show(oaContest2, allCvrs)
    }

    @Test
    fun testAvgAssortValues() {
        val oaContest1 = makeContestOA(
            22000, 18000, cvrPercent = .66,
            undervotePercent = .0, phantomPercent = .0, skewPct = .03, contestId = 1
        )
        println()
        print("oaContest1 = $oaContest1")
        val mvrs1 = oaContest1.makeTestMvrs("oaContest1")

        val oaContest2 = makeContestOA(
            2000, 1800, cvrPercent = .80,
            undervotePercent = .0, phantomPercent = .0, skewPct = .01, contestId = 2
        )
        val mvrs2 = oaContest2.makeTestMvrs("oaContest2")

        val allCvrs = merge(mvrs1, mvrs2)

        println("\noaContest1 with merged")
        show(oaContest1, allCvrs)

        println("=========================================")
        println("oaContest2 = $oaContest2")
        println("oaContest2 with mvrs")
        show(oaContest2, mvrs2)

        val Nc2 = allCvrs.filter { it.hasContest(2) }.count()
        println("\noaContest2.Nc=${oaContest2.Nc} count=$Nc2")

        println("oaContest2 with merged")
        show(oaContest2, allCvrs)
    }

    @Test
    fun testAvgAssortValuesWithUndervotes() {
        val oaContest1 = makeContestOA(
            22000, 18000, cvrPercent = .66,
            undervotePercent = .11, phantomPercent = .0, skewPct = .03, contestId = 1
        )
        println()
        print("oaContest1 = $oaContest1")
        val mvrs1 = oaContest1.makeTestMvrs("oaContest1")

        val oaContest2 = makeContestOA(
            2000, 1800, cvrPercent = .80,
            undervotePercent = .17, phantomPercent = .0, skewPct = .01, contestId = 2
        )
        val mvrs2 = oaContest2.makeTestMvrs("oaContest2")

        val allCvrs = merge(mvrs1, mvrs2)

        println("\noaContest1 with merged")
        show(oaContest1, allCvrs)

        println("=========================================")
        println("oaContest2 = $oaContest2")
        println("oaContest2 with mvrs")
        show(oaContest2, mvrs2)

        val Nc2 = allCvrs.filter { it.hasContest(2) }.count()
        println("\noaContest2.Nc=${oaContest2.Nc} count=$Nc2")

        println("oaContest2 with merged")
        show(oaContest2, allCvrs)
    }

    @Test
    fun testAvgAssortValuesWithPhantoms() {
        val oaContest1 = makeContestOA(
            22000, 18000, cvrPercent = .66,
            undervotePercent = .11, phantomPercent = .005, skewPct = .03, contestId = 1
        )
        println()
        print("oaContest1 = $oaContest1")
        val mvrs1 = oaContest1.makeTestMvrs("oaContest1")

        val oaContest2 = makeContestOA(
            2000, 1800, cvrPercent = .80,
            undervotePercent = .17, phantomPercent = .01, skewPct = .01, contestId = 2
        )
        val mvrs2 = oaContest2.makeTestMvrs("oaContest2")

        val allCvrs = merge(mvrs1, mvrs2)

        println("\noaContest1 with merged")
        show(oaContest1, allCvrs)

        println("=========================================")
        println("oaContest2 = $oaContest2")
        println("oaContest2 with mvrs")
        show(oaContest2, mvrs2)

        val Nc2 = allCvrs.filter { it.hasContest(2) }.count()
        println("\noaContest2.Nc=${oaContest2.Nc} count=$Nc2")

        println("oaContest2 with merged")
        show(oaContest2, allCvrs)
    }

    fun show(oaContest: OneAuditContest, mvrs: List<Cvr>, show: Boolean = false) {
        val contestUA: OAContestUnderAudit = oaContest.makeContestUnderAudit()
        val clcaAssertion = contestUA.minAssertion() as ClcaAssertion
        assertNotNull(clcaAssertion)
        val clcaAssorter = clcaAssertion.cassorter as OneAuditClcaAssorter
        println(clcaAssorter)

        val pAssorter = clcaAssorter.assorter()
        val oaAssorter = clcaAssorter.oaAssorter
        val passortAvg = margin2mean(pAssorter.calcAssorterMargin(contestUA.id, mvrs, show = show))
        val oassortAvg = margin2mean(oaAssorter.calcAssorterMargin(contestUA.id, mvrs, show = show))

        val mvrVotes = tabulateVotesWithUndervotes(mvrs.iterator(), oaContest.id, contestUA.ncandidates)
        println("  mvrVotes = ${mvrVotes} NC=${oaContest.Nc}")

        println("     pAssorter reportedMargin=${pAssorter.reportedMargin()} reportedAvg=${pAssorter.reportedMean()} assortAvg = $passortAvg")
        println("     oaAssorter reportedMargin=${oaAssorter.reportedMargin()} reportedAvg=${oaAssorter.reportedMean()} assortAvg = $oassortAvg")

        // the oaAssorter and pAssortAvg give same assortvbg, which I guess is the point of OneAudit.
        assertEquals(pAssorter.reportedMean(), passortAvg, doublePrecision)
        assertEquals(oaAssorter.reportedMean(), oassortAvg, doublePrecision)
        assertEquals(passortAvg, oassortAvg, doublePrecision)

        /*
        if (!doubleIsClose(minAllAssorter.reportedMean(), assortAvg, doublePrecision)) {
            println(" *****")
        } else {
            println()
        }
        assertEquals(minAllAssorter.reportedMean(), assortAvg, doublePrecision)

        val cvrs = mvrs.filter{ it.poolId == null}
        val cvrAssortMargin = minAllAssorter.calcAssorterMargin(oaContest.id, cvrs, show = show)
        val cvrReportedMargin = minAllAssorter.calcReportedMargin(oaContest.cvrVotes, oaContest.cvrNc)
        print("  cvrVotes = ${oaContest.cvrVotesAndUndervotes()} reportedMargin=${cvrReportedMargin}  cvrAssortAvg = ${margin2mean(cvrAssortMargin)} reportedAvg = ${margin2mean(cvrReportedMargin)}")
        if (!doubleIsClose(cvrReportedMargin, cvrAssortMargin, doublePrecision)) {
            println(" *****")
        } else {
            println()
        }
        assertEquals(cvrReportedMargin, cvrAssortMargin, doublePrecision)

        oaContest.pools.forEach { (id, pool) ->
            val poolCvrs = mvrs.filter{ it.poolId == id}
            val poolAssortAvg = margin2mean(minAllAssorter.calcAssorterMargin(oaContest.id, poolCvrs, show = show))
            val poolReportedAvg = margin2mean(minAllAssorter.calcReportedMargin(pool.votes, pool.ncards))
            print(
                "  pool-${id} votes = ${pool.votesAndUndervotes(oaContest.info.voteForN, contestUA.ncandidates)} " +
                        "poolAssortAvg = $poolAssortAvg reportedAvg = $poolReportedAvg"
            )
            if (!doubleIsClose(poolReportedAvg, poolAssortAvg, doublePrecision)) {
                println(" *****")
            } else {
                println()
            }
            assertEquals(poolReportedAvg, poolAssortAvg, doublePrecision)
        }

         */
    }

    fun merge(mvrs1: List<Cvr>, mvrs2: List<Cvr>): List<Cvr> {
        var mvr2count = 0
        val allCvrs = mutableListOf<Cvr>()
        mvrs1.forEach {
            if (mvr2count < mvrs2.size) {
                if (it.poolId == mvrs2[mvr2count].poolId) {
                    allCvrs.add(merge(it, mvrs2[mvr2count]))
                    mvr2count++
                } else {
                    allCvrs.add(it)
                }
            } else {
                allCvrs.add(it)
            }
        }
        for (i in mvr2count until mvrs2.size) allCvrs.add(mvrs2[mvr2count++])
        // println("  allCvrs.size=${allCvrs.size}")
        return allCvrs
    }

    fun merge(cvr1: Cvr, cvr2: Cvr): Cvr {
        require(cvr1.poolId == cvr2.poolId)

        val cvrb = CvrBuilder2("${cvr1.id}&${cvr2.id}", phantom = false, poolId = cvr1.poolId)
        cvr1.votes.forEach { (contestId, votes) ->
            cvrb.addContest(contestId, votes)
        }
        cvr2.votes.forEach { (contestId, votes) ->
            cvrb.addContest(contestId, votes)
        }
        return cvrb.build()
    }
}