package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.audit.MergePopulationsIntoCards
import org.cryptobiotic.rlauxe.audit.Population
import org.cryptobiotic.rlauxe.util.makePhantomCards
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.workflow.CardManifest
import kotlin.Int
import kotlin.String
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val debugAdjust = false

data class MvrCardAndPops(val mvrs: List<Cvr>, val cardManifest: List<AuditableCard>, val pools: List<OneAuditPoolIF>, val styles: List<Population>)

/**
 * Creates a set of contests and populations, with randomly chosen candidates and margins.
 * It can create cvrs that reflect the contests' exact votes.
 * Not for OneAudit, use makeOneAuditTest()
 * TODO use Vunder, which also models contests missing on a card
 */
data class MultiContestTestData(
    val ncontest: Int,
    val nballotStyles: Int,
    val totalBallots: Int, // including undervotes and phantoms
    val marginRange: ClosedFloatingPointRange<Double> = 0.01.. 0.03,
    val underVotePctRange: ClosedFloatingPointRange<Double> = 0.01.. 0.30, // needed to set Nc
    val phantomPctRange: ClosedFloatingPointRange<Double> = 0.00..  0.005, // needed to set Nc
    val addPoolId: Boolean = false, // add cardStyle info to cvrs and cards
    val ncands: Int? = null,
    val poolPct: Double? = null,  // if not null, make a pool with this pct with two ballotStyles
    val seqCands: Boolean = false // if true, use ncands = 2 .. ncontests + 1
) {
    val poolId = if (poolPct == null) null else 1
    val ballotStylePartition: Map<Int,Int>

    val contestTestBuilders: List<ContestTestDataBuilder>
    val contests: List<Contest>
    val populations: List<Population>
    var countBallots = 0

    init {
        require(ncontest > 0)
        require(nballotStyles > 0)
        require(totalBallots > nballotStyles * ncontest) // TODO

        ballotStylePartition = if (poolPct == null) partition(totalBallots, nballotStyles).toMap() // Map bsidx -> ncards in each ballot style (bs)
            else partitionWithPool(totalBallots, nballotStyles, poolPct).toMap()

        // between 2 and 4 candidates, margin is a random number in marginRange
        contestTestBuilders = List(ncontest) { it }.map {// id same as index
            var useNcands = ncands ?: max(Random.nextInt(5), 2)
            if (seqCands) useNcands = it + 2
            ContestTestDataBuilder(it, useNcands,
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
            fcontest.ballotStyles = bset
            contestBstyles[fcontest] = bset
        }

        // partition totalBallots amongst the ballotStyles
        populations = List(nballotStyles) { it }.map { idx ->
            var contestsForThisBs = contestBstyles.filter{ (fc, bset) -> bset.contains( idx ) }
                .map { (fc, _) -> fc }

            // every ballot style needs at least one contest. just make it first contest I guess
            if (contestsForThisBs.isEmpty()) contestsForThisBs = listOf(contestTestBuilders.first())
            val contestIds = contestsForThisBs.map { it.info.id }
            val ncards = ballotStylePartition[idx]!!
            countBallots += ncards

            Population("style$idx", idx, contestIds.toIntArray(), false).setNcards(ncards)
        }
        require(countBallots == totalBallots)
        countCards()
        contests = contestTestBuilders.map { it.makeContest() }
    }

    // set contest.ncards
    fun countCards() {
        populations.forEach { bs ->
            bs.possibleContests().forEach { contestId ->
                val contest = contestTestBuilders.find { it.info.id == contestId }!!
                contest.ncards += bs.ncards
            }
        }
    }

    // mvrs, cardPool, cardStyle
    fun makeCardPoolManifest(): MvrCardAndPops {
        // these are the mvr truth
        val mvrs = makeCardsFromContests()

        // the union of the first two styles
        val expandedContestIds = (populations[0].possibleContests() + populations[1].possibleContests()).toSet().sorted().toIntArray()

        val infos = contests.associate { it.id to it.info() }

        // expand the two cardStyles
        val expandedCardStyles = listOf(
            populations[0].copy(possibleContests = expandedContestIds),
            populations[1].copy(possibleContests = expandedContestIds),
        )

        // here we put the pool data into a single pool, and combine their contestIds, to get a diluted margin for testing
        val cardManifest = mutableListOf<AuditableCard>()
        val converter = MergePopulationsIntoCards(
            cards = mvrs,
            expandedCardStyles,
        )
        converter.forEach { cardManifest.add(it) }

        // we need to populate the pool tab with the votes
        val pool = OneAuditPoolFromCvrs("pool", 1, false, infos)
        expandedContestIds.forEach { id -> pool.contestTabs[id] = ContestTabulation(infos[id]!!) }
        mvrs.forEach { card ->
            if (card.poolId == 1) pool.accumulateVotes(card.cvr())
        }

        // TODO kludge
        val mvrsAsCvr = mvrs.map { it.cvr() }

        return MvrCardAndPops(mvrsAsCvr, cardManifest, listOf(pool), populations)
    }

    fun makeCardLocationManifest(): CardManifest {
        val cards = makeCardsFromContests()
        return CardManifest(CloseableIterable { cards.iterator() }, cards.size, populations)
    }

    override fun toString() = buildString {
        append("ncontest=$ncontest, nballotStyles=$nballotStyles, totalBallots=$totalBallots")
        appendLine(" marginRange=$marginRange underVotePct=$underVotePctRange phantomPct=$phantomPctRange")
        contestTestBuilders.forEach { fcontest ->
            append("  $fcontest")
            val bs4id = populations.filter{ it.hasContest(fcontest.contestId) }.map{ it.name }
            appendLine(" ballotStyles=$bs4id")
        }
        appendLine("")
        populations.forEach { appendLine("  $it") }
    }

    // TODO not positive I have the poolId correct
    fun makeCvrsFromContests(): List<Cvr> {
        contestTestBuilders.forEach { it.resetTracker() } // startFresh
        val cvrbs = CvrBuilders().addContests(contestTestBuilders.map { it.info })
        val result = mutableListOf<Cvr>()
        populations.forEach { cardStyle ->
            val fcontests = contestTestBuilders.filter { cardStyle.hasContest(it.info.id) }
            repeat(cardStyle.ncards) {
                // add regular Cvrs including undervotes
                val poolId = if ((poolPct != null) && cardStyle.id  < 2) 1 else null
                result.add(makeCvr(cvrbs, fcontests, poolId = poolId)) // TODO always just add ??
            }
        }

        val phantoms = makePhantomCvrs(contests)
        return result + phantoms
    }

    private fun makeCvr(cvrbs: CvrBuilders, fcontests: List<ContestTestDataBuilder>, poolId: Int?): Cvr {
        val cvrb = cvrbs.addCvr()
        fcontests.forEach { fcontest -> fcontest.addContestToCvr(cvrb) }
        return cvrb.build(poolId)
    }

    fun makeCardsFromContests(startCvrId : Int = 0): List<AuditableCard> {
        contestTestBuilders.forEach { it.resetTracker() } // startFresh

        var nextCardId = startCvrId
        val result = mutableListOf<AuditableCard>()
        populations.forEach { cardStyle ->
            val fcontests = contestTestBuilders.filter { cardStyle.hasContest(it.info.id) }
            repeat(cardStyle.ncards) {
                val poolId = if ((poolPct != null) && cardStyle.id  < 2) 1 else null
                result.add(makeCard(nextCardId++, fcontests, poolId = poolId, cardStyle=cardStyle.name))
            }
        }

        result.addAll(makePhantomCards(contests, startIdx = result.size))
        result.shuffle(Random)
        return result
    }

    private fun makeCard(nextCardId: Int, fcontests: List<ContestTestDataBuilder>, poolId: Int?, cardStyle: String?): AuditableCard {
        val cardBuilder = CardBuilder("card${nextCardId}", nextCardId, poolId, cardStyle)
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

    var ballotStyles: Set<Int> = emptySet()
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
        append("ContestTestData($contestId, ncands=$ncands, margin=${df(margin)}, $choiceFunction ncards=$ncards ballotStyles=$ballotStyles")
    }
}

// partition nthings into npartitions randomly
// return map partitionIdx -> nthings in the partition, where sum(nthings in the partition) = nthings
// NOTE partitionIdx not id!!
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

fun partitionWithPool(nthings: Int, npartitions: Int, poolPct: Double): List<Pair<Int, Int>> {
    val poolSize = roundToClosest(nthings * poolPct)
    val thingsLeft = nthings - poolSize
    val partitionsLeft = npartitions - 2

    val cutpoints = List(partitionsLeft - 1) { it }.map { Pair(2 + it, poolSize + Random.nextInt(thingsLeft)) }.toMutableList()
    cutpoints.add(Pair(partitionsLeft + 1, nthings)) // add the end point

    // put the two pool partition in front, then get a list sorted by cutpoint
    val sortedCutoffs =  cutpoints.sortedBy { it.second }
    val allCutoffs = listOf(Pair(0, poolSize/2), Pair(1, poolSize)) + sortedCutoffs

    // turn that into a partition, a list partitionIdx -> nthings in the partition
    var last = 0
    val partition = mutableListOf<Pair<Int, Int>>()
    allCutoffs.forEach { ps ->
        partition.add(Pair(ps.first, ps.second - last))
        last = ps.second
    }
    return partition
}