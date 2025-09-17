package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.ContestTabulation
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.CvrExport
import org.cryptobiotic.rlauxe.raire.IrvContestVotes
import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.margin2mean
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.mutableMapOf

// candidate for removal - use CardPool insted.
data class BallotPool(
    val name: String,
    val poolId: Int,
    val contest:Int,
    val ncards: Int,          // ncards for this contest in this pool; TODO hasStyles = false?
    val votes: Map<Int, Int>, // candid -> nvotes, for plurality. umm do we really need ?
) {
    // TODO does this really agree with the average assorter?
    // this could go from -1 to 1. TODO shouldnt that be -u to u ??
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

// could serialize CardPools in a seperate file.
// record the ContestTabulation (regular) or VoteConsolidator (IRV), using the cvrs in the pool.
// TODO what if you dont have cvrs in the pool? isnt that the whole point of OneAudit?? eg Boulder.
//  then you are just given ContestTabulation (with undervote count!!)
//  if you have and IRV contest, you must be given IrvContestVotes.VoteConsolidator which is needed to calculate the
//  RaireAssertions and probably the average assort value for the pool ??
open class CardPool(
    val poolName: String,
    val poolId: Int,
    val irvIds: Set<Int>,
    val contestInfos: Map<Int, ContestInfo>)
{
    val contestTabulations = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
    val irvVoteConsolidations = mutableMapOf<Int, IrvContestVotes>()  // contestId -> IrvContestVotes
    // a convenient place to keep this, calculated in createSfElectionFromCvrExportOA.addOAClcaAssorters
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()

    open fun accumulateVotes(cvr : CvrExport) {
        cvr.votes.forEach { (contestId, candIds) ->
            if (irvIds.contains(contestId)) {
                val irvContestVotes = irvVoteConsolidations.getOrPut(contestId) { IrvContestVotes(contestInfos[contestId]!!) }
                irvContestVotes.addVotes(candIds)
            } else {
                val contestTab = contestTabulations.getOrPut(contestId) { ContestTabulation(contestInfos[contestId]?.voteForN) }
                contestTab.addVotes(candIds)
            }
        }
    }

    fun toBallotPools(): List<BallotPool> {
        val bpools = mutableListOf<BallotPool>()
        contestTabulations.forEach { contestId, contestCount ->
            if (contestCount.ncards > 0) {
                bpools.add(BallotPool(poolName, poolId, contestId, contestCount.ncards, contestCount.votes))
            }
        }
        return bpools
    }

    fun addUndervote(contestId: Int) {
        if (irvVoteConsolidations.contains(contestId)) {
            val irvContestVotes = irvVoteConsolidations[contestId]!!
            irvContestVotes.ncards++
        } else {
            val contestTab = contestTabulations[contestId]!!
            contestTab.ncards++
        }
    }

    fun sumRegular(sumTab: MutableMap<Int, ContestTabulation>) {
        this.contestTabulations.forEach { (contestId, poolContestTab) ->
            val contestSum = sumTab.getOrPut(contestId) { ContestTabulation(contestInfos[contestId]?.voteForN) }
            contestSum.sum(poolContestTab)
        }
    }
}

class AssortAvg() {
    var ncards = 0
    var totalAssort = 0.0
    fun avg() : Double = if (ncards == 0) 0.0 else totalAssort / ncards
}

data class AssortAvgsInPools (
    val contest:Int,
    val assortAverage: Map<Int, Double>, // poolId -> average assort value
)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// create the ClcaAssorters for OneAudit. TODO Compare to SF createSfElectionFromCvrExportOA
fun addOAClcaAssorters(
    oaContests: List<OAContestUnderAudit>,
    cardIter: Iterator<AuditableCard>, // vs CvrExport
    cardPools: Map<Int, CardPool> // vs Map<String, CardPool>
) {
    // LOOK can probably skip this, we only want pool averages
    val unpooledAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>() // contestId -> assorter -> AssortAvg

    // sum all the assorters values in one pass across all the cvrs
    while (cardIter.hasNext()) {
        val card: AuditableCard = cardIter.next()
        val cvr = card.cvr()
        val assortAvg = if (card.poolId == null) unpooledAvg else cardPools[card.poolId]!!.assortAvg
        oaContests.forEach { contest ->
            val avg = assortAvg.getOrPut(contest.id) { mutableMapOf() }
            contest.pollingAssertions.forEach { assertion ->
                val passorter = assertion.assorter
                val assortAvg = avg.getOrPut(passorter) { AssortAvg() } // TODO could have a hash collision ?
                if (cvr.hasContest(contest.id)) {
                    assortAvg.ncards++
                    assortAvg.totalAssort += passorter.assort(cvr, usePhantoms = false) // TODO usePhantoms correct ??
                }
            }
        }
    }

    // create the clcaAssertions and add then to the oaContests
    oaContests.forEach { oaContest ->
        val clcaAssertions = oaContest.pollingAssertions.map { assertion ->
            val assortAverageTest = mutableMapOf<Int, Double>() // poolId -> average assort value
            cardPools.values.forEach { cardPool ->
                if (cardPool.assortAvg == null)
                    println("why?")
                if (cardPool.assortAvg!![oaContest.id] == null)
                    println("why2?")
                if (cardPool.assortAvg!![oaContest.id]!![assertion.assorter] == null)
                    println("why3?")
                val assortAvg = cardPool.assortAvg!![oaContest.id]!![assertion.assorter]!!
                assortAverageTest[cardPool.poolId] = assortAvg.avg()
            }

            val poolAvgs = AssortAvgsInPools(assertion.info.id, assortAverageTest)
            val clcaAssorter = OneAuditClcaAssorter(assertion.info, assertion.assorter, true, poolAvgs)
            ClcaAssertion(assertion.info, clcaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }

    // compare the assortAverage with the value computed from the contestTabulation (non-IRV only)
    cardPools.values.forEach { cardPool ->
        cardPool.contestTabulations.forEach { (contestId, contestTabulation) ->
            val avg = cardPool.assortAvg[contestId]!!
            avg.forEach { (assorter, assortAvg) ->
                val calcReportedMargin = assorter.calcReportedMargin(contestTabulation.votes, contestTabulation.ncards)
                val calcReportedMean = margin2mean(calcReportedMargin)
                val cvrAverage = assortAvg.avg()

                if (!doubleIsClose(calcReportedMean, cvrAverage)) {
                    println("pool ${cardPool.poolId} means not agree for assorter $assorter ")
                }
            }
        }
    }
}
