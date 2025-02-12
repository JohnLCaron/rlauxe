package org.cryptobiotic.rlauxe.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestContest {

    @Test
    fun testContestInfo() {
        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1), SocialChoiceFunction.PLURALITY)
        assertEquals(listOf(0, 1), info.candidateIds)
        assertEquals(1, info.nwinners)
        assertEquals("testContestInfo (0) candidates={cand0=0, cand1=1}", info.toString())

        val mess = assertFailsWith<IllegalArgumentException> {
            ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1), SocialChoiceFunction.SUPERMAJORITY)
        }.message
        assertEquals("SUPERMAJORITY requires minFraction", mess)

        val mess2 = assertFailsWith<IllegalArgumentException> {
            ContestInfo(
                "testContestInfo",
                0,
                mapOf("cand0" to 0, "cand1" to 1),
                SocialChoiceFunction.SUPERMAJORITY,
                minFraction = -.05
            )
        }.message
        assertEquals("minFraction between 0 and 1", mess2)

        val mess3 = assertFailsWith<IllegalArgumentException> {
            ContestInfo(
                "testContestInfo",
                0,
                mapOf("cand0" to 0, "cand1" to 1),
                SocialChoiceFunction.APPROVAL,
                minFraction = .35
            )
        }.message
        assertEquals("only SUPERMAJORITY can have minFraction", mess3)

        val mess4 = assertFailsWith<IllegalArgumentException> {
            ContestInfo(
                "testContestInfo",
                0,
                mapOf("cand0" to 0, "cand1" to 1),
                SocialChoiceFunction.APPROVAL,
                nwinners = 0
            )
        }.message
        assertEquals("nwinners between 1 and candidateNames.size", mess4)

        val mess5 = assertFailsWith<IllegalArgumentException> {
            ContestInfo(
                "testContestInfo",
                0,
                mapOf("cand0" to 0, "cand1" to 1),
                SocialChoiceFunction.APPROVAL,
                nwinners = 3
            )
        }.message
        assertEquals("nwinners between 1 and candidateNames.size", mess5)

        val mess6 = assertFailsWith<IllegalArgumentException> {
            ContestInfo(
                "testContestInfo",
                0,
                mapOf("cand0" to 0, "cand1" to 1, "canD1" to 2),
                SocialChoiceFunction.APPROVAL
            )
        }.message
        assertEquals("candidate names differ only by case: {cand0=0, cand1=1, canD1=2}", mess6)

        val mess7 = assertFailsWith<IllegalArgumentException> {
            ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1, "" to 2), SocialChoiceFunction.APPROVAL)
        }.message
        assertEquals("empty candidate name: {cand0=0, cand1=1, =2}", mess7)

        val mess8 = assertFailsWith<IllegalArgumentException> {
            ContestInfo(
                "testContestInfo",
                0,
                mapOf("cand0" to 0, "cand1" to 1, "cand2" to 1),
                SocialChoiceFunction.APPROVAL
            )
        }.message
        assertEquals("duplicate candidate id [0, 1, 1]", mess8)
    }

    @Test
    fun testContest() {
        val info = ContestInfo(
            "testContestInfo",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2),
            SocialChoiceFunction.PLURALITY
        )
        val contest = Contest(info, mapOf(0 to 100, 1 to 108), Nc=211, Np=0)
        assertEquals(info.id, contest.id)
        assertEquals(info.name, contest.name)
        assertEquals(info.choiceFunction, contest.choiceFunction)
        assertEquals(mapOf(0 to 100, 1 to 108, 2 to 0), contest.votes)
        assertEquals(211, contest.Nc)
        assertEquals(listOf(1), contest.winners)
        assertEquals(listOf(0, 2), contest.losers)
        assertEquals(listOf("cand1"), contest.winnerNames)
        assertEquals(
            "testContestInfo (0) Nc=211 Np=0 votes={0=100, 1=108, 2=0}",
            contest.toString()
        )

        val mess1 = assertFailsWith<IllegalArgumentException> {
            Contest(info, mapOf(0 to 100, 1 to 108, 3 to 2), Nc=222, Np=0)
        }.message
        assertEquals("'3' not found in contestInfo candidateIds [0, 1, 2]", mess1)

        val mess2 = assertFailsWith<IllegalArgumentException> {
            Contest(info, mapOf(0 to 100, 1 to 108), Nc=111, Np=0)
        }.message
        assertEquals("Nc 111 must be <= totalVotes 208", mess2)

        val contest2 = Contest(info, mapOf(0 to 100, 1 to 108), Nc=211, Np=0)
        assertEquals(contest.hashCode(), contest2.hashCode())
    }

    @Test
    fun testContestUnderAuditExceptions() {
        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2), SocialChoiceFunction.IRV)
        val contest = Contest(info, mapOf(0 to 100, 1 to 108), Nc=211, Np=0)

        val contestUAp = ContestUnderAudit(contest, isComparison = false)
        val mess1 = assertFailsWith<RuntimeException> {
            contestUAp.makePollingAssertions()
        }.message
        assertEquals("choice function IRV is not supported", mess1)

        val mess2 = assertFailsWith<RuntimeException> {
            contestUAp.makeClcaAssertions(emptyList())
        }.message
        assertEquals("makeComparisonAssertions() can be called only on comparison contest", mess2)

        val contestUAc = ContestUnderAudit(contest, isComparison = true)
        val mess3 = assertFailsWith<RuntimeException> {
            contestUAc.makePollingAssertions()
        }.message
        assertEquals("makePollingAssertions() can be called only on polling contest", mess3)

        val mess4 = assertFailsWith<RuntimeException> {
            contestUAc.makeClcaAssertions(emptyList())
        }.message
        assertEquals("choice function IRV is not supported", mess4)
    }
}