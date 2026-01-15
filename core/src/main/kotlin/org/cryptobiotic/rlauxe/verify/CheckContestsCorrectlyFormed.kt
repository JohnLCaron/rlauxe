package org.cryptobiotic.rlauxe.verify

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.core.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.min

private val logger = KotlinLogging.logger("createSfElectionFromCsvExportOANS")

fun checkContestsCorrectlyFormed(config: AuditConfig, contestsUA: List<ContestWithAssertions>, results: VerifyResults) {
    results.addMessage("checkContestsCorrectlyFormed")

    checkContestInfos(contestsUA, results)

    contestsUA.forEach { contestUA ->
        checkWinners(contestUA, results)

        if (contestUA.preAuditStatus == TestH0Status.InProgress && !contestUA.isIrv) {
            checkWinnerVotes(contestUA, results)

            // see if margin is too small
            if ((contestUA.minRecountMargin()?: 0.0) <= config.minRecountMargin) {
                logger.info{"*** MinMargin contest ${contestUA} recountMargin ${contestUA.minRecountMargin()} <= ${config.minRecountMargin}"}
                contestUA.preAuditStatus = TestH0Status.MinMargin
            } else {
                // see if too many phantoms
                val minMargin = contestUA.minDilutedMargin() ?: 0.0
                val adjustedMargin = minMargin - contestUA.phantomRate()
                if (config.removeTooManyPhantoms && adjustedMargin <= 0.0) {
                    logger.warn{"***TooManyPhantoms contest ${contestUA} adjustedMargin ${adjustedMargin} == $minMargin - ${contestUA.phantomRate()} < 0.0"}
                    contestUA.preAuditStatus = TestH0Status.TooManyPhantoms
                }
            }
        }
    }
}

fun checkContestInfos(contestsUA: List<ContestWithAssertions>, results: VerifyResults) {
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

fun checkWinners(contestUA: ContestWithAssertions, results: VerifyResults) {
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
fun checkWinnerVotes(contestUA: ContestWithAssertions, results: VerifyResults) {
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
