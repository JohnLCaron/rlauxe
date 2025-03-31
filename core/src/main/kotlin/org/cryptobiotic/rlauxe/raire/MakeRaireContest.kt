package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.RaireProblem
import au.org.democracydevelopers.raire.RaireSolution
import au.org.democracydevelopers.raire.algorithm.RaireResult
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import au.org.democracydevelopers.raire.assertions.NotEliminatedNext
import au.org.democracydevelopers.raire.audittype.BallotComparisonOneOnDilutedMargin
import au.org.democracydevelopers.raire.irv.IRVResult
import au.org.democracydevelopers.raire.irv.Votes
import au.org.democracydevelopers.raire.time.TimeOut
import org.cryptobiotic.rlauxe.core.ContestInfo

private val quiet = true

fun makeRaireContest(info: ContestInfo, voteConsolidator: VoteConsolidator, Nc: Int, Np: Int): RaireContestUnderAudit {
    val startingVotes = voteConsolidator.makeVoteList()
    val cvotes = voteConsolidator.makeVotes()
    val votes = Votes(cvotes, info.candidateIds.size)

    // Tabulates the outcome of the IRV election, returning the outcome as an IRVResult.
    val result: IRVResult = votes.runElection(TimeOut.never())
    if (!quiet) println(" runElection: possibleWinners=${result.possibleWinners.contentToString()} eliminationOrder=${result.eliminationOrder.contentToString()}")

    if (1 != result.possibleWinners.size) {
        throw RuntimeException("nwinners ${result.possibleWinners.size} must be 1")
    }
    val winner: Int = result.possibleWinners[0] // we need a winner in order to generate the assertions

    val problem = RaireProblem(
        mapOf("candidates" to info.candidateNames.keys.toList()),
        cvotes,
        info.candidateIds.size,
        winner,
        BallotComparisonOneOnDilutedMargin(Nc),
        null,
        null,
        null,
    )
    val raireSolution: RaireSolution = problem.solve()
    if (raireSolution.solution.Err != null) {
        throw RuntimeException("solution.solution.Err=${raireSolution.solution.Err}")
    }
    requireNotNull(raireSolution.solution.Ok) // TODO
    val raireResult: RaireResult = raireSolution.solution.Ok

    val raireAssertions = raireResult.assertions.map { aand ->
        val votes = if (aand.assertion is NotEliminatedNext) {
            val nen = (aand.assertion as NotEliminatedNext)
            val voteSeq = VoteSequences.eliminate(startingVotes, nen.continuing.toList())
            val nenChoices = voteSeq.nenChoices(nen.winner, nen.loser)
            val margin = voteSeq.margin(nen.winner, nen.loser, nenChoices)
            // println("    nenChoices = $nenChoices margin=$margin\n")
            require(aand.margin == margin)
            nenChoices

        } else {
            val neb = (aand.assertion as NotEliminatedBefore)
            val voteSeq = VoteSequences(startingVotes)
            val nebChoices = voteSeq.nebChoices(neb.winner, neb.loser)
            val margin = voteSeq.margin(neb.winner, neb.loser, nebChoices)
            // println("    nebChoices = $nebChoices margin=$margin\n")
            require(aand.margin == margin)
            nebChoices
        }

        // TODO the candidate Ids go from 0 ... ncandidats
        RaireAssertion.convertAssertion(info.candidateIds, aand, votes)
    }

    val winnerId = info.candidateIds[raireResult.winner] // convert back to "real" id
    val rcontestUA = RaireContestUnderAudit.makeFromInfo(
        info,
        winner = winnerId,
        Nc = Nc,
        Np = Np,
        raireAssertions,
    )
    rcontestUA.makeClcaAssertions()

    return rcontestUA
}