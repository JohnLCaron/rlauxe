package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.estimate.makeCvr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
 margin=0.0379 recount=0.0741 Nc=211 Np=0 Nu=3
 choiceFunction=PLURALITY nwinners=1, winners=[1]
   0 'cand0': votes=100
   1 'cand1': votes=108
   2 'cand2': votes=0
    Total=208"""
        assertEquals(expectedShow, contestUAc.show())

        assertEquals(0.07407407407407407, contestUAc.recountMargin(), doublePrecision)
    }


}