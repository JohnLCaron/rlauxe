package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCardManifestIterator
import org.cryptobiotic.rlauxe.audit.BatchIF
import org.cryptobiotic.rlauxe.audit.CardWithBatchName
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.audit.CvrsToCardsWithBatchNameIterator
import kotlin.Int
import kotlin.String
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val debugAdjust = false

// (mvrs, cards, pools, styles)
data class MvrCardAndPops(val mvrs: List<Cvr>, val cards: List<AuditableCard>, val pools: List<CardPoolIF>, val batches: List<BatchIF>)

/**
 * Creates a set of contests and populations, with randomly chosen candidates and margins.
 * It can create cvrs that reflect the contests' exact votes.
 * TODO Not for OneAudit, use makeOneAuditTest()
 * TODO use Vunder, which also models contests missing on a card
 * TODO rewrite
 */
data class MultiContestTestData(
    val ncontest: Int,
    val nballotStyles: Int,
    val totalBallots: Int, // including undervotes and phantoms
    val marginRange: ClosedFloatingPointRange<Double> = 0.01.. 0.03,
    val underVotePctRange: ClosedFloatingPointRange<Double> = 0.01.. 0.30, // needed to set Nc
    val phantomPctRange: ClosedFloatingPointRange<Double> = 0.00..  0.005, // needed to set Nc
    val ncands: Int? = null,
    // val poolPctForTestData: Double? = null,  // if not null, make a pool with this pct with two ballotStyles
    val seqCands: Boolean = false, // if true, use ncands = 2 .. ncontests + 1
    val hasSingleCardStyle: Boolean = false, // if true, use ncands = 2 .. ncontests + 1
    val auditType: AuditType = AuditType.CLCA,
) {
    // val poolId = if (poolPctForTestData == null) null else 1
    val ballotStylePartition: Map<Int, Int>

    val contestTestBuilders: List<ContestTestDataBuilder>
    val contests: List<Contest>
    val cardStyles: List<CardStyle>
    var countBallots = 0

    init {
        require(ncontest > 0)
        require(nballotStyles > 0)
        require(totalBallots > nballotStyles * ncontest) // TODO

        ballotStylePartition = partition(totalBallots, nballotStyles).toMap() // Map bsidx -> ncards in each ballot style (bs)
        // ballotStylePartition = if (poolPctForTestData == null) partition(totalBallots, nballotStyles).toMap() // Map bsidx -> ncards in each ballot style (bs)
        //    else partitionWithPool(totalBallots, nballotStyles, poolPctForTestData).toMap()

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
        cardStyles = List(nballotStyles) { it }.map { idx ->
            var contestsForThisBs = contestBstyles.filter{ (fc, bset) -> bset.contains( idx ) }
                .map { (fc, _) -> fc }

            // every ballot style needs at least one contest. just make it first contest I guess
            if (contestsForThisBs.isEmpty()) contestsForThisBs = listOf(contestTestBuilders.first())
            val contestIds = contestsForThisBs.map { it.info.id }
            val ncards = ballotStylePartition[idx]!!
            countBallots += ncards
            CardStyle("style$idx", idx, contestIds.toIntArray(), hasSingleCardStyle, ncards)
        }
        require(countBallots == totalBallots)
        countCards()
        contests = contestTestBuilders.map { it.makeContest() }
    }

    // set contest.ncards
    fun countCards() {
        cardStyles.forEach { bs ->
            bs.contests.forEach { contestId ->
                val contest = contestTestBuilders.find { it.info.id == contestId }!!
                contest.ncards += bs.ncards
            }
        }
    }

    // (mvrs, cards, pools, styles)
    fun makeMvrCardAndPops(): MvrCardAndPops {
        // these are the mvr truth
        val mvrs: List<Cvr> = makeCvrsFromContests()

        // the union of the first two styles TODO wtf

        /* expand the two cardStyles
        val expandedContestIds = (cardStyles[0].contests + cardStyles[1].contests).toSet().sorted().toIntArray()
        val expandedCardStyles = listOf(
            cardStyles[0].copy(contests = expandedContestIds),
            cardStyles[1].copy(contests = expandedContestIds),
        ) */

        // convert MVRS to cardWithBatchName
        val batchNameIter: CloseableIterator<CardWithBatchName> = CvrsToCardsWithBatchNameIterator(
            auditType,
            cvrs = Closer( mvrs.iterator()),  // hmmm fishy
            phantomCvrs = null, // mvrs already have the phantoms
            batches = cardStyles,
        )

        // cardWithBatchName to AuditableCard
        val cards = mutableListOf<AuditableCard>()
        val converter: CloseableIterator<AuditableCard> = MergeBatchesIntoCardManifestIterator(
            batchNameIter,
            batches = cardStyles,
        )
        converter.forEach { it ->
            cards.add(it)
        }

        /* we need to populate the pool tab with the votes
        val infos = contests.associate { it.id to it.info() }
        val pool = OneAuditPoolFromCvrs("pool", 1, false, infos)
        expandedContestIds.forEach { id -> pool.contestTabs[id] = ContestTabulation(infos[id]!!) }
        mvrs.forEach { mvr ->
            if (mvr.poolId == 1) pool.accumulateVotes(mvr)
        } */

        return MvrCardAndPops(mvrs, cards, emptyList(), cardStyles)
    }

    override fun toString() = buildString {
        append("ncontest=$ncontest, nballotStyles=$nballotStyles, totalBallots=$totalBallots")
        appendLine(" marginRange=$marginRange underVotePct=$underVotePctRange phantomPct=$phantomPctRange")
        contestTestBuilders.forEach { fcontest ->
            append("  $fcontest")
            val bs4id = cardStyles.filter{ it.hasContest(fcontest.contestId) }.map{ it.name }
            appendLine(" ballotStyles=$bs4id")
        }
        appendLine("")
        cardStyles.forEach { appendLine("  $it") }
    }

    fun makeCvrsFromContests(): List<Cvr> {
        contestTestBuilders.forEach { it.resetTracker() } // startFresh
        val cvrbs = CvrBuilders().addContests(contestTestBuilders.map { it.info })
        val result = mutableListOf<Cvr>()
        cardStyles.forEach { cardStyle ->
            val fcontests = contestTestBuilders.filter { cardStyle.hasContest(it.info.id) }
            repeat(cardStyle.ncards) {
                // add regular Cvrs including undervotes
                result.add( makeCvr(cvrbs, fcontests, poolId = cardStyle.id)) // hijack poolId
            }
        }

        result.addAll(makePhantomCvrs(contests))
        result.shuffle(Random)
        return result
    }

    private fun makeCvr(cvrbs: CvrBuilders, fcontests: List<ContestTestDataBuilder>, poolId: Int?): Cvr {
        val cvrb = cvrbs.addCvr()
        fcontests.forEach { fcontest -> fcontest.addContestToCvr(cvrb) }
        return cvrb.build(poolId)
    }

    fun makeCardsFromContests(startCvrId : Int = 0): List<CardWithBatchName> {
        contestTestBuilders.forEach { it.resetTracker() } // startFresh

        var nextCardId = startCvrId
        val result = mutableListOf<CardWithBatchName>()
        cardStyles.forEach { cardStyle ->
            val fcontests = contestTestBuilders.filter { cardStyle.hasContest(it.info.id) }
            repeat(cardStyle.ncards) {
                // val poolId = if ((poolPctForTestData != null) && cardStyle.id  < 2) 1 else null
                result.add(makeCard(nextCardId++, fcontests, poolId = cardStyle.id, cardStyle=cardStyle.name))
            }
        }

        result.addAll(makePhantomNoBatch(contests, startIdx = result.size))
        result.shuffle(Random)
        return result
    }

    private fun makeCard(nextCardId: Int, fcontests: List<ContestTestDataBuilder>, poolId: Int?, cardStyle: String?): CardWithBatchName {
        val cardBuilder = CardWithBatchNameBuilder("card${nextCardId}", nextCardId, poolId, cardStyle)
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
    fun addContestToCard(cvrb: CardWithBatchNameBuilder) {
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

data class CardStyle(
    val name: String,
    val id: Int,
    val contests: IntArray,
    val hasSingleCardStyle: Boolean,
    val ncards: Int
): BatchIF {
    override fun name() = name
    override fun id() = id
    override fun possibleContests() = contests
    override fun hasSingleCardStyle() = hasSingleCardStyle
    override fun hasContest(contestId: Int) = contests.contains(contestId)
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