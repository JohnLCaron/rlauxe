package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.Ballot
import org.cryptobiotic.rlauxe.workflow.BallotManifest
import org.cryptobiotic.rlauxe.workflow.BallotStyle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val debugAdjust = false

/**
 * Creates a set of contests and ballotStyles, with randomly chosen candidates and margins.
 * It can create cvrs that reflect the contests' exact votes.
 */
data class MultiContestTestData(
    val ncontest: Int,
    val nballotStyles: Int,
    val totalBallots: Int, // including undervotes and phantoms
    val marginRange: ClosedFloatingPointRange<Double> = 0.01.. 0.03,
    val underVotePctRange: ClosedFloatingPointRange<Double> = 0.01.. 0.30, // needed to set Nc
    val phantomPctRange: ClosedFloatingPointRange<Double> = 0.00..  0.005, // needed to set Nc
) {
    // generate with ballotStyles; but if hasStyles = false, then these are not visible to the audit
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
                marginRange.start + if (marginRange.endInclusive <= marginRange.start) 0.0 else Random.nextDouble(marginRange.endInclusive - marginRange.start),
                underVotePctRange.start + if (underVotePctRange.endInclusive <= underVotePctRange.start) 0.0 else Random.nextDouble(underVotePctRange.endInclusive - underVotePctRange.start),
                phantomPctRange.start + if (phantomPctRange.endInclusive <= phantomPctRange.start) 0.0 else Random.nextDouble(phantomPctRange.endInclusive - phantomPctRange.start),
                // TODO ChoiceFunction ??
            )
        }

        // every contest has between 1 and 4 ballot styles, randomly chosen
        val contestBstyles = mutableMapOf<ContestTestData, Set<Int>>()
        fcontests.forEach{
            val nbs = min(nballotStyles, 1 + Random.nextInt(4))
            val bset = mutableSetOf<Int>() // the ballot style idx, 0 based
            while (bset.size < nbs) { // randomly choose nbs ballot styles
                bset.add(Random.nextInt(nballotStyles))
            }
            contestBstyles[it] = bset
        }

        // partition totalBallots amongst the ballotStyles
        ballotStyles = List(nballotStyles) { it }.map {
            val contestsForThisBs = contestBstyles.filter{ (fc, bset) -> bset.contains( it ) }.map { (fc, _) -> fc }
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

    override fun toString() = buildString {
        append("ncontest=$ncontest, nballotStyles=$nballotStyles, totalBallots=$totalBallots")
        appendLine(" marginRange=$marginRange underVotePct=$underVotePctRange phantomPct=$phantomPctRange")
        fcontests.forEach { fcontest ->
            append("  $fcontest")
            val bs4id = ballotStyles.filter{ it.contestIds.contains(fcontest.contestId) }.map{ it.id }
            appendLine(" ballotStyles=$bs4id")
        }
        appendLine("")
        ballotStyles.forEach { appendLine("  $it") }
    }

    // includes undervotes and phantoms, size = totalBallots + phantom count
    fun makeBallotManifest(hasStyle: Boolean): BallotManifest {
        val ballots = mutableListOf<Ballot>()
        var ballotId = 0
        ballotStyles.forEach { ballotStyle ->
            repeat(ballotStyle.ncards) {
                val ballot = Ballot("ballot$ballotId", false, if (hasStyle) ballotStyle else null)
                ballots.add(ballot)
                ballotId++
            }
        }
        // add phantoms
        val ncardsByContest = fcontests.associate { Pair(it.contestId, it.ncards) }
        val phantoms = makePhantomBallots(contests, ncardsByContest)
        return BallotManifest(ballots + phantoms, ballotStyles)
    }

    fun makeCvrsAndBallotManifest(hasStyle: Boolean): Pair<List<Cvr>, BallotManifest> {
        val cvrs = makeCvrsFromContests()
        val ballots = mutableListOf<Ballot>()
        cvrs.forEach { cvr ->
            val ballot = Ballot(cvr.id, cvr.phantom, null, if (hasStyle) cvr.votes.keys.toList() else emptyList())
            ballots.add(ballot)
        }
        return Pair( cvrs, BallotManifest(ballots, ballotStyles))
    }

    // create new partitions each time this is called
    // includes undervotes and phantoms, size = totalBallots + phantom count
    fun makeCvrsFromContests(): List<Cvr> {
        fcontests.forEach { it.resetTracker() } // startFresh
        val cvrbs = CvrBuilders().addContests(fcontests.map { it.info })
        val result = mutableListOf<Cvr>()
        ballotStyles.forEach { ballotStyle ->
            val fcontests = fcontests.filter { ballotStyle.contestNames.contains(it.info.name) }
            repeat(ballotStyle.ncards) {
                // add regular Cvrs including undervotes
                result.add(makeCvr(cvrbs, fcontests))
            }
        }

        // add phantoms
        val ncardsPerContest = mutableMapOf<Int, Int>() // contestId -> ncards
        result.forEach { cvr ->
            cvr.votes.keys.forEach{ contestId -> ncardsPerContest.merge(contestId, 1) { a, b -> a + b } }
        }

        // is this the same?
        val ncardsByContest = fcontests.associate { Pair(it.contestId, it.ncards) }
        if (ncardsByContest != ncardsPerContest) {
            println("ncardsByContest")
        }
        require( ncardsByContest == ncardsPerContest)

        val phantoms = makePhantomCvrs(contests, ncardsPerContest)
        return result + phantoms
    }

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

    var ncards = 0 // sum of nvotes and underCount
    var underCount = 0
    var phantomCount = 0
    var adjustedVotes: List<Pair<Int, Int>> = emptyList() // includes undervotes
    var trackVotesRemaining = mutableListOf<Pair<Int, Int>>()
    var votesLeft = 0

    fun resetTracker() {
        trackVotesRemaining = mutableListOf()
        trackVotesRemaining.addAll(adjustedVotes)
        votesLeft = ncards
    }

    fun makeContest(): Contest {
        this.underCount = (this.ncards * undervotePct).toInt()
        this.phantomCount = (this.ncards * phantomPct).toInt()
        val Nc = this.ncards + this.phantomCount

        val nvotes = this.ncards - underCount
        if (nvotes == 0) {
            return Contest(this.info, emptyMap(), this.ncards, 0)
        }
        val votes: List<Pair<Int, Int>> = partition(nvotes, ncands)
        var svotes = votes.sortedBy { it.second }.reversed().toMutableList()

        // adjust the margin between the first and second highest votes.
        var adjust = 100
        while (abs(adjust) > 2) {
            if (debugAdjust) println("${this.info.name} before=$svotes")
            adjust = adjust(svotes, Nc)
            if (debugAdjust) println("${this.info.name} after=$svotes adjust=$adjust")
            svotes = svotes.sortedBy { it.second }.reversed().toMutableList()
            if (debugAdjust) println()
        }
        val contest = Contest(this.info, svotes.toMap(), Nc, this.phantomCount)

        svotes.add(Pair(ncands, underCount)) // the adjusted votes include the undervotes
        this.adjustedVotes = svotes // includes the undervotes
        return contest
    }

    // maybe adjust doesnt need the undervotes?
    fun adjust(svotes: MutableList<Pair<Int, Int>>, Nc: Int): Int {
        val winner = svotes[0]
        val loser = svotes[1]
        val wantMarginDiff = roundUp(margin * Nc)
        val haveMarginDiff = (winner.second - loser.second)
        val adjust: Int = roundUp((wantMarginDiff - haveMarginDiff) * 0.5) // can be positive or negetive
        svotes[0] = Pair(winner.first, winner.second + adjust)
        svotes[1] = Pair(loser.first, loser.second - adjust)
        return adjust // will be 0 when done
    }

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

        val checkVoteCount = trackVotesRemaining.sumOf { it.second }
        require(checkVoteCount == votesLeft)
        return candidateIdx
    }

    override fun toString() = buildString {
        append("ContestTestData($contestId, ncands=$ncands, margin=${df(margin)}, $choiceFunction ncards=$ncards")
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