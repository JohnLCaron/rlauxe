package org.cryptobiotic.rlauxe.estimate

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.BallotPool
import org.cryptobiotic.rlauxe.audit.BallotStyle
import org.cryptobiotic.rlauxe.audit.Batch
import org.cryptobiotic.rlauxe.audit.BatchIF
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.shuffle
import kotlin.random.Random

// specify the contests with exact number of votes
data class MultiContestCombineData(
    val contests: List<Contest>,
    val totalBallots: Int, // including undervotes and phantoms
    val poolId: Int? = null,
) {
    val contestVoteTrackers: List<ContestVoteTrackerOld>
    val batch = if (poolId == null) Batch.fromCvrBatch
        else Batch("batch$poolId", poolId, contests.map { it.id }.toIntArray(), false)

    init {
        require(contests.size > 0)
        contestVoteTrackers = contests.map { ContestVoteTrackerOld(it) }
    }

    // multicontest cvrs
    // create new partitions each time this is called
    // includes undervotes and phantoms, size = totalBallots + phantom count
    fun makeCardsFromContests(startCvrId : Int = 0, cardStyle:String?=null): Pair<List<AuditableCard>, List<Batch>> {
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

    private fun makeCard(nextCardId: Int, fcontests: List<ContestVoteTrackerOld>, cardStyle:String?): AuditableCard {
        //         constructor(location: String, index: Int, poolId: Int?, cardStyle: String?):
        val cardBuilder = AuditableCardBuilder("card${nextCardId}", nextCardId, 0, false, null, batch = batch)
        fcontests.forEach { fcontest -> fcontest.addContestToCard(cardBuilder) }
        return cardBuilder.build()
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
        VunderPool(vunders, pool.poolName, pool.poolId, pool.hasSingleCardStyle)
    }
    val poolMap = pools.associateBy { it.poolId }

    // multicontest cards
    fun makeCardsFromContests(): Pair<List<AuditableCard>, List<BatchIF>> {
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
        return AuditableCard("card${nextCardId}", nextCardId, 0L, false, cvr.votes, batch = pool)

    }
}

///////////////////////////////////////////////////////////////////////////////
class PoolBuilder(val ballotStyle: BallotStyle) {
    val tabs = mutableMapOf<Int, ContestTabulation>()
}

data class MultiContestFromBallotStyles(
    val contests: List<Contest>,
    val ballotStyles: List<BallotStyle>,
) {
    val ballotPools: List<BallotPool>
    val vunderPoolsMap: Map<Int, Map<Int, VunderPool>> // ballotPoolId -> CardPoolId -> VunderPool
    val cardPools: List<CardPool>

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

        /*
        pools = pbm.values.map { pb ->
            CardPool(pb.ballotStyle.name, pb.ballotStyle.id, pb.ballotStyle.hasSingleCardStyle, infos,
                pb.tabs, totalCards = pb.ballotStyle.nballots
            )
        }
        poolMap = pools.associateBy { it.poolId }

        vunderPools = pools.map { pool ->
            val vunders = pool.possibleContests().associate { contestId ->
                Pair( contestId, pool.votesAndUndervotes(contestId))
            }
            VunderPool(vunders, pool.poolName, pool.poolId, pool.hasSingleCardStyle)
        } */
    }

    // multicontest cards
    fun makeCardsFromContests(): Pair<List<AuditableCard>, List<BatchIF>> {
        var nextCardId = 0
        val result = mutableListOf<AuditableCard>()

        ballotPools.forEach { ballotPool ->
            val vunderPoolMap = vunderPoolsMap[ballotPool.poolId]!!

            // for each ballot we have to make a card for each CardPool
            repeat(ballotPool.nballots) {
                ballotPool.cardPools.forEach { cardPool ->
                    val vunderPool = vunderPoolMap[cardPool.poolId]!!
                    result.add(makeCard(nextCardId++, vunderPool, cardPool))
                }
            }
        }

        val phantoms = makePhantomCards(contests, startIdx = result.size)
        result.addAll(phantoms)
        return Pair(result, cardPools)
    }

    private fun makeCard(nextCardId: Int, vunderPool: VunderPool, pool:CardPool): AuditableCard {
        val cvrb2 = CvrBuilder2("card${nextCardId}", false, poolId = pool.id())
        vunderPool.simulatePooledCvr(cvrb2)
        val cvr = cvrb2.build()
        return AuditableCard("card${nextCardId}", nextCardId, 0L, false, cvr.votes, batch = pool)
    }
}