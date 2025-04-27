package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

// TODO fix tests
class TestOneAuditClcaAssorter {

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

    // @Test TODO match with SHANGRLA, make tests pass
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

    @Test
    fun testMakeContestOA() {
        val contest = makeContestOA(20000, 18000, cvrPercent = .66,
            undervotePercent = .0, phantomPercent = .0, skewPct = .03)
        val contestUA = contest.makeContestUnderAudit()
        println(contestUA)
        showPct("  cvrs", contest.cvrVotes, contest.cvrNc)
        contest.pools.values.forEach { pool -> showPct("  pool ${pool.name}", pool.votes, pool.ncards) }
        showPct("  allVotes", contest.votes, contest.Nc)
        println()

        // data class Cvr(
        //    val id: String,
        //    val votes: Map<Int, IntArray>, // contest -> list of candidates voted for; for IRV, ranked first to last
        //    val phantom: Boolean = false,
        //    val poolId: Int? = null,
        val winnerPool = Cvr("winner", mapOf(0 to intArrayOf(0)), poolId=1)
        val loserPool = Cvr("loser", mapOf(0 to intArrayOf(1)), poolId=1)
        val otherPool = Cvr("other", mapOf(0 to intArrayOf(2)), poolId=1)
        val bassorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        println(bassorter)

        val assorterMargin = bassorter.cvrAssortMargin
        val assorterMean = margin2mean(assorterMargin)

        val poolMargin = contest.pools[1]!!.calcReportedMargin(0, 1)
        val poolAverage = margin2mean(poolMargin)
        println("assorterMargin=v=$assorterMargin poolMargin=$poolMargin poolAverage=pa=$poolAverage ")
        println()

        println(" winner: (2 - pa) / (2 - v)  = ${(2.0 - poolAverage) / (2 - assorterMargin) } ") // voted for other
        println(" other: (1.5 - pa) / (2 - v)  = ${(1.5 - poolAverage) / (2 - assorterMargin) } ") // voted for winner
        println(" loser: (1 - pa) / (2 - v) = ${(1.0 - poolAverage) / (2 - assorterMargin) } ") // voted for loser

        val min = (1.0 - poolAverage) / (2 - assorterMargin)
        val max = (2.0 - poolAverage) / (2 - assorterMargin)
        val at = OaAffine(min, max)

        val loserVote = (1.0 - poolAverage) / (2 - assorterMargin)
        val otherVote = (1.5 - poolAverage) / (2 - assorterMargin)
        val winnerVote = (2.0 - poolAverage) / (2 - assorterMargin)
        // println("  loserVote=$loserVote, otherVote=$otherVote, winnerVote=$winnerVote ")
        // println("loserVoteAt=${at.trans(loserVote)}, otherVoteAt=${at.trans(otherVote)}, winnerVoteAt=${at.trans(winnerVote)} ")
        println()

        // bassort(mvr, cvr)
        println("Pool")
        println(" bassort(winnerPool, anyPool)=${bassorter.bassort(winnerPool, winnerPool)} ")
        println(" bassort(otherPool, anyPool)=${bassorter.bassort(otherPool, winnerPool)} ")
        println(" bassort(loserPool, anyPool)=${bassorter.bassort(loserPool, winnerPool)} ")
        println("bassort = ([2, 1.5, 1] - poolAvg) / (2 - assorterMargin)} ")

        println()
        // it doesnt matter what the cvr is, it just matters that its in the pool, so cvr_assort always = poolAverage
        // bassort(mvr: Cvr, cvr: Cvr)
        assertEquals(otherVote, bassorter.bassort(otherPool, winnerPool), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserPool, winnerPool), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerPool, winnerPool), doublePrecision)

        assertEquals(otherVote, bassorter.bassort(otherPool, loserPool), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserPool, loserPool), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerPool, loserPool), doublePrecision)

        assertEquals(otherVote, bassorter.bassort(otherPool, otherPool), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserPool, otherPool), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerPool, otherPool), doublePrecision)

        //////////

        val winnerCvr = Cvr("winner", mapOf(0 to intArrayOf(0)))
        val loserCvr = Cvr("loser", mapOf(0 to intArrayOf(1)))
        val otherCvr = Cvr("other", mapOf(0 to intArrayOf(2)))

        println("CVR pool")
        println(" bassort(winnerCvr, winnerCvr)=${bassorter.bassort(winnerCvr, winnerCvr)} ")  // noerror
        println(" bassort(otherCvr, winnerCvr)=${bassorter.bassort(otherCvr, winnerCvr)} ") // noerror/2
        println(" bassort(loserCvr, winnerCvr)=${bassorter.bassort(loserCvr, winnerCvr)} ") // 0
        println()
        println(" bassort(winnerCvr, otherCvr)=${bassorter.bassort(winnerCvr, otherCvr)} ") // 1.5 * noerror
        println(" bassort(otherCvr, otherCvr)=${bassorter.bassort(otherCvr, otherCvr)} ") // noerror
        println(" bassort(loserCvr, otherCvr)=${bassorter.bassort(loserCvr, otherCvr)} ") // noerror/2
        println()
        println(" bassort(winnerCvr, loserCvr)=${bassorter.bassort(winnerCvr, loserCvr)} ") // 2 * noerror
        println(" bassort(otherCvr, loserCvr)=${bassorter.bassort(otherCvr, loserCvr)} ") // 1.5 * noerror
        println(" bassort(loserCvr, loserCvr)=${bassorter.bassort(loserCvr, loserCvr)} ") // noerror
        println()
        println("bassort = [0, .5, 1, 1.5, 2] * noerror=${bassorter.bassort(loserCvr, loserCvr)} ")
    }

    // makeContestOA (0) Nc=38000 Np=0 votes={0=20000, 1=18000}
    //OneAuditComparisonAssorter for contest makeContestOA (0)
    //  assorter= winner=0 loser=1 reportedMargin=0.0526 reportedMean=0.5263
    //  cvrAssortMargin=0.05263157894736842 noerror=0.5135135135135135 upperBound=1.027027027027027 avgCvrAssortValue=null
    //assorterMargin=v=0.05263157894736842 poolMargin=0.006493506493506494 poolAverage=pa=0.5032467532467533
    //
    // winner: (2 - pa) / (2 - v)  = 0.7686030186030186
    // other: (1.5 - pa) / (2 - v)  = 0.5118462618462618
    // loser: (1 - pa) / (2 - v) = 0.25508950508950506
    //
    //Pool
    // bassort(winnerPool, anyPool)=0.7686030186030185
    // bassort(otherPool, anyPool)=0.5118462618462618
    // bassort(loserPool, anyPool)=0.25508950508950506
    //bassort = ([2, 1.5, 1] - poolAvg) / (2 - assorterMargin)}
    //
    //CVR pool
    // bassort(winnerCvr, winnerCvr)=0.5135135135135135
    // bassort(otherCvr, winnerCvr)=0.25675675675675674
    // bassort(loserCvr, winnerCvr)=0.0
    //
    // bassort(winnerCvr, otherCvr)=0.7702702702702702
    // bassort(otherCvr, otherCvr)=0.5135135135135135
    // bassort(loserCvr, otherCvr)=0.25675675675675674
    //
    // bassort(winnerCvr, loserCvr)=1.027027027027027
    // bassort(otherCvr, loserCvr)=0.7702702702702702
    // bassort(loserCvr, loserCvr)=0.5135135135135135
    //
    //bassort = [0, .5, 1, 1.5, 2] * noerror=0.5135135135135135

    @Test
    fun testMakeContestOAwithAffine() {
        val contest = makeContestOA(20000, 18000, cvrPercent = .66, undervotePercent = .0, phantomPercent = .0, skewPct = .03)
        val contestUA = contest.makeContestUnderAudit()
        println(contestUA)

        val winnerCvr = Cvr("winner", mapOf(0 to intArrayOf(0)), poolId=1)
        val loserCvr = Cvr("loser", mapOf(0 to intArrayOf(1)), poolId=1)
        val otherCvr = Cvr("other", mapOf(0 to intArrayOf(2)), poolId=1)

        val bassorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        println(bassorter)

        val assorterMargin = bassorter.cvrAssortMargin

        val poolMargin = contest.pools[1]!!.calcReportedMargin(0, 1)
        val poolAverage = margin2mean(poolMargin)
        println("assorterMargin=v=$assorterMargin poolMargin=$poolMargin poolAverage=pa=$poolAverage ")
        println()

        // bassort in [0, .5, 1, 1.5, 2] * noerror = [twoOver, oneOver, nuetral, oneUnder, twoUnder]
        // noerror = 1 / (2 - v/u) = u / (2u - v)

        // let u = 1
        // the possible values are
        //   [1 - pa, 3/2 - pa, 2 - pa] * noerror

        println(" loser: (1 - pa) / (2 - v) = ${(1.0 - poolAverage) / (2 - assorterMargin) } ") // voted for loser
        println(" other: (1.5 - pa) / (2 - v)  = ${(1.5 - poolAverage) / (2 - assorterMargin) } ") // voted for winner
        println(" winner: (2 - pa) / (2 - v)  = ${(2.0 - poolAverage) / (2 - assorterMargin) } ") // voted for other

        val min = (1.0 - poolAverage) / (2 - assorterMargin)
        val max = (2.0 - poolAverage) / (2 - assorterMargin)
        val at = OaAffine(min, max)

        val loserVote = at.trans((1.0 - poolAverage) / (2 - assorterMargin))
        val otherVote = at.trans((1.5 - poolAverage) / (2 - assorterMargin))
        val winnerVote = at.trans((2.0 - poolAverage) / (2 - assorterMargin))
        println("  loserVote=$loserVote, otherVote=$otherVote, winnerVote=$winnerVote ")
        println()


        // bassort(mvr, cvr)
        println(" bassort(other, winner)=${bassorter.bassort(otherCvr, winnerCvr)} ")
        println(" bassort(winner, winner)=${bassorter.bassort(winnerCvr, winnerCvr)} ")
        println(" bassort(loser, winner)=${bassorter.bassort(loserCvr, winnerCvr)} ")

        // it doesnt matter what the cvr is, it just matters that its in the pool, so cvr_assort always = poolAverage
        // bassort(mvr: Cvr, cvr: Cvr)
        assertEquals(otherVote, bassorter.bassort(otherCvr, winnerCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, winnerCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, winnerCvr), doublePrecision)

        assertEquals(otherVote, bassorter.bassort(otherCvr, loserCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, loserCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, loserCvr), doublePrecision)

        assertEquals(otherVote, bassorter.bassort(otherCvr, otherCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, otherCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, otherCvr), doublePrecision)

    }


    class OaAffine (val min: Double, max: Double) {
        val ir = 1.0 / (max - min)
        fun trans(x: Double): Double {
            return (x - min) * ir
        }
    }

    @Test
    fun testPoolAssorterValues() {
        val assorterMargin = .05
        repeat(20) {
            val poolAverage = (51.0 + it) / 100
            println("poolAverage=$poolAverage assorterMargin=$assorterMargin ")
            println("   (1 - pa) / (2 - v) = ${(1.0 - poolAverage) / (2 - assorterMargin)} ") // voted for loser
            println("   (1.5 - pa) / (2 - v)  = ${(1.5 - poolAverage) / (2 - assorterMargin)} ") // voted for winner
            println("   (2 - pa) / (2 - v)  = ${(2.0 - poolAverage) / (2 - assorterMargin)} ") // voted for other
        }
    }

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

    @Test
    fun genRegularBets() {
        repeat(20) {
            val a = (51.0 + it)/100
            val margin = 2 * a - 1
            val noerror = 1.0 / (2 - margin)
            val winnerVote = 2 / (2 - margin)
            println("avgAssortValue=${df(a)} margin=${df(margin)} noerror=${df(noerror)} winner=${df(winnerVote)} ")
        }
    }

    @Test
    fun genBatchBets() {
        repeat(20) {
            val a = (51.0 + it)/100
            val margin = 2 * a - 1
            val loserVote = (1.0 - a) / (3 - 2*a)
            val winnerVote = (2.0 - a) / (3 - 2*a)
            val avg = (winnerVote + loserVote)/2

            println("avgAssortValue=${df(a)} margin=${df(margin)} loserVote=${df(loserVote)} winner=${df(winnerVote)} avg=$avg")
        }
    }

    @Test
    fun genBatchBets2() {  // ONEAudit p 11; same as above. see eq 10 for rescaling.
        repeat(20) {
            val a = (51.0 + it)/100
            val v = 2 * a - 1
            val loserVote = (1.0 - (v + 1)/2) / (2 - v)
            val winnerVote = (2.0 - (v + 1)/2) / (2 - v)
            println("avgAssortValue=${df(a)} margin=${df(v)} loserVote=${df(loserVote)} winner=${df(winnerVote)} ")
        }
    }

    @Test
    fun genBatchBets3() {  // ONEAudit p 11; same as above. see eq 10 for rescaling.
        for (lamda in listOf(1.0, 1.33, 1.66, 1.99)) {
            println("\nlamda=$lamda")
            repeat(10) {
                val a = (51.0 + it) / 100
                val v = 2 * a - 1
                val winnerAssort = (2.0 - a) / (2 - v)
                val loserAssort = (1.0 - a) / (2 - v)
                val winnerF = betF(winnerAssort, lamda)
                val loserF = betF(loserAssort, lamda)
                val product = winnerF * loserF

                print("avgAssortValue=${df(a)} winnerAssort=${df(winnerAssort)} loserAssort=${df(loserAssort)}")
                print(" winnerF=${df(winnerF)} loserF=${df(loserF)}")
                println(" product=${df(product)}")
            }
        }
    }

    fun betF(xj: Double, lamda: Double): Double {
        return 1.0 + lamda * (xj - .5) // (1 + λi (Xi − µi )) ALPHA eq 10, SmithRamdas eq 34 (WoR)
    }

    // noCvr     7 = 0.24305555555555555 m=0.5 bet = 1.3822132305208128 tj=0.6448479893800689, Tj = 0.71610651140852 pj = 1.3964403116976076
    //card113     12 = 0.5135869565217391 m=0.5 bet = 1.358891811147106 tj=1.018463203955803, Tj = 0.7838092123617311 pj = 1.2758206770584575
    //noCvr     13 = 0.7569444444444444 m=0.5 bet = 1.3671953397727525 tj=1.3512932470249432, Tj = 1.059156095620347 pj = 0.94414789674066
}