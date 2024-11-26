package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

private val debug = false

/*
    val test = MultiContestTestData(20, 11, 20000)
    val contests: List<Contest> = test.makeContests()
    val testCvrs = test.makeCvrsFromContests()
    val ballots = test.makeBallots()

    val test = MultiContestTestData(20, 11, 20000)
    val contestsUA: List<ContestUnderAudit> = test.makeContests().map { ContestUnderAudit(it, it.Nc) }
    val cvrsUAP = test.makeCvrsFromContests().map { CvrUnderAudit.fromCvrIF( it, false) }
 */
// creates a set of contests and ballotStyles, with randomly chosen candidates and margins.
// It can create cvrs that reflect the contests' exact votes.
data class MultiContestTestData(
    val ncontest: Int, val nballotStyles: Int, val totalBallots: Int,
    val debug: Boolean = false, val minMargin: Double = 0.005,
) {
    val fcontests: List<TestContest>
    val ballotStyles: List<BallotStyle>
    var countBallots = 0

    init {
        // between 2 and 4 candidates, margin random number between minMargin and minMargin + .02
        fcontests = List(ncontest) { it }.map {
            val ncands = max(Random.nextInt(5), 2)
            TestContest(it, ncands, minMargin + Random.nextDouble(0.2))
        }

        // between 1 and ncontest contests, randomly chosen
        ballotStyles = List(nballotStyles) { it }.map {
            val ncInStyle = if (ncontest == 1) 1 else
                1 + Random.nextInt(ncontest - 1) // TODO use a power law distribution (weighted to small values) ? more realistic ??
            val contestIndexes = mutableSetOf<Int>()
            while (contestIndexes.size < ncInStyle) {
                contestIndexes.add(Random.nextInt(ncontest))
            }
            val contestList = contestIndexes.map { fcontests[it].info.name }
            val contestIds = contestIndexes.map { fcontests[it].info.id }
            val ncards = (totalBallots.toDouble() / nballotStyles).toInt()
            countBallots += ncards
            BallotStyle(contestList, contestIds, ncards)
        }
        countCards()
    }

    fun countCards() {
        ballotStyles.forEach { bs ->
            bs.contestNames.forEach { contestName ->
                val contest = fcontests.find { it.info.name == contestName }!!
                contest.ncards += bs.ncards
            }
        }
    }

    fun makeContests(): List<Contest> {
        return fcontests.map { it.makeContest() }
    }

    fun makeBallots(): List<BallotUnderAudit> {
        val result = mutableListOf<BallotUnderAudit>()
        var ballotId = 0
        ballotStyles.forEach { ballotStyle ->
            repeat(ballotStyle.ncards) {
                result.add(BallotUnderAudit(ballotId, ballotStyle))
                ballotId++
            }
        }
        return result
    }

    override fun toString() = buildString {
        appendLine("SampleData(ncontest=$ncontest, nballotStyles=$nballotStyles, totalBallots=$totalBallots")
        fcontests.forEach { appendLine(it) }
        ballotStyles.forEach { appendLine(it) }
    }

    fun makeCvrsFromContests(): List<Cvr> {
        fcontests.forEach { it.resetTracker() } // startFresh
        val cvrbs = CvrBuilders().addContests(fcontests.map { it.info })
        val result = mutableListOf<Cvr>()
        ballotStyles.forEach { ballotStyle ->
            val fcontests = fcontests.filter { ballotStyle.contestNames.contains(it.info.name) }
            repeat(ballotStyle.ncards) {
                result.add(randomSample(cvrbs, fcontests))
            }
        }
        return result.toList()
    }

    private fun randomSample(cvrbs: CvrBuilders, fcontests: List<TestContest>): Cvr {
        val cvrb = cvrbs.addCrv()
        fcontests.forEach { fcontest ->
            cvrb.addContest(fcontest.info.name, fcontest.chooseCandidate(Random.nextInt(fcontest.votesLeft))).done()
        }
        return cvrb.build()
    }
}

// This creates a multicandidate contest with the two closest candidates having exactly the given margin.
// It can create cvrs that reflect this contest's vote; so can be used in simulating the audit.
// The cvrs are not multicontest.
data class TestContest(
    val contestId: Int,
    val ncands: Int,
    val margin: Double,
    val choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
) {
    val candidateNames: List<String> = List(ncands) { it }.map { "cand$it" }
    val info = ContestInfo("contest$contestId", contestId, candidateNames = listToMap(candidateNames), choiceFunction)

    var ncards = 0
    var adjustedVotes: List<Pair<Int, Int>> = emptyList()
    var trackVotesRemaining = mutableListOf<Pair<Int, Int>>()
    var votesLeft = 0

    fun adjust(svotes: MutableList<Pair<Int, Int>>): Int {
        val winner = svotes[0]
        val loser = svotes[1]
        val wantMarginDiff = (margin * ncards).toInt()
        val haveMarginDiff = (winner.second - loser.second)
        val adjust: Int = (wantMarginDiff - haveMarginDiff) / 2 // can be positive or negetive
        svotes.set(0, Pair(winner.first, winner.second + adjust))
        svotes[1] = Pair(loser.first, loser.second - adjust)
        return adjust // will be 0 when done
    }

    fun resetTracker() {
        trackVotesRemaining = mutableListOf()
        trackVotesRemaining.addAll(adjustedVotes)
        votesLeft = ncards
    }

    fun makeContest(): Contest {
        val nvotes = this.ncards
        if (nvotes == 0) {
            return Contest(this.info, emptyMap(), this.ncards)
        }

        // pick (ncands - 1) numbers to partition the votes
        val partition = List(ncands - 1) { it }.map { Pair(it, Random.nextInt(nvotes)) }.toMutableList()
        partition.add(Pair(ncands - 1, nvotes)) // add the end point

        // turn those into votes
        val psort = partition.sortedBy { it.second }
        var last = 0
        val votes = mutableListOf<Pair<Int, Int>>()
        psort.forEach { ps ->
            votes.add(Pair(ps.first, ps.second - last))
            last = ps.second
        }

        // adjust the seasoning, i mean the margin
        var adjust = 100
        var svotes = votes.sortedBy { it.second }.reversed().toMutableList()
        while (abs(adjust) > 2) {
            if (debug) println("${this.info.name} before=$svotes")
            adjust = adjust(svotes)
            if (debug) println("${this.info.name} after=$svotes adjust=$adjust")
            svotes = svotes.sortedBy { it.second }.reversed().toMutableList()
            if (debug) println()
        }
        this.adjustedVotes = svotes
        return Contest(this.info, svotes.toMap(), this.ncards)
    }

    // used for standalone contest
    fun makeCvrs(): List<Cvr> {
        resetTracker()
        val cvrbs = CvrBuilders().addContests(listOf(this.info))
        val result = mutableListOf<Cvr>()
        repeat(this.ncards) {
            val cvrb = cvrbs.addCrv()
            cvrb.addContest(info.name, chooseCandidate(Random.nextInt(votesLeft))).done()
            result.add(cvrb.build())
        }
        return result.toList()
    }

    // choice is a number from 0..votesLeft
    // shrink the partition as votes are taken from it
    fun chooseCandidate(choice: Int): Int {
        val check = trackVotesRemaining.map { it.second }.sum()
        require(check == votesLeft)

        var sum = 0
        var nvotes = 0
        var idx = 0
        while (idx < ncands) {
            nvotes = trackVotesRemaining[idx].second
            sum += nvotes
            if (choice < sum) break
            idx++
        }
        val candidateId = trackVotesRemaining[idx].first
        require(nvotes > 0)
        trackVotesRemaining[idx] = Pair(candidateId, nvotes - 1)
        votesLeft--
        return candidateId
    }

    override fun toString() = buildString {
        append(" FuzzedContest(contestId=$contestId, ncands=$ncands, margin=${df(margin)}, choiceFunction=$choiceFunction countCards=$ncards")
    }
}
