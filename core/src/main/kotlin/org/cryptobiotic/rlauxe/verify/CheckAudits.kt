package org.cryptobiotic.rlauxe.verify

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateCardPools
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

private val logger = KotlinLogging.logger("createSfElectionFromCsvExportOANS")

// TODO move to verify
fun checkContestsCorrectlyFormed(auditConfig: AuditConfig, contestsUA: List<ContestUnderAudit>) {

    contestsUA.forEach { contestUA ->
        if (contestUA.preAuditStatus == TestH0Status.InProgress && !contestUA.isIrv) {
            checkWinners(contestUA)

            // see if margin is too small
            if (contestUA.recountMargin() <= auditConfig.minRecountMargin) {
                logger.info{"*** MinMargin contest ${contestUA} recountMargin ${contestUA.recountMargin()} <= ${auditConfig.minRecountMargin}"}
                contestUA.preAuditStatus = TestH0Status.MinMargin
            } else {
                // see if too many phantoms
                val minMargin = contestUA.minMargin()
                val adjustedMargin = minMargin - contestUA.contest.phantomRate()
                if (auditConfig.removeTooManyPhantoms && adjustedMargin <= 0.0) {
                    logger.warn{"***TooManyPhantoms contest ${contestUA} adjustedMargin ${adjustedMargin} == $minMargin - ${contestUA.contest.phantomRate()} < 0.0"}
                    contestUA.preAuditStatus = TestH0Status.TooManyPhantoms
                }
            }
        }
        // println("contest ${contestUA} minMargin ${minMargin} + phantomRate ${contestUA.contest.phantomRate()} = adjustedMargin ${adjustedMargin}")
    }
}

// check winners are correctly formed
fun checkWinners(contestUA: ContestUnderAudit, ) {
    val contest = if (contestUA.contest is Contest) contestUA.contest
        // else if (contestUA.contest is OneAuditContest && contestUA.contest.contest is Contest) contestUA.contest.contest
        else null
    if (contest == null) return

    val sortedVotes: List<Map.Entry<Int, Int>> = contest.votes.entries.sortedByDescending { it.value } // TODO wtf?
    val nwinners = contest.winners.size

    // make sure that the winners are unique
    val winnerSet = mutableSetOf<Int>()
    winnerSet.addAll(contest.winners)
    if (winnerSet.size != contest.winners.size) {
        logger.warn{"*** winners in contest ${contest} have duplicates"}
        contestUA.preAuditStatus = TestH0Status.ContestMisformed
        return
    }

    // see if theres a tie TODO check this
    val winnerMin: Int = sortedVotes.take(nwinners).map{ it.value }.min()
    if (sortedVotes.size > nwinners) {
        val firstLoser = sortedVotes[nwinners]
        if (firstLoser.value == winnerMin ) {
            logger.warn{"*** tie in contest ${contest}"}
            contestUA.preAuditStatus = TestH0Status.MinMargin
            return
        }
    }

    // check that the top nwinners are in winners list
    sortedVotes.take(nwinners).forEach { (candId, vote) ->
        if (!contest.winners.contains(candId)) {
            logger.warn{"*** winners ${contest.winners} does not contain candidateId $candId"}
            contestUA.preAuditStatus = TestH0Status.ContestMisformed
            return
        }
    }
}

// TODO use VerifyContests instead. problem here is whether the ballot pools have to be added or not.
fun checkContestsWithCvrs(contestsUA: List<ContestUnderAudit>, cvrs: CloseableIterator<Cvr>,
                          cardPools: List<CardPoolIF>?, show: Boolean = false) = buildString {

    val infos = contestsUA.associate { it.id to it.contest.info() }
    val allVotes = mutableMapOf<Int, ContestTabulation>()
    allVotes.sumContestTabulations(tabulateCvrs(cvrs, infos))
    if (cardPools != null)
        allVotes.sumContestTabulations(tabulateCardPools(cardPools.iterator(), infos))

    if (show) {
        appendLine("tabulateCvrs")
        allVotes.toSortedMap().forEach { (key, value) ->
            appendLine(" $key : $value")
        }
    }

    contestsUA.filter { it.preAuditStatus == TestH0Status.InProgress && !it.isIrv }.forEach { contestUA ->
        val contestVotes = contestUA.contest.votes()!!
        val contestTab = allVotes[contestUA.id]
        if (contestTab == null) {
            appendLine("*** contest ${contestUA.id} not found in tabulated Cvrs")
            contestUA.preAuditStatus = TestH0Status.ContestMisformed
        } else {
            if (!checkEquivilentVotes(contestVotes, contestTab.votes)) {
                appendLine("*** contest ${contestUA.id} votes disagree with cvrs = $contestTab marking as ContestMisformed")
                appendLine("  contestVotes = $contestVotes")
                appendLine("  tabulation   = ${contestTab.votes}")
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
            } else {
                appendLine("contest ${contestUA.id} contestVotes matches cvrTabulation")
            }
        }
    }
}

// ok if one has zero votes and the other doesnt
fun checkEquivilentVotes(votes1: Map<Int, Int>, votes2: Map<Int, Int>, ) : Boolean {
    if (votes1 == votes2) return true
    val votes1z = votes1.filter{ (_, vote) -> vote != 0 }
    val votes2z = votes2.filter{ (_, vote) -> vote != 0 }
    return votes1z == votes2z
}

//////////////////////////////////////////////////////////////////////////////
// TODO use ContestTabulation ??

// Number of votes in each contest, return contestId -> candidateId -> nvotes
fun tabulateVotesFromCvrs(cvrs: Iterator<Cvr>): Map<Int, Map<Int, Int>> {
    val votes = mutableMapOf<Int, MutableMap<Int, Int>>()
    for (cvr in cvrs) {
        for ((con, conVotes) in cvr.votes) {
            val accumVotes = votes.getOrPut(con) { mutableMapOf() }
            for (cand in conVotes) {
                val accum = accumVotes.getOrPut(cand) { 0 }
                accumVotes[cand] = accum + 1
            }
        }
    }
    return votes
}

fun tabulateVotesWithUndervotes(cvrs: Iterator<Cvr>, contestId: Int, ncands: Int, voteForN: Int = 1): Map<Int, Int> {
    val result = mutableMapOf<Int, Int>()
    cvrs.forEach{ cvr ->
        if (cvr.hasContest(contestId) && !cvr.phantom) {
            val candVotes = cvr.votes[contestId] // should always succeed
            if (candVotes != null) {
                if (candVotes.size < voteForN) {  // undervote
                    val count = result[ncands] ?: 0
                    result[ncands] = count + (voteForN - candVotes.size)
                }
                for (cand in candVotes) {
                    val count = result[cand] ?: 0
                    result[cand] = count + 1
                }
            }
        }
    }
    return result
}


