package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
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
class TestAssorterClca {

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
        val contest = makeContestFromCvrs(info, listOf(winnerCvr, loserCvr, otherCvr))

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val awinnerAvg = .51
        val margin = 2.0 * awinnerAvg - 1.0 // reported assorter margin
        assertEquals(.02, margin, doublePrecision)
        val bassorter = ClcaAssorter(info, assorter, awinnerAvg)
        assertEquals(.02, mean2margin(bassorter.avgCvrAssortValue), doublePrecision)

        ////////////////////////////////////////////////////////////////////////////////////////////////

        assertEquals(1.0, assorter.assort(winnerCvr)) // voted for the winner
        assertEquals(0.0, assorter.assort(loserCvr))  // voted for the loser
        assertEquals(0.5, assorter.assort(otherCvr))  // voted for someone else
        // so assort in {0, .5, 1}

        assertEquals(0.0, bassorter.overstatementError(winnerCvr, winnerCvr, true))
        assertEquals(-1.0, bassorter.overstatementError(winnerCvr, loserCvr, true))
        assertEquals(-0.5, bassorter.overstatementError(winnerCvr, otherCvr, true))

        assertEquals(1.0, bassorter.overstatementError(loserCvr, winnerCvr, true))
        assertEquals(0.0, bassorter.overstatementError(loserCvr, loserCvr, true))
        assertEquals(0.5, bassorter.overstatementError(loserCvr, otherCvr, true))

        assertEquals(0.5, bassorter.overstatementError(otherCvr, winnerCvr, true))
        assertEquals(-0.5, bassorter.overstatementError(otherCvr, loserCvr, true))
        assertEquals(0.0, bassorter.overstatementError(otherCvr, otherCvr, true))
        // overstatementError in [-1, -.5, 0, .5, 1]

        val noerror = 1.0 / (2.0 - margin)
        assertEquals(.5050505050505051, noerror, doublePrecision)
        assertEquals(1.0 / (3 - 2 * awinnerAvg), noerror, doublePrecision)
        assertEquals(noerror, bassorter.noerror(), doublePrecision)

        // bassort(mvr: Cvr, cvr:Cvr)
        // (1 − ωi /upper) / (2 − margin/upper), upper == assorter.upper
        assertEquals(noerror, bassorter.bassort(winnerCvr, winnerCvr))         // no error
        assertEquals(2 * noerror, bassorter.bassort(winnerCvr, loserCvr))      // cvr flipped vote from winner to loser
        assertEquals(1.5 * noerror, bassorter.bassort(winnerCvr, otherCvr))    // flipped vote from winner to other

        assertEquals(0.0, bassorter.bassort(loserCvr, winnerCvr))              // flipped vote from loser to winner
        assertEquals(noerror, bassorter.bassort(loserCvr, loserCvr))           // no error
        assertEquals(0.5 * noerror, bassorter.bassort(loserCvr, otherCvr))       // flipped vote from loser to other

        assertEquals(0.5 * noerror, bassorter.bassort(otherCvr, winnerCvr))      // flipped vote from other to winner
        assertEquals(1.5 * noerror, bassorter.bassort(otherCvr, loserCvr))       // flipped vote from other to loser
        assertEquals(noerror, bassorter.bassort(otherCvr, otherCvr))           // no error

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
        val cwinner = ClcaAssorter(info, awinner, awinnerAvg)
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()
        println("cwinnerAvg=$cwinnerAvg <= awinnerAvg=$awinnerAvg")
        assertTrue(cwinnerAvg <= awinnerAvg)
        assertTrue(cwinnerAvg > 0.5)

        val aloser = PluralityAssorter.makeWithVotes(contest, winner = 1, loser = 0)
        val aloserAvg = cvrs.map { aloser.assort(it) }.average()
        val closer = ClcaAssorter(info, aloser, aloserAvg, check=false)
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
        val cwinner = ClcaAssorter(contest.info, assort, assortAvg, check=false)
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()

        println(" ($winner, $loser)= $cwinnerAvg")
        return cwinnerAvg
    }

    //////////////////////////////////////////////////////////////////////////////////

    @Test
    fun testSupermajorityNoMajority() {
        val info = ContestInfo(
            name = "ABC",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
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

    @Test
    fun testSupermajorityMultipleWinners() {
        val info = ContestInfo(
            name = "ABC",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap( "A", "B", "C"),
            nwinners = 2,
            minFraction = 0.35,
        )
        val counts = listOf(1000, 980, 100)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val contest = makeContestFromCvrs(info, cvrs)

        repeat(3) { winner ->
            val assortAvg = testNwaySupermajority(contest, cvrs, winner)
            assertEquals(assortAvg > .5, contest.winners.contains(winner))
        }
        //  (0)= 0.5752212389380481
        // (1)= 0.5689277899343718
        // (2)= 0.38404726735598166
    }

    @Test
    fun testNCandidateSuperMajority() {
        val info = ContestInfo(
            name = "ABCs",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E", "F", "G"),
            nwinners = 3,
            minFraction = 0.25,
        )

        val counts = listOf(1600, 1300, 500, 1500, 50, 12, 1)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val ncandidates = counts.size
        val contest = makeContestFromCvrs(info, cvrs)

        repeat(ncandidates) { winner ->
            val assortAvg = testNwaySupermajority(contest, cvrs, winner)
            assertEquals(assortAvg > .5, contest.winners.contains(winner))
        }

        // (0)= 0.5390171056204459
        // (1)= 0.5060412949273885
        // (2)= 0.4350646504492631
        // (3)= 0.5275577996279489
        // (4)= 0.403250050781993
        // (5)= 0.4007752250979454
        // (6)= 0.4000644875256966
    }

    fun testNwaySupermajority(contest : Contest, cvrs: List<Cvr>, winner: Int): Double {
        val assort = SuperMajorityAssorter.makeWithVotes(contest, winner, contest.info.minFraction!!)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        val cwinner = ClcaAssorter(contest.info, assort, assortAvg, check=false)
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()

        println(" ($winner)= $cwinnerAvg")
        return cwinnerAvg
    }

    // referenced in PollingVsComparison.md
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
        val contest = makeContestUAfromCvrs(info, cvrs)
        val contestAU = contest.makeClcaAssertions()
        val compareAssertion = contestAU.clcaAssertions.first()
        val compareAssorter1 = compareAssertion.cassorter

        // check the same
        val compareAssorter2 = makeContestUAfromCvrs(info, cvrs).makeClcaAssertions().clcaAssertions.first().cassorter
        assertEquals(compareAssorter1, compareAssorter2)

        // check assort values for ComparisonSamplerSimulation

        // PluralityAssorter winner=0 loser=1
        //  flip2votes 0.5 != 0.0
        //    cvr=card-379 (false) 0: [0]
        //    alteredMvr=card-379 (false) 0: [1, 0]
        val passorter = compareAssorter1.assorter()
        assertEquals(1.0, passorter.assort(Cvr(contest.id, listOf(0))))
        assertEquals(0.0, passorter.assort(Cvr(contest.id, listOf(1))))
        assertEquals(0.5, passorter.assort(Cvr(contest.id, listOf(1,0))))
        assertEquals(0.5, passorter.assort(Cvr(contest.id, listOf())))
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
        val cvrs = listOf(winnerCvr, loserCvr, otherCvr, phantomCvr)
        val contest = makeContestFromCvrs(info, cvrs)

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val awinnerAvg = .51
        val margin = 2.0 * awinnerAvg - 1.0 // reported assorter margin
        assertEquals(.02, margin, doublePrecision)
        val bassorter = ClcaAssorter(info, assorter, awinnerAvg)
        assertEquals(.02, mean2margin(bassorter.avgCvrAssortValue), doublePrecision)

        assertEquals(1.0, assorter.assort(winnerCvr)) // voted for the winner
        assertEquals(0.0, assorter.assort(loserCvr))  // voted for the loser
        assertEquals(0.5, assorter.assort(otherCvr))  // voted for someone else
        assertEquals(0.5, assorter.assort(phantomCvr, false))  // ignore cvr is a phantom
        assertEquals(0.0, assorter.assort(phantomCvr, true))  // cvr is a phantom
        // so assort in {0, .5, 1}

        assertEquals(0.0, bassorter.overstatementError(winnerCvr, winnerCvr, true))
        assertEquals(-1.0, bassorter.overstatementError(winnerCvr, loserCvr, true))
        assertEquals(-0.5, bassorter.overstatementError(winnerCvr, otherCvr, true))
        assertEquals(-0.5, bassorter.overstatementError(winnerCvr, phantomCvr, true))

        assertEquals(1.0, bassorter.overstatementError(loserCvr, winnerCvr, true))
        assertEquals(0.0, bassorter.overstatementError(loserCvr, loserCvr, true))
        assertEquals(0.5, bassorter.overstatementError(loserCvr, otherCvr, true))
        assertEquals(0.5, bassorter.overstatementError(loserCvr, phantomCvr, true))

        assertEquals(0.5, bassorter.overstatementError(otherCvr, winnerCvr, true))
        assertEquals(-0.5, bassorter.overstatementError(otherCvr, loserCvr, true))
        assertEquals(0.0, bassorter.overstatementError(otherCvr, otherCvr, true))

        assertEquals(1.0, bassorter.overstatementError(phantomCvr, winnerCvr, true)) // check
        assertEquals(0.0, bassorter.overstatementError(phantomCvr, loserCvr, true)) // check
        assertEquals(0.5, bassorter.overstatementError(phantomCvr, phantomCvr, true)) // check, usual case
        // so overstatementError in [-1, -.5, 0, .5, 1]

        // TODO hasStyle parameter doesnt matter unless mvr doesnt have the contest. See testHasStyles below.
        for (mvr in cvrs) {
            for (cvr in cvrs) {
                assertEquals(bassorter.overstatementError(mvr, cvr, false), bassorter.overstatementError(mvr, cvr, true))
            }
        }

        val noerror = 1.0 / (2.0 - margin)
        assertEquals(.5050505050505051, noerror, doublePrecision)
        assertEquals(1.0 / (3 - 2 * awinnerAvg), noerror, doublePrecision)
        assertEquals(noerror, bassorter.noerror(), doublePrecision)
        println("noerror = $noerror")

        // bassort in [0, .5, 1, 1.5, 2] * noerror = [twoOver, oneOver, nuetral, oneUnder, twoUnder]
        assertEquals(noerror, bassorter.bassort(winnerCvr, winnerCvr))         // no error
        assertEquals(2 * noerror, bassorter.bassort(winnerCvr, loserCvr))      // cvr flipped vote from winner to loser
        assertEquals(1.5 * noerror, bassorter.bassort(winnerCvr, otherCvr))    // cvr flipped vote from winner to other
        assertEquals(1.5 * noerror, bassorter.bassort(winnerCvr, phantomCvr))  // found winner: oneUnder

        assertEquals(0.0, bassorter.bassort(loserCvr, winnerCvr))              // cvr flipped vote from loser to winner
        assertEquals(noerror, bassorter.bassort(loserCvr, loserCvr))           // no error
        assertEquals(0.5*noerror, bassorter.bassort(loserCvr, otherCvr))       // cvr flipped vote from loser to other
        assertEquals(0.5*noerror, bassorter.bassort(loserCvr, phantomCvr))     // found loser: oneOver

        assertEquals(0.5*noerror, bassorter.bassort(otherCvr, winnerCvr))      // cvr flipped vote from other to winner
        assertEquals(1.5*noerror, bassorter.bassort(otherCvr, loserCvr))       // cvr flipped vote from other to loser
        assertEquals(noerror, bassorter.bassort(otherCvr, otherCvr))           // no error

        assertEquals(0.0, bassorter.bassort(phantomCvr, winnerCvr))           // no mvr, cvr reported winner, : twoOver
        assertEquals(noerror, bassorter.bassort(phantomCvr, loserCvr))                 // no mvr, cvr reported loser: nuetral
        assertEquals(0.5*noerror, bassorter.bassort(phantomCvr, phantomCvr))  // no mvr, no cvr: oneOver (common case i assume)
        assertEquals(1.5*noerror, bassorter.bassort(winnerCvr, phantomCvr))   // mvr reported winner, no cvr: oneUnder
        assertEquals(.5*noerror, bassorter.bassort(loserCvr, phantomCvr))     // mvr reported lose, no cvr: oneOver
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
        val cvrs = listOf(winnerCvr, loserCvr, otherCvr, phantomCvr)
        val contest = makeContestFromCvrs(info, cvrs)

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val awinnerAvg = .51
        val bassorter = ClcaAssorter(info, assorter, awinnerAvg)

        val differentContest = Cvr("diff", mapOf(1 to IntArray(0)))

        assertEquals(-0.5, bassorter.overstatementError(winnerCvr, differentContest, false))

        val mess = assertFailsWith<RuntimeException> {
            assertEquals(0.0, bassorter.overstatementError(winnerCvr, differentContest, true))
        }.message!!
        assertTrue(mess.contains("does not contain contest"))

        assertEquals(0.5, bassorter.overstatementError(differentContest, winnerCvr, false))
        assertEquals(1.0, bassorter.overstatementError(differentContest, winnerCvr, true))
    }

    @Test
    fun testMeanAssort() {
        val N = 1000
        val cvrMean = 0.55

        val info = ContestInfo("standard", 0, listToMap("A", "B"), choiceFunction = SocialChoiceFunction.PLURALITY)
        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contest = makeContestUAfromCvrs(info, cvrs)
        val contestAU = contest.makeClcaAssertions()
        val compareAssertion = contestAU.clcaAssertions.first()
        val cassorter = compareAssertion.cassorter

        val theta = cassorter.meanAssort()
        val expected = 1.0 / (3 - 2 * cvrMean)
        assertEquals(expected, theta, doublePrecision)

        val calcMargin = cassorter.calcClcaAssorterMargin(cvrs.zip(cvrs))
        val calcMean = margin2mean(calcMargin)
        assertEquals(expected, calcMean, doublePrecision)
    }

}