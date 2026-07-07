package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.core.AboveThreshold
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import kotlin.test.Test
import kotlin.test.assertEquals

class TestThresholdValues {

    // from belgium Hainaut
    @Test
    fun testBelowThresholdValues() {
        // DHondtContest 'Bruxelles' (2) DHONDT voteForN=1 votes={2=120155, 3=96516, 7=86927, 10=58645, 5=49425, 9=34143, 12=24826, 8=14472, 1=12754, 13=6579, 14=3287,
        val votes = mapOf(1 to 12754, 2 to 120155, 3 to 96516, 4 to 3032, 5 to 49425, 6 to 1688, 7 to 86927, 8 to 14472, 9 to 34143, 10 to 58645, 11 to 1534, 12 to 24826, 13 to 6579, 14 to 3287, 15 to 1604, 16 to 1467, 17 to 1872)
        val candidates = votes.map { "cand${it.key}" to it.key }.toMap()

        val f = 0.05
        val info = ContestInfo(
            name = "ABC",
            id = 0,
            choiceFunction = SocialChoiceFunction.THRESHOLD,
            candidateNames = candidates,
            minFraction = f,
            nwinners = 1,
        )

        val contest = Contest(info, votes, Nc = 550514, Ncast = 550514)

        val pct = 12754/550514.0
        val bt = BelowThreshold.makeFromVotes(info, 1, contest.votes, contest.Nc)
        val bte = bt.noerror(true)
        assertEquals(0.5122759791759759, bte)
        val margin = bt.margin(true)
        assertEquals(0.0252248472862584, margin)
    }

    @Test
    fun testAboveThresholdValues() {
        // DHondtContest 'Bruxelles' (5) DHONDT voteForN=1 votes={2=120155, 3=96516, 7=86927, 10=58645, 5=49425, 9=34143, 12=24826, 8=14472, 1=12754, 13=6579, 14=3287, 4=3032, 17=1872, 6=1688, 15=1604, 11=1534, 16=1467}
        val votes = mapOf(1 to 12754, 2 to 120155, 3 to 96516, 4 to 3032, 5 to 49425, 6 to 1688, 7 to 86927, 8 to 14472, 9 to 34143, 10 to 58645, 11 to 1534, 12 to 24826, 13 to 6579, 14 to 3287, 15 to 1604, 16 to 1467, 17 to 1872)
        val candidates = votes.map { "cand${it.key}" to it.key }.toMap()

        val f = 0.05
        val info = ContestInfo(
            name = "ABC",
            id = 0,
            choiceFunction = SocialChoiceFunction.THRESHOLD,
            candidateNames = candidates,
            minFraction = f,
            nwinners = 1,
        )

        val contest = Contest(info, votes, Nc = 550514, Ncast = 550514)

        val pct = 86927/550514.0
        val bt = AboveThreshold.makeFromVotes(info, 7, contest.votes, f, contest.Nc)
        val bte = bt.noerror(true)
        assertEquals(0.5622845269157378, bte)
        val margin = bt.margin(true)
        assertEquals(2.215409599029271, margin) // upper is 10, so margin > 1
    }
}