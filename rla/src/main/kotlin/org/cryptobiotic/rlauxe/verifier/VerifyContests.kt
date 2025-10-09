package org.cryptobiotic.rlauxe.verifier

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.AssortAvg
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.CardPoolFromCvrs
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.unpooled
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardCsvReader
import org.cryptobiotic.rlauxe.persist.csv.CardPoolsFromBallotPools
import org.cryptobiotic.rlauxe.persist.csv.readBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.mean2margin
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.text.appendLine

class VerifyContests(val auditRecordLocation: String, val show: Boolean = false) {
    val auditConfig: AuditConfig
    val contests: List<ContestUnderAudit>
    val infos: Map<Int, ContestInfo>
    val cards: Iterable<AuditableCard>
    val ballotPools: List<BallotPool>

    init {
        val publisher = Publisher(auditRecordLocation)
        val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
        auditConfig = auditConfigResult.unwrap()

        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        contests = if (contestsResults is Ok) contestsResults.unwrap().sortedBy { it.id }
        else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()} err = $contestsResults")

        infos = contests.associate { it.id to it.contest.info() }
        cards = AuditableCardCsvReader(publisher)

        ballotPools = readBallotPoolCsvFile(publisher.ballotPoolsFile())
    }

    fun verify() = buildString {
        val contestSummary = verifyCvrs(contests, CvrIteratorAdapter(cards.iterator()), ballotPools, infos)
        append(contestSummary.results)
        // if (ballotPools.isNotEmpty()) appendLine(verifyBallotPools(contests, contestSummary))
        appendLine(verifyAssortAvg(contests, CvrIteratorAdapter(cards.iterator())))
    }

    fun verifyContest(contest: ContestUnderAudit) = buildString {
        val contest1 = listOf(contest)
        val contestSummary = verifyCvrs(contest1, CvrIteratorAdapter(cards.iterator()), ballotPools, infos)
        append(contestSummary.results)
        // if (ballotPools.isNotEmpty()) appendLine(verifyBallotPools(contest1, contestSummary))
        appendLine(verifyAssortAvg(contest1, CvrIteratorAdapter(cards.iterator())))
    }
}


data class ContestSummary(
    val allVotes: Map<Int, ContestTabulation>,
    val cardPoolVotes: Map<Int, ContestTabulation>,
    val nonPoolTabs: Map<Int, ContestTabulation>,
    val results: String
)

fun verifyCvrs(
    contests: List<ContestUnderAudit>,
    cardIter: Iterator<Cvr>,
    ballotPools: List<BallotPool>,
    infos: Map<Int, ContestInfo>,
    show: Boolean = false
): ContestSummary {
    val allCvrVotes = mutableMapOf<Int, ContestTabulation>()
    val nonpoolCvrVotes = mutableMapOf<Int, ContestTabulation>()

    while (cardIter.hasNext()) {
        val card = cardIter.next()
        card.votes.forEach { (contestId, cands) ->
            val allTab = allCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
            allTab.addVotes(cands)
            if (card.poolId == null) {
                val nonpoolCvrTab = nonpoolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                nonpoolCvrTab.addVotes(cands)
            }
        }
    }

    val cardPoolVotes = mutableMapOf<Int, ContestTabulation>()
    val allVotes = if (ballotPools.isEmpty()) {
        allCvrVotes
    } else {
        // use the cardPools to tabulate contests
        val cardPools = CardPoolsFromBallotPools(ballotPools, infos)

        cardPools.cardPoolMap.values.forEach { cardPool ->
            cardPool.voteTotals.forEach { (contestId, cands) ->
                val poolTab = cardPoolVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                cands.forEach { (candId, nvotes) -> poolTab.addVote(candId, nvotes) }
                poolTab.ncards += cardPool.ncards
                poolTab.undervotes += cardPool.undervotes()[contestId]!!
            }
        }

        val sumVotes = mutableMapOf<Int, ContestTabulation>()
        sumVotes.sumContestTabulations(nonpoolCvrVotes)
        sumVotes.sumContestTabulations(cardPoolVotes)
        sumVotes
    }

    val results = buildString {
        var allOk = true

        // check contest.Ncast == contestTab.ncards
        // and contestUA.Nu != contestTab.undervotes
        contests.forEach { contestUA ->
            val contestTab = allVotes[contestUA.id]
            if (contestTab == null) {
                appendLine("*** contest ${contestUA.id} not found in tabulated Cvrs")
                allOk = false

            } else {
                val Ncast = contestUA.Nc - contestUA.Np
                if (Ncast != contestTab.ncards) {
                    appendLine("*** contest ${contestUA.id} ncards ${Ncast} disagree with cvrs = ${contestTab.ncards}")
                    allOk = false
                }
                if (contestUA.Nu != contestTab.undervotes) {
                    appendLine("*** contest ${contestUA.id} undervotes ${contestUA.Nu} disagree with cvrs = ${contestTab.undervotes}")
                    allOk = false
                }
            }
        }
        if (show && allOk) appendLine("\ncontest.Ncast == contestTab.ncards for all contests\n")

        // check contest.votes == cvrTab.votes (non-IRV)
        // TODO move to CheckAudits?
        contests.filter { it.preAuditStatus == TestH0Status.InProgress && !it.isIrv }.forEach { contestUA ->
            val contestVotes = contestUA.contest.votes()!!
            val contestTab = allVotes[contestUA.id]
            if (contestTab == null) {
                appendLine("*** contest ${contestUA.id} not found in tabulated Cvrs")
                allOk = false
            } else {
                if (!checkEquivilentVotes(contestVotes, contestTab.votes)) {
                    appendLine("*** contest ${contestUA.id} votes disagree with cvrs = $contestTab")
                    appendLine("  contestVotes = $contestVotes")
                    appendLine("  tabulation   = ${contestTab.votes}")
                    allOk = false
                } else {
                    if (show) appendLine("contest ${contestUA.id} contest.votes matches cvrTabulation")
                }
            }
        }
        appendLine("verifyCvrs allOk = $allOk\n")
    }

    return ContestSummary(allVotes, cardPoolVotes, nonpoolCvrVotes, results)
}

// problem is that the cvrs dont have the votes on them, which is what passort needs to assort.
// so should just use addOAClcaAssortersFromMargin. But how to test this?
/*
  sum(bassort(mvr, cvr).
 */

fun verifyAssortAvg(
    contests: List<ContestUnderAudit>,
    cardIter: Iterator<Cvr>,
    show: Boolean = false
) = buildString {
    var allOk = true

    // sum all the assorters values in one pass across all the cvrs, including Pools
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    while (cardIter.hasNext()) {
        val card: Cvr = cardIter.next()

        contests.forEach { contest ->
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

    // compare the assortAverage with the contest's reportedMargin which is what passorter used.
    contests.forEach { contest ->
        val contestAssortAvg = assortAvg[contest.id]!!
        contest.pollingAssertions.forEach { assertion ->
            val passorter = assertion.assorter
            val assortAvg = contestAssortAvg[passorter]!!
            if (!doubleIsClose(passorter.reportedMargin(), assortAvg.margin())) {
                appendLine("**** margin does not agree for contest ${contest.id} assorter '$passorter'")
                appendLine("     reportedMean= ${passorter.reportedMean()} cvrs.assortAvg= ${assortAvg.avg()} ")
                allOk = false
            } else {
                if (show) appendLine("  margin agrees with assort avg ${assortAvg.margin()} for contest ${contest.id} assorter '$passorter'")
            }
        }
    }
    appendLine("verifyAllAssortAvg allOk = $allOk")
}

/*
  For each assertion and pool, when mvr == cvr:
    Sum{bassort(cvr, cvr)} = noerror * ncvr + Sum_i { Sum_pooli{ bassort(cvr_k, cvr_k), k = 1..pooli }, i = 1..npools }

within pooli:
    Sum_pooli{ bassort(cvr_k, cvr_k), k = 1..pooli }
            = Sum_pooli{ noerror * (1 - poolAvg_i + assort(cvr_k)), k = 1..pooli }
            = noerror * Sum_pooli{ 1 - poolAvg_i + assort(cvr_k), k = 1..pooli }
            = noerror * (Sum_pooli{1} - Sum_pooli{poolAvg_i} + Sum_pooli{assort(cvr_k), k = 1..pooli }
            = noerror * (pooli_ncards - pooli_ncards * poolAvg_i + Sum_pooli{assort(cvr_k), k = 1..pooli }

     poolAvg_i = Sum_pooli{assort(cvr_k)} / pooli_ncards, so the last two terms cancel for each pool, and
     Sum_pooli{ bassort(cvr_k, cvr_k), k = 1..pool =  noerror * pooli_ncards

    Sum{bassort(cvr, cvr)} / noerror = ncvr + Sum { npooli }, i = 1..npools }
    Sum{bassort(cvr, cvr)} / noerror = totalNumberOfCvrs
 */

// this only works if the cvrs have the votes in them
fun verifyBAssortAvg(
    contests: List<ContestUnderAudit>,
    cardIter: Iterator<Cvr>,
    show: Boolean = false,
) = buildString {
    var allOk = true

    // sum all the assorters values in one pass across all the cvrs, including Pools
    val bassortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    while (cardIter.hasNext()) {
        val card: Cvr = cardIter.next()

        contests.forEach { contest ->
            val avg = bassortAvg.getOrPut(contest.id) { mutableMapOf() }
            contest.clcaAssertions.forEach { assertion ->
                val cassorter = assertion.cassorter
                val passorter = assertion.assorter
                val assortAvg = avg.getOrPut(passorter) { AssortAvg() } // TODO could have a hash collision ?
                if (card.hasContest(contest.id)) {
                    assortAvg.ncards++
                    assortAvg.totalAssort += cassorter.bassort(card, card, hasStyle = true) / cassorter.noerror()
                }
            }
        }
    }

    // compare the assortAverage with the contest's reportedMargin which is what passorter used.
    contests.forEach { contest ->
        val contestAssortAvg = bassortAvg[contest.id]!!
        contest.clcaAssertions.forEach { assertion ->
            val passorter = assertion.assorter
            val assortAvg = contestAssortAvg[passorter]!!
            println("  assortAvg ${assortAvg.avg()} for contest ${contest.id} assorter '$passorter'")
            require(doubleIsClose(1.0, assortAvg.avg()))
        }
    }
}

    /* specialized debugging, use pool 20
    fun verifyPoolAssortAvg(
        contests: List<ContestUnderAudit>,
        cardIter: Iterator<Cvr>,
        ballotPools: List<BallotPool>,
    ) = buildString {
        val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
        var totalCards = 0
        var poolCards = 0

        // sum all the assorters values in one pass across just the pooled cvrs
        while (cardIter.hasNext()) {
            val card: Cvr = cardIter.next()
            if (card.poolId != 20) continue

            contests.forEach { contest ->
                val avg = assortAvg.getOrPut(contest.id) { mutableMapOf() }
                contest.pollingAssertions.forEach { assertion ->
                    val passorter = assertion.assorter
                    val assortAvg = avg.getOrPut(passorter) { AssortAvg() } // TODO could have a hash collision ?
                    if (card.hasContest(contest.id)) {
                        totalCards++
                        if (card.poolId != null) poolCards++
                        assortAvg.ncards++
                        assortAvg.totalAssort += passorter.assort(card, usePhantoms = false) // TODO usePhantoms correct ??
                    }
                }
            }
        }
        appendLine("POOL 20: totalCards=$totalCards poolCards=$poolCards")

        val pool20 = ballotPools.find { it.poolId == 20 && it.contestId == 16 }
        val contest = contests.find { it.id == 16 } as OAContestUnderAudit
        contest.clcaAssertions.forEach { cassertion ->
            val oacassorter = cassertion.cassorter as OneAuditClcaAssorter
            val expectAvg = oacassorter.poolAverages.assortAverage[20]!!
            val have = assortAvg[16]?.get(cassertion.assorter)!!
            val haveAvg = have.avg()
            val haveMargin = have.margin()
            appendLine("  avg contest ${contest.id} assorter ${oacassorter.shortName()} expectAvg=${expectAvg} have=${have.avg()}")
            appendLine("  margin expect=${mean2margin(expectAvg)} have=${have.margin()}")
            appendLine()

            /* val assortAvg = contestAssortAvg[passorter]!!
            if (!doubleIsClose(passorter.reportedMargin(), assortAvg.margin())) {
                appendLine("**** margin does not agree for contest ${contest.id} assorter $passorter ")
                appendLine("     reportedMargin= ${passorter.reportedMargin()} assortAvg.margin= ${assortAvg.margin()} ")
            } else {
                appendLine("  margin agrees for contest ${contest.id} assorter $passorter ")
            } */
        }
    } */

