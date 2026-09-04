package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.betting.ClcaErrorRates
import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactCount
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val Npop = 1_000_000
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
        assertEquals(20/2000.toDouble(), assorter.margin(true), doublePrecision)

        val awinnerAvg = .505
        val margin = 2.0 * awinnerAvg - 1.0 // reported assorter margin
        assertEquals(.01, assorter.margin(true), doublePrecision)

        assertEquals(1.0, assorter.assort(winnerCvr)) // voted for the winner
        assertEquals(0.0, assorter.assort(loserCvr))  // voted for the loser
        assertEquals(0.5, assorter.assort(otherCvr))  // voted for someone else
        // so assort in {0, .5, 1}

        ////////////////////////////////////////////////////////////////////////////////////////////////

        val cassorter = ClcaAssorter(info, assorter, true)
        assertEquals(.01, mean2margin(awinnerAvg), doublePrecision)
        assertEquals(margin, cassorter.assorterMargin, doublePrecision)
        assertEquals(0.0, cassorter.overstatementError(winnerCvr, winnerCvr))
        assertEquals(-1.0, cassorter.overstatementError(winnerCvr, loserCvr))
        assertEquals(-0.5, cassorter.overstatementError(winnerCvr, otherCvr))

        assertEquals(1.0, cassorter.overstatementError(loserCvr, winnerCvr))
        assertEquals(0.0, cassorter.overstatementError(loserCvr, loserCvr))
        assertEquals(0.5, cassorter.overstatementError(loserCvr, otherCvr))

        assertEquals(0.5, cassorter.overstatementError(otherCvr, winnerCvr))
        assertEquals(-0.5, cassorter.overstatementError(otherCvr, loserCvr))
        assertEquals(0.0, cassorter.overstatementError(otherCvr, otherCvr))
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

        println(" sampleSize = ${cassorter.sampleSizeNoErrors(Npop, 2 * 0.9, .05)} for alpha = .05")
        println(" sampleSize = ${cassorter.sampleSizeNoErrors(Npop, 2 * 0.9, .025)} for alpha = .025")
        println(" sampleSize = ${cassorter.sampleSizeNoErrors(Npop, 2 * 0.9, .1)} for alpha = .1")

        val estSize = cassorter.sampleSizeWithErrors(Npop, 2 * 0.9, .05, ClcaErrorRates.empty(noerror, 1.0));
        println(" estSize = $estSize")
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
        val cwinner = ClcaAssorter(info, awinner, true)
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()
        println("cwinnerAvg=$cwinnerAvg <= awinnerAvg=$awinnerAvg")
        assertTrue(cwinnerAvg <= awinnerAvg)
        assertTrue(cwinnerAvg > 0.5)

        val aloser = PluralityAssorter.makeWithVotes(contest, winner = 1, loser = 0)
        val aloserAvg = cvrs.map { aloser.assort(it) }.average()
        val closer = ClcaAssorter(info, aloser, true, check=false)
        assertEquals(mean2margin(aloserAvg), closer.assorterMargin, doublePrecision)
        assertEquals(aloserAvg, margin2mean(closer.assorterMargin))

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
        val cwinner = ClcaAssorter(contest.info, assort, true, check=false)
        val cwinnerAvg = cvrs.map { cwinner.bassort(it, it) }.average()
        assertEquals(assortAvg, margin2mean(cwinner.assorterMargin), doublePrecision)

        println(" ($winner, $loser)= $cwinnerAvg")
        return cwinnerAvg
    }

    //////////////////////////////////////////////////////////////////////////////////

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
        val winnerCvr = makeCvr(0, name="win")
        val loserCvr = makeCvr(1, name="los")
        val otherCvr = makeCvr(2, name="oth")
        val phantomCvr = Cvr("phantom", mapOf(0 to IntArray(0)), phantom = true)

        val votes = mapOf(0 to 1000, 1 to 990) // Map<Int, Int>
        val contest =  Contest(info, votes, 2000, Ncast=1990)

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        assertEquals(10/2000.toDouble(), assorter.margin(true), doublePrecision)

        val awinnerAvg = assorter.dilutedMean()
        assertEquals(awinnerAvg, assorter.dilutedMean(), doublePrecision)
        val margin = 2.0 * awinnerAvg - 1.0 // reported assorter margin
        assertEquals(assorter.margin(true), margin, doublePrecision)

        assertEquals(1.0, assorter.assort(winnerCvr)) // voted for the winner
        assertEquals(0.0, assorter.assort(loserCvr))  // voted for the loser
        assertEquals(0.5, assorter.assort(otherCvr))  // voted for someone else
        assertEquals(0.5, assorter.assort(phantomCvr, false))  // ignore cvr is a phantom
        assertEquals(0.0, assorter.assort(phantomCvr, true))  // cvr is a phantom
        // so assort in {0, .5, 1}

        val cassorter = ClcaAssorter(info, assorter, true)
        val cassorterNo = ClcaAssorter(info, assorter, false)
        val noerror = cassorter.noerror()
        assertEquals(margin, cassorter.assorterMargin, doublePrecision)
        assertEquals(awinnerAvg, margin2mean(cassorter.assorterMargin))

        assertEquals(0.0, cassorter.overstatementError(winnerCvr, winnerCvr))
        assertEquals(-1.0, cassorter.overstatementError(winnerCvr, loserCvr))
        assertEquals(-0.5, cassorter.overstatementError(winnerCvr, otherCvr))
        assertEquals(-0.5, cassorter.overstatementError(winnerCvr, phantomCvr))

        assertEquals(1.0, cassorter.overstatementError(loserCvr, winnerCvr))
        assertEquals(0.0, cassorter.overstatementError(loserCvr, loserCvr))
        assertEquals(0.5, cassorter.overstatementError(loserCvr, otherCvr))
        assertEquals(0.5, cassorter.overstatementError(loserCvr, phantomCvr))

        assertEquals(0.5, cassorter.overstatementError(otherCvr, winnerCvr))
        assertEquals(-0.5, cassorter.overstatementError(otherCvr, loserCvr))
        assertEquals(0.0, cassorter.overstatementError(otherCvr, otherCvr))

        assertEquals(1.0, cassorter.overstatementError(phantomCvr, winnerCvr)) // check
        assertEquals(0.0, cassorter.overstatementError(phantomCvr, loserCvr)) // check
        assertEquals(0.5, cassorter.overstatementError(phantomCvr, phantomCvr)) // check, usual case
        // so overstatementError in [-1, -.5, 0, .5, 1]

        val cvrs = listOf(winnerCvr, loserCvr, otherCvr, phantomCvr)
        for (mvr in cvrs) {
            for (cvr in cvrs) {
                println("cvr-mvr overstatement ${cvr.id}-${mvr.id} = ${cassorter.overstatementError(mvr, cvr)} " +
                        "bassort=${cassorter.bassort(mvr, cvr)/noerror}")
            }
        }

        // TODO hasStyle parameter doesnt matter unless mvr doesnt have the contest. See testHasStyles below.
        for (mvr in cvrs) {
            for (cvr in cvrs) {
                assertEquals(cassorterNo.overstatementError(mvr, cvr), cassorter.overstatementError(mvr, cvr))
            }
        }

        assertEquals(1.0 / (2.0 - margin), noerror, doublePrecision)
        assertEquals(1.0 / (3 - 2 * awinnerAvg), noerror, doublePrecision)
        println("noerror = $noerror")

        //     open fun bassort(mvr: CvrIF, cvr:CvrIF): Double { // }, hasStyle:Boolean? = null): Double {

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
        assertEquals(noerror, cassorter.bassort(phantomCvr, loserCvr))        // no mvr, cvr reported loser: nuetral
        assertEquals(0.5*noerror, cassorter.bassort(phantomCvr, phantomCvr))  // no mvr, no cvr: oneOver (common case i assume)
        assertEquals(0.5*noerror, cassorter.bassort(phantomCvr, otherCvr))    // no mvr, other cvr: oneOver (common case i assume)
        assertEquals(1.5*noerror, cassorter.bassort(winnerCvr, phantomCvr))   // mvr reported winner, no cvr: oneUnder
        assertEquals(.5*noerror, cassorter.bassort(loserCvr, phantomCvr))     // mvr reported loser, no cvr: oneOver
        assertEquals(1.0*noerror, cassorter.bassort(otherCvr, phantomCvr))    // mvr reported other, no cvr: oneOver
    }

    // SHANGRLA testAsertion.py test_overstatement_assorter()
    @Test
    fun testShangrlaOverstatementAssorter() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C"),
        )
        val winner = makeCvr(0)
        val loser = makeCvr(1)
        val other = makeCvr(2)
        val diffcontest = Cvr("diff", mapOf(1 to IntArray(0)))
        val phantom = Cvr("phantom", mapOf(0 to IntArray(0)), phantom = true)

        val cvrs = listOf(winner, winner, loser, other, phantom, diffcontest)
        val contest = makeContestFromCvrs(info, cvrs)

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val cassorterHasStyle = ClcaAssorter(info, assorter, true)
        val cassorterNoStyle = ClcaAssorter(info, assorter, false)

        //         winner = ["Alice"]
        //         loser = ["Bob"]
        //         mvr_dict = [{'id': 1, 'votes': {'AvB': {'Alice':True}}},     0 winner
        //                    {'id': 2, 'votes': {'AvB': {'Bob':True}}},        1 loser
        //                    {'id': 3, 'votes': {'AvB': {}}},                  2 other
        //                    {'id': 4, 'votes': {'CvD': {'Elvis':True, 'Candy':False}}},   // 3 diffcontest
        //                    {'id': 'phantom_1', 'votes': {'AvB': {}}, 'phantom': True}]   // 4 phantom
        //        mvrs = CVR.from_dict(mvr_dict)
        //
        //        cvr_dict = [{'id': 1, 'votes': {'AvB': {'Alice':True}}},
        //                    {'id': 2, 'votes': {'AvB': {'Bob':True}}},
        //                    {'id': 3, 'votes': {'AvB': {}}},
        //                    {'id': 4, 'votes': {'CvD': {'Elvis':True}}},
        //                    {'id': 'phantom_1', 'votes': {'AvB': {}}, 'phantom': True}]
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[0], use_style=True) == 0    winner, winner
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[0], use_style=False) == 0   winner, winner
        assertEquals(0.0, cassorterHasStyle.overstatementError(winner, winner))
        assertEquals(0.0, cassorterNoStyle.overstatementError(winner, winner))

        //
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[1], use_style=True) == -1   winner, loser
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[1], use_style=False) == -1  winner, loser
        assertEquals(-1.0, cassorterHasStyle.overstatementError(winner, loser))
        assertEquals(-1.0, cassorterNoStyle.overstatementError(winner, loser))

        //
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=True) == 1/2  other, winner
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=False) == 1/2 other, winner
        assertEquals(0.5, cassorterHasStyle.overstatementError(other, winner))
        assertEquals(0.5, cassorterNoStyle.overstatementError(other, winner))

        //
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[1], use_style=True) == -1/2   other, loser
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[1], use_style=False) == -1/2  other, loser
        assertEquals(-0.5, cassorterHasStyle.overstatementError(other, loser))
        assertEquals(-0.5, cassorterNoStyle.overstatementError(other, loser))

        //
        //        assert aVb.assorter.overstatement(mvrs[1], cvrs[0], use_style=True) == 1      loser, winner
        //        assert aVb.assorter.overstatement(mvrs[1], cvrs[0], use_style=False) == 1       loser, winner
        assertEquals(1.0, cassorterHasStyle.overstatementError(loser, winner))
        assertEquals(1.0, cassorterNoStyle.overstatementError(loser, winner))

        //
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=True) == 1/2      other, winner
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=False) == 1/2      other, winner
        assertEquals(0.5, cassorterHasStyle.overstatementError(other, winner))
        assertEquals(0.5, cassorterNoStyle.overstatementError(other, winner))

        //
        //        assert aVb.assorter.overstatement(mvrs[3], cvrs[0], use_style=True) == 1          diffcontest, winner
        //        assert aVb.assorter.overstatement(mvrs[3], cvrs[0], use_style=False) == 1/2       diffcontest, winner
        assertEquals(1.0, cassorterHasStyle.overstatementError(diffcontest, winner))
        assertEquals(0.5, cassorterNoStyle.overstatementError(diffcontest, winner))

        //            tst = aVb.assorter.overstatement(mvrs[3], cvrs[3], use_style=True)        diffcontest, diffcontest
        //            raise AssertionError('aVb is not contained in the mvr or cvr')
        // assertThrows<RuntimeException> {
        assertTrue(cassorterHasStyle.overstatementError(diffcontest, diffcontest).isNaN())

        //         assert aVb.assorter.overstatement(mvrs[3], cvrs[3], use_style=False) == 0    diffcontest
        assertEquals(0.0, cassorterNoStyle.overstatementError(diffcontest, diffcontest))

        //
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style=True) == 1/2    phantom, phantom
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style=False) == 1/2   phantom, phantom
        assertEquals(0.5, cassorterHasStyle.overstatementError(phantom, phantom))
        assertEquals(0.5, cassorterNoStyle.overstatementError(phantom, phantom))

        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[2], use_style=True) == 1/2
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[2], use_style=False) == 1/2
        assertEquals(0.5, cassorterHasStyle.overstatementError(phantom, other))
        assertEquals(0.5, cassorterNoStyle.overstatementError(phantom, other))

        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[0], use_style=True) == 1     phantom, winner
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[0], use_style=False) == 1    phantom, winner
        assertEquals(1.0, cassorterHasStyle.overstatementError(phantom, winner))
        assertEquals(1.0, cassorterNoStyle.overstatementError(phantom, winner))

        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[1], use_style=True) == 0      phantom, loser
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[1], use_style=False) == 0     phantom, loser
        assertEquals(0.0, cassorterHasStyle.overstatementError(phantom, loser))
        assertEquals(0.0, cassorterNoStyle.overstatementError(phantom, loser))

        //          try:
        //            assert aVb.assorter.overstatement(mvrs[4], cvrs[3], use_style=True)
        //            raise AssertionError('aVb is not contained in the cvr')
        //        except ValueError:
        //            pass
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[3], use_style=False) == 1/2
        assertTrue(cassorterHasStyle.overstatementError(phantom, diffcontest).isNaN())
        assertEquals(0.5, cassorterNoStyle.overstatementError(phantom, diffcontest))

        val noerror = cassorterHasStyle.noerror()
        println("  noerror = $noerror")

        assertEquals(-0.5, cassorterNoStyle.overstatementError(winner, diffcontest))
        assertTrue(cassorterHasStyle.overstatementError(winner, diffcontest).isNaN())

        assertEquals(1.0, cassorterHasStyle.overstatementError(diffcontest, winner))
        assertEquals(0.0, cassorterHasStyle.overstatementError(diffcontest, loser))
        assertEquals(0.5, cassorterHasStyle.overstatementError(diffcontest, other))

        assertEquals(0.0 * noerror, cassorterHasStyle.bassort(diffcontest, winner))
        assertEquals(1.0 * noerror, cassorterHasStyle.bassort(diffcontest, loser))
        assertEquals(0.5 * noerror, cassorterHasStyle.bassort(diffcontest, other))

        // hasStyle = false
        assertEquals(0.5, cassorterNoStyle.overstatementError(diffcontest, winner))
        assertEquals(-0.5, cassorterNoStyle.overstatementError(diffcontest, loser))
        assertEquals(0.0, cassorterNoStyle.overstatementError(diffcontest, other))

        assertEquals(0.5 * noerror, cassorterNoStyle.bassort(diffcontest, winner))
        assertEquals(1.5 * noerror, cassorterNoStyle.bassort(diffcontest, loser))
        assertEquals(1.0 * noerror, cassorterNoStyle.bassort(diffcontest, other))
    }

    @Test
    fun testNoError() {
        val N = 1000
        val cvrMean = 0.55

        val info = ContestInfo("standard", 0, listToMap("A", "B"), choiceFunction = SocialChoiceFunction.PLURALITY)
        val cvrs = makeCvrsByExactMean(N, cvrMean)
        val contestUA = makeContestUAfromCvrs(info, cvrs, NpopIn=cvrs.size+2)
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