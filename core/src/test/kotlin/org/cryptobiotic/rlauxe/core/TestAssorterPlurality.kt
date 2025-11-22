package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.util.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactCount
import org.cryptobiotic.rlauxe.estimate.makeCvrsByExactMean
import org.cryptobiotic.rlauxe.util.*
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

// See SHANGRLA 2.1
class TestAssorterPlurality {

    @Test
    fun testBasics() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C"),
        )
        val cvr0 = makeCvr(0)
        val cvr1 = makeCvr(1)
        val cvr2 = makeCvr(2)
        val contest = makeContestFromCvrs(info, listOf(cvr0, cvr1, cvr2))

        val assorter = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val avalue0 = assorter.assort(cvr0)
        val avalue1 = assorter.assort(cvr1)
        val avalue2 = assorter.assort(cvr2)

        // A in {0, 1/2, 1}
        assertEquals(0.0, avalue1)
        assertEquals(0.5, avalue2)
        assertEquals(1.0, avalue0)
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

        val winner = PluralityAssorter.makeWithVotes(contest, winner = 0, loser = 1)
        val winnerAvg = cvrs.map { winner.assort(it) }.average()
        assertEquals(.55, winnerAvg)
        assertEquals(winner.reportedMean(), winnerAvg)

        val loser = PluralityAssorter.makeWithVotes(contest, winner = 1, loser = 0)
        val loserAvg = cvrs.map { loser.assort(it) }.average()
        assertEquals(.45, loserAvg)
        assertEquals(loser.reportedMean(), loserAvg)
    }

    @Test
    fun testThreeCandidatePluralityAssorter() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C"),
        )
        val cvr0 = makeCvr(0)
        val cvr1 = makeCvr(1)
        val cvr2 = makeCvr(2)
        val contest = makeContestFromCvrs(info, listOf(cvr0, cvr1, cvr2))

        val winner12 = PluralityAssorter.makeWithVotes(contest, winner = 1, loser = 2)
        assertEquals(0.5, winner12.assort(cvr0))
        assertEquals(1.0, winner12.assort(cvr1))
        assertEquals(0.0, winner12.assort(cvr2))

        val cvrs = listOf(cvr0, cvr1, cvr2)
        val winnerAvg = cvrs.map { winner12.assort(it) }.average()
        assertEquals(winner12.reportedMean(), winnerAvg, doublePrecision)
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
        val assort = PluralityAssorter.makeWithVotes(contest, winner, loser)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        assertEquals(assort.reportedMean(), assortAvg, doublePrecision)

        assertEquals((counts[winner] + counts[other]*.5)/counts.sum(), assortAvg)
        print(" ($winner, $loser, $other)= $assortAvg == ")
        return assortAvg
    }

    @Test
    fun testNCandidatePluralityAvg() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val ncandidates = counts.size
        val contest = makeContestFromCvrs(info, cvrs)

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
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
            nwinners = 2,
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val contest = makeContestFromCvrs(info, cvrs)

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
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "A", "B", "C", "D", "E"),
            nwinners = 2,
        )
        val counts = listOf(1000, 980, 3000, 50, 3001)
        val cvrs: List<Cvr> = makeCvrsByExactCount(counts)
        val contest = makeContestFromCvrs(info, cvrs)

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
        val assort = PluralityAssorter.makeWithVotes(contest, winner, loser)
        val assortAvg = cvrs.map { assort.assort(it) }.average()
        assertEquals(assort.reportedMean(), assortAvg, doublePrecision)

        val others = counts.mapIndexed { idx, it -> if (idx != winner && idx != loser) it else 0}.sum()
        assertEquals((counts[winner] + others *.5)/counts.sum(), assortAvg, "winner=$winner loser=$loser")
        println(" ($winner, $loser)= $assortAvg")
        return assortAvg
    }

    @Test
    fun testAssortValues() {
        val info = ContestInfo(
            name = "ABCs",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("Alice", "Bob", "Candy"),
            nwinners = 1,
        )
        val contest = Contest(info, mapOf(0 to 52, 1 to 44), Nc=100, Ncast=100, )
        val contestUA = ContestUnderAudit(contest, isClca = false).addStandardAssertions()
        val assorter = contestUA.pollingAssertions.first().assorter
        assertTrue(assorter is PluralityAssorter)
        assertEquals(0, assorter.winner())
        assertEquals(1, assorter.loser())

        assertEquals(1.0, assorter.assort(makeCvr(0)))
        assertEquals(0.0, assorter.assort(makeCvr(1)))
        assertEquals(0.5, assorter.assort(makeCvr(2)))
        assertEquals(0.5, assorter.assort(makeCvr(3)))

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

    @Test
    fun testAssortValuesNWinners() {
        val info = ContestInfo(
            name = "ABCs",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("Alice", "Bob", "Candy"),
            nwinners = 2,
        )
        val contest = Contest(info, mapOf(0 to 52, 1 to 44), Nc = 100, Ncast = 100)
        println(contest.show())

        val contestUA = ContestUnderAudit(contest, isClca = false).addStandardAssertions()
        contestUA.pollingAssertions.forEach { assertion ->
            val assorter = assertion.assorter
            println("  assorter = $assorter")
        }

        val assertion = contestUA.minPollingAssertion()!!
        assertTrue(assertion.winner == 1)
        assertTrue(assertion.loser == 2)
        val assorter = assertion.assorter

        assertEquals(0.5, assorter.assort(makeCvr(0)))
        assertEquals(1.0, assorter.assort(makeCvr(1)))
        assertEquals(0.0, assorter.assort(makeCvr(2)))
        assertEquals(0.5, assorter.assort(makeCvr(3)))

        assertEquals(1.0, assorter.assort(Cvr("id", mapOf(0 to intArrayOf(0,1)))))
        assertEquals(1.0, assorter.assort(Cvr("id", mapOf(0 to intArrayOf(1,0)))))
        assertEquals(1.0, assorter.assort(Cvr("id", mapOf(0 to intArrayOf(1,3)))))

        assertEquals(0.0, assorter.assort(Cvr("id", mapOf(0 to intArrayOf(0,2)))))
        assertEquals(0.0, assorter.assort(Cvr("id", mapOf(0 to intArrayOf(2,0)))))
        assertEquals(0.0, assorter.assort(Cvr("id", mapOf(0 to intArrayOf(2,3)))))

        // may be the problem. votes doesnt know when a cvr has both the winner and the loser on it
        assertEquals(0.5, assorter.assort(Cvr("id", mapOf(0 to intArrayOf(1,2)))))
        assertEquals(0.5, assorter.assort(Cvr("id", mapOf(0 to intArrayOf(0,1,2)))))
        assertEquals(0.5, assorter.assort(Cvr("id", mapOf(0 to intArrayOf(0,1,2,3)))))

    }
}