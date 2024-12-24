package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.workflow.makeContestUAFromCvrs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestCvrs {

    @Test
    fun testTabulateVotes() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap("A", "B", "C", "D", "E"),
            nwinners = 2,
            minFraction = .42
        )
        val cvrs = CvrBuilders()
            .addCvr().addContest("AvB").addCandidate("0", 0).ddone()
            .addCvr().addContest("AvB", "1").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            .addCvr().addContest("AvB", "2").ddone()
            // artifact of creating Contests and candidates from cvrs.
            .addCvr().addContest("AvB").addCandidate("3", 0).ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .addCvr().addContest("AvB", "4").ddone()
            .build()
        val contestUA = ContestUnderAudit(info, cvrs, true, true)
        assertEquals(2, contestUA.contest.winners.size)
        assertEquals(11, contestUA.Nc)
    }

    @Test
    fun testSampleMeans() {
        val N = 111
        val theta = .575
        val cvrs = makeCvrsByExactMean(N, theta)
        println(" N=${cvrs.size} theta=$theta withoutReplacement")

        val votes: Map<Int, Map<Int, Int>> = tabulateVotes(cvrs) // contest -> candidate -> count
        val contests: List<Contest> = makeContestsFromCvrs(votes, cardsPerContest(cvrs))

        val contestsUA = makeContestUAFromCvrs(contests, cvrs, true) // contest -> candidate -> count
        contestsUA.forEach { println(it) }
        assertEquals(1, contestsUA.size)
        val contestUA = contestsUA.first()
        assertEquals(1, contestUA.contest.winners.size)
        assertEquals(111, contestUA.Nc)
    }

    @Test
    fun detectWrongContest() {
        val N = 111
        val theta = .575
        val cvrs = makeCvrsByExactMean(N, theta)
        println(" N=${cvrs.size} theta=$theta withoutReplacement")

        val info = ContestInfo(
            name = "AvB",
            id = 22,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap("A", "B", "C", "D", "E"),
            minFraction = .42
        )

        val m = assertFailsWith<RuntimeException> {
            val contest = makeContestFromCvrs(info, cvrs)
            makeContestUAFromCvrs(listOf(contest), cvrs, true) // contest -> candidate -> count
        }.message

//        assertNotNull(m)
//        assertContains(m, "no contest for contest id=0")
    }

    @Test
    fun detectMissingWinner() {
        val N = 111
        val theta = .575
        val cvrs = makeCvrsByExactMean(N, theta)
        println(" N=${cvrs.size} theta=$theta withoutReplacement")

        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidateNames = listToMap("A", "B", "C", "D", "E"),
            minFraction = .42
        )
        val contest = makeContestFromCvrs(info, cvrs)

        //val m = assertFailsWith<RuntimeException> {
            makeContestUAFromCvrs(listOf(contest), cvrs, true) // contest -> candidate -> count
       // }.message!!
       // assertContains(m, "contest winner= 2 not found in cvrs")
    }

    @Test
    fun detectWrongWinner() {
        val N = 111
        val theta = .575
        val cvrs = makeCvrsByExactMean(N, theta)
        println(" N=${cvrs.size} theta=$theta withoutReplacement")

        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap("A", "B", "C", "D", "E"),
        )
        val contest = makeContestFromCvrs(info, cvrs)
        val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()
        val asrtns = contestUA.pollingAssertions
        val assort = asrtns.first().assorter

//        val m = assertFailsWith<RuntimeException> {
            makeContestUAFromCvrs(listOf(contest), cvrs) // contest -> candidate -> count
//        }.message!!
//        assertContains(m, "wrong contest winners= [1]")
    }

    @Test
    fun testMargin2mean() {
        //  contest1 margin=0.1573 wanted=571 sample=10823
        val margin = .1575
        assertEquals(.578, margin2mean(margin), .01)
    }

}