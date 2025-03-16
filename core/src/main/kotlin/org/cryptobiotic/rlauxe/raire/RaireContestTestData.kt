package org.cryptobiotic.rlauxe.raire

import au.org.democracydevelopers.raire.RaireProblem
import au.org.democracydevelopers.raire.RaireSolution
import au.org.democracydevelopers.raire.algorithm.RaireResult
import au.org.democracydevelopers.raire.assertions.Assertion
import au.org.democracydevelopers.raire.assertions.AssertionAndDifficulty
import au.org.democracydevelopers.raire.assertions.NotEliminatedBefore
import au.org.democracydevelopers.raire.assertions.NotEliminatedNext
import au.org.democracydevelopers.raire.audittype.BallotComparisonOneOnDilutedMargin
import au.org.democracydevelopers.raire.irv.IRVResult
import au.org.democracydevelopers.raire.irv.Votes
import au.org.democracydevelopers.raire.time.TimeOut
import org.cryptobiotic.rlauxe.core.*

import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.listToMap
import kotlin.random.Random

/** Simulation of Raire Contest */
data class RaireContestTestData(
    val contestId: Int,
    val ncands: Int,
    val ncards: Int,        // Nc
    val minMargin: Double,
    val undervotePct: Double,   // TODO
    val phantomPct: Double,
    val excessVotes: Int? = null,   // control this for testing
) {
    val candidateNames: List<String> = List(ncands) { it }.map { "cand$it" }
    val candidateIds: List<Int> = List(ncands) { it }.map { it }
    val info = ContestInfo("rcontest$contestId", contestId, candidateNames = listToMap(candidateNames), SocialChoiceFunction.IRV)

    val underCount = (this.ncards * undervotePct).toInt()
    val phantomCount = (this.ncards * phantomPct).toInt()
    val Nc = this.ncards

    // this whole thing depends on RaireCvr separate from Cvr ??
    fun makeCvrs(): List<RaireCvr> {
        var count = 0
        val cvrs = mutableListOf<RaireCvr>()

        val excess = excessVotes ?: (this.ncards * minMargin).toInt()
        repeat(excess) {
            cvrs.add(makeCvrWithLeading0(count++))
        }
        repeat(this.ncards - excess - this.phantomCount) {
            cvrs.add(makeCvr(count++))
        }
        repeat(this.phantomCount) {
            val pcvr = Cvr("pcvr$count", mapOf(contestId to IntArray(0)), phantom=true)
            count++
            cvrs.add(RaireCvr(pcvr))
        }
        // println("makeCvrs: excess=$excess phantoms=${this.phantomCount}")
        cvrs.shuffle()
        return cvrs
    }

    private fun makeCvrWithLeading0(cvrIdx: Int): RaireCvr {
        // vote for a random number of candidates, including 0
        val nprefs = 1 + Random.nextInt(ncands-1)
        val prefs = mutableListOf<Int>()
        prefs.add(0) // vote for zero first
        while (prefs.size < nprefs) {
            val voteFor = Random.nextInt(ncands)
            if (!prefs.contains(voteFor)) prefs.add(voteFor)
        }
        return RaireCvr(Cvr("cvr$cvrIdx", mapOf(contestId to prefs.toIntArray())))
    }

    private fun makeCvr(cvrIdx: Int): RaireCvr {
        // vote for a random number of candidates, including 0
        val nprefs = Random.nextInt(ncands)
        val prefs = mutableListOf<Int>()
        while (prefs.size < nprefs) {
            val voteFor = Random.nextInt(ncands)
            if (!prefs.contains(voteFor)) prefs.add(voteFor)
        }
        return RaireCvr(Cvr("cvr$cvrIdx", mapOf(contestId to prefs.toIntArray())))
    }

    fun adjustRanks(testCvrs: List<RaireCvr>, margin: Int, winner: Int, loser: Int) {
        var have = margin
        val want = this.minMargin * this.ncards
        var cvrIdx = 0
        // println("have=$have, want = $want")
        while (have < want && cvrIdx < testCvrs.size) {
            val rcvr = testCvrs[cvrIdx]
            val votes: IntArray = rcvr.cvr.votes[contestId]!!
            if (votes.contains(winner) && votes.contains(loser)) {
                val rank_winner = rcvr.get_vote_for(contestId, winner)
                val rank_loser = rcvr.get_vote_for(contestId, loser)
                if (rank_winner > rank_loser) {
                    // switch winner and loser TODO Mutable votes!!
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

fun simulateRaireContest(contest: RaireContest, quiet: Boolean = true): List<Cvr> {
    val minMargin = 0.0
    repeat(11) {
        val result = trytoMakeRaireContest(contest.Nc, contest.id, contest.ncandidates, minMargin, contest.undervoteRate(), contest.phantomRate(), quiet)
        if (result != null) return result.second
    }
    throw RuntimeException("failed 11 times to make raire contest with N=${contest.Nc} minMargin=$minMargin")
}

fun makeRaireContest(N: Int, contestId: Int, ncands:Int, minMargin: Double, undervotePct: Double = .05, phantomPct: Double = .005, quiet: Boolean = true): Pair<RaireContestUnderAudit, List<Cvr>> {
    repeat(11) {
        val result = trytoMakeRaireContest(N, contestId, ncands, minMargin, undervotePct, phantomPct, quiet)
        if (result != null) return result
    }
    throw RuntimeException("failed 11 times to make raire contest with N=$N minMargin=$minMargin")
}

fun trytoMakeRaireContest(N: Int, contestId: Int, ncands:Int, minMargin: Double, undervotePct: Double, phantomPct: Double, quiet: Boolean = false): Pair<RaireContestUnderAudit, List<Cvr>>? {
    val testContest = RaireContestTestData(contestId, ncands=ncands, ncards=N, minMargin=minMargin, undervotePct = undervotePct, phantomPct = phantomPct)
    val testCvrs = testContest.makeCvrs()

    var round = 1
    if (!quiet) println("===================================\nRound $round")
    var solution = findMinAssertion(testContest, testCvrs, quiet)
    if (solution == null) {
        println("round 1 solution is null")
        return null
    }

    var marginPct = solution.second.margin / testContest.ncards.toDouble()

    // iteratively modify the testCvrs until the minAssertion margin is > margin TODO cleanup
    while (marginPct < minMargin) {
        val aandd = solution!!.third

        when (aandd.assertion) {
            is NotEliminatedNext -> {
                val nen = (aandd.assertion as NotEliminatedNext)
                testContest.adjustRanks(testCvrs, aandd.margin, nen.winner, nen.loser)
            }

            is NotEliminatedBefore -> {
                val neb = (aandd.assertion as NotEliminatedBefore)
                testContest.adjustRanks(testCvrs, aandd.margin, neb.winner, neb.loser)
            }

            else -> {
                throw RuntimeException("unexpected assertion type ${aandd.assertion::class.simpleName}")
            }
        }

        if (!quiet) println("===================================\nRound $round")
        solution = findMinAssertion(testContest, testCvrs, quiet)
        if (solution == null) {
            println("round $round solution is null")
            return null
        }
        marginPct = solution.second.margin / testContest.ncards.toDouble()
        round++
    }

    val vc = VoteConsolidator()
    testCvrs.forEach {
        val votes = it.cvr.votes[testContest.info.id]
        if (votes != null) {
            vc.addVote(votes)
        }
    }
    val startingVotes = vc.makeVoteList()

    val raireAssertions = solution!!.second.assertions.map {  aand ->
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

        RaireAssertion.convertAssertion(testContest.info.candidateIds, aand, votes)
    }

    val rcontestUA = RaireContestUnderAudit.makeFromInfo(
        testContest.info,
        winner=solution.first,
        Nc=testContest.Nc,
        Np=testContest.phantomCount,
        raireAssertions,
    )

    return Pair(rcontestUA, testCvrs.map { it.cvr })
}

// TODO using testCvrs.size as Nc I think
// return Triple(winner, solution.solution.Ok, minAssertion)
fun findMinAssertion(testContest: RaireContestTestData, testCvrs: List<RaireCvr>, quiet: Boolean): Triple<Int, RaireResult, AssertionAndDifficulty>? {
    val vc = VoteConsolidator()
    testCvrs.forEach {
        val votes = it.cvr.votes[testContest.info.id]
        if (votes != null) {
            vc.addVote(votes)
        }
    }
    val cvotes = vc.makeVotes()
    val votes = Votes(cvotes, testContest.ncands)

    // Tabulates the outcome of the IRV election, returning the outcome as an IRVResult.
    val result: IRVResult = votes.runElection(TimeOut.never())
    if (!quiet) println(" runElection: possibleWinners=${result.possibleWinners.contentToString()} eliminationOrder=${result.eliminationOrder.contentToString()}")

    if (1 != result.possibleWinners.size) {
        // println("nwinners ${result.possibleWinners.size} must be 1")
        return null
    }
    val winner:Int = result.possibleWinners[0] // we need a winner in order to generate the assertions

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
    val solution: RaireSolution = problem.solve()
    if (solution.solution.Err != null) {
        println("solution.solution.Err=${solution.solution.Err}")
        return null
    }
    requireNotNull(solution.solution.Ok) // TODO
    val solutionResult: RaireResult = solution.solution.Ok
    val minAssertion: AssertionAndDifficulty = solutionResult.assertions.find { it.margin == solutionResult.margin }!!

    if (!quiet) {
        val marginPct = solutionResult.margin / testContest.ncards.toDouble()
        println(" solutionResult: margin=${solutionResult.margin} marginPct=${df(marginPct)} difficulty=${df(solutionResult.difficulty)} nassertions=${solutionResult.assertions.size}")
        solutionResult.assertions.forEach {
            val isMinAssertion = if (it == minAssertion) "*" else ""
            println("   ${showAssertion(it.assertion)} margin=${it.margin} difficulty=${df(it.difficulty)} $isMinAssertion")
        }
    }

    return Triple(winner, solutionResult, minAssertion)
}


fun showAssertion(assertion: Assertion) = buildString {
    if (assertion is NotEliminatedBefore) {
        append("   NotEliminatedBefore winner=${assertion.winner} loser=${assertion.loser} ")
    } else if (assertion is NotEliminatedNext) {
        append("   NotEliminatedNext winner=${assertion.winner} loser=${assertion.loser} continuing=${assertion.continuing.contentToString()} ")
    }
}
