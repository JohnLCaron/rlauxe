package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.irv.IRVResult
import au.org.democracydevelopers.raire.irv.Votes
import au.org.democracydevelopers.raire.time.TimeOut
import kotlin.test.Test
import kotlin.test.assertEquals

class TestIrvCount {

    // Need a lot of tries to be sure to get some ties
    @Test
    fun testRepeat() {
        var idx = 0
        repeat(111) {
            println("\n$idx ===================================")
            testIrvCount()
            idx++
        }
    }

    @Test
    fun testIrvCount() {
        val N = 20000
        val ncands = 7
        val minMargin = .05
        val undervotePct = 0.0
        val phantomPct = 0.0
        val testContest = RaireContestTestData(
            0,
            ncands = ncands,
            ncards = N,
            minMargin = minMargin,
            undervotePct = undervotePct,
            phantomPct = phantomPct,
            excessVotes = 0,
        )
        val candidateIds = testContest.info.candidateIds
        val testCvrs = testContest.makeCvrs()

        var round = 1
        println("\nRound $round")

        val vc = VoteConsolidator()
        testCvrs.forEach {
            val votes = it.cvr.votes[testContest.info.id]
            if (votes != null) {
                vc.addVote(votes)
            }
        }
        val cvotes = vc.makeVotes()
        val votes = Votes(cvotes, testContest.ncands)

        val irvCount = IrvCount(cvotes, candidateIds)
        val rootPath = irvCount.rootPath

        val raireCount = candidateIds.map { votes.firstPreferenceOnlyTally(it) }
        val rlauxeCount = candidateIds.map { rootPath.candVotes[it] }
        assertEquals(raireCount, rlauxeCount)
        // println(" raireCount round $round = ${raireCount}")
        var roundWinner = RoundWinner()
        while (!roundWinner.done) {
            round++
            println("Round $round")
            roundWinner = irvCount.nextRoundCount()

            if (!roundWinner.done) {
                val continuing = rootPath.viable.toIntArray()
                val raireCount = votes.restrictedTallies(continuing)
                // println(" raireCount round $round = ${raireCount.contentToString()}")
                continuing.forEachIndexed { idx, cand ->
                    assertEquals(raireCount[idx], rootPath.candVotes[cand])
                }
            }
        }
         val mult = if (roundWinner.winners.size > 1) "multipleWinenrs" else ""
        println("winner=$roundWinner} $mult")

         // so does this agree with votes.runElection() from raire=java library?

         // Tabulates the outcome of the IRV election, returning the outcome as an IRVResult.
         val result: IRVResult = votes.runElection(TimeOut.never())
         val raireWinners = mutableSetOf<Int>()
         result.possibleWinners.forEach { raireWinners.add(it) }
         assertEquals(raireWinners, roundWinner.winners)
     }
}
