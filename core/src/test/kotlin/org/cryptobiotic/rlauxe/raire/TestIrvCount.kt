package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.RaireProblem
import au.org.democracydevelopers.raire.RaireSolution
import au.org.democracydevelopers.raire.algorithm.RaireResult
import au.org.democracydevelopers.raire.assertions.AssertionAndDifficulty
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import au.org.democracydevelopers.raire.assertions.NotEliminatedNext
import au.org.democracydevelopers.raire.audittype.BallotComparisonOneOnDilutedMargin
import au.org.democracydevelopers.raire.irv.IRVResult
import au.org.democracydevelopers.raire.irv.Votes
import au.org.democracydevelopers.raire.time.TimeOut
import org.cryptobiotic.rlauxe.util.df
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
        for (ncands in 3..7) {
            println("\n====================================\n")
            println("ncands=$ncands")
            testIrvCount(ncands)
        }
    }

    fun testIrvCount(ncands: Int) {
        val N = 20000
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
        println("================================\n")

         //// so does this agree with votes.runElection() from raire-java library?
         // Tabulates the outcome of the IRV election, returning the outcome as an IRVResult.
         val irvResult: IRVResult = votes.runElection(TimeOut.never())
         val raireWinners = mutableSetOf<Int>()
         irvResult.possibleWinners.forEach { raireWinners.add(it) }
         assertEquals(raireWinners, roundWinner.winners)

        /// solve for the assertions from raire-java library
        val problem = RaireProblem(
            mapOf("candidates" to testContest.candidateNames),
            cvotes,
            testContest.ncands,
            roundWinner.winners.first(),
            BallotComparisonOneOnDilutedMargin(testCvrs.size),
            null,
            null,
            null,
        )
        val solution: RaireSolution = problem.solve()
        if (solution.solution.Err != null) {
            println("solution.solution.Err=${solution.solution.Err}")
            return
        }
        requireNotNull(solution.solution.Ok) // TODO
        val solutionResult: RaireResult = solution.solution.Ok
        val minAssertion: AssertionAndDifficulty = solutionResult.assertions.find { it.margin == solutionResult.margin }!!

        val startingVotes = vc.makeVoteList()
        val rassertions = mutableListOf<RaireAssertion>()
        solutionResult.assertions.forEach {
            val isMinAssertion = if (it == minAssertion) "*" else ""
            println("${showAssertion(it.assertion)} margin=${it.margin} difficulty=${df(it.difficulty)} $isMinAssertion")

            val choices = if (it.assertion is NotEliminatedNext) {
                val nen = (it.assertion as NotEliminatedNext)
                val voteSeq = VoteSequences.eliminate(startingVotes, nen.continuing.toList())
                val nenChoices = voteSeq.nenChoices(nen.winner, nen.loser)
                val margin = voteSeq.margin(nen.winner, nen.loser, nenChoices)
                println("    nenChoices = $nenChoices margin=$margin\n")
                assertEquals(it.margin, margin)
                nenChoices

            } else {
                val neb = (it.assertion as NotEliminatedBefore)
                val voteSeq = VoteSequences(startingVotes)
                val nebChoices = voteSeq.nebChoices(neb.winner, neb.loser)
                val margin = voteSeq.margin(neb.winner, neb.loser, nebChoices)
                println("    nebChoices = $nebChoices margin=$margin\n")
                assertEquals(it.margin, margin)
                nebChoices
            }

            val rassertion = RaireAssertion.convertAssertion(testContest.info.candidateIds, it, choices)
            println("    rassertion=${rassertion}")
            rassertions.add(rassertion)
        }

     }
}
