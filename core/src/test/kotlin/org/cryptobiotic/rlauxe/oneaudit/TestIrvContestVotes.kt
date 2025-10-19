package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.raire.VoteList
import kotlin.test.Test
import kotlin.test.assertEquals

class TestIrvContestVotes {

    @Test
    fun testVoteConsolidator() {
            val info = ContestInfo(
            "testIrvContestVotes",
            0,
            mapOf("cand11" to 11, "cand12" to 12, "cand123" to 123, "cand111" to 111),
            SocialChoiceFunction.IRV,
        )
        println(info)

        var target = ContestTabulation(info)
        target.addVotes(intArrayOf(111,11) )
        target.addVotes(intArrayOf(12,123) )
        target.addVotes(intArrayOf(12,123) )

        println(target.irvVotes)
        assertEquals(listOf(
            VoteList(1, listOf(3, 0)),
            VoteList(2, listOf(1,2))),
            target.irvVotes.makeVoteList())

        ////
        target = ContestTabulation(info)
        target.addVotes(intArrayOf(111,11,11) )
        target.addVotes(intArrayOf(12,123,12,12,12,12,12) )

        println(target.irvVotes)
    }
}
