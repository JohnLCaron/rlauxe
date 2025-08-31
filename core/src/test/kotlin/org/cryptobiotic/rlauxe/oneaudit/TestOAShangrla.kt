package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

// TODO fix tests
class TestOAShangrla {

    // OneAudit, section 2.3
    // "compares the manual interpretation of individual cards to the implied “average” CVR of the reporting batch each card belongs to"
    //
    // Let bi denote the true votes on the ith ballot card; there are N cards in all.
    // Let ci denote the voting system’s interpretation of the ith card, for ballots in C, cardinality |C|.
    // Ballot cards not in C are partitioned into G ≥ 1 disjoint groups {G_g}, g=1..G for which reported assorter subtotals are available.
    //
    //     Ā(c) ≡ Sum(A(ci))/N be the average CVR assort value
    //     margin ≡ 2Ā(c) − 1, the _reported assorter margin_
    //
    //     ωi ≡ A(ci) − A(bi)   overstatementError
    //     τi ≡ (1 − ωi /upper) ≥ 0, since ωi <= upper
    //     B(bi, ci) ≡ τi / (2 − margin/upper) = (1 − ωi /upper) / (2 − margin/upper)
    //
    //    Ng = |G_g|
    //    Ā(g) ≡ assorter_mean_poll = (winner total - loser total) / Ng
    //    margin ≡ 2Ā(g) − 1 ≡ v = 2*assorter_mean_poll − 1
    //    mvr has loser vote = (1-assorter_mean_poll)/(2-v/u)
    //    mvr has winner vote = (2-assorter_mean_poll)/(2-v/u)
    //    otherwise = 1/2

    /* @Test TODO match with SHANGRLA, make tests pass
    fun testOAShangrla() {
        val winnerNoCvr = makeCvr(0, "noCvr", poolId=1)
        val loserNoCvr = makeCvr(1, "noCvr", poolId=1)
        val otherNoCvr = makeCvr(2, "noCvr", poolId=1)

        val margin = .6571495728340697
        val N = 10000
        // val contestOA: OneAuditContest = makeContestOA(N, margin, poolPct = 0.66, poolMargin = mean2margin(0.625))
        // fun makeContestOA(margin: Double, Nc: Int, cvrPercent: Double, skewVotesPercent: Double, undervotePercent: Double, phantomPercent: Double): OneAuditContest {
        val contestOA: OneAuditContest = makeContestOA(margin, N, cvrPercent = 0.33, undervotePercent = 0.0, phantomPercent = 0.0) // poolMargin = mean2margin(0.625))
        val contestUA = contestOA.makeContestUnderAudit()
        val cassorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter

        val assortMargin = cassorter.assorter.reportedMargin()
        val assortMean = margin2mean(assortMargin)
        assertEquals(margin2mean(margin), assortMean, .0001)

        val winnerAssortValue = cassorter.assorter.assort(winnerNoCvr, usePhantoms = false)
        val loserAssortValue = cassorter.assorter.assort(loserNoCvr, usePhantoms = false)
        val otherAssortValue = cassorter.assorter.assort(otherNoCvr, usePhantoms = false)
        println("winnerAssortValue=$winnerAssortValue loserAssortValue=$loserAssortValue otherAssortValue=$otherAssortValue ")
        assertEquals(1.0, winnerAssortValue)
        assertEquals(0.0, loserAssortValue)
        assertEquals(0.5, otherAssortValue)

        println("pool=${contestOA.pools[1]}")
        val poolMargin = contestOA.pools[1]!!.calcReportedMargin(0, 1)
        assertEquals(mean2margin(0.625), poolMargin)

        // From Shangrla
        // tally_pool 31-119 means: 0.625
        // mvr_assort: 0.5, cvr_assort: 0.625
        // overstatement=0.6515990033578625 margin=0.6571495728340697
        //
        // mvr_assort: 1.0, cvr_assort: 1.0
        //overstatement=0.7446845752661285 margin=0.6571495728340697
        assertEquals(0.6515990033578625, cassorter.bassort(otherNoCvr, winnerNoCvr), .0001)

        // for pooled assort from pool with avg Ā(g)
        //   mvr has loser vote = (1 - Ā(g)/u) / (2-v/u)
        //   mvr has winner vote = (1 - (Ā(g)-1)/u) / (2-v/u)
        //   mvr has other vote = (1 - (Ā(g)-.5)/u) / (2-v/u) = 1/2

        //         val mvr_assort = if (mvr.phantom || (hasStyle && !mvr.hasContest(contestOA.id))) 0.0
        //                         else this.assorter.assort(mvr, usePhantoms = false)
        //
        //        val cvr_assort = if (cvr.phantom) .5 else avgBatchAssortValue
        //        overstatement = cvr_assort - mvr_assort

        // o =  poolAvgAssort-1 // winner
        // o =  poolAvgAssort // loser
        // o =  poolAvgAssort-0.5 // other

        // B(bi, ci) = (1-o/u)/(2-v/u), where
        //                o is the overstatement
        //                u is the upper bound on the value the assorter assigns to any ballot
        //                v is the assorter margin
        // u=1
        //    mvr has winner vote = (1-(poolAvgAssort-1))/(2-v) = (2-poolAvgAssort)/(2-v)
        //    mvr has loser vote =  (1-poolAvgAssort)/(2-v) = (1-poolAvgAssort)/(2-v)
        //    mvr has other vote =  (1-(poolAvgAssort-.5))/(2-v) = (1.5-poolAvgAssort)/(2-v)

        val poolAvgAssort = margin2mean(poolMargin)
        val loserVoteNoCvr = (1.0 - poolAvgAssort) / (2 - margin)
        val winnerVoteNoCvr = (2.0 - poolAvgAssort) / (2 - margin)
        val otherVoteNoCvr = (1.5 - poolAvgAssort) / (2 - margin)
        println("poolAvgAssort=$poolAvgAssort loserVoteNoCvr=$loserVoteNoCvr winnerVoteNoCvr=$winnerVoteNoCvr otherVoteNoCvr=$otherVoteNoCvr")

        // mvr assort always return poolAvg because it has a poolId
        println(" mvr winner bassort=${cassorter.bassort(winnerNoCvr, winnerNoCvr)} ")
        println(" mvr loser bassort=${cassorter.bassort(loserNoCvr, winnerNoCvr)} ")
        println(" mvr other bassort=${cassorter.bassort(otherNoCvr, winnerNoCvr)} ")

        assertEquals(winnerVoteNoCvr, cassorter.bassort(winnerNoCvr, winnerNoCvr), .0001)
        assertEquals(loserVoteNoCvr, cassorter.bassort(loserNoCvr, winnerNoCvr), .0001)
        assertEquals(otherVoteNoCvr, cassorter.bassort(otherNoCvr, winnerNoCvr), .0001)

        val expect = """OneAuditComparisonAssorter for contest makeContestOA (0)
  assorter= winner=0 loser=1 reportedMargin=0.6572 reportedMean=0.8286
  cvrAssortMargin=0.6572 noerror=0.7447125409591897 upperBound=1.4894250819183794 avgCvrAssortValue=null"""
        assertEquals(expect, cassorter.toString())
    }

     */

    /*
    @Test
    fun testONE() {
        // two candidate plurailty
        val contest = makeContestOA(1000, 923, cvrPercent = .57, .007, undervotePercent = .0, phantomPercent = .0)
        println(contest)

        val testCvrs = contest.makeTestCvrs()
        val contestOA = contest.makeContestUnderAudit()
        println(contestOA)

        val bassorter = contestOA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        println(bassorter)
        println("reportedMargin = ${bassorter.assorter.reportedMargin()} clcaMargin = ${mean2margin(bassorter.meanAssort())} ")

        // sanity check
        val allCount = testCvrs.count()
        val cvrCount = testCvrs.filter { it.id != "noCvr" }.count()
        val noCount = testCvrs.filter { it.id == "noCvr" }.count()
        println("allCount = $allCount cvrCount=$cvrCount noCount=$noCount")
        assertEquals(allCount, contest.Nc)
        assertEquals(cvrCount, contest.strata[1].Ng)
        assertEquals(noCount, contest.strata[0].Ng)

        // whats the plurality assort value average?
        val pluralityAssorter = bassorter.assorter
        val allAvgp = testCvrs.map { pluralityAssorter.assort(it) }.average()
        val cvrAvgp = testCvrs.filter { it.id != "noCvr" }.map { pluralityAssorter.assort(it) }.average()
        val noAvgp = testCvrs.filter { it.id == "noCvr" }.map { pluralityAssorter.assort(it) }.average()
        println("allAvgp = $allAvgp cvrAvgp=$cvrAvgp noAvgp=$noAvgp")
        assertEquals(allAvgp, bassorter.avgCvrAssortValue)
        assertEquals(cvrAvgp, bassorter.stratumInfos["hasCvr"]!!.avgBatchAssortValue)
        assertEquals(noAvgp, bassorter.stratumInfos["noCvr"]!!.avgBatchAssortValue)

        assertEquals(0.5200208008320333, allAvgp, doublePrecision)

        // whats the comparison assort value average?
        val allAvg = testCvrs.map { bassorter.bassort(it, it) }.average()
        val cvrAvg = testCvrs.filter { it.id != "noCvr" }.map { bassorter.bassort(it, it) }.average()
        val noAvg = testCvrs.filter { it.id == "noCvr" }.map { bassorter.bassort(it, it) }.average()
        println("allAvg = $allAvg cvrAvg=$cvrAvg noAvg=$noAvg")

        val pairs = testCvrs.zip(testCvrs)
        val calc = bassorter.calcAssorterMargin(pairs)
        assertEquals(allAvg, margin2mean(calc))

        // TODO what should this equal??
//        assertEquals(0.5103270265473376, allAvg, doublePrecision)
 //       assertEquals(0.5039080459770175, cvrAvg, doublePrecision)
   //     assertEquals(0.5188442211055272, noAvg, doublePrecision)

        val allWeighted = (cvrAvg * contest.strata[1].Ng + noAvg * contest.strata[0].Ng) / contest.Nc
        assertEquals(allAvg, allWeighted, doublePrecision)

        println("clcaMargin = ${mean2margin(bassorter.meanAssort())}")
        println("clcaMean = ${bassorter.meanAssort()}")
        // assertEquals(allAvg, margin2mean(bassorter.clcaMargin), doublePrecision)
    }

     */
}
