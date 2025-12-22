package org.cryptobiotic.rlauxe.raire

import kotlin.test.Test
import kotlin.test.assertEquals

class TestCompareIrvCountWithRaire {

    // Need a lot of tries to be sure to get some ties
    // @Test
    fun testRepeat() {
        var idx = 0
        repeat(111) {
            println("\n$idx ===================================")
            testCompareIrvCountWithRaire()
            idx++
        }
    }

    @Test
    fun testCompareIrvCountWithRaire() {
        for (ncands in 3..7) {
            println("\n====================================\n")
            println("ncands=$ncands")
            testCompareIrvCountWithRaire(ncands)
        }
    }

    fun testCompareIrvCountWithRaire(ncands: Int) {
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
        testCvrs.forEach { cvr ->
            val votes = cvr.votes[testContest.info.id]
            if (votes != null) {
                vc.addVote(votes)
            }
        }
        val votes = vc.makeVotes(testContest.ncands)

        val irvCount = IrvCount(votes.votes, candidateIds)
        val rootPath = irvCount.rootPath

        val raireCount = candidateIds.map { votes.firstPreferenceOnlyTally(it) }
        val rlauxeCount = candidateIds.map { rootPath.candVotes[it] }
        assertEquals(raireCount, rlauxeCount)
        // println(" raireCount round $round = ${raireCount}")

        /*
        var roundWinner = IrvWinners()
        while (!roundWinner.done) {
            round++
            println("Round $round")
            roundWinner = irvCount.runRound()

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
            println("${showIrvAssertion(it.assertion)} margin=${it.margin} difficulty=${df(it.difficulty)} $isMinAssertion")

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

         */
     }
}
