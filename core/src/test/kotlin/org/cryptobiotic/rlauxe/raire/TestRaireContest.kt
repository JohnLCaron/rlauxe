package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.doublePrecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestRaireContest {

    @Test
    fun testContestBasics() {
        val info = ContestInfo(
            "testContestInfo",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2, "cand3" to 3, "cand4" to 4, "cand42" to 42),
            SocialChoiceFunction.IRV
        )
        //     override val info: ContestInfo,
        //    override val winners: List<Int>,
        //    override val Nc: Int,
        //    override val Np: Int,
        val contest = RaireContest(info, winners=listOf(1), Nc=211, Np=1)
        assertEquals(info.id, contest.id)
        assertEquals(info.choiceFunction, contest.choiceFunction)
        assertEquals(211, contest.Nc)
        assertEquals(listOf(1), contest.winners)
        assertEquals(listOf(0, 2, 3, 4, 42), contest.losers)
        assertEquals(listOf("cand1"), contest.winnerNames)
        assertEquals(
            "RaireContest(info='testContestInfo' (0) candidates={cand0=0, cand1=1, cand2=2, cand3=3, cand4=4, cand42=42}, winners=[1], Nc=211, Np=1)",
            contest.toString()
        )

        assertEquals(-1, contest.undervotes) // TODO
        assertEquals(1/211.toDouble(), contest.phantomRate())
    }

    @Test
    fun testRaireContestUnderAudit() {
        val (rcontestUA: RaireContestUnderAudit, rcvrs: List<Cvr>) = simulateRaireTestContest(5000, contestId=111, ncands=3, minMargin=.04, quiet = true)
        rcontestUA.makeClcaAssertions()

        assertEquals(rcontestUA, rcontestUA)
        assertEquals(rcontestUA.hashCode(), rcontestUA.hashCode())

        val (rcontestUA2: RaireContestUnderAudit, _) = simulateRaireTestContest(5000, contestId=111, ncands=3, minMargin=.04, quiet = true)
        assertNotEquals(rcontestUA, rcontestUA2)
        assertNotEquals(rcontestUA.hashCode(), rcontestUA2.hashCode())

       /* val expectedShowCandidates = """
   0 'cand0': votes=100
   1 'cand1': votes=108
   2 'cand2': votes=0
    Total=208
        """.replaceIndent("   ")
        assertEquals(expectedShowCandidates, rcontestUA.showCandidates()) */

        assertTrue(rcontestUA.showShort().startsWith("rcontest111 (111) Nc=5000 winner0 losers [1, 2] minMargin="))
        assertTrue(rcontestUA.show().contains("recount=-1.0000 Nc=5000 Np=25 Nu=-1\n choiceFunction=IRV nwinners=1, winners=[0]"))
        assertEquals(-1.0, rcontestUA.recountMargin(), doublePrecision)
    }


}