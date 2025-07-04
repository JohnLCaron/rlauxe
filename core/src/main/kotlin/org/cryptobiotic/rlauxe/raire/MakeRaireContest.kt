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

// gets RaireAssertions from raire-java libray
fun makeRaireContestUA(info: ContestInfo, voteConsolidator: VoteConsolidator, Nc: Int, Np: Int): RaireContestUnderAudit {
    // TODO consistency checks on voteConsolidator
    // all candidate indexes
    val startingVotes = voteConsolidator.makeVoteList()
    val cvotes = voteConsolidator.makeVotes()
    val votes = Votes(cvotes, info.candidateIds.size)

    //// TODO seems like we just need to know the winner. we could replace this with IrvCount
    //      then we could add annotation here, currently in makeIrvContests
    // Tabulates the outcome of the IRV election, returning the outcome as an IRVResult.
    val irvResult: IRVResult = votes.runElection(TimeOut.never())
    if (!quiet) println(" runElection: possibleWinners=${irvResult.possibleWinners.contentToString()} eliminationOrder=${irvResult.eliminationOrder.contentToString()}")

    if (1 != irvResult.possibleWinners.size) {
        throw RuntimeException("nwinners ${irvResult.possibleWinners.size} must be 1")
    }
    val winner: Int = irvResult.possibleWinners[0] // we need a winner in order to generate the assertions

    //// heres the hard part - solving for the assertions
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
    requireNotNull(raireSolution.solution.Ok)
    val raireResult: RaireResult = raireSolution.solution.Ok

    // public class RaireResult {
    //    public AssertionAndDifficulty[] assertions;
    //    public double difficulty;
    //    public int margin;
    //    public int winner;
    //    public int num_candidates;
    //    public TimeTaken time_to_determine_winners;
    //    public TimeTaken time_to_find_assertions;
    //    public TimeTaken time_to_trim_assertions;
    //    public boolean warning_trim_timed_out;
    //    private static final boolean USE_DIVING = true;

    // public class AssertionAndDifficulty {
    //    public final Assertion assertion;
    //    public final double difficulty;
    //    public final int margin;
    //    public final Map<String, Object> status;

    val raireAssertions = raireResult.assertions.map { aand ->
        val votes = if (aand.assertion is NotEliminatedNext) {
            val nen = (aand.assertion as NotEliminatedNext)
            val voteSeq = VoteSequences.eliminate(startingVotes, nen.continuing.toList())
            val nenChoices = voteSeq.nenFirstChoices(nen.winner, nen.loser)
            val margin = voteSeq.margin(nen.winner, nen.loser, nenChoices)
            // println("    nenChoices = $nenChoices margin=$margin\n")
            require(aand.margin == margin)
            nenChoices

        } else {
            val neb = (aand.assertion as NotEliminatedBefore)
            val voteSeq = VoteSequences(startingVotes)
            val nebChoices = voteSeq.nebFirstChoices(neb.winner, neb.loser)
            val margin = voteSeq.margin(neb.winner, neb.loser, nebChoices)
            // println("    nebChoices = $nebChoices margin=$margin\n")
            require(aand.margin == margin)
            nebChoices
        }

        RaireAssertion.convertAssertion(info.candidateIds, aand, votes)
    }

    val rcontestUA = RaireContestUnderAudit.makeFromInfo(
        info,
        winnerIndex = raireResult.winner,
        Nc = Nc,
        Np = Np,
        raireAssertions,
    )
    // rcontestUA.makeClcaAssertions()

    return rcontestUA
}