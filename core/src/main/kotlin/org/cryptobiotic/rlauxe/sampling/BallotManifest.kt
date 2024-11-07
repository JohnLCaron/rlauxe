package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.Contest

interface BallotManifest {
    fun contests(): List<Contest>
    fun cardsForContest(contestId: Int): Int
}