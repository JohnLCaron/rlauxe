package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.raire.IrvContestVotes
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import kotlin.test.Test

class TestIrvContestVotes {

    @Test
    fun testIrvContestVotes() {
            val info = ContestInfo(
            "testIrvContestVotes",
            0,
            mapOf("cand11" to 11, "cand12" to 12, "cand123" to 123, "cand111" to 111),
            SocialChoiceFunction.IRV,
        )
        println(info)

        val target = IrvContestVotes(info)
        target.addVotes(intArrayOf(111,11) )
        target.addVotes(intArrayOf(12,123) )
        target.addVotes(intArrayOf(12,123) )

        println(target.vc)
    }

    @Test
    fun testVoteConsolidator() {
        val info = ContestInfo(
            "testIrvContestVotes",
            0,
            mapOf("cand11" to 11, "cand12" to 12, "cand123" to 123, "cand111" to 111),
            SocialChoiceFunction.IRV,
        )
        println(info)

        val target = VoteConsolidator()
        target.addVote(intArrayOf(111,11) )
        target.addVote(intArrayOf(12,123) )
        target.addVote(intArrayOf(12,123) )

        println(target)
    }
}
