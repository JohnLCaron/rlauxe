package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.util.makeCvr
import org.cryptobiotic.rlauxe.util.makeCvrsByExactCount
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

// See SHANGRLA 2.3
class TestAssorterSuperMajority {

    @Test
    fun testThreeCandidateSuperMajorityAssorter() {
        val contest = Contest(
            id = "ABC",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listOf( "A", "B", "C"),
            winnerNames = listOf("A"),
            minFraction = 0.60,
        )
        val cvr0 = makeCvr(0)
        val cvr1 = makeCvr(1)
        val cvr2 = makeCvr(2)
        val minFraction = contest.minFraction!!
        val winner12 = SuperMajorityAssorter(contest, winner = 1, minFraction)
        assertEquals(1.0 / (2 * winner12.minFraction), winner12.upperBound)
        assertEquals(0.0, winner12.assort(cvr0)) // bi has a mark for exactly one candidate and not Alice
        assertEquals(0.5 / minFraction, winner12.assort(cvr1)) // // bi has a mark for Alice and no one else
        assertEquals(0.0, winner12.assort(cvr2)) // // bi has a mark for exactly one candidate and not Alice

        val votes = mutableMapOf<Int, Map<Int, Int>>()
        votes[0] = mapOf(0 to 1, 2 to 1)
        val cvr02 = Cvr("card", votes)
        assertEquals(0.5, winner12.assort(cvr02)) // otherwise
    }

    @Test
    fun testThreeCandidateNoMajority() {
        val contest = Contest(
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
            val assortAvg = testNway(contest, cvrs, counts, winner)
            assertTrue(assortAvg < .5)
        }
        // (0)= 0.40064102564103043
        // (1)= 0.39262820512820956
        // (2)= 0.04006410256410255
    }

    @Test
    fun testThreeCandidateMultipleWinners() {
        val contest = Contest(
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
            val assortAvg = testNway(contest, cvrs, counts, winner)
            assertEquals(assortAvg > .5, contest.winners.contains(winner))
        }
        //  (0)= 0.6868131868131773
        // (1)= 0.6730769230769145
        // (2)= 0.06868131868131867
    }

    @Test
    fun testNCandidateSuperMajority() {
        val counts = listOf(1600, 1300, 500, 1500, 50, 12, 1)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val ncandidates = counts.size

        val contest = Contest(
            id = "ABCs",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listOf( "A", "B", "C", "D", "E", "F", "G"),
            winnerNames = listOf("A", "B", "D"),
            minFraction = 0.25,
        )

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
        val assort = SuperMajorityAssorter(contest, winner, contest.minFraction!!)
        assertEquals(1.0 / (2 * assort.minFraction), assort.upperBound)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        val n = counts.sum().toDouble()
        val p = counts[winner] / n
        val q = counts.sum() / n
        // pq/(2f ) + (1 − q)/2
        val avg = p * q / (2 * contest.minFraction!!) + (1.0 - q) / 2.0
        assertEquals(avg, assortAvg, doublePrecision)
        println(" ($winner)= $assortAvg")
        return assortAvg
    }

}