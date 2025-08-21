package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContest

fun checkContestsCorrectlyFormed(auditConfig: AuditConfig, contestsUA: List<ContestUnderAudit>) {

    contestsUA.forEach { contestUA ->
        if (contestUA.preAuditStatus == TestH0Status.InProgress && contestUA.choiceFunction != SocialChoiceFunction.IRV) {
            checkWinners(contestUA)

            // see if margin is too small
            if (contestUA.recountMargin() <= auditConfig.minRecountMargin) {
                println("*** MinMargin contest ${contestUA} recountMargin ${contestUA.recountMargin()} <= ${auditConfig.minRecountMargin}")
                contestUA.preAuditStatus = TestH0Status.MinMargin
            } else {
                // see if too many phantoms
                val minMargin = contestUA.minMargin()
                val adjustedMargin = minMargin - contestUA.contest.phantomRate()
                if (auditConfig.removeTooManyPhantoms && adjustedMargin <= 0.0) {
                    println("***TooManyPhantoms contest ${contestUA} adjustedMargin ${adjustedMargin} == $minMargin - ${contestUA.contest.phantomRate()} < 0.0")
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
        else if (contestUA.contest is OneAuditContest && contestUA.contest.contest is Contest) contestUA.contest.contest
        else null
    if (contest == null) return

    val sortedVotes: List<Map.Entry<Int, Int>> = contest.votes.entries.sortedByDescending { it.value } // TODO wtf?
    val nwinners = contest.winners.size

    // make sure that the winners are unique
    val winnerSet = mutableSetOf<Int>()
    winnerSet.addAll(contest.winners)
    if (winnerSet.size != contest.winners.size) {
        println("*** winners in contest ${contest} have duplicates")
        contestUA.preAuditStatus = TestH0Status.ContestMisformed
        return
    }

    // see if theres a tie TODO check this
    val winnerMin: Int = sortedVotes.take(nwinners).map{ it.value }.min()
    if (sortedVotes.size > nwinners) {
        val firstLoser = sortedVotes[nwinners]
        if (firstLoser.value == winnerMin ) {
            println("*** tie in contest ${contest}")
            contestUA.preAuditStatus = TestH0Status.MinMargin
            return
        }
    }

    // check that the top nwinners are in winners list
    sortedVotes.take(nwinners).forEach { (candId, vote) ->
        if (!contest.winners.contains(candId)) {
            println("*** winners ${contest.winners} does not contain candidateId $candId")
            contestUA.preAuditStatus = TestH0Status.ContestMisformed
            return
        }
    }
}

fun checkContestsWithCvrs(contestsUA: List<ContestUnderAudit>, cvrs: Iterator<Cvr>, show: Boolean = false) {
    val votes = tabulateCvrs(cvrs)
    if (show) {
        println("tabulateCvrs")
        votes.toSortedMap().forEach { (key, value) ->
            println(" $key : $value")
        }
    }
    contestsUA.filter { it.preAuditStatus == TestH0Status.InProgress && it.choiceFunction != SocialChoiceFunction.IRV }.forEach { contestUA ->
        val contestVotes = (contestUA.contest as Contest).votes
        val contestTab = votes[contestUA.id]
        if (contestTab == null) {
            println("*** contest ${contestUA.contest} not found in tabulated Cvrs")
            contestUA.preAuditStatus = TestH0Status.ContestMisformed
        } else {
            // add in the pool votes
            if (contestUA.contest is OneAuditContest) {
                contestUA.contest.pools.values.forEach { pool ->
                    contestTab.addVotes(pool.votes)
                }
            }

            if (!checkEquivilentVotes(contestVotes, contestTab.votes)) {
                println("*** contest ${contestUA.contest} votes disagree with cvrs = $contestTab marking as ContestMisformed")
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
            } else if (show) {
                println("contest ${contestUA.contest} cvrVotes = $contestTab")
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

// not used
// find first index where pvalue < riskLimit, and stays below the riskLimit for the rest of the sequence
fun samplesNeeded(pvalues: List<Double>, riskLimit: Double): Int {
    var firstIndex = -1
    pvalues.forEachIndexed { idx, pvalue ->
        if (pvalue <= riskLimit && firstIndex < 0) {
            firstIndex = idx
        } else if (pvalue >= riskLimit) {
            firstIndex = -1
        }
    }
    return firstIndex
}

//////////////////////////////////////////////////////////////////////////////

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

// has both votes and ncards, return contestId -> ContestTabulation
fun tabulateCvrs(cvrs: Iterator<Cvr>): Map<Int, ContestTabulation> {
    val votes = mutableMapOf<Int, ContestTabulation>()
    for (cvr in cvrs) {
        for ((con, conVotes) in cvr.votes) {
            val tab = votes.getOrPut(con) { ContestTabulation() }
            tab.ncards++
            tab.addVotes(conVotes)
        }
    }
    return votes
}

class ContestTabulation {
    val votes = mutableMapOf<Int, Int>()
    var ncards = 0

    fun addVote(cand: Int, vote: Int) {
        val accum = votes.getOrPut(cand) { 0 }
        votes[cand] = accum + vote
    }

    fun addVotes(cands: IntArray) {
        cands.forEach { addVote(it, 1) }
    }

    fun addVotes(cands: Map<Int, Int>) {
        cands.forEach { (candId, nvotes) -> addVote(candId, nvotes) }
    }

    // undervotes = info.voteForN * ncards - nvotes
    // undervotes / ncards = info.voteForN - nvotes / ncards
    fun undervotePct(voteForN: Int): Double {
        val nvotes = votes.map { it.value }.sum()
        return (voteForN * ncards - nvotes) / ncards.toDouble()
    }

    override fun toString(): String {
        return "${votes.toList().sortedBy{ it.second }.reversed().toMap()} ncards=$ncards)"
    }
}
