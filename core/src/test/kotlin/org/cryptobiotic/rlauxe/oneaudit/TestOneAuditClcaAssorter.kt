package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.PluralityAssorter
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

// oa_polling.ipynb
// assorter_mean_all = (whitmer-schuette)/N
// v = 2*assorter_mean_all-1
// u_b = 2*u/(2*u-v)  # upper bound on the overstatement assorter
// noerror = u/(2*u-v)

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

//    Ng = |G_g|
//    Ā(g) ≡ assorter_mean_poll = (winner total - loser total) / Ng
//    margin ≡ 2Ā(g) − 1 ≡ v = 2*assorter_mean_poll − 1
//    mvr has loser vote = (1-assorter_mean_poll)/(2-v/u)
//    mvr has winner vote = (2-assorter_mean_poll)/(2-v/u)
//    otherwise = 1/2

class TestOneAuditClcaAssorter {

    @Test
    fun testOABasics() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C"),
        )
        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)
        val cvrs = listOf(winnerCvr, winnerCvr, winnerCvr, loserCvr, otherCvr)
        val contest = makeContestFromCvrs(info, cvrs)

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        assertEquals(1.0, assorter.assort(winnerCvr)) // voted for the winner
        assertEquals(0.0, assorter.assort(loserCvr))  // voted for the loser
        assertEquals(0.5, assorter.assort(otherCvr))  // voted for someone else
        // so assort in {0, .5, 1}

        val awinnerAvg = cvrs.map { assorter.assort(it) }.average()
        val margin = 2.0 * awinnerAvg - 1.0 // reported assorter margin

        val stratum = OneAuditStratum("card", true, contest.info, contest.votes, cvrs.size, 0)

        val contestOA = OneAuditContest(contest.info, listOf(stratum))
        val bassorter = OneAuditComparisonAssorter(contestOA, assorter, awinnerAvg)

        // assertEquals(noerror, bassorter.clcaMargin, doublePrecision)
        assertEquals(1.0 / (2.0 - margin), bassorter.noerror(), doublePrecision)
        assertEquals(2 * bassorter.noerror(), bassorter.upperBound(), doublePrecision)
        assertEquals(assorter, bassorter.assorter())

        // full
        //  mvr has loser vote = (1 - Ā(g)/u) / (2-v/u)
        //  mvr has winner vote = (1 - (Ā(g)-1)/u) / (2-v/u)
        //  mvr has other vote = (1 - (Ā(g)-.5)/u) / (2-v/u) = 1/2

        // u=1
        //    mvr has loser vote =  (1-assorter_mean_poll)/(2-v)
        //    mvr has winner vote = (2-assorter_mean_poll)/(2-v)
        val loserVote = (1.0 - awinnerAvg) / (2 - margin)
        val winnerVote = (2.0 - awinnerAvg) / (2 - margin)
        println("loserVote=$loserVote winner=$winnerVote ")

        println(" mvr other bassort=${bassorter.bassort(otherCvr, winnerCvr)} ")
        println(" mvr winner bassort=${bassorter.bassort(winnerCvr, winnerCvr)} ")
        println(" mvr loser bassort=${bassorter.bassort(loserCvr, winnerCvr)} ")

        // TODO failing
        assertEquals(0.5, bassorter.bassort(otherCvr, winnerCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, winnerCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, winnerCvr), doublePrecision)

        assertEquals(0.5, bassorter.bassort(otherCvr, loserCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, loserCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, loserCvr), doublePrecision)

        assertEquals(0.5, bassorter.bassort(otherCvr, otherCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, otherCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, otherCvr), doublePrecision)
    }

    @Test
    fun testMakeContestUnderAudit() {
        val contest = makeContestOA(2000, 1800, cvrPercent = .66, 0.05, undervotePercent = .0, phantomPercent = .0)
        val testCvrs = contest.makeTestCvrs()
        val contestUA = contest.makeContestUnderAudit(testCvrs)
        println(contestUA)

        val winnerCvr = makeCvr(0, "noCvr")
        val loserCvr = makeCvr(1, "noCvr")
        val otherCvr = makeCvr(2, "noCvr")

        val bassorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditComparisonAssorter
        println(bassorter)

        val assorter_mean_poll = bassorter.stratumInfos["noCvr"]!!.avgBatchAssortValue
        val margin = mean2margin(assorter_mean_poll)

        //    mvr has loser vote = (1-assorter_mean_poll)/(2-v/u)
        //    mvr has winner vote = (2-assorter_mean_poll)/(2-v/u)
        val loserVote = (1.0 - assorter_mean_poll) / (2 - margin)
        val winnerVote = (2.0 - assorter_mean_poll) / (2 - margin)
        println("loserVote=$loserVote winner=$winnerVote ")

        println(" mvr other bassort=${bassorter.bassort(otherCvr, winnerCvr)} ")
        println(" mvr winner bassort=${bassorter.bassort(winnerCvr, winnerCvr)} ")
        println(" mvr loser bassort=${bassorter.bassort(loserCvr, winnerCvr)} ")

        assertEquals(0.5, bassorter.bassort(otherCvr, winnerCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, winnerCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, winnerCvr), doublePrecision)

        assertEquals(0.5, bassorter.bassort(otherCvr, loserCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, loserCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, loserCvr), doublePrecision)

        assertEquals(0.5, bassorter.bassort(otherCvr, otherCvr), doublePrecision)
        assertEquals(loserVote, bassorter.bassort(loserCvr, otherCvr), doublePrecision)
        assertEquals(winnerVote, bassorter.bassort(winnerCvr, otherCvr), doublePrecision)
    }

    @Test
    fun testONE() {
        // two candidate plurailty
        val contest = makeContestOA(1000, 923, cvrPercent = .57, .007, undervotePercent = .0, phantomPercent = .0)
        println(contest)

        val testCvrs = contest.makeTestCvrs()
        val contestOA = contest.makeContestUnderAudit(testCvrs)
        println(contestOA)

        val bassorter = contestOA.minClcaAssertion()!!.cassorter as OneAuditComparisonAssorter
        println(bassorter)
        println("reportedMargin = ${bassorter.assorter.reportedMargin()} clcaMargin = ${bassorter.clcaMargin} ")

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

        println("clcaMargin = ${bassorter.clcaMargin}")
        println("clcaMean = ${margin2mean(bassorter.clcaMargin)}")
        assertEquals(allAvg, margin2mean(bassorter.clcaMargin), doublePrecision)
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