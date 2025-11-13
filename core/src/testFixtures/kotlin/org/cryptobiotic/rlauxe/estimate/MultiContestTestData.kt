package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.audit.CardLocationManifest
import org.cryptobiotic.rlauxe.audit.CardStyle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val debugAdjust = false

/**
 * Creates a set of contests and cardStyles, with randomly chosen candidates and margins.
 * It can create cvrs that reflect the contests' exact votes.
 * Not for OneAudit, use makeOneContestUA()
 */
data class MultiContestTestData(
    val ncontest: Int,
    val nballotStyles: Int,
    val totalBallots: Int, // including undervotes and phantoms
    val hasStyle: Boolean, // not used i think
    val marginRange: ClosedFloatingPointRange<Double> = 0.01.. 0.03,
    val underVotePctRange: ClosedFloatingPointRange<Double> = 0.01.. 0.30, // needed to set Nc
    val phantomPctRange: ClosedFloatingPointRange<Double> = 0.00..  0.005, // needed to set Nc
    val poolId: Int? = null
) {
    // generate with ballotStyles; but if hasStyle = false, then these are not visible to the audit
    val ballotStylePartition = partition(totalBallots, nballotStyles).toMap() // Map bsidx -> ncards in each ballot style (bs)

    val contestTestBuilders: List<ContestTestDataBuilder>
    val contests: List<Contest>
    val ballotStyles: List<CardStyle>
    var countBallots = 0

    init {
        require(ncontest > 0)
        require(nballotStyles > 0)
        require(totalBallots > nballotStyles * ncontest) // TODO

        // between 2 and 4 candidates, margin is a random number in marginRange
        contestTestBuilders = List(ncontest) { it }.map {// id same as index
            val ncands = max(Random.nextInt(5), 2)
            ContestTestDataBuilder(it, ncands,
                marginRange.start + if (marginRange.endInclusive <= marginRange.start) 0.0 else Random.nextDouble(marginRange.endInclusive - marginRange.start),
                underVotePctRange.start + if (underVotePctRange.endInclusive <= underVotePctRange.start) 0.0 else Random.nextDouble(underVotePctRange.endInclusive - underVotePctRange.start),
                phantomPctRange.start + if (phantomPctRange.endInclusive <= phantomPctRange.start) 0.0 else Random.nextDouble(phantomPctRange.endInclusive - phantomPctRange.start),
                // TODO ChoiceFunction ??
            )
        }

        // every contest has between 1 and 4 ballot styles, randomly chosen
        val contestBstyles = mutableMapOf<ContestTestDataBuilder, Set<Int>>() // fcontest -> set(ballot style id)
        contestTestBuilders.forEach{ fcontest ->
            val nbs = min(nballotStyles, 1 + Random.nextInt(4))
            val bset = mutableSetOf<Int>() // the ballot style idx, 0 based
            while (bset.size < nbs) { // randomly choose nbs ballot styles
                bset.add(Random.nextInt(nballotStyles))
            }
            contestBstyles[fcontest] = bset
        }

        // partition totalBallots amongst the ballotStyles
        ballotStyles = List(nballotStyles) { it }.map {
            var contestsForThisBs = contestBstyles.filter{ (fc, bset) -> bset.contains( it ) }.map { (fc, _) -> fc }
            // every ballot style needs at least one contest. just make it first contest I guess
            if (contestsForThisBs.isEmpty()) contestsForThisBs = listOf(contestTestBuilders.first())
            val contestList = contestsForThisBs.map { it.info.name }
            val contestIds = contestsForThisBs.map { it.info.id }
            val ncards = ballotStylePartition[it]!!
            countBallots += ncards
            CardStyle.make(it, contestList, contestIds, ncards)
        }
        require(countBallots == totalBallots)
        countCards()
        contests = contestTestBuilders.map { it.makeContest() }
    }

    fun countCards() {
        ballotStyles.forEach { bs ->
            bs.contestNames.forEach { contestName ->
                val contest = contestTestBuilders.find { it.info.name == contestName }!!
                contest.ncards += bs.ncards
            }
        }
    }

    fun makeCardLocationManifest(): CardLocationManifest {
        val cards = CloseableIterable { makeCardsFromContests().iterator() }
        return CardLocationManifest(cards, ballotStyles)
    }

    override fun toString() = buildString {
        append("ncontest=$ncontest, nballotStyles=$nballotStyles, totalBallots=$totalBallots")
        appendLine(" marginRange=$marginRange underVotePct=$underVotePctRange phantomPct=$phantomPctRange")
        contestTestBuilders.forEach { fcontest ->
            append("  $fcontest")
            val bs4id = ballotStyles.filter{ it.contestIds.contains(fcontest.contestId) }.map{ it.id }
            appendLine(" ballotStyles=$bs4id")
        }
        appendLine("")
        ballotStyles.forEach { appendLine("  $it") }
    }

    fun makeCvrsFromContests(): List<Cvr> {
        contestTestBuilders.forEach { it.resetTracker() } // startFresh
        val cvrbs = CvrBuilders().addContests(contestTestBuilders.map { it.info })
        val result = mutableListOf<Cvr>()
        ballotStyles.forEach { ballotStyle ->
            val fcontests = contestTestBuilders.filter { ballotStyle.contestNames.contains(it.info.name) }
            repeat(ballotStyle.ncards) {
                // add regular Cvrs including undervotes
                result.add(makeCvr(cvrbs, fcontests))
            }
        }

        val phantoms = makePhantomCvrs(contests)
        return result + phantoms
    }

    private fun makeCvr(cvrbs: CvrBuilders, fcontests: List<ContestTestDataBuilder>): Cvr {
        val cvrb = cvrbs.addCvr()
        fcontests.forEach { fcontest -> fcontest.addContestToCvr(cvrb) }
        return cvrb.build(poolId)
    }

    // multicontest cvrs
    // create new partitions each time this is called
    // includes undervotes and phantoms, size = totalBallots + phantom count
    /* fun makeCardsFromContests(): List<AuditableCard> {
        contestTestBuilders.forEach { it.resetTracker() } // startFresh
        val cvrbs = CardBuilders().addContests(contestTestBuilders.map { it.info })
        val result = mutableListOf<AuditableCard>()
        ballotStyles.forEach { ballotStyle ->
            val fcontests = contestTestBuilders.filter { ballotStyle.contestNames.contains(it.info.name) }
            repeat(ballotStyle.ncards) {
                // add regular Cvrs including undervotes
                result.add(makeCard(cvrbs, fcontests))
            }
        }

        val phantoms = makePhantomCards(contests, result.size)
        return result + phantoms
    } */

    fun makeCardsFromContests(startCvrId : Int = 0): List<AuditableCard> {
        contestTestBuilders.forEach { it.resetTracker() } // startFresh

        var nextCardId = startCvrId
        val result = mutableListOf<AuditableCard>()
        ballotStyles.forEach { ballotStyle ->
            val fcontests = contestTestBuilders.filter { ballotStyle.contestNames.contains(it.info.name) }
            repeat(ballotStyle.ncards) {
                // add regular Cvrs including undervotes
                result.add(makeCard(nextCardId++, fcontests))
            }
        }

        val phantoms = makePhantomCards(contests, startIdx=result.size)
        return result + phantoms
    }

    private fun makeCard(nextCardId: Int, fcontests: List<ContestTestDataBuilder>): AuditableCard {
        val cardBuilder = CardBuilder("card${nextCardId}", nextCardId)
        fcontests.forEach { fcontest -> fcontest.addContestToCard(cardBuilder) }
        return cardBuilder.build(poolId)
    }
}

// This creates a multicandidate contest with the two closest candidates having exactly the given margin.
// It can create cvrs that exactly reflect this contest's vote; so can be used in simulating the audit.
// The cvrs are not multicontest.
data class ContestTestDataBuilder(
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
    var adjustedVotes: List<Pair<Int, Int>> = emptyList() // (cand, nvotes) includes undervotes
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
            return Contest(this.info, emptyMap(), Nc, this.ncards)
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
        val contest = Contest(this.info, svotes.toMap(), Nc, this.ncards)

        svotes.add(Pair(ncands, underCount)) // the adjusted votes include the undervotes TODO check this
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

    // choose Candidate, add contest, including undervote
    fun addContestToCard(cvrb: CardBuilder) {
        val candidateIdx = chooseCandidate(Random.nextInt(votesLeft))
        if (candidateIdx == ncands) {
            cvrb.replaceContestVote(info.id, null) // undervote
        } else {
            cvrb.replaceContestVote(info.id, info.candidateIds[candidateIdx])
        }
    }

    // choose Candidate, add contest, including undervote
    fun addContestToCvr(cvrb: CvrBuilder) {
        val candidateIdx = chooseCandidate(Random.nextInt(votesLeft))
        if (candidateIdx == ncands) {
            cvrb.addContest(info.name) // undervote
        } else {
            cvrb.addContest(info.name, info.candidateIds[candidateIdx])
        }
    }

    // choice is a number from 0..votesLeft
    // shrink the partition as votes are taken from it
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
// return map partitionIdx -> nvotes, where sum(nvotes) = nthings NOTE partitionIdx not id!!
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