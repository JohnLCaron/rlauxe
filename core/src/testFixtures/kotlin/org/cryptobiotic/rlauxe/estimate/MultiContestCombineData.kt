package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.BallotPool
import org.cryptobiotic.rlauxe.audit.BallotStyle
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.random.Random

// specify the contests with exact number of votes
data class MultiContestCombineData(
    val contests: List<Contest>,
    val totalBallots: Int, // including undervotes and phantoms
    val poolId: Int? = null,
) {
    val contestVoteTrackers: List<ContestVoteTracker>
    val batch = if (poolId == null) CardStyle.fromCvrBatch
        else CardStyle("batch$poolId", poolId, contests.map { it.id }.toIntArray(), false)

    init {
        require(contests.size > 0)
        contestVoteTrackers = contests.map { ContestVoteTracker(it) }
    }

    // multicontest cvrs
    // create new partitions each time this is called
    // includes undervotes and phantoms, size = totalBallots + phantom count
    fun makeCardsFromContests(startCvrId : Int = 0, cardStyle:String?=null): Pair<List<AuditableCard>, List<CardStyle>> {
        contestVoteTrackers.forEach { it.resetTracker() } // startFresh

        var nextCardId = startCvrId
        val result = mutableListOf<AuditableCard>()
        repeat(totalBallots) {
            // add regular Cvrs including undervotes and phantoms
            result.add( makeCard(nextCardId++, contestVoteTrackers, cardStyle))
        }

        val phantoms = makePhantomCards(contests, startIdx = result.size)
        result.addAll(phantoms)
        // result.shuffle(Random)
        return Pair(result, listOf(batch))
    }

    private fun makeCard(nextCardId: Int, fcontests: List<ContestVoteTracker>, cardStyle:String?): AuditableCard {
        //         constructor(location: String, index: Int, poolId: Int?, cardStyle: String?):
        val cardBuilder = AuditableCardBuilder("card${nextCardId}", null, nextCardId, 0, false, cardStyle = batch)
        fcontests.forEach { fcontest -> fcontest.addContestToCard(cardBuilder) }
        return cardBuilder.build()
    }
}

data class ContestVoteTracker(
    val contest: Contest,
) {
    val info = contest.info
    val ncands = info.candidateIds.size
    val candIdToIdx = info.candidateIds.mapIndexed { idx, id -> Pair(id, idx) }.toMap()

    var trackVotesRemaining = mutableListOf<Pair<Int, Int>>()
    var votesLeft = 0

    fun resetTracker() {
        trackVotesRemaining = mutableListOf()
        contest.votes.forEach{ (candId, votes) -> trackVotesRemaining.add( Pair(candIdToIdx[candId]!!, votes)) }
        trackVotesRemaining.add( Pair(ncands, contest.Nundervotes()))
        votesLeft = contest.Ncast
    }

    // choose Candidate, add contest, including undervote
    fun addContestToCvr(cvrb: CvrBuilder) {
        if (votesLeft == 0) return
        val candidateIdx = chooseCandidate(Random.nextInt(votesLeft))
        if (candidateIdx == ncands) {
            cvrb.addContest(info.name) // undervote
        } else {
            cvrb.addContest(info.name, info.candidateIds[candidateIdx])
        }
    }

    // choose Candidate, add contest, including undervote
    fun addContestToCard(cvrb: AuditableCardBuilder) {
        if (votesLeft == 0)
            return
        val candidateIdx = chooseCandidate(Random.nextInt(votesLeft))
        if (candidateIdx == ncands) {
            cvrb.replaceContestVote(info.id, null) // undervote
        } else {
            cvrb.replaceContestVote(info.id, info.candidateIds[candidateIdx])
        }
    }
    fun addContestToCardNoBatch(cvrb: CardWithBatchNameBuilder) {
        if (votesLeft == 0)
            return
        val candidateIdx = chooseCandidate(Random.nextInt(votesLeft))
        if (candidateIdx == ncands) {
            cvrb.replaceContestVote(info.id, null) // undervote
        } else {
            cvrb.replaceContestVote(info.id, info.candidateIds[candidateIdx])
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
}

// specify the contests with exact number of votes and use pools to generate CVRs
data class MultiContestCombinePools(
    val contests: List<Contest>,
    val totalBallots: Int, // including undervotes and phantoms
    val pools: List<CardPool>,
) {
    val vunderPools = pools.map { pool ->
        val vunders = pool.possibleContests().associate { contestId ->
            Pair( contestId, pool.votesAndUndervotes(contestId))
        }
        VunderPool(vunders, pool.poolName, pool.poolId, pool.hasExactContests)
    }
    val poolMap = pools.associateBy { it.poolId }

    // multicontest cards
    fun makeCardsFromContests(): Pair<List<AuditableCard>, List<StyleIF>> {
        var nextCardId = 0
        val result = mutableListOf<AuditableCard>()

        vunderPools.forEach { vunderPool ->
            val pool = poolMap[vunderPool.poolId]!!
            repeat(pool.ncards()) {
                // add regular Cvrs including undervotes and phantoms
                result.add( makeCard(nextCardId++, vunderPool, pool))
            }
        }

        val phantoms = makePhantomCards(contests, startIdx = result.size)
        result.addAll(phantoms)
        return Pair(result, pools)
    }

    private fun makeCard(nextCardId: Int, vunderPool: VunderPool, pool:CardPool): AuditableCard {
        val cvrb2 = CvrBuilder2("card${nextCardId}", false, poolId = pool.id())
        vunderPool.simulatePooledCvr(cvrb2)
        val cvr = cvrb2.build()
        return AuditableCard("card${nextCardId}", null, nextCardId, 0L, false, null, cvr.votes, style = pool)

    }
}

///////////////////////////////////////////////////////////////////////////////
enum class CSDtype { cardStyles, ballotStyles, noStyles}

class PoolBuilder(val ballotStyle: BallotStyle) {
    val tabs = mutableMapOf<Int, ContestTabulation>()
}

data class MultiContestFromBallotStyles(
    val contests: List<Contest>,
    val ballotStyles: List<BallotStyle>,
    val csd: CSDtype
) {
    val ballotPools: List<BallotPool>
    val vunderPoolsMap: Map<Int, Map<Int, VunderPool>> // ballotPoolId -> CardPoolId -> VunderPool
    val cardPools: List<CardPool>
    val noCardStyle: CardStyle

    init {
        val pbm = ballotStyles.associate { it.id to PoolBuilder(it) }
        val infos = contests.associate { it.id to it.info() }

        val contestSplits = mutableMapOf<Int, MutableList<BallotStyle>>()
        ballotStyles.forEach { bs -> bs.possibleContests().forEach {
                val list = contestSplits.getOrPut(it) { mutableListOf() }
                list.add(bs)
            }
        }

        contestSplits.forEach { contestId, list ->
            val contest = contests.find { it.id == contestId }!!
            if (list.size > 1) { // split contests that are in multiple ballotStyles
                val total = list.sumOf { it.nballots }
                list.forEach { bs ->
                    val frac = bs.nballots / total.toDouble()
                    val contestFrac = contest.votes.mapValues{ roundToClosest(frac * it.value) }
                    val pb = pbm[bs.id]!!
                    pb.tabs[contest.id] = ContestTabulation(infos[contest.id]!!, contestFrac, roundToClosest(frac * contest.Nc))
                }
            } else {
                val pb = pbm[list.first().id]!!
                pb.tabs[contest.id] = ContestTabulation(infos[contest.id]!!, contest.votes, contest.Nc)
            }
        }

        ballotPools = pbm.values.map { pb -> BallotPool(pb.ballotStyle, infos, pb.tabs, pb.ballotStyle.nballots) }

        vunderPoolsMap = ballotPools.associate { it.poolId to it.makeVunderPools() }

        cardPools = ballotPools.map { it.cardPools }.flatten().toSet().toList()

        //     //     val name: String,
        //    //    val id: Int,
        //    //    val possibleContests: IntArray,      // the list of possible contests.
        //    //    val hasExactContests: Boolean
        val possibleContests = contests.map { it.id }.sorted().toIntArray()
        noCardStyle = CardStyle("noCardStyle", 0, possibleContests, false)  // aka "all"
    }

    // multicontest cards
    fun makeCardsFromContests(): Pair<List<AuditableCard>, List<StyleIF>> {
        var nextCardIdx = 0
        var nextBallotIdx = 0
        val result = mutableListOf<AuditableCard>()

        ballotPools.forEach { ballotPool ->
            val vunderPoolMap = vunderPoolsMap[ballotPool.poolId]!!

            // for each ballot we have to make a card for each CardPool
            repeat(ballotPool.nballots) {
                ballotPool.cardPools.forEachIndexed { cardIndex, cardPool ->
                    val vunderPool = vunderPoolMap[cardPool.poolId]!!
                    val cardStyle = when (csd) {
                        CSDtype.cardStyles -> cardPool
                        CSDtype.ballotStyles -> ballotPool
                        else -> noCardStyle
                    }
                    val cardName = "${ballotPool.name()}-ballot$nextBallotIdx-card$cardIndex"
                    result.add(makeCard(cardName, nextCardIdx, vunderPool, cardStyle))
                    nextCardIdx++
                }
                nextBallotIdx++
            }
        }

        val phantoms = makePhantomCards(contests, startIdx = result.size)
        result.addAll(phantoms)

        val cardStyles = when (csd) {
            CSDtype.cardStyles -> cardPools
            CSDtype.ballotStyles -> ballotPools
            else -> listOf(noCardStyle)
        }
        return Pair(result, cardStyles)
    }

    private fun makeCard(cardName: String, cardId: Int, vunderPool: VunderPool, cardStyle:StyleIF): AuditableCard {
        val cvrb2 = CvrBuilder2(cardName, false, poolId = cardStyle.id())
        vunderPool.simulatePooledCvr(cvrb2)
        val cvr = cvrb2.build()
        return AuditableCard(cardName, null, cardId, 0L, false, null, cvr.votes, style = cardStyle)
    }
}