package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactCount
import org.cryptobiotic.rlauxe.util.margin2mean
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

// See SHANGRLA 2.3
class TestAssorterSuperMajority {

    @Test
    fun testBasics() {
        val info = ContestInfo(
            name = "ABC",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap( "A", "B", "C"),
            minFraction = 0.60,
        )
        val cvr0 = makeCvr(0)
        val cvr1 = makeCvr(1)
        val cvr2 = makeCvr(2)
        val contest = makeContestFromCvrs(info, listOf(cvr0, cvr1, cvr2))

        val minFraction = contest.info.minFraction!!
        val superAssorter = SuperMajorityAssorter.makeWithVotes(contest, winner = 1, minFraction)
        assertEquals(1.0 / (2 * superAssorter.minFraction), superAssorter.upperBound())
        println("minFraction = $minFraction upperBound=${superAssorter.upperBound()}")

        assertEquals(0.0, superAssorter.assort(cvr0)) // bi has a mark for exactly one candidate and not Alice
        assertEquals(superAssorter.upperBound(), superAssorter.assort(cvr1)) // // bi has a mark for Alice and no one else
        assertEquals(0.0, superAssorter.assort(cvr2)) // // bi has a mark for exactly one candidate and not Alice

        val votes = mutableMapOf<Int, IntArray>()
        votes[0] = intArrayOf(0, 2)
        val cvr02 = Cvr("card", votes)
        assertEquals(0.5, superAssorter.assort(cvr02)) // otherwise

        // A in {0, 1/2, u}

    }

    @Test
    fun testThreeCandidateNoMajority() {
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
            val assortAvg = testNway(contest, cvrs, counts, winner)
            assertTrue(assortAvg < .5)
        }
        // (0)= 0.40064102564103043
        // (1)= 0.39262820512820956
        // (2)= 0.04006410256410255
    }

    // @Test not allowed
    fun testThreeCandidateMultipleWinners() {
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
            val assortAvg = testNway(contest, cvrs, counts, winner)
            assertEquals(assortAvg > .5, contest.winners.contains(winner))
        }
        //  (0)= 0.6868131868131773
        // (1)= 0.6730769230769145
        // (2)= 0.06868131868131867
    }

    // @Test not allowed
    fun testNCandidateSuperMajority() {
        val counts = listOf(1600, 1300, 500, 1500, 50, 12, 1)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val ncandidates = counts.size

        val info = ContestInfo(
            name = "ABCs",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E", "F", "G"),
            nwinners = 3,
            minFraction = 0.25,
        )
        val contest = makeContestFromCvrs(info, cvrs)

        repeat(ncandidates) { winner ->
            val assortAvg = testNway(contest, cvrs, counts, winner)
            assertEquals(assortAvg > .5, contest.winners.contains(winner))
        }

        //  (0)= 0.6447713076768083
        // (1)= 0.5238766874874068
        // (2)= 0.20149103364900262
        // (3)= 0.6044731009470079
        // (4)= 0.02014910336490026
        // (5)= 0.0048357848075760625
        // (6)= 4.0298206729800525E-4
    }

    fun testNway(contest: Contest, cvrs: List<Cvr>, counts: List<Int>, winner: Int): Double {
        val assort = SuperMajorityAssorter.makeWithVotes(contest, winner, contest.info.minFraction!!)
        assertEquals(1.0 / (2 * assort.minFraction), assort.upperBound())
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        assertEquals(margin2mean(assort.reportedMargin), assortAvg, doublePrecision)

        val n = counts.sum().toDouble()
        val p = counts[winner] / n
        val q = counts.sum() / n
        // pq/(2f ) + (1 − q)/2
        val avg = p * q / (2 * contest.info.minFraction!!) + (1.0 - q) / 2.0
        assertEquals(avg, assortAvg, doublePrecision)
        println(" ($winner)= $assortAvg")
        return assortAvg
    }

    @Test
    fun testAssortValues() {
        val f = 0.60
        val info = ContestInfo(
            name = "ABC",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap( "A", "B", "C"),
            minFraction = f,
            nwinners = 1,
        )
        val contest = Contest(info, mapOf(1 to 66, 2 to 33), iNc=100, Np=0)
        val contestUA = ContestUnderAudit(contest, isComparison = false)
        val assorter = contestUA.pollingAssertions.first().assorter
        assertTrue(assorter is SuperMajorityAssorter)
        assertEquals(1, assorter.winner())
        assertEquals(-1, assorter.loser())

        val minFraction = contest.info.minFraction!!
        assertEquals(1.0 / (2 * minFraction), assorter.upperBound())
        assertEquals(0.0, assorter.assort(makeCvr(0))) // bi has a mark for exactly one candidate and not Alice
        assertEquals(0.5 / minFraction, assorter.assort(makeCvr(1))) // // bi has a mark for Alice and no one else
        assertEquals(0.0, assorter.assort(makeCvr(2))) // // bi has a mark for exactly one candidate and not Alice

        // undervote
        assertEquals(0.5, assorter.assort(Cvr("id", mapOf(0 to IntArray(0)), phantom = false), usePhantoms = false))
        assertEquals(0.5, assorter.assort(Cvr("id", mapOf(0 to IntArray(0)), phantom = false), usePhantoms = true))
        // phantom
        assertEquals(0.5, assorter.assort(Cvr("id", mapOf(0 to IntArray(0)), phantom = true), usePhantoms = false))
        assertEquals(0.0, assorter.assort(Cvr("id", mapOf(0 to IntArray(0)), phantom = true), usePhantoms = true))

        // contest not on cvr
        assertEquals(0.5, assorter.assort(Cvr("id", mapOf(1 to IntArray(0)), phantom = false), usePhantoms = false))
        assertEquals(0.5, assorter.assort(Cvr("id", mapOf(1 to IntArray(0)), phantom = false), usePhantoms = true))
        assertEquals(0.5, assorter.assort(Cvr("id", mapOf(1 to IntArray(0)), phantom = true), usePhantoms = false))
        assertEquals(0.5, assorter.assort(Cvr("id", mapOf(1 to IntArray(0)), phantom = true), usePhantoms = true))
    }

}