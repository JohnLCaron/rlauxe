package org.cryptobiotic.rlauxe.verify

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.core.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.min

private val logger = KotlinLogging.logger("createSfElectionFromCsvExportOANS")

fun checkContestsCorrectlyFormed(config: AuditConfig, contestsUA: List<ContestUnderAudit>, results: VerifyResults) {
    results.addMessage("checkContestsCorrectlyFormed")

    checkContestInfos(contestsUA, results)

    contestsUA.forEach { contestUA ->
        checkWinners(contestUA, results)

        if (contestUA.preAuditStatus == TestH0Status.InProgress && !contestUA.isIrv) {
            checkWinnerVotes(contestUA, results)

            // see if margin is too small
            if (contestUA.minRecountMargin() <= config.minRecountMargin) {
                logger.info{"*** MinMargin contest ${contestUA} recountMargin ${contestUA.minRecountMargin()} <= ${config.minRecountMargin}"}
                contestUA.preAuditStatus = TestH0Status.MinMargin
            } else {
                // see if too many phantoms
                val minMargin = contestUA.minMargin()
                val adjustedMargin = minMargin - contestUA.contest.phantomRate()
                if (config.removeTooManyPhantoms && adjustedMargin <= 0.0) {
                    logger.warn{"***TooManyPhantoms contest ${contestUA} adjustedMargin ${adjustedMargin} == $minMargin - ${contestUA.contest.phantomRate()} < 0.0"}
                    contestUA.preAuditStatus = TestH0Status.TooManyPhantoms
                }
            }
        }
    }
}

fun checkContestInfos(contestsUA: List<ContestUnderAudit>, results: VerifyResults) {
    val contestNames = mutableSetOf<String>()
    val contestIds = mutableSetOf<Int>()
    contestsUA.forEach { contestUA ->
        // 2. over all contests, verify that the names and ids are unique.
        if (!contestNames.add(contestUA.name)) {
            results.addError("Contest ${contestUA.name} duplicate name")
            contestUA.preAuditStatus = TestH0Status.ContestMisformed
        }
        if (!contestIds.add(contestUA.id)) {
            results.addError("Contest ${contestUA.id} duplicate id")
            contestUA.preAuditStatus = TestH0Status.ContestMisformed
        }

        // 1. for each contest, verify that candidate names and candidate ids are unique.
        val candNames = mutableSetOf<String>()
        val candIds = mutableSetOf<Int>()
        contestUA.contest.info().candidateNames.forEach { name, id ->
            if (!candNames.add(name)) {
                results.addError("Contest ${contestUA.name} (${contestUA.id}) candidate $name duplicate name")
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
            }
            if (!candIds.add(id)) {
                results.addError("Contest ${contestUA.name} (${contestUA.id}) candidate $id duplicate id")
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
            }
        }
    }
}

fun checkWinners(contestUA: ContestUnderAudit, results: VerifyResults) {
    val contest = contestUA.contest
    val info = contest.info()

    // hmmm, maybe we allow writeIns not in info ?? as long as they dont win or lose?
    /* if (contest is Contest) {
        contest.votes.keys.forEach { candId ->
            if (!info.candidateIds.contains(candId)) results.addError("Contest ${info.name} (${info.id}) has vote for candidate $candId not in ContestInfo")
        }
    } */

    // 1. verify that the candidateIds match whats in the ContestInfo
    val candidatesInContest = contest.winners() + contest.losers()
    candidatesInContest.forEach { candId ->
        if (!info.candidateIds.contains(candId)) {
            results.addError("Contest ${info.name} (${info.id}) has candidate $candId not in ContestInfo")
            contestUA.preAuditStatus = TestH0Status.ContestMisformed
        }
    }

    // 2. verify that the candidateIds are unique
    val candidatesIds = mutableSetOf<Int>()
    candidatesInContest.forEach {
        if (!candidatesIds.add(it)) {
            results.addError("Contest ${info.name} (${info.id}) has duplicate contestId $candidatesIds")
            contestUA.preAuditStatus = TestH0Status.ContestMisformed
        }
    }

    // 3. verify that nwinners == min(ncandidates, info.nwinners)
    if (info.choiceFunction != SocialChoiceFunction.DHONDT) {
        val maxwinners = min(info.candidateIds.size, info.nwinners)
        if (contest.winners().size != maxwinners) {
            results.addError("Contest ${info.name} (${info.id}) has ${contest.winners().size} winners should be $maxwinners")
            contestUA.preAuditStatus = TestH0Status.ContestMisformed
        }
    }
}

// 3. verify that the winners have more votes than the losers (margins > 0 for all assertions)
// 4. check that the top nwinners are in the list of winners
fun checkWinnerVotes(contestUA: ContestUnderAudit, results: VerifyResults) {
    val contest = contestUA.contest as Contest
    val info = contest.info

    val sortedVotes: List<Map.Entry<Int, Int>> = contest.votes.entries.sortedByDescending { it.value } // highest vote count first
    val nwinners = contest.winners.size

    // 3. verify that the winners have more votes than the losers (margins > 0 for all assertions)
    sortedVotes.take(nwinners).forEach { (candId, _) ->
        if (!contest.winners.contains(candId)) {
            results.addError("Contest ${info.name} (${info.id}) winners ${contest.winners} should contain candidateId $candId")
            contestUA.preAuditStatus = TestH0Status.ContestMisformed
            return
        }
    }

    // see if theres a tie
    val winnerMin: Int? = sortedVotes.take(nwinners).minOfOrNull { it.value }
    if (sortedVotes.size > nwinners) {
        val firstLoser = sortedVotes[nwinners]
        if (firstLoser.value == winnerMin ) {
            results.addError("Contest ${info.name} (${info.id}) has a tie: ${sortedVotes.map { it.value }} ")
            contestUA.preAuditStatus = TestH0Status.MinMargin
            return
        }
    }
}


//////////////////////////////////////////////////////////////////////////////
/*

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
*/


