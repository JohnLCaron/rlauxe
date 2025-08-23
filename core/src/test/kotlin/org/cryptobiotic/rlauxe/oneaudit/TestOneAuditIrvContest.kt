package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.raire.RaireAssertion
import org.cryptobiotic.rlauxe.raire.RaireContest
import kotlin.test.Test
import kotlin.test.assertEquals

class TestOneAuditIrvContest {

    @Test
    fun testContestBasics() {
        val info = ContestInfo(
            "TestOneAuditIrvContest",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2, "cand3" to 3, "cand4" to 4, "cand42" to 42),
            SocialChoiceFunction.IRV,
            voteForN = 6,
        )
        val Nc = 212
        val Np = 1
        val contest = RaireContest(info, winners=listOf(1), Nc=Nc, Ncast=Nc-Np)

        // val contestOA = OneAuditContest.make(contest, cvrVotes, cvrPercent = cvrPercent, undervotePercent = undervotePercent, phantomPercent = phantomPercent)
        val cvrVotes = mapOf(0 to 100, 1 to 200, 2 to 42, 3 to 7, 4 to 0) // worthless?
        val cvrNc = 200
        
        // data class BallotPool(
        //    val name: String,
        //    val poolId: Int,
        //    val contest:Int,
        //    val ncards: Int,          // ncards for this contest in this pool; TODO hasStyles = false?
        //    val votes: Map<Int, Int>, // candid -> nvotes // the diff from ncards tell you the undervotes
        //)
        val pool = BallotPool("swim", 42, 0, 11, mapOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0))
            
        //         fun make(contest: ContestIF,
        //                  cvrVotes: Map<Int, Int>,   // candidateId -> nvotes
        //                  cvrNc: Int,                // the diff from cvrVotes tells you the undervotes
        //                  pools: List<BallotPool>,   // pools for this contest
        //                  Np: Int): OneAuditContest {
        val contestOA = OneAuditContest.make(contest, cvrVotes, cvrNc, listOf(pool))

        val contestOAUA =  OneAuditIrvContest(contestOA, true, emptyList<RaireAssertion>())
        contestOAUA.makeClcaAssertionsFromReportedMargin()

        assertEquals(contestOAUA, contestOAUA)
        assertEquals(contestOAUA.hashCode(), contestOAUA.hashCode())
        assertEquals("TestOneAuditIrvContest (0) votes=N/A Nc=212 minMargin=0.0000", contestOAUA.showShort(), )
        assertEquals(-1.0, contestOAUA.recountMargin(), doublePrecision)
    }
}