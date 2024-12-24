package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
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
    val ncontest: Int,
    val nballotStyles: Int,
    val totalBallots: Int, // not including undervotes and phantoms
    val marginRange: OpenEndRange<Double> = 0.01..< 0.03,
    val underVotePct: OpenEndRange<Double> = 0.01..< 0.30, // needed to set Nc
    val phantomPct: OpenEndRange<Double> = 0.00..< 0.05, // needed to set Nc
) {
    val ballotStylePartition = partition(totalBallots, nballotStyles).toMap() // Map bsidx -> ncards in each ballot style (bs)

    val fcontests: List<ContestTestData>
    val contests: List<Contest>
    val ballotStyles: List<BallotStyle>
    var countBallots = 0

    init {
        require(ncontest > 0)
        require(nballotStyles > 0)
        require(totalBallots > nballotStyles * ncontest) // TODO

        // between 2 and 4 candidates, margin is a random number in marginRange
        fcontests = List(ncontest) { it }.map {// id same as index
            val ncands = max(Random.nextInt(5), 2)
            ContestTestData(it, ncands,
                marginRange.start + if (marginRange.isEmpty()) 0.0 else Random.nextDouble(marginRange.endExclusive - marginRange.start),
                underVotePct.start + if (underVotePct.isEmpty()) 0.0 else Random.nextDouble(underVotePct.endExclusive - underVotePct.start),
                phantomPct.start + if (phantomPct.isEmpty()) 0.0 else Random.nextDouble(phantomPct.endExclusive - phantomPct.start),
                // TODO ChoiceFunction ??
            )
        }

        // every contest has between 1 and 4 ballot styles, randomly chosen
        val contestBs = mutableMapOf<ContestTestData, Set<Int>>()
        fcontests.forEach{
            val nbs = min(nballotStyles, 1 + Random.nextInt(4))
            val bset = mutableSetOf<Int>() // the ballot style idx, 0 based
            while (bset.size < nbs) { // randomly choose nbs ballot styles
                bset.add(Random.nextInt(nballotStyles))
            }
            contestBs[it] = bset
        }

        // partition totalBallots amongst the ballotStyles
        ballotStyles = List(nballotStyles) { it }.map {
            val contestsForThisBs = contestBs.filter{ (fc, bset) -> bset.contains( it ) }.map { (fc, _) -> fc }
            val contestList = contestsForThisBs.map { it.info.name }
            val contestIds = contestsForThisBs.map { it.info.id }
            val ncards = ballotStylePartition[it]!!
            countBallots += ncards
            BallotStyle.make(it, contestList, contestIds, ncards)
        }
        require(countBallots == totalBallots)
        countCards()
        contests = fcontests.map { it.makeContest() }
    }

    fun countCards() {
        ballotStyles.forEach { bs ->
            bs.contestNames.forEach { contestName ->
                val contest = fcontests.find { it.info.name == contestName }!!
                contest.ncards += bs.ncards
            }
        }
    }

    // TODO phantoms, undervotes ??
    fun makeBallotsForPolling(): List<Ballot> {
        val result = mutableListOf<Ballot>()
        var ballotId = 0
        ballotStyles.forEach { ballotStyle ->
            repeat(ballotStyle.ncards) {
                result.add(Ballot("ballot$ballotId", false, ballotStyle.id))
                ballotId++
            }
        }
        return result
    }

    override fun toString() = buildString {
        appendLine("MultiContestTestData(ncontest=$ncontest, nballotStyles=$nballotStyles, totalBallots=$totalBallots")
        fcontests.forEach { fcontest ->
            append(fcontest)
            val bs4id = ballotStyles.filter{ it.contestIds.contains(fcontest.contestId) }.map{ it.id }
            appendLine(" ballotStyles=$bs4id")
        }
        appendLine("")
        ballotStyles.forEach { appendLine(it) }
    }

    // create new partitions each time this is called
    fun makeCvrsFromContests(): List<Cvr> {
        fcontests.forEach { it.resetTracker() } // startFresh
        val cvrbs = CvrBuilders().addContests(fcontests.map { it.info })
        val result = mutableListOf<Cvr>()
        ballotStyles.forEach { ballotStyle ->
            val fcontests = fcontests.filter { ballotStyle.contestNames.contains(it.info.name) }
            repeat(ballotStyle.ncards) {
                result.add(makeCvr(cvrbs, fcontests))
            }
        }
        // add phantoms
        val ncardsPerContest = mutableMapOf<Int, Int>() // contestId -> ncards
        result.forEach { cvr ->
            cvr.votes.keys.forEach{ contestId -> ncardsPerContest.merge(contestId, 1) { a, b -> a + b } }
        }
        val phantoms = makePhantomCvrs(contests, ncardsPerContest)

        return result + phantoms
    }

    // add regular Cvrs including undervotes
    private fun makeCvr(cvrbs: CvrBuilders, fcontests: List<ContestTestData>): Cvr {
        val cvrb = cvrbs.addCvr()
        fcontests.forEach { fcontest -> fcontest.addContestToCvr(cvrb) }
        return cvrb.build()
    }
}

// This creates a multicandidate contest with the two closest candidates having exactly the given margin.
// It can create cvrs that exactly reflect this contest's vote; so can be used in simulating the audit.
// The cvrs are not multicontest.
data class ContestTestData(
    val contestId: Int,
    val ncands: Int,
    val margin: Double, // margin of top highest vote getters, not counting undervotePct, phantomPct
    val undervotePct: Double, // needed to set Nc
    val phantomPct: Double, // needed to set Nc
    val choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
) {
    val candidateNames: List<String> = List(ncands) { it }.map { "cand$it" }
    val info = ContestInfo("contest$contestId", contestId, candidateNames = listToMap(candidateNames), choiceFunction)

    var ncards = 0
    var underCount = 0
    var adjustedVotes: List<Pair<Int, Int>> = emptyList() // includes undervotes
    var trackVotesRemaining = mutableListOf<Pair<Int, Int>>()
    var votesLeft = 0

    fun resetTracker() {
        trackVotesRemaining = mutableListOf()
        trackVotesRemaining.addAll(adjustedVotes)
        votesLeft = ncards
    }

    fun makeContest(): Contest {
        val underCount = (this.ncards * undervotePct).toInt() // close enough
        this.underCount = underCount

        val nvotes = this.ncards - underCount
        if (nvotes == 0) {
            return Contest(this.info, emptyMap(), this.ncards)
        }
        val votes: List<Pair<Int, Int>> = partition(nvotes, ncands)

        /* pick (ncands - 1) numbers to partition the votes
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
         */
        var svotes = votes.sortedBy { it.second }.reversed().toMutableList()
        svotes.add(Pair(ncands, underCount)) // represents the undervotes, always at the end

        // adjust the margin between the first and second highest votes.
        var adjust = 100
        while (abs(adjust) > 2) {
            if (debug) println("${this.info.name} before=$svotes")
            adjust = adjust(svotes)
            if (debug) println("${this.info.name} after=$svotes adjust=$adjust")
            svotes = svotes.sortedBy { it.second }.reversed().toMutableList()
            if (debug) println()
        }
        this.adjustedVotes = svotes // includes the undervotes

        //    Let V_c = votes for contest C, V_c <= Nb_c <= N_c.
        //    Let U_c = undervotes for contest C = Nb_c - V_c >= 0.
        //    Let Np_c = nphantoms for contest C = N_c - Nb_c, and are added to the ballots before sampling or sample size estimation.
        //    Then V_c + U_c + Np_c = N_c.
        // V_c = ncards
        // U_c = upct * N_c
        // Np_c = ppct * N_c
        // N_c = V_c + U_c + Np_c
        // N_c = V_c + upct * N_c + ppct * N_c
        // 1 = V_c/N_c + upct + ppct
        // N_c = V_c / (1 - upct - ppct)

        val removeUndervotes = svotes.filter{ it.first != ncands }
        val Nc = this.ncards / (1.0 - undervotePct - phantomPct)
        return Contest(this.info, removeUndervotes.toMap(), Nc.toInt())
    }

    fun adjust(svotes: MutableList<Pair<Int, Int>>): Int {
        val winner = svotes[0]
        val loser = svotes[1]
        val wantMarginDiff = ceil(margin * ncards).toInt()
        val haveMarginDiff = (winner.second - loser.second)
        val adjust: Int = ceil((wantMarginDiff - haveMarginDiff) * 0.5).toInt() // can be positive or negetive
        svotes.set(0, Pair(winner.first, winner.second + adjust))
        svotes[1] = Pair(loser.first, loser.second - adjust)
        return adjust // will be 0 when done
    }

    /* used for standalone contest
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
    */

    // choose Candidate, add contest, including undervote (no candidate selected)
    fun addContestToCvr(cvrb: CvrBuilder) {
        val candidateIdx = chooseCandidate(Random.nextInt(votesLeft))
        if (candidateIdx == ncands) {
            cvrb.addContest(info.name) // undervote
        } else {
            cvrb.addContest(info.name, candidateIdx)
        }
    }

    // choice is a number from 0..votesLeft
    // shrink the partition as votes are taken from it
    // same as ContestSimulation
    fun chooseCandidate(choice: Int): Int {
        var sum = 0
        var nvotes = 0
        var idx = 0
        while (idx <= ncands) {
            nvotes = trackVotesRemaining[idx].second
            sum += nvotes
            if (choice < sum) break
            idx++
        }
        val candidateIdx = trackVotesRemaining[idx].first
        require(nvotes > 0)
        trackVotesRemaining[idx] = Pair(candidateIdx, nvotes - 1)
        votesLeft--

        val check = trackVotesRemaining.map { it.second }.sum()
        if (check != votesLeft) {
            println("check")
        }
        require(check == votesLeft)
        return candidateIdx
    }

    override fun toString() = buildString {
        append(" ContestTestData(contestId=$contestId, ncands=$ncands, margin=${df(margin)}, choiceFunction=$choiceFunction countCards=$ncards")
    }
}

// partition nthings into npartitions randomly
// return map partIdx -> nvotes, sum(nvotes) = nthings // index not id!!
fun partition(nthings: Int, npartitions: Int): List<Pair<Int, Int>> {
    val cutoffs = List(npartitions - 1) { it }.map { Pair(it, Random.nextInt(nthings)) }.toMutableList()
    cutoffs.add(Pair(npartitions - 1, nthings)) // add the end point

    // put in order
    val sortedCutoffs = cutoffs.sortedBy { it.second }

    // turn that into a partition
    var last = 0
    val partition = mutableListOf<Pair<Int, Int>>()
    sortedCutoffs.forEach { ps ->
        partition.add(Pair(ps.first, ps.second - last))
        last = ps.second
    }
    return partition
}