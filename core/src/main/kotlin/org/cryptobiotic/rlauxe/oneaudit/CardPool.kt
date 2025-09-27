package org.cryptobiotic.rlauxe.oneaudit

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.ContestTabulation
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.CvrExport.Companion.unpooled
import org.cryptobiotic.rlauxe.raire.IrvContestVotes
import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.mutableMapOf

private val logger = KotlinLogging.logger("CardPool")

// candidate for removal - use CardPool insted.
data class BallotPool(
    val name: String,
    val poolId: Int,
    val contestId :Int,
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
//  if you have any IRV contest, you must be given IrvContestVotes.VoteConsolidator which is needed to calculate the
//  RaireAssertions and probably the average assort value for the pool ??
open class CardPool(
    val poolName: String,
    val poolId: Int,
    val irvIds: Set<Int>, // TODO could make a CardPoolIRV
    val contestInfos: Map<Int, ContestInfo>)
{
    val contestTabulations = mutableMapOf<Int, ContestTabulation>()  // contestId -> ContestTabulation
    val irvVoteConsolidations = mutableMapOf<Int, IrvContestVotes>()  // contestId -> IrvContestVotes

    // a convenient place to keep this, calculated in addOAClcaAssorters()
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

    // this is when you have CVRs. (sfoa, sfoans)
    open fun accumulateVotes(cvr : Cvr) {
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

    // TODO sfoa, sfoan serialize BallotPool, but only use it to get map name -> id. Needed?
    fun toBallotPools(): List<BallotPool> {
        val bpools = mutableListOf<BallotPool>()
        contestTabulations.forEach { contestId, contestCount ->
            if (contestCount.ncards > 0) {
                bpools.add(BallotPool(poolName, poolId, contestId, contestCount.ncards, contestCount.votes))
            }
        }
        return bpools
    }

    // sfoans needs to add undervotes
    fun addUndervote(contestId: Int) {
        if (irvVoteConsolidations.contains(contestId)) {
            val irvContestVotes = irvVoteConsolidations[contestId]!!
            irvContestVotes.ncards++
        } else {
            val contestTab = contestTabulations[contestId]!!
            contestTab.ncards++
        }
    }

    // sum regular (non IRV) votes into sumTab. (sfoa)
    fun sumRegular(sumTab: MutableMap<Int, ContestTabulation>) {
        this.contestTabulations.forEach { (contestId, poolContestTab) ->
            val contestSum = sumTab.getOrPut(contestId) { ContestTabulation(contestInfos[contestId]?.voteForN) }
            contestSum.sum(poolContestTab)
        }
    }

    fun contests() = (contestTabulations.map { it.key } + irvVoteConsolidations.map { it.key }).toSortedSet().toIntArray()
}

// for calculating average from running total, see addOAClcaAssorters
class AssortAvg() {
    var ncards = 0
    var totalAssort = 0.0
    fun avg() : Double = if (ncards == 0) 0.0 else totalAssort / ncards
    fun margin() : Double = mean2margin(avg())

    override fun toString(): String {
        return "AssortAvg(ncards=$ncards, totalAssort=$totalAssort avg=${avg()})"
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

fun addOAClcaAssorters(
    oaContests: List<OAContestUnderAudit>,
    cardIter: Iterator<Cvr>,
    cardPools: Map<Int, CardPool>
) {
    // sum all the assorters values in one pass across all the cvrs
    while (cardIter.hasNext()) {
        val card: Cvr = cardIter.next()
        if (card.poolId == null) continue

        val assortAvg = cardPools[card.poolId]!!.assortAvg
        oaContests.forEach { contest ->
            val avg = assortAvg.getOrPut(contest.id) { mutableMapOf() }
            contest.pollingAssertions.forEach { assertion ->
                val passorter = assertion.assorter
                val assortAvg = avg.getOrPut(passorter) { AssortAvg() } // TODO could have a hash collision ?
                if (card.hasContest(contest.id)) {
                    assortAvg.ncards++
                    assortAvg.totalAssort += passorter.assort(card, usePhantoms = false) // TODO usePhantoms correct ??
                }
            }
        }
    }

    // create the clcaAssertions and add then to the oaContests
    oaContests.forEach { oaContest ->
        val clcaAssertions = oaContest.pollingAssertions.map { assertion ->
            val assortAverageTest = mutableMapOf<Int, Double>() // poolId -> average assort value
            cardPools.values.forEach { cardPool ->
                val contestAA = cardPool.assortAvg[oaContest.id]
                if (contestAA != null) {
                    val assortAvg = contestAA[assertion.assorter]
                    if (assortAvg != null) {
                        assortAverageTest[cardPool.poolId] = assortAvg.avg()
                    } else {
                        logger.warn { "cardPool ${cardPool.poolId} missing assertion ${assertion.assorter}" }
                    }
                } else if (cardPool.poolName != unpooled) {
                    logger.warn { "cardPool ${cardPool.poolId} missing contest ${oaContest.id}" }
                }
            }

            val poolAvgs = AssortAvgsInPools(assortAverageTest)
            val clcaAssorter = OneAuditClcaAssorter(assertion.info, assertion.assorter, true, poolAvgs)
            ClcaAssertion(assertion.info, clcaAssorter)
        }
        oaContest.clcaAssertions = clcaAssertions
    }

    // compare the assortAverage with the value computed from the contestTabulation (non-IRV only)
    cardPools.values.forEach { cardPool ->
        cardPool.contestTabulations.forEach { (contestId, contestTabulation) ->
            val avg = cardPool.assortAvg[contestId]
            if (avg != null) {
                avg.forEach { (assorter, assortAvg) ->
                    val calcReportedMargin =
                        assorter.calcReportedMargin(contestTabulation.votes, contestTabulation.ncards)
                    val calcReportedMean = margin2mean(calcReportedMargin)
                    val cvrAverage = assortAvg.avg()

                    if (!doubleIsClose(calcReportedMean, cvrAverage)) {
                        println("pool ${cardPool.poolId} means not agree for contest $contestId assorter $assorter ")
                        println("     calcReportedMean= ${calcReportedMean} cvrAverage= $cvrAverage ")
                        println("     ${assortAvg} contestTabulation= $contestTabulation ")
                        println()
                    }
                    // pool 24 means not agree for contest 18 assorter  winner=0 loser=3 reportedMargin=0.0546 reportedMean=0.5273
                    //  calcReportedMean= 0.5295774647887324 cvrAverage= 0.5294943820224719
                    //  AssortAvg(ncards=356, totalAssort=188.5 avg=0.5294943820224719) contestTabulation= {0=70, 1=63, 2=86, 3=49} nvotes=268 ncards=355 undervotes=0 overvotes=0 novote=0 underPct= 0%
                }
            } else {
                logger.warn { "cardPool ${cardPool.poolId} missing contest ${contestId}" }
            }
        }
    }
}
