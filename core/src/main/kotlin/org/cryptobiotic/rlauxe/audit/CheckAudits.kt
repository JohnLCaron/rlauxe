package org.cryptobiotic.rlauxe.audit

import org.cryptobiotic.rlauxe.core.*

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
    val sortedVotes: List<Map.Entry<Int, Int>> = (contestUA.contest as Contest).votes.entries.sortedByDescending { it.value } // TODO wtf?
    val contest = contestUA.contest
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

fun checkContestsWithCards(contestsUA: List<ContestUnderAudit>, cards: Iterator<AuditableCard>, show: Boolean = false) {
    val votes = tabulateCvrs(CvrIteratorAdapter(cards))
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
            if (!checkEquivilentVotes(contestVotes, contestTab.votes)) {
                println("*** contest ${contestUA.contest} cvrVotes = $contestTab")
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
            } else if (show) {
                println("    contest ${contestUA.contest} cvrVotes = $contestTab")
            }
            require(checkEquivilentVotes(contestUA.contest.votes, contestVotes))
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

    override fun toString(): String {
        return "${votes.toList().sortedBy{ it.second }.reversed().toMap()} ncards=$ncards)"
    }
}
