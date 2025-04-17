package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith

class TestContest {

    @Test
    fun testContestInfo() {
        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1), SocialChoiceFunction.PLURALITY)
        assertEquals(listOf(0, 1), info.candidateIds)
        assertEquals(1, info.nwinners)
        assertEquals("'testContestInfo' (0) candidates={cand0=0, cand1=1}", info.toString())

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
    fun testContestBasics() {
        val info = ContestInfo(
            "testContestInfo",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2),
            SocialChoiceFunction.PLURALITY
        )
        val contest = Contest(info, mapOf(0 to 100, 1 to 108), Nc=211, Np=1)
        assertEquals(info.id, contest.id)
        assertEquals(info.name, contest.name)
        assertEquals(info.choiceFunction, contest.choiceFunction)
        assertEquals(mapOf(0 to 100, 1 to 108, 2 to 0), contest.votes)
        assertEquals(211, contest.Nc)
        assertEquals(listOf(1), contest.winners)
        assertEquals(listOf(0, 2), contest.losers)
        assertEquals(listOf("cand1"), contest.winnerNames)
        assertEquals(
            "testContestInfo (0) Nc=211 Np=1 votes={1=108, 0=100, 2=0}",
            contest.toString()
        )

        val mess1 = assertFailsWith<IllegalArgumentException> {
            Contest(info, mapOf(0 to 100, 1 to 108, 3 to 2), Nc=222, Np=0)
        }.message
        assertEquals("'3' not found in contestInfo candidateIds [0, 1, 2]", mess1)

        // TODO
/*        val mess2 = assertFailsWith<IllegalArgumentException> {
            Contest(info, mapOf(0 to 100, 1 to 108), Nc=111, Np=0)
        }.message
        assertEquals("Nc 111 must be > totalVotes 208", mess2) */

        val contest2 = Contest(info, mapOf(0 to 100, 1 to 108), Nc=211, Np=1)
        assertEquals(contest, contest2)
        assertEquals(contest.hashCode(), contest2.hashCode())

        assertEquals("testContestInfo (0) Nc=211 Np=1 votes={1=108, 0=100, 2=0}", contest.toString())
        assertEquals("Contest(info='testContestInfo' (0) candidates={cand0=0, cand1=1, cand2=2}, Nc=211, Np=1, id=0, name='testContestInfo', choiceFunction=PLURALITY, ncandidates=3, votes={1=108, 0=100, 2=0}, winnerNames=[cand1], winners=[1], losers=[0, 2])",
            contest.show())


        // assertEquals((211-208-1)/211.toDouble(), contest.undervoteRate())
        assertEquals(1/211.toDouble(), contest.phantomRate())
    }

    @Test
    fun testContestUnderAudit() {
        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2), SocialChoiceFunction.PLURALITY)
        val contest = Contest(info, mapOf(0 to 100, 1 to 108), Nc=211, Np=0)

        val contestUAp = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()
        val cvrs = listOf(makeCvr(1), makeCvr(1), makeCvr(0))
        val contestUAc = ContestUnderAudit(contest, isComparison = true).makeClcaAssertions()

        assertNotEquals(contestUAp, contestUAc)
        assertNotEquals(contestUAp.hashCode(), contestUAc.hashCode())

        val contestUAc2 = ContestUnderAudit(contest, isComparison = true).makeClcaAssertions()
        assertEquals(contestUAc2, contestUAc)
        assertEquals(contestUAc2.hashCode(), contestUAc.hashCode())
        assertEquals(contestUAc2.toString(), contestUAc.toString())
        assertEquals(contestUAc2.showShort(), contestUAc.showShort())

        val expectedShowCandidates = """
               0 'cand0': votes=100
               1 'cand1': votes=108
               2 'cand2': votes=0
                Total=208
        """.replaceIndent("   ")
        assertEquals(expectedShowCandidates, contestUAc.showCandidates())

        val expectedShow = """'testContestInfo' (0) votes={1=108, 0=100, 2=0}
 margin=0.0379 recount=0.0741 Nc=211 Np=0
 choiceFunction=PLURALITY nwinners=1, winners=[1]
   0 'cand0': votes=100
   1 'cand1': votes=108
   2 'cand2': votes=0
    Total=208"""
        assertEquals(expectedShow, contestUAc.show())

        assertEquals(0.07407407407407407, contestUAc.recountMargin(), doublePrecision)
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
            contestUAp.makeClcaAssertions()
        }.message
        assertEquals("makeComparisonAssertions() can be called only on comparison contest", mess2)

        val contestUAc = ContestUnderAudit(contest, isComparison = true)
        val mess3 = assertFailsWith<RuntimeException> {
            contestUAc.makePollingAssertions()
        }.message
        assertEquals("makePollingAssertions() can be called only on polling contest", mess3)

        val mess4 = assertFailsWith<RuntimeException> {
            contestUAc.makeClcaAssertions()
        }.message
        assertEquals("choice function IRV is not supported", mess4)

        assertNotEquals(contestUAp, contestUAc)
        assertNotEquals(contestUAp.hashCode(), contestUAc.hashCode())
    }

    @Test
    fun testMakeWithCandidateNames() {
        // the candidates
        val info = ContestInfo(
            "Kalamazoo", 0,
            mapOf(
                "Butkovich" to 0,
                "Gelineau" to 1,
                "Kurland" to 2,
                "Schuette" to 4,
                "Whitmer" to 5,
                "Schleiger" to 3,
            ),
            SocialChoiceFunction.PLURALITY,
            nwinners = 1,
        )

        val contest = Contest.makeWithCandidateNames(
            info,
            mapOf(
                "Whitmer" to 3765,
                "Butkovich" to 6,
                "Gelineau" to 56,
                "Kurland" to 23,
                "Schleiger" to 19,
                "Schuette" to 1349,
            ),
            Nc = 42000,
            Np = 0, // assume all undervotes I guess
        )

        println("contest = $contest")
        val expected = mapOf(0 to 6, 1 to 56, 2 to 23, 3 to 19, 4 to 1349, 5 to 3765)
        assertEquals(expected, contest.votes)

    }
}