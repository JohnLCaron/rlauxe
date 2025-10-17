package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.tabulateBallotPools
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import org.cryptobiotic.rlauxe.util.margin2mean
import kotlin.collections.forEach


private val logger = KotlinLogging.logger("CardPool")

// this is really CardPoolForContest: used for serialization to csv
data class BallotPool(
    val name: String,
    val poolId: Int,
    val contestId :Int,
    val ncards: Int,          // ncards for this contest in this pool; TODO hasStyles = false?
    val votes: Map<Int, Int>, // candid -> nvotes, for plurality. TODO add undervotes ??
) {

    fun calcReportedMargin(winner: Int, loser: Int): Double {
        if (ncards == 0) return 0.0
        val winnerVote = votes[winner] ?: 0
        val loserVote = votes[loser] ?: 0
        return (winnerVote - loserVote) / ncards.toDouble()
    }

    fun votesAndUndervotes(voteForN: Int, ncandidates: Int): Map<Int, Int> {
        val poolVotes = votes.values.sum()
        val poolUndervotes = ncards * voteForN - poolVotes
        return (votes.map { Pair(it.key, it.value)} + Pair(ncandidates, poolUndervotes)).toMap()
    }

    fun votesAndUndervotes(voteForN: Int): VotesAndUndervotes {
        val poolUndervotes = ncards * voteForN - votes.values.sum()
        return VotesAndUndervotes(votes, poolUndervotes, voteForN)
    }

    fun reportedAverage(winner: Int, loser: Int): Double {
        val winnerVotes = votes[winner] ?: 0
        val loserVotes = votes[loser] ?: 0
        val reportedMargin = (winnerVotes - loserVotes) / ncards.toDouble() // TODO dont know Nc
        return margin2mean(reportedMargin)
    }
}

fun addOAClcaAssortersFromMargin(
    oaContests: List<OAContestUnderAudit>,
    balotPools: List<BallotPool>,
) {
    oaContests.forEach { oaUA ->
        val clcaAssertions = oaUA.pollingAssertions.map { assertion ->
            val passort = assertion.assorter
            val pairs = balotPools.filter{ it.contestId == oaUA.id }.map { pool ->
                val avg = pool.reportedAverage(passort.winner(), passort.loser())
                // println("pool ${pool.poolId} avg $avg")
                Pair(pool.poolId, avg)
            }
            val poolAvgs = AssortAvgsInPools(pairs.toMap())
            val clcaAssertion = OneAuditClcaAssorter(assertion.info, passort, true, poolAvgs)
            ClcaAssertion(assertion.info, clcaAssertion)
        }
        oaUA.clcaAssertions = clcaAssertions
    }
}

fun List<BallotPool>.toCardPools(infos: Map<Int, ContestInfo>) : List<CardPoolIF> {
    val reaggs = mutableMapOf<Int, MutableList<BallotPool>>()
    this.forEach { pool ->
        val reagg = reaggs.getOrPut(pool.poolId) { mutableListOf() }
        reagg.add(pool)
    }

    val cardPoolMap = reaggs.mapValues { (poolId, ballotPools) ->
        val contestTabs = tabulateBallotPools(ballotPools.iterator(), infos) // contestId - contestTab
        val voteTotals = contestTabs.mapValues { it.value.votes }

        // TODO assumes that ncards is the same for all ballot pools. wont be true for CardPoolFromCvrs
        //   might be easier to just serialize CardPool instead of BallotPool
        CardPoolWithBallotStyle(ballotPools[0].name, poolId, voteTotals, infos)
    }

    println(cardPoolMap)
    return cardPoolMap.map { it.value }
}

/////////////////////////////////////////////////
/*
class CardPoolsFromBallotPools(
    val ballotPools: List<BallotPool>,
    val infos: Map<Int, ContestInfo>) {

    val cardPoolMap: Map<Int, CardPoolFromBallotPools> // poolId -> pool

    init {
        val reaggs = mutableMapOf<Int, MutableList<BallotPool>>()
        ballotPools.forEach { pool ->
            val reagg = reaggs.getOrPut(pool.poolId) { mutableListOf() }
            reagg.add(pool)
        }
        cardPoolMap = reaggs.mapValues { (poolId, ballotPools) ->
            val voteTotals = ballotPools.associate { Pair(it.contestId, it.votes) }
            // TODO assumes that ncards is the same for all ballot pools
            CardPoolFromBallotPools(ballotPools[0].name, poolId, voteTotals, ballotPools[0].ncards)
        }
    }

    fun showPoolVotes(width: Int = 4) = buildString {
        val contestIds = infos.values.map { it.id }.sorted()
        appendLine("votes, undervotes")
        append("${trunc("poolName", 9)}:")
        contestIds.forEach { append("${nfn(it, width)}|") }
        appendLine()

        cardPoolMap.values.filter { it.poolName != unpooled}.forEach { cardpool ->
            appendLine(cardpool.showVotes(contestIds, width))
        }

        // TODO add sums
    }

    inner class CardPoolFromBallotPools(
        val poolName: String,
        override val poolId: Int,
        val voteTotals: Map<Int, Map<Int, Int>>,
        val ncards: Int,
    ) : CardPoolIF {
        // TODO fill this in from the margins?? or get rid of ??
        override val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

        override fun regVotes() = voteTotals.mapValues { RegVotesImpl(it.value, ncards) }
        override fun ncards() = ncards
        override fun contains(contestId: Int) = voteTotals.containsKey(contestId)

        fun showVotes(contestIds: Collection<Int>, width: Int = 4) = buildString {
            append("${trunc(poolName, 9)}:")
            contestIds.forEach { id ->
                val contestVote = voteTotals[id]
                if (contestVote == null)
                    append("    |")
                else {
                    val sum = contestVote.map { it.value }.sum()
                    append("${nfn(sum, width)}|")
                }
            }
            appendLine()

            val undervotes = undervotes()
            append("${trunc("", 9)}:")
            contestIds.forEach { id ->
                val contestVote = voteTotals[id]
                if (contestVote == null)
                    append("    |")
                else {
                    val undervote = undervotes[id]!!
                    append("${nfn(undervote, width)}|")
                }
            }
            appendLine()
        }

        fun undervotes(): Map<Int, Int> {  // contest -> undervote
            val undervote = voteTotals.map { (id, cands) ->
                val sum = cands.map { it.value }.sum()
                val info = infos[id]!!
                Pair(id, ncards * info.voteForN - sum)
            }
            return undervote.toMap().toSortedMap()
        }
    }
} */

