package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.makeCvr
import org.cryptobiotic.rlauxe.util.makeCvrsByExactCount
import org.cryptobiotic.rlauxe.util.makeCvrsByExactMean
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

// See SHANGRLA 2.1
class TestAssorterPlurality {

    @Test
    fun testBasics() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B", "C"),
            winnerNames = listOf("A"),
        )
        val assorter = PluralityAssorter(contest, winner = 0, loser = 1)
        val cvr0 = makeCvr(0)
        val cvr1 = makeCvr(1)
        val cvr2 = makeCvr(2)

        val avalue0 = assorter.assort(cvr0)
        val avalue1 = assorter.assort(cvr1)
        val avalue2 = assorter.assort(cvr2)
        assertEquals(1.0, avalue0)
        assertEquals(0.0, avalue1)
        assertEquals(0.5, avalue2)
    }

    @Test
    fun testTwoCandidatePlurality() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B"),
            winnerNames = listOf("A"),
        )
        val cvrs: List<Cvr> = makeCvrsByExactMean(ncards = 100, mean = .55)

        val winner = PluralityAssorter(contest, winner = 0, loser = 1)
        val winnerAvg = cvrs.map { winner.assort(it) }.average()
        assertEquals(.55, winnerAvg)

        val loser = PluralityAssorter(contest, winner = 1, loser = 0)
        val loserAvg = cvrs.map { loser.assort(it) }.average()
        assertEquals(.45, loserAvg)
    }

    @Test
    fun testThreeCandidatePluralityAssorter() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B", "C"),
            winnerNames = listOf("A"),
        )
        val cvr0 = makeCvr(0)
        val cvr1 = makeCvr(1)
        val cvr2 = makeCvr(2)
        val winner12 = PluralityAssorter(contest, winner = 1, loser = 2)
        assertEquals(0.5, winner12.assort(cvr0))
        assertEquals(1.0, winner12.assort(cvr1))
        assertEquals(0.0, winner12.assort(cvr2))
    }

    @Test
    fun testThreeCandidatePluralityAvg() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B", "C"),
            winnerNames = listOf("A"),
        )
        val counts = listOf(1000, 980, 100)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)

        test3way(contest, cvrs, counts, 0, 1, 2)
        test3way(contest, cvrs, counts, 0, 2, 1)
        test3way(contest, cvrs, counts, 1, 0, 2)
        test3way(contest, cvrs, counts, 1, 2, 0)
        test3way(contest, cvrs, counts, 2, 0, 1)
        test3way(contest, cvrs, counts, 2, 1, 0)

        //  (0, 1, 2)= 0.5048076923076923
        // (0, 2, 1)= 0.7163461538461539
        // (1, 0, 2)= 0.4951923076923077
        // (1, 2, 0)= 0.7115384615384616
        // (2, 0, 1)= 0.28365384615384615
        // (2, 1, 0)= 0.28846153846153844
        // rejectNull if all assertions are > 1/2

        assertEquals(test3way(contest, cvrs, counts, 0, 1, 2), testNway(contest, cvrs, counts, 0, 1))
        assertEquals(test3way(contest, cvrs, counts, 0, 2, 1), testNway(contest, cvrs, counts, 0, 2))
        assertEquals(test3way(contest, cvrs, counts, 1, 0, 2), testNway(contest, cvrs, counts, 1, 0))
        assertEquals(test3way(contest, cvrs, counts, 1, 2, 0), testNway(contest, cvrs, counts, 1, 2))
        assertEquals(test3way(contest, cvrs, counts, 2, 0, 1), testNway(contest, cvrs, counts, 2, 0))
        assertEquals(test3way(contest, cvrs, counts, 2, 1, 0), testNway(contest, cvrs, counts, 2, 1))

        repeat(3) { winner ->
            repeat(3) { loser ->
                if (winner != loser) testNway(contest, cvrs, counts, winner, loser)
            }
        }
    }

    fun test3way(contest : Contest, cvrs: List<Cvr>, counts: List<Int>, winner: Int, loser:Int, other: Int): Double {
        val assort = PluralityAssorter(contest, winner, loser)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        assertEquals((counts[winner] + counts[other]*.5)/counts.sum(), assortAvg)
        print(" ($winner, $loser, $other)= $assortAvg == ")
        return assortAvg
    }

    @Test
    fun testNCandidatePluralityAvg() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B", "C", "D", "E"),
            winnerNames = listOf("E"),
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val ncandidates = counts.size

        repeat(ncandidates) { winner ->
            var allPass = true
            repeat(ncandidates) { loser ->
                if (winner != loser) {
                    val avg = testNway(contest, cvrs, counts, winner, loser)
                    allPass = allPass && (avg > .5)
                }
            }
            assertEquals(contest.winners.contains(winner), allPass)
        }

        //  (0, 1)= 0.5012451749470801
        // (0, 2)= 0.3754825052919935
        // (0, 3)= 0.559145809986303
        // (0, 4)= 0.3754202465446395
        // (1, 0)= 0.49875482505291996
        // (1, 2)= 0.37423733034491347
        // (1, 3)= 0.557900635039223
        // (1, 4)= 0.37417507159755947
        // (2, 0)= 0.6245174947080064
        // (2, 1)= 0.6257626696550865
        // (2, 3)= 0.6836633046943096
        // (2, 4)= 0.499937741252646
        // (3, 0)= 0.4408541900136969
        // (3, 1)= 0.442099364960777
        // (3, 2)= 0.3163366953056905
        // (3, 4)= 0.3162744365583364
        // (4, 0)= 0.6245797534553604
        // (4, 1)= 0.6258249284024405
        // (4, 2)= 0.500062258747354
        // (4, 3)= 0.6837255634416636
    }

    @Test
    fun testMultipleWinners() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B", "C", "D", "E"),
            winnerNames = listOf("C", "E"),
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)

        // this is the test one makes in production
        // there are nwinners * (ncandidates - nwinners) assertions = 2 x 3 = 6
        for (winner in contest.winners) {
            var allPass = true
            for (loser in contest.losers) {
                val avg = testNway(contest, cvrs, counts, winner, loser)
                allPass = allPass && (avg > .5)
            }
            if (contest.winners.contains(winner)) assertTrue(allPass)
        }
        // (2, 0)= 0.6245174947080064
        // (2, 1)= 0.6257626696550865
        // (2, 3)= 0.6836633046943096
        // (4, 0)= 0.6245797534553604
        // (4, 1)= 0.6258249284024405
        // (4, 3)= 0.6837255634416636
    }

    @Test
    fun testNoMissingMultipleWinners() {
        val contest = Contest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listOf( "A", "B", "C", "D", "E"),
            winnerNames = listOf("C", "E"),
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)

        val ncandidates = counts.size
        repeat(ncandidates) { cand1 ->
            var allPass = true
            repeat(ncandidates) { cand2 ->
                if (cand1 != cand2 && (contest.losers.contains(cand1) || contest.losers.contains(cand2))) {
                    val avg = testNway(contest, cvrs, counts, cand1, cand2)
                    allPass = allPass && (avg > .5)
                }
            }
            assertEquals(contest.winners.contains(cand1), allPass, "winner=$cand1")
        }
        // (0, 1)= 0.5012451749470801
        // (0, 2)= 0.3754825052919935
        // (0, 3)= 0.559145809986303
        // (0, 4)= 0.3754202465446395
        // (1, 0)= 0.49875482505291996
        // (1, 2)= 0.37423733034491347
        // (1, 3)= 0.557900635039223
        // (1, 4)= 0.37417507159755947
        // (2, 0)= 0.6245174947080064
        // (2, 1)= 0.6257626696550865
        // (2, 3)= 0.6836633046943096
        // (3, 0)= 0.4408541900136969
        // (3, 1)= 0.442099364960777
        // (3, 2)= 0.3163366953056905
        // (3, 4)= 0.3162744365583364
        // (4, 0)= 0.6245797534553604
        // (4, 1)= 0.6258249284024405
        // (4, 3)= 0.6837255634416636
    }

    fun testNway(contest : Contest, cvrs: List<Cvr>, counts: List<Int>, winner: Int, loser:Int): Double {
        val assort = PluralityAssorter(contest, winner, loser)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        val others = counts.mapIndexed { idx, it -> if (idx != winner && idx != loser) it else 0}.sum()
        if ((counts[winner] + others *.5)/counts.sum() != assortAvg) {
            println()
        }
        assertEquals((counts[winner] + others *.5)/counts.sum(), assortAvg, "winner=$winner loser=$loser")
        println(" ($winner, $loser)= $assortAvg")
        return assortAvg
    }
}