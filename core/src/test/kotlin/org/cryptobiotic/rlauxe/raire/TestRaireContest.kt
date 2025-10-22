package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.util.tabulateCvrs
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
        val contest = RaireContest(info, winners=listOf(1), Nc=211, Ncast=210, undervotes = 1)
        assertEquals(info.id, contest.id)
        assertEquals(info.choiceFunction, contest.choiceFunction)
        assertEquals(211, contest.Nc)
        assertEquals(1, contest.undervotes)
        assertEquals(listOf(1), contest.winners)
        assertEquals(listOf(0, 2, 3, 4, 42), contest.losers)
        assertEquals(listOf("cand1"), contest.winnerNames)
        assertEquals( "RaireContest(info='testContestInfo' (0) candidates=[0, 1, 2, 3, 4, 42] choiceFunction=IRV nwinners=1 voteForN=1, winners=[1], Nc=211, Ncast=210, undervotes=1)",
            contest.toString()
        )

        // assertEquals(-1, contest.undervotes) // TODO
        assertEquals(1/211.toDouble(), contest.phantomRate())
    }

    @Test
    fun testSimulateRaireTestContest() {
        val contestId=111
        val (rcontestUA: RaireContestUnderAudit, _: List<Cvr>) = simulateRaireTestContest(5000, contestId=contestId, ncands=3, minMargin=.04, quiet = true)
        rcontestUA.addClcaAssertionsFromReportedMargin() // why do we have to call this, why doesnt simulateRaireTestContest add these ??

        assertEquals(rcontestUA, rcontestUA)
        assertEquals(rcontestUA.hashCode(), rcontestUA.hashCode())

        val (rcontestUA2: RaireContestUnderAudit, _) = simulateRaireTestContest(5000, contestId=111, ncands=3, minMargin=.04, quiet = true)
        assertNotEquals(rcontestUA, rcontestUA2)
        assertNotEquals(rcontestUA.hashCode(), rcontestUA2.hashCode())

        println(rcontestUA.showShort())
        assertTrue(rcontestUA.showShort().startsWith("rcontest111 (111) Nc=5000 winner 0 losers [1, 2] minMargin="))
        println(rcontestUA.show())
        assertTrue(rcontestUA.show().contains("recount=-1.0000 Nc=5000 Np=25 Nu=250"), rcontestUA.show())
        assertEquals(-1.0, rcontestUA.recountMargin(), doublePrecision)
    }

    @Test
    fun testMakeRaireContestUA() {
        val contestId=111
        val (rcontestUA: RaireContestUnderAudit, rcvrs: List<Cvr>) = simulateRaireTestContest(5000, contestId=contestId, ncands=5, minMargin=.04, quiet = true)
        rcontestUA.addClcaAssertionsFromReportedMargin()

        val info = rcontestUA.contest.info()
        val contestTab = tabulateCvrs(rcvrs.iterator(), mapOf(info.id to info))
        val tab = contestTab[info.id]!!
        assertTrue(tab.irvVotes.nvotes() > 0)

        val rc = makeRaireContestUA(info, contestTab[info.id]!!, rcontestUA.Nc)

        assertTrue(rc.recountMargin() > 0.0)
        assertTrue(rc.recountMargin() < 1.0)

        rc.rassertions.forEach {
            print(it.show())
            println(" remaining = ${it.remaining(info.candidateIds)}")
        }

        rc.addClcaAssertionsFromReportedMargin()
        rc.clcaAssertions.forEach {
            print(it.show())
        }
    }

}