package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.doublePrecision
import kotlin.test.*

class TestContest {

    @Test
    fun testContestInfo() {
        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1), SocialChoiceFunction.PLURALITY)
        assertEquals(listOf(0, 1), info.candidateIds)
        assertEquals(1, info.nwinners)
        assertEquals("'testContestInfo' (0) candidates=[0, 1] choiceFunction=PLURALITY nwinners=1 voteForN=1", info.toString())

        val mess = assertFailsWith<IllegalArgumentException> {
            ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1), SocialChoiceFunction.THRESHOLD)
        }.message
        assertEquals("THRESHOLD requires minFraction", mess)

        val mess2 = assertFailsWith<IllegalArgumentException> {
            ContestInfo(
                "testContestInfo",
                0,
                mapOf("cand0" to 0, "cand1" to 1),
                SocialChoiceFunction.THRESHOLD,
                minFraction = -.05
            )
        }.message
        assertEquals("minFraction must be between 0 and 1", mess2)

        val mess3 = assertFailsWith<IllegalArgumentException> {
            ContestInfo(
                "testContestInfo",
                0,
                mapOf("cand0" to 0, "cand1" to 1),
                SocialChoiceFunction.APPROVAL,
                minFraction = .35
            )
        }.message
        assertEquals("APPROVAL may not have minFraction", mess3)

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
        val contest = Contest(info, mapOf(0 to 100, 1 to 108), Nc=211, Ncast = 210)
        assertEquals(info.id, contest.id)
        assertEquals(info.name, contest.name)
        assertEquals(info.choiceFunction, contest.choiceFunction)
        assertEquals(mapOf(0 to 100, 1 to 108, 2 to 0), contest.votes)
        assertEquals(211, contest.Nc)
        assertEquals(listOf(1), contest.winners)
        assertEquals(listOf(0, 2), contest.losers)
        assertEquals(listOf("cand1"), contest.winnerNames)
        assertEquals(
            "testContestInfo (0) Nc=211 Np=1 votes={1=108, 0=100, 2=0} undervotes=2, voteForN=1",
            contest.toString()
        )

        val mess0 = assertFailsWith<IllegalArgumentException> {
            Contest(info, mapOf(0 to 100, 1 to 116), Nc = 211, Ncast=219)
        }.message
        assertNotNull(mess0)
        assertEquals("contest 0 Ncast= 219 must be <= Nc= 211", mess0)

        val mess1 = assertFailsWith<IllegalArgumentException> {
            Contest(info, mapOf(0 to 100, 1 to 108, 3 to 2), Nc=222, Ncast=222)
        }.message
        assertEquals("'3' not found in contestInfo candidateIds [0, 1, 2]", mess1)

        val mess2 = assertFailsWith<IllegalArgumentException> {
            Contest(info, mapOf(0 to 100, 1 to 116), Nc = 211, Ncast=209)
        }.message
        assertNotNull(mess2)
        assertEquals("contest 0 nvotes= 216 must be <= nwinners=1 * (Nc=211 - Np=2) = 209", mess2)

        val contest2 = Contest(info, mapOf(0 to 100, 1 to 108), Nc=211,Ncast=210)
        assertEquals(contest, contest2)
        assertEquals(contest.hashCode(), contest2.hashCode())

        assertEquals("testContestInfo (0) Nc=211 Np=1 votes={1=108, 0=100, 2=0} undervotes=2, voteForN=1", contest.toString())
        assertEquals( """'testContestInfo' (0) PLURALITY voteForN=1 votes={1=108, 0=100, 2=0} undervotes=2, voteForN=1
   winners=[1] Nc=211 Np=1 Nu=2 sumVotes=208""",
            contest.show())

        // assertEquals((211-208-1)/211.toDouble(), contest.undervoteRate())
        assertEquals(1/211.toDouble(), contest.phantomRate())
    }

    @Test
    fun testContestSMBasics() {
        val info = ContestInfo(
            "testContestInfo",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2),
            SocialChoiceFunction.THRESHOLD,
            minFraction = .55,
        )
        val contest = Contest(info, mapOf(0 to 100, 1 to 125), Nc=227, Ncast=225)
        assertEquals(info.id, contest.id)
        assertEquals(info.name, contest.name)
        assertEquals(info.choiceFunction, contest.choiceFunction)
        assertEquals(mapOf(0 to 100, 1 to 125, 2 to 0), contest.votes)
        assertEquals(227, contest.Nc)
        assertEquals(2, contest.Np())
        assertEquals(listOf(1), contest.winners)
        assertEquals(listOf(0, 2), contest.losers)
        assertEquals(listOf("cand1"), contest.winnerNames)
        assertEquals(
            "testContestInfo (0) Nc=227 Np=2 votes={1=125, 0=100, 2=0} undervotes=0, voteForN=1",
            contest.toString()
        )
        println("margin(1,0) = ${contest.margin(1,0)}")
        assertEquals(25/227.toDouble(), contest.margin(1,0))

        assertTrue(contest.percent(1) > info.minFraction!!)
    }

    @Test
    fun testContestSMnoWinner() {
        val info = ContestInfo(
            "testContestInfo",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2),
            SocialChoiceFunction.THRESHOLD,
            minFraction = .55,
        )
        val contest = Contest(info, mapOf(0 to 100, 1 to 123, 2 to 2), Nc=227, Ncast=225)
        assertEquals(info.id, contest.id)
        assertEquals(info.name, contest.name)
        assertEquals(info.choiceFunction, contest.choiceFunction)
        assertEquals(mapOf(0 to 100, 1 to 123, 2 to 2), contest.votes)
        assertEquals(227, contest.Nc)
        assertEquals(2, contest.Np())
        assertEquals(emptyList(), contest.winners)
        assertEquals(listOf(0, 1, 2), contest.losers)
        assertEquals(emptyList(), contest.winnerNames)
        assertEquals(
            "testContestInfo (0) Nc=227 Np=2 votes={1=123, 0=100, 2=2} undervotes=0, voteForN=1",
            contest.toString()
        )
        println("margin(1,0) = ${contest.margin(1,0)}")
        assertEquals(23/227.toDouble(), contest.margin(1,0))

        assertTrue(contest.percent(1) < info.minFraction!!)

        val contestUAc = ContestUnderAudit(contest, isClca = true).addStandardAssertions()
        contestUAc.clcaAssertions.forEach { println("  ${it.cassorter.assorter.desc()} ${it.cassorter}") }
        println("minAssert = ${contestUAc.minAssertion()}")
    }

    @Test
    fun testContestSMerrs() {
        val info = ContestInfo(
            "testContestInfo",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2),
            SocialChoiceFunction.THRESHOLD,
            minFraction = .55,
        )
        val mess = assertFailsWith<IllegalArgumentException> {
            Contest(info, mapOf(0 to 100, 1 to 116), Nc = 211, Ncast=209)
        }.message
        assertNotNull(mess)
        assertEquals("contest 0 nvotes= 216 must be <= nwinners=1 * (Nc=211 - Np=2) = 209", mess)
    }

    // @Test not allowed
    fun testContestSMtwoWinners() {
        val info = ContestInfo(
            "testContestInfo",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2),
            SocialChoiceFunction.THRESHOLD,
            minFraction = .33,
            nwinners = 2
        )
        val contest = Contest(info, mapOf(0 to 100, 1 to 123, 2 to 2), Nc=227, Ncast=225)
        assertEquals(info.id, contest.id)
        assertEquals(info.name, contest.name)
        assertEquals(info.choiceFunction, contest.choiceFunction)
        assertEquals(mapOf(0 to 100, 1 to 123, 2 to 2), contest.votes)
        assertEquals(227, contest.Nc)
        assertEquals(2, contest.Np())
        assertEquals(listOf(1, 0), contest.winners)
        assertEquals(listOf(2), contest.losers)
        assertEquals(listOf("cand1", "cand0"), contest.winnerNames)
        assertEquals(
            "testContestInfo (0) Nc=227 Np=2 votes={1=123, 0=100, 2=2}",
            contest.toString()
        )
        println("margin(1,0) = ${contest.margin(1,0)} percent(1) = ${contest.percent(1)}")
        assertEquals(23/227.toDouble(), contest.margin(1,0))
        println("margin(1,2) = ${contest.margin(1,2)} percent(2) = ${contest.percent(2)}")
        assertEquals(121/227.toDouble(), contest.margin(1,2))
        println("margin(0,2) = ${contest.margin(0,2)} percent(0) = ${contest.percent(0)}")
        assertEquals(98/227.toDouble(), contest.margin(0,2))

        assertTrue(contest.percent(0) >= info.minFraction!!)
        assertTrue(contest.percent(1) >= info.minFraction!!)
        assertTrue(contest.percent(2) < info.minFraction!!)

        val contestUAc = ContestUnderAudit(contest, isClca = true).addStandardAssertions()
        contestUAc.clcaAssertions.forEach { println("  ${it.cassorter.assorter.desc()} ${it.cassorter}") }
        println("minAssert = ${contestUAc.minAssertion()}")
    }

    @Test
    fun testContestUnderAudit() {
        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2), SocialChoiceFunction.PLURALITY)
        val contest = Contest(info, mapOf(0 to 100, 1 to 108), Nc=211, Ncast=209)

        val contestUAp = ContestUnderAudit(contest, isClca = false).addStandardAssertions()
        val contestUAc = ContestUnderAudit(contest, isClca = true).addStandardAssertions()

        assertNotEquals(contestUAp, contestUAc)
        assertNotEquals(contestUAp.hashCode(), contestUAc.hashCode())

        val contestUAc2 = ContestUnderAudit(contest, isClca = true).addStandardAssertions()
        assertEquals(contestUAc2, contestUAc)
        assertEquals(contestUAc2.hashCode(), contestUAc.hashCode())
        assertEquals(contestUAc2.toString(), contestUAc.toString())
        assertEquals(contestUAc2.showShort(), contestUAc.showShort())

        val expectedShowCandidates = """
               0 'cand0': votes=100 
               1 'cand1': votes=108  (winner)
               2 'cand2': votes=0 
                Total=208
        """.replaceIndent("   ")
        assertEquals(expectedShowCandidates, contestUAc.contest.showCandidates())

        val expectedShow = """Contest 'testContestInfo' (0) PLURALITY voteForN=1 votes={1=108, 0=100, 2=0} undervotes=1, voteForN=1
   winners=[1] Nc=211 Np=2 Nu=1 sumVotes=208
   1/0 votes=108/100 diff=8 (w-l)/w =0.07407407407407407
   0 'cand0': votes=100 
   1 'cand1': votes=108  (winner)
   2 'cand2': votes=0 
    Total=208"""
        assertEquals(expectedShow, contestUAc.show())

        assertEquals(0.07407407407407407, contestUAc.minRecountMargin(), doublePrecision)
    }

    @Test
    fun testContestUnderAuditIrvException() {
        val info = ContestInfo("testContestInfo", 0, mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2), SocialChoiceFunction.IRV)
        val contest = Contest(info, mapOf(0 to 100, 1 to 108), Nc = 211, Ncast=211)

        val mess1 = assertFailsWith<RuntimeException> {
            ContestUnderAudit(contest, isClca = false).addStandardAssertions()
        }.message
        assertEquals("choice function IRV is not supported", mess1)
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
            Ncast=42000, // assume all undervotes I guess
        )

        println("contest = $contest")
        val expected = mapOf(0 to 6, 1 to 56, 2 to 23, 3 to 19, 4 to 1349, 5 to 3765)
        assertEquals(expected, contest.votes)

    }
}