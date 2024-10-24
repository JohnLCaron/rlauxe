package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.makeStandardComparisonAssorter
import org.cryptobiotic.rlauxe.util.makeCvr
import org.cryptobiotic.rlauxe.util.makeCvrsByExactCount
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.theta2margin
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

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
class TestComparisonAssorter {

    @Test
    fun testBasics() {
        val contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B", "C"),
            winnerNames = listOf("A"),
        )
        val assorter = PluralityAssorter(contest, winner = 0, loser = 1)
        val awinnerAvg = .51
        val margin = 2.0 * awinnerAvg - 1.0 // reported assorter margin
        assertEquals(.02, margin, doublePrecision)
        val bassorter = ComparisonAssorter(contest, assorter, awinnerAvg)
        assertEquals(.02, bassorter.margin, doublePrecision)

        val winnerCvr = makeCvr(0)
        val loserCvr = makeCvr(1)
        val otherCvr = makeCvr(2)

        assertEquals(1.0, assorter.assort(winnerCvr)) // voted for the winner
        assertEquals(0.0, assorter.assort(loserCvr))  // voted for the loser
        assertEquals(0.5, assorter.assort(otherCvr))  // voted for someone else
        // so assort in {0, .5, 1}

        assertEquals(0.0, bassorter.overstatementError(winnerCvr, winnerCvr))
        assertEquals(-1.0, bassorter.overstatementError(winnerCvr, loserCvr))
        assertEquals(-0.5, bassorter.overstatementError(winnerCvr, otherCvr))

        assertEquals(1.0, bassorter.overstatementError(loserCvr, winnerCvr))
        assertEquals(0.0, bassorter.overstatementError(loserCvr, loserCvr))
        assertEquals(0.5, bassorter.overstatementError(loserCvr, otherCvr))

        assertEquals(0.5, bassorter.overstatementError(otherCvr, winnerCvr))
        assertEquals(-0.5, bassorter.overstatementError(otherCvr, loserCvr))
        assertEquals(0.0, bassorter.overstatementError(otherCvr, otherCvr))
        // overstatementError in [-1, -.5, 0, .5, 1]

        val noerror = 1.0 / (2.0 - margin)
        assertEquals(.5050505050505051, noerror, doublePrecision)
        assertEquals(1.0 / (3 - 2 * awinnerAvg), noerror, doublePrecision)
        assertEquals(noerror, bassorter.noerror, doublePrecision)

        // bassort(mvr: Cvr, cvr:Cvr)
        // (1 − ωi /upper) / (2 − margin/upper), upper == assorter.upper
        assertEquals(noerror, bassorter.bassort(winnerCvr, winnerCvr))         // no error
        assertEquals(2 * noerror, bassorter.bassort(winnerCvr, loserCvr))      // cvr flipped vote from winner to loser
        assertEquals(1.5 * noerror, bassorter.bassort(winnerCvr, otherCvr))    // flipped vote from winner to other

        assertEquals(0.0, bassorter.bassort(loserCvr, winnerCvr))              // flipped vote from loser to winner
        assertEquals(noerror, bassorter.bassort(loserCvr, loserCvr))           // no error
        assertEquals(0.5*noerror, bassorter.bassort(loserCvr, otherCvr))       // flipped vote from loser to other

        assertEquals(0.5*noerror, bassorter.bassort(otherCvr, winnerCvr))      // flipped vote from other to winner
        assertEquals(1.5*noerror, bassorter.bassort(otherCvr, loserCvr))       // flipped vote from other to loser
        assertEquals(noerror, bassorter.bassort(otherCvr, otherCvr))           // no error

        // so bassort in [0, 2 / (2 - margin)] = [0, 2 / (3 - 2 * Aavg)] in {0, .5, 1, 1.5, 2} * noerror
        // so bassort in [0, 2*noerror], where noerror > .5. since margin > 0, since awinnerAvg > .5.
        val assortValues = listOf(0.0, .5, 1.0, 1.5, 2.0).map { it * noerror }
        println(" bassort in $assortValues where margin = $margin noerror=$noerror")

        val assortValuesN = assortValues.map { it / noerror / 2 }
        println(" assortValuesN in $assortValuesN")
    }

    @Test
    fun testTwoCandidatePlurality() {
        val contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B"),
            winnerNames = listOf("A"),
        )
        val cvrs: List<Cvr> = makeCvrsByExactMean(ncards = 100, mean = .55)

        val awinner = PluralityAssorter(contest, winner = 0, loser = 1)
        val awinnerAvg = cvrs.map { awinner.assort(it) }.average()
        val cwinner = ComparisonAssorter(contest, awinner, awinnerAvg)
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()
        println("cwinnerAvg=$cwinnerAvg <= awinnerAvg=$awinnerAvg")
        assertTrue(cwinnerAvg <= awinnerAvg)
        assertTrue(cwinnerAvg > 0.5)

        val aloser = PluralityAssorter(contest, winner = 1, loser = 0)
        val aloserAvg = cvrs.map { aloser.assort(it) }.average()
        val closer = ComparisonAssorter(contest, aloser, aloserAvg, false)
        val closerAvg = cvrs.map { closer.bassort(it, it) }.average()
        println("closerAvg=$closerAvg < aloserAvg=$aloserAvg")
        assertTrue(closerAvg < 0.5)
    }

    @Test
    fun testThreeCandidatePluralityAvg() {
        val contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B", "C"),
            winnerNames = listOf("A"),
        )
        val counts = listOf(1000, 980, 100)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)

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
        val contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B", "C"),
            winnerNames = listOf("A"),  // TODO not using winners here
        )
        val realWinner = 4
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val ncandidates = counts.size

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

    fun testNwayPlurality(contest : AuditContest, cvrs: List<Cvr>, winner: Int, loser:Int): Double {
        val assort = PluralityAssorter(contest, winner, loser)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        val cwinner = ComparisonAssorter(contest, assort, assortAvg, false)
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()

        println(" ($winner, $loser)= $cwinnerAvg")
        return cwinnerAvg
    }

    //////////////////////////////////////////////////////////////////////////////////

    @Test
    fun testSupermajorityNoMajority() {
        val contest = AuditContest(
            id = "ABC",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listOf( "A", "B", "C"),
            winnerNames = listOf(),
            minFraction = 0.60,
        )
        val counts = listOf(1000, 980, 100)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)

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
        val contest = AuditContest(
            id = "ABC",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listOf( "A", "B", "C"),
            winnerNames = listOf("A", "B"),
            minFraction = 0.35,
        )
        val counts = listOf(1000, 980, 100)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)

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
        val counts = listOf(1600, 1300, 500, 1500, 50, 12, 1)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val ncandidates = counts.size

        val contest = AuditContest(
            id = "ABCs",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listOf( "A", "B", "C", "D", "E"),
            winnerNames = listOf("A", "B", "D"),
            minFraction = 0.25,
        )

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

    fun testNwaySupermajority(contest : AuditContest, cvrs: List<Cvr>, winner: Int): Double {
        val assort = SuperMajorityAssorter(contest, winner, contest.minFraction!!)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        val cwinner = ComparisonAssorter(contest, assort, assortAvg, false)
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()

        println(" ($winner)= $cwinnerAvg")
        return cwinnerAvg
    }

    @Test
    fun testBvsV() {
        val thetas = listOf(.501, .5025, .505, .51, .52, .53, .54, .55, .575, .6, .65, .7)
        val ff = "%8.4f"
        println("  theta   margin  noerror marginB marginB/margin")
        for (theta in thetas) {
            val margin = theta2margin(theta)
            //         val noerror = 1.0 / (2.0 - margin)
            val noerror = 1.0/(2.0-margin) // assorter mean
            val marginB = theta2margin(noerror)
            println("${ff.format(theta)} ${ff.format(margin)} ${ff.format(noerror)} ${ff.format(marginB)} ${ff.format(marginB/margin)}")
        }
    }

    @Test
    fun testStandardComparisonAssorter() {
        val N = 1000
        val cvrMean = 0.55
        val cvrs = makeCvrsByExactMean(N, cvrMean)

        val contest = AuditContest("standard", 0, listOf("A", "B"), listOf("A"))
        val compareAudit = makeComparisonAudit(contests = listOf(contest), cvrs = cvrs)
        val compareAssertion = compareAudit.assertions[contest]!!.first()
        val compareAssorter1 = compareAssertion.assorter

        val compareAssorter2 = makeStandardComparisonAssorter(cvrMean)

        assertEquals(compareAssorter1, compareAssorter2)
    }
}