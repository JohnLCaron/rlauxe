package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.sampling.tabulateVotes
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TestTabulateVotes {

    @Test
    fun testTabulateVotes() {
        val contest = Contest(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap("A", "B", "C", "D", "E"),
            winnerNames = listOf("C", "E"),
            minFraction = .42
        )
        val cvrs = CvrBuilders()
            .addCrv().addContest("AvB").addCandidate("0", 0).ddone()
            .addCrv().addContest("AvB", "1").ddone()
            .addCrv().addContest("AvB", "2").ddone()
            .addCrv().addContest("AvB", "2").ddone()
            .addCrv().addContest("AvB", "2").ddone()
            .addCrv().addContest("AvB", "2").ddone()
            // artifact of creating Contests and candidates from cvrs.
            .addCrv().addContest("AvB").addCandidate("3", 0).ddone()
            .addCrv().addContest("AvB", "4").ddone()
            .addCrv().addContest("AvB", "4").ddone()
            .addCrv().addContest("AvB", "4").ddone()
            .addCrv().addContest("AvB", "4").ddone()
            .build()
        val cvras = tabulateVotes(listOf(contest), cvrs)
        cvras.forEach { println(it) }
        assertEquals(1, cvras.size)
        val cvra = cvras.first()
        assertEquals(11, cvra.ncvrs)
        assertEquals(2, cvra.contest.winners.size)
        assertNull(cvra.upperBound)
    }

    @Test
    fun testSampleMeans() {
        val N = 111
        val theta = .575
        val cvrs = makeCvrsByExactMean(N, theta)
        println(" N=${cvrs.size} theta=$theta withoutReplacement")

        val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs) // contest -> candidate -> count
        val contests: List<Contest> = makeContestsFromCvrs(votes, cardsPerContest(cvrs))

        val cvras = tabulateVotes(contests, cvrs) // contest -> candidate -> count
        cvras.forEach { println(it) }
        assertEquals(1, cvras.size)
        val cvra = cvras.first()
        assertEquals(111, cvra.ncvrs)
        assertEquals(1, cvra.contest.winners.size)
        assertNull(cvra.upperBound)
    }

    @Test
    fun detectWrongContest() {
        val N = 111
        val theta = .575
        val cvrs = makeCvrsByExactMean(N, theta)
        println(" N=${cvrs.size} theta=$theta withoutReplacement")

        val contest = Contest(
            name = "AvB",
            id = 22,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap("A", "B", "C", "D", "E"),
            winnerNames = listOf("C", "E"),
            minFraction = .42
        )

        val m = assertFailsWith<RuntimeException> {
            tabulateVotes(listOf(contest), cvrs) // contest -> candidate -> count
        }.message

        assertNotNull(m)
        assertContains(m, "no contest for contest id= 0")
    }

    @Test
    fun detectMissingWinner() {
        val N = 111
        val theta = .575
        val cvrs = makeCvrsByExactMean(N, theta)
        println(" N=${cvrs.size} theta=$theta withoutReplacement")

        val contest = Contest(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap("A", "B", "C", "D", "E"),
            winnerNames = listOf("C", "E"),
            minFraction = .42
        )

        val m = assertFailsWith<RuntimeException> {
            tabulateVotes(listOf(contest), cvrs) // contest -> candidate -> count
            println("tf")
        }.message!!
        assertContains(m, "contest winner= 2 not found in cvrs")
    }

    @Test
    fun detectWrongWinner() {
        val N = 111
        val theta = .575
        val cvrs = makeCvrsByExactMean(N, theta)
        println(" N=${cvrs.size} theta=$theta withoutReplacement")

        val contest = Contest(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C", "D", "E"),
            winnerNames = listOf("B"),
        )

        val m = assertFailsWith<RuntimeException> {
            tabulateVotes(listOf(contest), cvrs) // contest -> candidate -> count
            println("tf")
        }.message!!
        assertContains(m, "wrong contest winners= [1]")
    }

}