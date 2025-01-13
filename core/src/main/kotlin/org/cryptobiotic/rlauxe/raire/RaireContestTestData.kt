package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.RaireProblem
import au.org.democracydevelopers.raire.algorithm.RaireResult
import au.org.democracydevelopers.raire.assertions.Assertion
import au.org.democracydevelopers.raire.assertions.AssertionAndDifficulty
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import au.org.democracydevelopers.raire.assertions.NotEliminatedNext
import au.org.democracydevelopers.raire.audittype.BallotComparisonOneOnDilutedMargin
import au.org.democracydevelopers.raire.irv.Votes
import au.org.democracydevelopers.raire.time.TimeOut
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.listToMap
import kotlin.random.Random

private const val debug = false

/** Simulation of Raire Contest */
data class RaireContestTestData(
    val contestId: Int,
    val ncands: Int,
    val ncards: Int,
    val margin: Double, // TODO not using
    val undervotePct: Double, // TODO not using
    val phantomPct: Double,
) {
    val candidateNames: List<String> = List(ncands) { it }.map { "cand$it" }
    val info = ContestInfo("rcontest$contestId", contestId, candidateNames = listToMap(candidateNames), SocialChoiceFunction.IRV)

    val underCount = (this.ncards * undervotePct).toInt()
    val phantomCount = (this.ncards * phantomPct).toInt()
    val Nc = this.ncards + this.phantomCount

    fun makeCvrs(): List<RaireCvr> {
        val rcvrs = makeRandomCvrs()
        val vc = VoteConsolidator()
        rcvrs.forEach {
            val votes = it.cvr.votes[contestId]
            if (votes != null) {
                vc.addVote(votes)
            }
        }
        val cvotes = vc.makeVotes()

        return rcvrs
    }

    fun makeRandomCvrs(): List<RaireCvr> {
        var count = 0
        val cvrs = mutableListOf<RaireCvr>()
        repeat(this.ncards) {
            cvrs.add(makeCvr(count++))
        }
        repeat(this.phantomCount) {
            val pcvr = Cvr("pcvr$count", mapOf(contestId to IntArray(0)), phantom=true)
            count++
            cvrs.add(RaireCvr(pcvr))
        }
        return cvrs
    }

    // TODO must be able to set the margin, to get testable assertions. Random gives margins like .0011
    private fun makeCvr(cvrIdx: Int): RaireCvr {
        // vote for a random number of candidates, including 0
        val nprefs = Random.nextInt(ncands)
        val prefs = mutableListOf<Int>()
        while(prefs.size < nprefs) {
            val voteFor = Random.nextInt(ncands)
            if (!prefs.contains(voteFor)) prefs.add(voteFor)
        }
        return RaireCvr(Cvr("cvr$cvrIdx", mapOf(contestId to prefs.toIntArray())))
    }

    // adjust in place
    fun adjust(testCvrs: List<RaireCvr>, minAssertion: AssertionAndDifficulty) {
        var have = minAssertion.margin
        val want = this.margin * this.ncards
        val nen = minAssertion.assertion as NotEliminatedNext
        val winner = nen.winner
        val loser = nen.loser
        var cvrIdx = 0
        while (have < want) {
            val rcvr = testCvrs[cvrIdx]
            val votes: IntArray = rcvr.cvr.votes[contestId]!!
            if (votes.contains(winner) && votes.contains(loser)) {
                val rank_winner = rcvr.get_vote_for(contestId, winner)
                val rank_loser = rcvr.get_vote_for(contestId, loser)
                if (rank_winner > rank_loser) {
                    // switch winner and loser
                    votes[rank_winner-1] = loser
                    votes[rank_loser-1] = winner
                    have++
                }
            }
            cvrIdx++
        }
    }

    override fun toString() = buildString {
        append("ContestTestData($contestId, ncands=$ncands, ncards=$ncards, undervotePct=${df(undervotePct)} phantomPct=${df(phantomPct)} Nc=$Nc")
    }
}

fun makeRaireContest(N: Int, margin: Double): Pair<RaireContestUnderAudit, List<Cvr>> {
    val ncands = 4

    val testContest = RaireContestTestData(0, ncands=ncands, ncards=N, margin=margin, undervotePct = .10, phantomPct = .005)
    val testCvrs = testContest.makeCvrs()

    var round = 1
    println("===================================\nRound $round")
    var solution = findMinAssertion(testContest, testCvrs)

    var marginPct = 0.0
    while (marginPct < margin) {
        testContest.adjust(testCvrs, solution.third)
        println("===================================\nRound $round")
        solution = findMinAssertion(testContest, testCvrs)
        marginPct = solution.second.margin / testContest.ncards.toDouble()
        round++
    }

    val raireAssertions = solution.second.assertions.map { RaireAssertion.convertAssertion(testContest.info.candidateIds, it) }

    val rcontentUA = RaireContestUnderAudit.makeFromInfo(
        testContest.info,
        winner=solution.first,
        Nc=testContest.Nc,
        Np=testContest.phantomCount,
        raireAssertions,
    )

    return Pair(rcontentUA, testCvrs.map { it.cvr })
}

fun findMinAssertion(testContest: RaireContestTestData, testCvrs: List<RaireCvr>): Triple<Int, RaireResult, AssertionAndDifficulty> {
    val vc = VoteConsolidator()
    testCvrs.forEach {
        val votes = it.cvr.votes[testContest.info.id]
        if (votes != null) {
            vc.addVote(votes)
        }
    }
    val cvotes = vc.makeVotes()

    // public Votes(Vote[] votes, int numCandidates) throws RaireException {
    val votes = Votes(cvotes, testContest.ncands)
    val result = votes.runElection(TimeOut.never())
    println(" runElection: possibleWinners=${result.possibleWinners.contentToString()} eliminationOrder=${result.eliminationOrder.contentToString()}")
    require(1 == result.possibleWinners.size) { "nwinners ${result.possibleWinners.size} must be 1" } // TODO
    val winner:Int = result.possibleWinners[0]

    // Map<String, Object> metadata,
    // Vote[] votes,
    // int num_candidates,
    // Integer winner,
    // AuditType audit,
    // TrimAlgorithm trim_algorithm,
    // Double difficulty_estimate,
    // Double time_limit_seconds) {
    val problem = RaireProblem(
        mapOf("candidates" to testContest.candidateNames),
        cvotes,
        testContest.ncands,
        winner,
        BallotComparisonOneOnDilutedMargin(testCvrs.size),
        null,
        null,
        null,
    )
    val solution = problem.solve()
    requireNotNull(solution.solution.Ok) // TODO
    val solutionResult: RaireResult = solution.solution.Ok

    val marginPct = solutionResult.margin / testContest.ncards.toDouble()
    println(" solutionResult: margin=${solutionResult.margin} marginPct=${df(marginPct)} difficulty=${df(solutionResult.difficulty)} nassertions=${solutionResult.assertions.size}")
    solutionResult.assertions.forEach { it ->
        println("   ${showAssertion(it.assertion)} margin=${it.margin} difficulty=${df(it.difficulty)} ")
    }

    val minAssertion: AssertionAndDifficulty = solutionResult.assertions.find { it.margin == solutionResult.margin }!!
    return Triple(winner, solutionResult, minAssertion)
}


fun showAssertion(assertion: Assertion) = buildString {
    if (assertion is NotEliminatedBefore) {
        append("   NotEliminatedBefore winner=${assertion.winner} loser=${assertion.loser} ")
    } else if (assertion is NotEliminatedNext) {
        append("   NotEliminatedNext winner=${assertion.winner} loser=${assertion.loser} continuing=${assertion.continuing.contentToString()} ")
    }
}
