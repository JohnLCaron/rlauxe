package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

// TODO fix tests
class TestOABasics {

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
    //     B(bi, ci) ≡ τi / (2 − margin/upper) = (1 − ωi /upper) / (2 − margin/upper) = (1 + A(bi) - poolAvg) / (2 − margin/upper)
    //
    //    Ng = |G_g|
    //    Ā(g) ≡ assorter_mean_poll = (winner total - loser total) / Ng TODO: IS TRUE ??
    //    margin ≡ 2Ā(g) − 1 ≡ v = 2*assorter_mean_poll − 1
    //    mvr has loser vote = (1-assorter_mean_poll)/(2-v/u)
    //    mvr has winner vote = (2-assorter_mean_poll)/(2-v/u)
    //    otherwise = 1/2

    @Test
    fun testMakeOneContestUA() {
        val (contestUA, cvrs) = makeOneContestUA(20000, 18000, cvrPercent = .66, undervotePercent = .0, phantomPercent = .0, skewPct = .03)
        println(contestUA)
        val cvrVotes =  tabulateVotesFromCvrs(cvrs.iterator())
        showPct("  cvrVotes", cvrVotes[0]!!, contestUA.Nc)

        val contest = contestUA.contest as Contest
        showPct("  allVotes", contest.votes, contest.Nc)
        println()
        assertEquals(contest.votes, cvrVotes[0])

        val oaAssorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        println(oaAssorter)
        val assorter = oaAssorter.assorter
        val assortAvgFromCvrs = assorter.calcAssortAvgFromCvrs(0, cvrs)
        val assorterMargin = mean2margin(assortAvgFromCvrs)

        val reportedMargin = oaAssorter.cvrAssortMargin
        val reportedMean = margin2mean(reportedMargin)
        println("assortAvgFromCvrs = $assortAvgFromCvrs reportedMean=$reportedMean")
        assertEquals(assortAvgFromCvrs, reportedMean, doublePrecision)

        val pools = oaAssorter.poolAverages
        pools.assortAverage.forEach{ println(" avg for pool $it") }
        val poolAverage = pools.assortAverage[1]!!

        val poolAvgFromCvrs = assorter.calcAssortAvgFromCvrs(0, cvrs.filter { it.poolId == 1})
        println("poolAvgFromCvrs = $poolAvgFromCvrs avgFromPools=$poolAverage")
        println()

        println(" winner: (2 - pa) / (2 - v)  = ${(2.0 - poolAverage) / (2 - assorterMargin) } ") // voted for other
        println(" other: (1.5 - pa) / (2 - v)  = ${(1.5 - poolAverage) / (2 - assorterMargin) } ") // voted for winner
        println(" loser: (1 - pa) / (2 - v) = ${(1.0 - poolAverage) / (2 - assorterMargin) } ") // voted for loser

        val min = (1.0 - poolAverage) / (2 - assorterMargin)
        val max = (2.0 - poolAverage) / (2 - assorterMargin)

        val loserVote = (1.0 - poolAverage) / (2 - assorterMargin)
        val otherVote = (1.5 - poolAverage) / (2 - assorterMargin)
        val winnerVote = (2.0 - poolAverage) / (2 - assorterMargin)
        // println("  loserVote=$loserVote, otherVote=$otherVote, winnerVote=$winnerVote ")
        // println("loserVoteAt=${at.trans(loserVote)}, otherVoteAt=${at.trans(otherVote)}, winnerVoteAt=${at.trans(winnerVote)} ")
        println()

        val winnerPool = Cvr("winner", mapOf(0 to intArrayOf(0)), poolId=1)
        val loserPool = Cvr("loser", mapOf(0 to intArrayOf(1)), poolId=1)
        val otherPool = Cvr("other", mapOf(0 to intArrayOf(2)), poolId=1)
        val underPool = Cvr("under", mapOf(0 to intArrayOf()), poolId=1)
        val missingPool = Cvr("under", mapOf(1 to intArrayOf(2)), poolId=1)

        // it doesnt matter what the cvr is, it just matters that its in the pool, so cvr_assort always = poolAverage
        // bassort(mvr: Cvr, cvr: Cvr)
        println("Pool")
        println(" bassort(winnerVote, anyVote)=${oaAssorter.bassort(winnerPool, winnerPool)} ")
        println(" bassort(otherVote, anyVote)=${oaAssorter.bassort(otherPool, winnerPool)} ")
        println(" bassort(loserVote, anyVote)=${oaAssorter.bassort(loserPool, winnerPool)} ")
        println(" bassort(underVote, anyVote)=${oaAssorter.bassort(underPool, winnerPool)} ")
        println(" bassort(missingContest, anyVote)=${oaAssorter.bassort(missingPool, winnerPool)} ")
        println("bassort = ([2, 1.5, 1] - poolAvg) / (2 - assorterMargin)} ")

        println()
        assertEquals(otherVote, oaAssorter.bassort(otherPool, winnerPool), doublePrecision)
        assertEquals(loserVote, oaAssorter.bassort(loserPool, winnerPool), doublePrecision)
        assertEquals(winnerVote, oaAssorter.bassort(winnerPool, winnerPool), doublePrecision)

        assertEquals(otherVote, oaAssorter.bassort(otherPool, loserPool), doublePrecision)
        assertEquals(loserVote, oaAssorter.bassort(loserPool, loserPool), doublePrecision)
        assertEquals(winnerVote, oaAssorter.bassort(winnerPool, loserPool), doublePrecision)

        assertEquals(otherVote, oaAssorter.bassort(otherPool, otherPool), doublePrecision)
        assertEquals(loserVote, oaAssorter.bassort(loserPool, otherPool), doublePrecision)
        assertEquals(winnerVote, oaAssorter.bassort(winnerPool, otherPool), doublePrecision)

        //////////

        val winnerCvr = Cvr("winner", mapOf(0 to intArrayOf(0)))
        val loserCvr = Cvr("loser", mapOf(0 to intArrayOf(1)))
        val otherCvr = Cvr("other", mapOf(0 to intArrayOf(2)))

        println("CVR pool")
        println(" bassort(winnerCvr, winnerCvr)=${oaAssorter.bassort(winnerCvr, winnerCvr)} ")  // noerror
        println(" bassort(otherCvr, winnerCvr)=${oaAssorter.bassort(otherCvr, winnerCvr)} ") // noerror/2
        println(" bassort(loserCvr, winnerCvr)=${oaAssorter.bassort(loserCvr, winnerCvr)} ") // 0
        println()
        println(" bassort(winnerCvr, otherCvr)=${oaAssorter.bassort(winnerCvr, otherCvr)} ") // 1.5 * noerror
        println(" bassort(otherCvr, otherCvr)=${oaAssorter.bassort(otherCvr, otherCvr)} ") // noerror
        println(" bassort(loserCvr, otherCvr)=${oaAssorter.bassort(loserCvr, otherCvr)} ") // noerror/2
        println()
        println(" bassort(winnerCvr, loserCvr)=${oaAssorter.bassort(winnerCvr, loserCvr)} ") // 2 * noerror
        println(" bassort(otherCvr, loserCvr)=${oaAssorter.bassort(otherCvr, loserCvr)} ") // 1.5 * noerror
        println(" bassort(loserCvr, loserCvr)=${oaAssorter.bassort(loserCvr, loserCvr)} ") // noerror
        println()
        println("bassort = [0, .5, 1, 1.5, 2] * noerror=${oaAssorter.bassort(loserCvr, loserCvr)} ")
    }

    // AffineTransform code removed 8/19/2025. See history before then to revive

    /* @Test
    fun testMakeContestOAwithAffine() {
        val (contestUA, cvrs) = makeOneContestUA(20000, 18000, cvrPercent = .66, undervotePercent = .0, phantomPercent = .0, skewPct = .03)
        println(contestUA)
        val cvrVotes =  tabulateVotesFromCvrs(cvrs.iterator())
        showPct("  cvrVotes", cvrVotes[0]!!, contestUA.Nc)

        val contest = contestUA.contest as Contest
        showPct("  allVotes", contest.votes, contest.Nc)
        println()
        assertEquals(contest.votes, cvrVotes[0])

        val oaAssorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        println(oaAssorter)
        val assorter = oaAssorter.assorter
        val assortAvgFromCvrs = assorter.calcAssortAvgFromCvrs(0, cvrs)
        val assorterMargin = mean2margin(assortAvgFromCvrs)

        val reportedMargin = oaAssorter.cvrAssortMargin
        val reportedMean = margin2mean(reportedMargin)
        println("assortAvgFromCvrs = $assortAvgFromCvrs reportedMean=$reportedMean")
        assertEquals(assortAvgFromCvrs, reportedMean, doublePrecision)

        val pools = oaAssorter.poolAverages
        pools.assortAverage.forEach{ println(" avg for pool $it") }
        val poolAverage = pools.assortAverage[1]!!
        
        /*
        val contest = makeContestOA(20000, 18000, cvrPercent = .66, undervotePercent = .0, phantomPercent = .0, skewPct = .03)
        val contestUA = contest.makeContestUnderAudit()
        println(contestUA)

        val winnerCvr = Cvr("winner", mapOf(0 to intArrayOf(0)), poolId=1)
        val loserCvr = Cvr("loser", mapOf(0 to intArrayOf(1)), poolId=1)
        val otherCvr = Cvr("other", mapOf(0 to intArrayOf(2)), poolId=1)

        val oaAssorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        println(oaAssorter)

        val assorterMargin = oaAssorter.cvrAssortMargin

        val poolMargin = contest.pools[1]!!.calcReportedMargin(0, 1)
        val poolAverage = margin2mean(poolMargin)
        println("assorterMargin=v=$assorterMargin poolMargin=$poolMargin poolAverage=pa=$poolAverage ")
        println() */

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

        val winnerCvr = Cvr("winner", mapOf(0 to intArrayOf(0)), poolId=1)
        val loserCvr = Cvr("loser", mapOf(0 to intArrayOf(1)), poolId=1)
        val otherCvr = Cvr("other", mapOf(0 to intArrayOf(2)), poolId=1)

        // bassort(mvr, cvr)
        println(" bassort(other, winner)=${oaAssorter.bassort(otherCvr, winnerCvr)} ")
        println(" bassort(winner, winner)=${oaAssorter.bassort(winnerCvr, winnerCvr)} ")
        println(" bassort(loser, winner)=${oaAssorter.bassort(loserCvr, winnerCvr)} ")

        // it doesnt matter what the cvr is, it just matters that its in the pool, so cvr_assort always = poolAverage
        // bassort(mvr: Cvr, cvr: Cvr)
        assertEquals(otherVote, oaAssorter.bassort(otherCvr, winnerCvr), doublePrecision)
        assertEquals(loserVote, oaAssorter.bassort(loserCvr, winnerCvr), doublePrecision)
        assertEquals(winnerVote, oaAssorter.bassort(winnerCvr, winnerCvr), doublePrecision)

        assertEquals(otherVote, oaAssorter.bassort(otherCvr, loserCvr), doublePrecision)
        assertEquals(loserVote, oaAssorter.bassort(loserCvr, loserCvr), doublePrecision)
        assertEquals(winnerVote, oaAssorter.bassort(winnerCvr, loserCvr), doublePrecision)

        assertEquals(otherVote, oaAssorter.bassort(otherCvr, otherCvr), doublePrecision)
        assertEquals(loserVote, oaAssorter.bassort(loserCvr, otherCvr), doublePrecision)
        assertEquals(winnerVote, oaAssorter.bassort(winnerCvr, otherCvr), doublePrecision)
    }


    class OaAffine (val min: Double, max: Double) {
        val ir = 1.0 / (max - min)
        fun trans(x: Double): Double {
            return (x - min) * ir
        }
    }

     */

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
