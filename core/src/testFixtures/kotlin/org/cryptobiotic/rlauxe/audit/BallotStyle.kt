package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.util.ContestTabulation

/* From SamplePopulations.md
 * CardStyle = the full and exact list of contests on a card.
 * card.exactContests = list of contests that are on this card = CardStyle = "we know exactly what contests are on this card".
 * card.possibleContests = list of contests that might be on this card.
 * Batch = "population batch" = a distinct container of cards, from which we can retreive named cards (even if its just by an index into a sorted list).
 * batch.possibleContests = list of contests that are in this batch.
 * batch.hasExactContests = true if all cards in the batch have a single known CardStyle = "we know exactly what contests are on each card".
 */

// multicard ballot
data class BallotStyle(
    val name: String,
    val id: Int,
    val hasExactContests: Boolean,     // aka hasStyle: if all cards have exactly the contests in possibleContests
    val cardStyles: List<CardStyle>,      // one for each card; contests are disjoint
    val nballots: Int,                    // does this belong ?? or just for BallotPool
)  : StyleIF {
    val contests = mutableSetOf<Int>()
    init {
        cardStyles.forEach { cs -> cs.possibleContests.forEach { contests.add(it) } }
    }
    override fun name() = name
    override fun id() = id
    override fun hasExactContests() = hasExactContests
    override fun hasContest(contestId: Int) = contests.contains(contestId)
    override fun possibleContests() = contests.toList().sorted().toIntArray()
    fun ncards() = cardStyles.size
}

// multicard ballot pool
data class BallotPool(
    val ballotStyle: BallotStyle,
    val infos: Map<Int, ContestInfo>,
    val contestTabs: Map<Int, ContestTabulation>,  // all contests
    val nballots: Int,
): CardPoolIF {
    override fun name() = ballotStyle.name
    override fun id() = ballotStyle.id
    override fun hasExactContests() = ballotStyle.hasExactContests
    override fun ncards() = nballots * ballotStyle.ncards()

    override fun hasContest(contestId: Int) = ballotStyle.hasContest(contestId)
    override fun possibleContests() = ballotStyle.possibleContests()

    override val poolName = ballotStyle.name
    override val poolId = ballotStyle.id

    val cardPools: List<CardPool> // cardPools have disjoint contests

    init {
        cardPools = ballotStyle.cardStyles.map { cardStyle ->
            val infos = cardStyle.possibleContests.associate { it to infos[it]!! }
            val tabs = cardStyle.possibleContests.associate { it to contestTabs[it]!! }
            CardPool(
                cardStyle.name, cardStyle.id, cardStyle.hasExactContests,
                infos, tabs, totalCards = ballotStyle.nballots
            )
        }
    }

    // needed?
    override fun contestTab(contestId: Int) = contestTabs[contestId]
    override fun votesAndUndervotes(contestId: Int): Vunder {
        val contestTab = contestTabs[contestId]!!
        return contestTab.votesAndUndervotes(poolId, ncards(), hasExactContests())
    }

    // one for each CardPool
    fun makeVunderPools() : Map<Int, VunderPool> {
        return cardPools.associate { it.poolId to makeVunderPool(it) }
    }

    fun makeVunderPool(cardPool: CardPool) : VunderPool {
        val vunders = cardPool.possibleContests().associate { contestId ->
            Pair( contestId, cardPool.votesAndUndervotes(contestId))
        }
        return VunderPool(vunders, cardPool.poolName, cardPool.poolId, cardPool.hasExactContests)
    }
}

