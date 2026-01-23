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
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.oneaudit.AssortAvgsInPools
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditRatesFromPools
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.margin2mean
import kotlin.collections.forEach

private val quiet = true
private val logger = KotlinLogging.logger("MakeRaireContest")

// make RaireContestWithAssertions from ContestTabulation; get RaireAssertions from raire-java libray
// note ivrRoundsPaths are filled in
// used by CreateSfElection
fun makeRaireContest(info: ContestInfo, contestTab: ContestTabulation, Nc: Int, Nbin: Int): RaireContestWithAssertions {
    // TODO consistency checks on voteConsolidator
    // all candidate indexes
    val vc = contestTab.irvVotes
    val startingVotes = vc.makeVoteList()
    val votes = vc.makeVotes(info.candidateIds.size)

    // Tabulates the outcome of the IRV election, returning the outcome as an IRVResult.
    val irvResult: IRVResult = votes.runElection(TimeOut.never())
    if (!quiet) logger.debug{" runElection: possibleWinners=${irvResult.possibleWinners.contentToString()} eliminationOrder=${irvResult.eliminationOrder.contentToString()}"}

    if (1 != irvResult.possibleWinners.size) {
        // throw RuntimeException("nwinners ${irvResult.possibleWinners.size} must be 1")
        logger.warn{"${info.id} nwinners ${irvResult.possibleWinners.size} must be 1"}
    }
    val winner: Int = irvResult.possibleWinners[0] // we need a winner in order to generate the assertions

    //// heres the hard part - solving for the assertions
    val problem = RaireProblem(
        mapOf("candidates" to info.candidateNames.keys.toList()),
        votes.votes,
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
            val margin = voteSeq.marginInVotes(nen.winner, nen.loser, nenChoices)
            require(aand.margin == margin)
            nenChoices

        } else {
            val neb = (aand.assertion as NotEliminatedBefore)
            val voteSeq = VoteSequences(startingVotes)
            val nebChoices = voteSeq.nebFirstChoices(neb.winner, neb.loser)
            val margin = voteSeq.marginInVotes(neb.winner, neb.loser, nebChoices)
            require(aand.margin == margin)
            nebChoices
        }

        RaireAssertion.convertAssertion(info.candidateIds, aand, votes)
    }

    //          fun makeFromInfo(
    //                 info: ContestInfo,
    //                 winnerIndex: Int,
    //                 Nc: Int,
    //                 Ncast: Int,
    //                 Nundervotes: Int,
    //                 assertions: List<RaireAssertion>
    //         )
    val rcontestUA = RaireContestWithAssertions.makeFromInfo(
        info,
        winnerIndex = raireResult.winner,
        Nc = Nc,
        Ncast = Nc - contestTab.nphantoms,
        undervotes = contestTab.undervotes,
        raireAssertions,
        Nbin,
    )

    val candidateIdxs = info.candidateIds.mapIndexed { idx, candidateId -> idx } // TODO use candidateIdToIndex?
    val irvCount = IrvCount(votes.votes, candidateIdxs)
    val roundResultByIdx = irvCount.runRounds()

    // now convert results back to using the real Ids:
    val roundPathsById = roundResultByIdx.ivrRoundsPaths.map { roundPath ->
        val roundsById = roundPath.rounds.map { round -> round.convert(info.candidateIds) }
        IrvRoundsPath(roundsById, roundPath.irvWinner.convert(info.candidateIds))
    }
    (rcontestUA.contest as RaireContest).roundsPaths.addAll(roundPathsById)

    return rcontestUA
}

// contestTab.irvVotes must include the pooled data, since we generate the RaireAssertions from them.
fun makeRaireOneAuditContest(info: ContestInfo, contestTab: ContestTabulation, Nc: Int, Nbin: Int, oneAuditPools: List<OneAuditPoolFromCvrs>): RaireContestWithAssertions {
    val vc = contestTab.irvVotes
    val startingVotes = vc.makeVoteList()
    val votes = vc.makeVotes(info.candidateIds.size)

    // Tabulates the outcome of the IRV election, returning the outcome as an IRVResult.
    val irvResult: IRVResult = votes.runElection(TimeOut.never())
    if (!quiet) logger.debug{" runElection: possibleWinners=${irvResult.possibleWinners.contentToString()} eliminationOrder=${irvResult.eliminationOrder.contentToString()}"}

    if (1 != irvResult.possibleWinners.size) {
        // throw RuntimeException("nwinners ${irvResult.possibleWinners.size} must be 1")
        logger.warn{"${info.id} nwinners ${irvResult.possibleWinners.size} must be 1"}
    }
    val winner: Int = irvResult.possibleWinners[0] // we need a winner in order to generate the assertions

    //// heres the hard part - solving for the assertions
    val problem = RaireProblem(
        mapOf("candidates" to info.candidateNames.keys.toList()),
        votes.votes,
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
    val raireAssertions = raireResult.assertions.map { aand ->
        val votes = if (aand.assertion is NotEliminatedNext) {
            val nen = (aand.assertion as NotEliminatedNext)
            val voteSeq = VoteSequences.eliminate(startingVotes, nen.continuing.toList())
            val nenChoices = voteSeq.nenFirstChoices(nen.winner, nen.loser)
            val margin = voteSeq.marginInVotes(nen.winner, nen.loser, nenChoices)
            require(aand.margin == margin)
            nenChoices

        } else {
            val neb = (aand.assertion as NotEliminatedBefore)
            val voteSeq = VoteSequences(startingVotes)
            val nebChoices = voteSeq.nebFirstChoices(neb.winner, neb.loser)
            val margin = voteSeq.marginInVotes(neb.winner, neb.loser, nebChoices)
            require(aand.margin == margin)
            nebChoices
        }

        RaireAssertion.convertAssertion(info.candidateIds, aand, votes)
    }

    val rcontestUA = RaireContestWithAssertions.makeFromInfo(
        info,
        winnerIndex = raireResult.winner,
        Nc = Nc,
        Ncast = contestTab.ncards,
        undervotes = contestTab.undervotes,
        raireAssertions,
        Nbin,
    )

    // sanity check
    rcontestUA.assertions.forEach { assertion ->
        val irvVotes = contestTab.irvVotes.makeVotes(info.candidateIds.size)
        val raireAssorter = assertion.assorter as RaireAssorter
        val margin = raireAssorter.calcMargin(irvVotes, rcontestUA.Npop)
        if (!doubleIsClose(margin,raireAssorter.dilutedMargin())) {
            raireAssorter.calcMargin(irvVotes, rcontestUA.Npop)
            println("margin $margin != ${raireAssorter.dilutedMargin()} raireAssorter.dilutedMargin()")
        }
    }

    // use RaireAssorter with an ClcaAssorterOneAudit
    setPoolAssorterAveragesForRaire(listOf(rcontestUA), oneAuditPools)

    val candidateIdxs = info.candidateIds.mapIndexed { idx, candidateId -> idx } // TODO use candidateIdToIndex?
    val irvCount = IrvCount(votes.votes, candidateIdxs)
    val roundResultByIdx = irvCount.runRounds()

    // now convert results back to using the real Ids:
    val roundPathsById = roundResultByIdx.ivrRoundsPaths.map { roundPath ->
        val roundsById = roundPath.rounds.map { round -> round.convert(info.candidateIds) }
        IrvRoundsPath(roundsById, roundPath.irvWinner.convert(info.candidateIds))
    }
    (rcontestUA.contest as RaireContest).roundsPaths.addAll(roundPathsById)


    return rcontestUA
}

// use raireAssorter.calcMargin to set the pool assorter averages.
fun setPoolAssorterAveragesForRaire(
    oaContests: List<ContestWithAssertions>,
    pools: List<OneAuditPoolFromCvrs>, // poolId -> pool
) {
    val oneAuditErrorsFromPools = OneAuditRatesFromPools(pools)

    // ClcaAssorter already has the contest-wide reported margin. We just have to add the pool assorter averages
    // create the clcaAssertions and add then to the oaContests
    oaContests.filter { it.isIrv }. forEach { oaContest ->
        val contestId = oaContest.id
        val info = oaContest.contest.info()
        val clcaAssertions = oaContest.assertions.map { assertion ->
            val raireAssorter = assertion.assorter as RaireAssorter
            val assortAverages = mutableMapOf<Int, Double>() // poolId -> average assort value
            pools.filter { it.ncards() > 0}.forEach { cardPool ->
                if (cardPool.hasContest(contestId)) {
                    val tab = cardPool.contestTabs[oaContest.id]!!
                    val irvVotes: Votes = tab.irvVotes.makeVotes(oaContest.ncandidates)
                    val poolMargin = raireAssorter.calcMargin(irvVotes, cardPool.ncards())
                    assortAverages[cardPool.poolId] = margin2mean(poolMargin)
                }
            }
            val oaAssorter = ClcaAssorterOneAudit(assertion.info, assertion.assorter,
                dilutedMargin = assertion.assorter.dilutedMargin(),
                poolAverages = AssortAvgsInPools(assortAverages))

            oaAssorter.oaAssortRates = oneAuditErrorsFromPools.oaErrorRates(oaContest, oaAssorter)

            ClcaAssertion(assertion.info, oaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }
}

/* TODO who uses?
fun makeTestContestOAIrv(): RaireContestUnderAudit {

    val info = ContestInfo(
        "TestOneAuditIrvContest",
        0,
        mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2, "cand3" to 3, "cand4" to 4, "cand42" to 42),
        SocialChoiceFunction.IRV,
        voteForN = 6,
    )
    val Nc = 2120
    val Np = 2
    val rcontest = RaireContest(info, winners = listOf(1), Nc = Nc, Ncast = Nc - Np, undervotes = 0)

    // where did these come from ??
    val assert1 = RaireAssertion(1, 0, 0.0, 42, RaireAssertionType.winner_only)
    val assert2 = RaireAssertion(
        1, 2, 0.0, 422, RaireAssertionType.irv_elimination,
        listOf(2), mapOf(1 to 1, 2 to 2, 3 to 3)
    )

    val oaIrv = RaireContestUnderAudit(rcontest, rassertions = listOf(assert1, assert2), Nc)

    /* add pools

    // val contestOA = OneAuditContest.make(contest, cvrVotes, cvrPercent = cvrPercent, undervotePercent = undervotePercent, phantomPercent = phantomPercent)
    //val cvrVotes = mapOf(0 to 100, 1 to 200, 2 to 42, 3 to 7, 4 to 0) // worthless?
    //val cvrNc = 200
    // val pool = BallotPool("swim", 42, 0, 11, mapOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0))
    val pools = emptyList<CardPoolIF>() // TODO

    val clcaAssertions = oaIrv.pollingAssertions.map { assertion ->
        val passort = assertion.assorter
        val pairs = pools.map { pool ->
            Pair(pool.poolId, 0.55)
        }
        val poolAvgs = AssortAvgsInPools(pairs.toMap())
        val clcaAssertion = ClcaAssorterOneAudit(assertion.info, passort, oaIrv.makeDilutedMargin(passort), poolAvgs)
        ClcaAssertion(assertion.info, clcaAssertion)
    }
    oaIrv.clcaAssertions = clcaAssertions */

    return oaIrv
} */