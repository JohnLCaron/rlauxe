package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactCount
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/*
Possible assort values are bassort in [0, 1/2, 1, 3/2, 2] * noerror, where:
    0   = flipped vote from loser to winner
    1/2 = flipped vote from loser to other, or other to winner
    1 = no error
    3/2 = flipped vote from other to loser, or winner to other
    2   = flipped vote from winner to loser

    noerror = 1.0 / (2.0 - margin) == 1.0 / (3 - 2 * awinnerAvg), which ranges from .5 to 1.0.

    If you normalize the assorter values by dividing by noerror/2:
    then bassort in [0, 1/4, 1/2, 3/4, 1], and upperlimit = 1
 */

// See SHANGRLA 3.2
class TestClcaAssorter {

    @Test
    fun testBasics() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C"),
        )
        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)
        val votes = mapOf(0 to 1010, 1 to 990) // Map<Int, Int>
        val contest =  Contest(info, votes, 2000, Ncast=2000)

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        assertEquals(20/2000.toDouble(), assorter.reportedMargin(), doublePrecision)

        val awinnerAvg = .505
        val margin = 2.0 * awinnerAvg - 1.0 // reported assorter margin
        assertEquals(.01, assorter.reportedMargin(), doublePrecision)

        assertEquals(1.0, assorter.assort(winnerCvr)) // voted for the winner
        assertEquals(0.0, assorter.assort(loserCvr))  // voted for the loser
        assertEquals(0.5, assorter.assort(otherCvr))  // voted for someone else
        // so assort in {0, .5, 1}

        ////////////////////////////////////////////////////////////////////////////////////////////////

        val cassorter = ClcaAssorter(info, assorter, hasStyle=true, dilutedMargin=assorter.reportedMargin())
        assertEquals(.01, mean2margin(awinnerAvg), doublePrecision)
        assertEquals(margin, cassorter.dilutedMargin, doublePrecision)
        assertEquals(0.0, cassorter.overstatementError(winnerCvr, winnerCvr, true))
        assertEquals(-1.0, cassorter.overstatementError(winnerCvr, loserCvr, true))
        assertEquals(-0.5, cassorter.overstatementError(winnerCvr, otherCvr, true))

        assertEquals(1.0, cassorter.overstatementError(loserCvr, winnerCvr, true))
        assertEquals(0.0, cassorter.overstatementError(loserCvr, loserCvr, true))
        assertEquals(0.5, cassorter.overstatementError(loserCvr, otherCvr, true))

        assertEquals(0.5, cassorter.overstatementError(otherCvr, winnerCvr, true))
        assertEquals(-0.5, cassorter.overstatementError(otherCvr, loserCvr, true))
        assertEquals(0.0, cassorter.overstatementError(otherCvr, otherCvr, true))
        // overstatementError in [-1, -.5, 0, .5, 1]

        val noerror = 1.0 / (2.0 - margin)
        assertEquals(.5025125628140703, noerror, doublePrecision)
        assertEquals(1.0 / (3 - 2 * awinnerAvg), noerror, doublePrecision)
        assertEquals(noerror, cassorter.noerror(), doublePrecision)

        // bassort(mvr: Cvr, cvr:Cvr)
        // (1 − ωi /upper) / (2 − margin/upper), upper == assorter.upper
        assertEquals(noerror, cassorter.bassort(winnerCvr, winnerCvr))         // no error
        assertEquals(2 * noerror, cassorter.bassort(winnerCvr, loserCvr))      // cvr flipped vote from winner to loser
        assertEquals(1.5 * noerror, cassorter.bassort(winnerCvr, otherCvr))    // flipped vote from winner to other

        assertEquals(0.0, cassorter.bassort(loserCvr, winnerCvr))              // flipped vote from loser to winner
        assertEquals(noerror, cassorter.bassort(loserCvr, loserCvr))           // no error
        assertEquals(0.5 * noerror, cassorter.bassort(loserCvr, otherCvr))       // flipped vote from loser to other

        assertEquals(0.5 * noerror, cassorter.bassort(otherCvr, winnerCvr))      // flipped vote from other to winner
        assertEquals(1.5 * noerror, cassorter.bassort(otherCvr, loserCvr))       // flipped vote from other to loser
        assertEquals(noerror, cassorter.bassort(otherCvr, otherCvr))           // no error

        // so bassort in [0, 2 / (2 - margin)] = [0, 2 / (3 - 2 * Aavg)] in {0, .5, 1, 1.5, 2} * noerror
        // so bassort in [0, 2*noerror], where noerror > .5. since margin > 0, since awinnerAvg > .5.
        val assortValues = listOf(0.0, .5, 1.0, 1.5, 2.0).map { it * noerror }
        println(" bassort in $assortValues where margin = $margin noerror=$noerror")
        println(" bassort in [0, .5, 1, 1.5, 2] * noerror = [twoOver, oneOver, nuetral, oneUnder, twoUnder]")

        val assortValuesN = assortValues.map { it / noerror / 2 }
        println(" assortValuesN in $assortValuesN")
    }

    @Test
    fun testTwoCandidatePlurality() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B"),
        )
        val cvrs = makeCvrsByExactMean(ncards = 100, mean = .55)
        val contest = makeContestFromCvrs(info, cvrs)

        val awinner = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val awinnerAvg = cvrs.map { awinner.assort(it) }.average()
        val cwinner = ClcaAssorter(info, awinner, hasStyle=true, dilutedMargin=awinner.reportedMargin())
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()
        println("cwinnerAvg=$cwinnerAvg <= awinnerAvg=$awinnerAvg")
        assertTrue(cwinnerAvg <= awinnerAvg)
        assertTrue(cwinnerAvg > 0.5)

        val aloser = PluralityAssorter.makeWithVotes(contest, winner = 1, loser = 0)
        val aloserAvg = cvrs.map { aloser.assort(it) }.average()
        val closer = ClcaAssorter(info, aloser, hasStyle=true, check=false, dilutedMargin=aloser.reportedMargin())
        assertEquals(mean2margin(aloserAvg), closer.dilutedMargin, doublePrecision)
        assertEquals(aloserAvg, margin2mean(closer.dilutedMargin))

        val closerAvg = cvrs.map { closer.bassort(it, it) }.average()
        println("closerAvg=$closerAvg < aloserAvg=$aloserAvg")
        assertTrue(closerAvg < 0.5)
    }

    @Test
    fun testThreeCandidatePluralityAvg() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C"),
        )
        val counts = listOf(1000, 980, 100)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val contest = makeContestFromCvrs(info, cvrs)

        repeat(3) { winner ->
            repeat(3) { loser ->
                if (winner != loser) {
                    val avg = testNwayPlurality(contest, cvrs, winner, loser)
                    if (contest.winners.contains(winner)) assertTrue(avg > .5)
                }
            }
        }
        //  (0, 1)= 0.5024154589372037
        // (0, 2)= 0.6380368098159575
        // (1, 0)= 0.4976076555024047
        // (1, 2)= 0.6341463414634328
        // (2, 0)= 0.4110671936758784
        // (2, 1)= 0.41269841269842567
    }

    @Test
    fun testNCandidatePluralityAvg() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
        )
        val realWinner = 4
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val ncandidates = counts.size
        val contest = makeContestFromCvrs(info, cvrs)

        repeat(ncandidates) { winner ->
            var allPass = true
            repeat(ncandidates) { loser ->
                if (winner != loser) {
                    val avg = testNwayPlurality(contest, cvrs, winner, loser)
                    allPass = allPass && (avg > .5)
                }
            }
            if (winner == realWinner) assertTrue(allPass)
        }
    }

    fun testNwayPlurality(contest : Contest, cvrs: List<Cvr>, winner: Int, loser:Int): Double {
        val assort = PluralityAssorter.makeWithVotes(contest, winner, loser)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        val cwinner = ClcaAssorter(contest.info, assort, hasStyle=true, check=false, dilutedMargin=assort.reportedMargin())
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()
        assertEquals(assortAvg, margin2mean(cwinner.dilutedMargin), doublePrecision)

        println(" ($winner, $loser)= $cwinnerAvg")
        return cwinnerAvg
    }

    //////////////////////////////////////////////////////////////////////////////////

    @Test
    fun testSupermajorityNoMajority() {
        val info = ContestInfo(
            name = "ABC",
            id = 0,
            choiceFunction = SocialChoiceFunction.THRESHOLD,
            candidateNames = listToMap( "A", "B", "C"),
            minFraction = 0.60,
        )
        val counts = listOf(1000, 980, 100)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val contest = makeContestFromCvrs(info, cvrs)

        repeat(3) { winner ->
            val assortAvg = testNwaySupermajority(contest, cvrs, winner)
            assertTrue(assortAvg < .5)
        }
        //  (0)= 0.44673539518899164
        // (1)= 0.4429301533219812
        // (2)= 0.3221809169764444
    }

    fun testNwaySupermajority(contest : Contest, cvrs: List<Cvr>, winner: Int): Double {
        val assort = SuperMajorityAssorter.makeWithVotes(contest, winner, contest.info.minFraction!!)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        val cwinner = ClcaAssorter(contest.info, assort, hasStyle=true, check=false, dilutedMargin=assort.reportedMargin())
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()
        assertEquals(assortAvg, margin2mean(cwinner.dilutedMargin), doublePrecision)

        println(" ($winner)= $cwinnerAvg")
        return cwinnerAvg
    }

    @Test
    fun testBvsV() {
        val thetas = listOf(.501, .5025, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val ff = "%8.4f"
        println("  theta   margin  noerror marginB marginB/margin")
        for (theta in thetas) {
            val margin = mean2margin(theta)
            //         val noerror = 1.0 / (2.0 - margin)
            val noerror = 1.0/(2.0-margin) // assorter mean
            val marginB = mean2margin(noerror)
            println("${ff.format(theta)} ${ff.format(margin)} ${ff.format(noerror)} ${ff.format(marginB)} ${ff.format(marginB/margin)}")
        }
    }

    @Test
    fun testStandardComparisonAssorter() {
        val N = 1000
        val cvrMean = 0.55

        val info = ContestInfo("standard", 0, listToMap("A", "B"), choiceFunction = SocialChoiceFunction.PLURALITY)
        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contestUA = makeContestUAfromCvrs(info, cvrs)
        val compareAssertion = contestUA.clcaAssertions.first()
        val compareAssorter1 = compareAssertion.cassorter

        // check the same
        val compareAssorter2 = makeContestUAfromCvrs(info, cvrs).clcaAssertions.first().cassorter
        assertEquals(compareAssorter1, compareAssorter2)

        // check assort values for ComparisonSamplerSimulation

        // PluralityAssorter winner=0 loser=1
        //  flip2votes 0.5 != 0.0
        //    cvr=card-379 (false) 0: [0]
        //    alteredMvr=card-379 (false) 0: [1, 0]
        val passorter = compareAssorter1.assorter()
        assertEquals(1.0, passorter.assort(Cvr(info.id, listOf(0))))
        assertEquals(0.0, passorter.assort(Cvr(info.id, listOf(1))))
        assertEquals(0.5, passorter.assort(Cvr(info.id, listOf(1,0))))
        assertEquals(0.5, passorter.assort(Cvr(info.id, listOf())))
    }


    @Test
    fun testPhantoms() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C"),
        )
        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)
        val phantomCvr = Cvr("phantom", mapOf(0 to IntArray(0)), phantom = true)

        val votes = mapOf(0 to 1000, 1 to 990) // Map<Int, Int>
        val contest =  Contest(info, votes, 2000, Ncast=1990)

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        assertEquals(10/2000.toDouble(), assorter.reportedMargin(), doublePrecision)

        val awinnerAvg = assorter.reportedMean()
        assertEquals(awinnerAvg, assorter.reportedMean(), doublePrecision)
        val margin = 2.0 * awinnerAvg - 1.0 // reported assorter margin
        assertEquals(assorter.reportedMargin(), margin, doublePrecision)

        assertEquals(1.0, assorter.assort(winnerCvr)) // voted for the winner
        assertEquals(0.0, assorter.assort(loserCvr))  // voted for the loser
        assertEquals(0.5, assorter.assort(otherCvr))  // voted for someone else
        assertEquals(0.5, assorter.assort(phantomCvr, false))  // ignore cvr is a phantom
        assertEquals(0.0, assorter.assort(phantomCvr, true))  // cvr is a phantom
        // so assort in {0, .5, 1}

        val cassorter = ClcaAssorter(info, assorter, hasStyle=true, dilutedMargin=assorter.reportedMargin())
        assertEquals(margin, cassorter.dilutedMargin, doublePrecision)
        assertEquals(awinnerAvg, margin2mean(cassorter.dilutedMargin))

        assertEquals(0.0, cassorter.overstatementError(winnerCvr, winnerCvr, true))
        assertEquals(-1.0, cassorter.overstatementError(winnerCvr, loserCvr, true))
        assertEquals(-0.5, cassorter.overstatementError(winnerCvr, otherCvr, true))
        assertEquals(-0.5, cassorter.overstatementError(winnerCvr, phantomCvr, true))

        assertEquals(1.0, cassorter.overstatementError(loserCvr, winnerCvr, true))
        assertEquals(0.0, cassorter.overstatementError(loserCvr, loserCvr, true))
        assertEquals(0.5, cassorter.overstatementError(loserCvr, otherCvr, true))
        assertEquals(0.5, cassorter.overstatementError(loserCvr, phantomCvr, true))

        assertEquals(0.5, cassorter.overstatementError(otherCvr, winnerCvr, true))
        assertEquals(-0.5, cassorter.overstatementError(otherCvr, loserCvr, true))
        assertEquals(0.0, cassorter.overstatementError(otherCvr, otherCvr, true))

        assertEquals(1.0, cassorter.overstatementError(phantomCvr, winnerCvr, true)) // check
        assertEquals(0.0, cassorter.overstatementError(phantomCvr, loserCvr, true)) // check
        assertEquals(0.5, cassorter.overstatementError(phantomCvr, phantomCvr, true)) // check, usual case
        // so overstatementError in [-1, -.5, 0, .5, 1]

        // TODO hasStyle parameter doesnt matter unless mvr doesnt have the contest. See testHasStyles below.
        val cvrs = listOf(winnerCvr, loserCvr, otherCvr, phantomCvr)
        for (mvr in cvrs) {
            for (cvr in cvrs) {
                assertEquals(cassorter.overstatementError(mvr, cvr, false), cassorter.overstatementError(mvr, cvr, true))
            }
        }

        val noerror = 1.0 / (2.0 - margin)
        assertEquals(1.0 / (3 - 2 * awinnerAvg), noerror, doublePrecision)
        assertEquals(noerror, cassorter.noerror(), doublePrecision)
        println("noerror = $noerror")

        // bassort in [0, .5, 1, 1.5, 2] * noerror = [twoOver, oneOver, nuetral, oneUnder, twoUnder]
        assertEquals(noerror, cassorter.bassort(winnerCvr, winnerCvr))         // no error
        assertEquals(2 * noerror, cassorter.bassort(winnerCvr, loserCvr))      // cvr flipped vote from winner to loser
        assertEquals(1.5 * noerror, cassorter.bassort(winnerCvr, otherCvr))    // cvr flipped vote from winner to other
        assertEquals(1.5 * noerror, cassorter.bassort(winnerCvr, phantomCvr))  // found winner: oneUnder

        assertEquals(0.0, cassorter.bassort(loserCvr, winnerCvr))              // cvr flipped vote from loser to winner
        assertEquals(noerror, cassorter.bassort(loserCvr, loserCvr))           // no error
        assertEquals(0.5*noerror, cassorter.bassort(loserCvr, otherCvr))       // cvr flipped vote from loser to other
        assertEquals(0.5*noerror, cassorter.bassort(loserCvr, phantomCvr))     // found loser: oneOver

        assertEquals(0.5*noerror, cassorter.bassort(otherCvr, winnerCvr))      // cvr flipped vote from other to winner
        assertEquals(1.5*noerror, cassorter.bassort(otherCvr, loserCvr))       // cvr flipped vote from other to loser
        assertEquals(noerror, cassorter.bassort(otherCvr, otherCvr))           // no error

        assertEquals(0.0, cassorter.bassort(phantomCvr, winnerCvr))           // no mvr, cvr reported winner, : twoOver
        assertEquals(noerror, cassorter.bassort(phantomCvr, loserCvr))                 // no mvr, cvr reported loser: nuetral
        assertEquals(0.5*noerror, cassorter.bassort(phantomCvr, phantomCvr))  // no mvr, no cvr: oneOver (common case i assume)
        assertEquals(1.5*noerror, cassorter.bassort(winnerCvr, phantomCvr))   // mvr reported winner, no cvr: oneUnder
        assertEquals(.5*noerror, cassorter.bassort(loserCvr, phantomCvr))     // mvr reported lose, no cvr: oneOver
    }

    @Test
    fun testHasStyles() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C"),
        )
        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)
        val phantomCvr = Cvr("phantom", mapOf(0 to IntArray(0)), phantom = true)
        val cvrs = listOf(winnerCvr, winnerCvr, loserCvr, otherCvr, phantomCvr)
        val contest = makeContestFromCvrs(info, cvrs)

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val cassorter = ClcaAssorter(info, assorter, hasStyle=true, dilutedMargin=assorter.reportedMargin())
        val noerror = cassorter.noerror()
        println("  noerror = $noerror")

        val differentContest = Cvr("diff", mapOf(1 to IntArray(0)))

        assertEquals(-0.5, cassorter.overstatementError(winnerCvr, differentContest, false))
        val mess = assertFailsWith<RuntimeException> {
            assertEquals(0.0, cassorter.overstatementError(winnerCvr, differentContest, true))
        }.message!!
        assertTrue(mess.contains("does not contain contest"))

        // contest does not appear on the mvr
        assertEquals(0.5, cassorter.overstatementError(differentContest, winnerCvr, false))
        assertEquals(-0.5, cassorter.overstatementError(differentContest, loserCvr, false))
        assertEquals(0.0, cassorter.overstatementError(differentContest, otherCvr, false))

        assertEquals(1.0, cassorter.overstatementError(differentContest, winnerCvr, true))
        assertEquals(0.0, cassorter.overstatementError(differentContest, loserCvr, true))
        assertEquals(0.5, cassorter.overstatementError(differentContest, otherCvr, true))

        assertEquals(0.5 * noerror, cassorter.bassort(differentContest, winnerCvr, false))
        assertEquals(1.5 * noerror, cassorter.bassort(differentContest, loserCvr, false))
        assertEquals(1.0 * noerror, cassorter.bassort(differentContest, otherCvr, false))

        assertEquals(0.0 * noerror, cassorter.bassort(differentContest, winnerCvr, true))
        assertEquals(1.0 * noerror, cassorter.bassort(differentContest, loserCvr, true))
        assertEquals(0.5 * noerror, cassorter.bassort(differentContest, otherCvr, true))
    }

    @Test
    fun testNoError() {
        val N = 1000
        val cvrMean = 0.55

        val info = ContestInfo("standard", 0, listToMap("A", "B"), choiceFunction = SocialChoiceFunction.PLURALITY)
        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contestUA = makeContestUAfromCvrs(info, cvrs)
        val compareAssertion = contestUA.clcaAssertions.first()
        val cassorter = compareAssertion.cassorter

        val theta = cassorter.noerror()
        val expected = 1.0 / (3 - 2 * cvrMean)
        assertEquals(expected, theta, doublePrecision)

        val calcMargin = cassorter.calcClcaAssorterMargin(cvrs.zip(cvrs))
        val calcMean = margin2mean(calcMargin)
        assertEquals(expected, calcMean, doublePrecision)
    }

    @Test
    fun showNoError() {
        println("upper=1")
        repeat(20) {
            val mean = 0.5 + it / 40.0
            val noerror1 = 1.0 / (3 - 2 * mean)
            assertEquals(1.0 / (2 - mean2margin(mean)), noerror1)

            println(" mean=${dfn(mean, 3)} noerror=${dfn(noerror1, 3)}")
        }

        println("\nupper=2")
        repeat(20) {
            val mean = 0.5 + it / 40.0
            val noerror = noerror(mean2margin(mean), 2.0)
            println(" mean=${dfn(mean,3)} noerror=${dfn(noerror, 3)}")
        }

        println("\nupper=10")
        repeat(20) {
            val mean = 0.5 + it / 40.0
            val noerror = noerror(mean2margin(mean), 10.0)
            println(" mean=${dfn(mean,3)} noerror=${dfn(noerror, 3)}")
        }
    }
}

fun ClcaAssorter.calcClcaAssorterMargin(cvrPairs: Iterable<Pair<Cvr, Cvr>>): Double {
    val mean = cvrPairs.filter{ it.first.hasContest(info.id) }
        .map { bassort(it.first, it.second) }.average()
    return mean2margin(mean)
}