package org.cryptobiotic.rlauxe.verifier

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.AssortAvg
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardCsvReader
import org.cryptobiotic.rlauxe.persist.csv.readBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.mean2margin
import kotlin.collections.forEach

class VerifyContests(val auditRecordLocation: String) {
    val auditConfig: AuditConfig
    val contests: List<ContestUnderAudit>
    val cards: Iterable<AuditableCard>
    val ballotPools: List<BallotPool>

    init {
        val publisher = Publisher(auditRecordLocation)
        val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
        auditConfig = auditConfigResult.unwrap()

        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        contests = if (contestsResults is Ok) contestsResults.unwrap()
        else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()} err = $contestsResults")

        cards = AuditableCardCsvReader(publisher)

        ballotPools = readBallotPoolCsvFile(publisher.ballotPoolsFile())
    }

    fun verify(show: Boolean = false) = buildString {
        appendLine("verify audit in ${auditRecordLocation}")
        appendLine(checkContestsWithCvrs(contests, CvrIteratorAdapter(cards.iterator()), ballotPools=ballotPools, show = show))
    }

    fun verifyContest(contest: ContestUnderAudit, show: Boolean = false) = buildString {
        appendLine("  verify contest = ${contest}")
        // appendLine(checkContestsWithCvrs(listOf(contest), CvrIteratorAdapter(cards.iterator()), ballotPools=ballotPools, show = show))
        appendLine(verifyPoolAssortAvg(listOf(contest), CvrIteratorAdapter(cards.iterator()), ballotPools=ballotPools, show = show))
    }
}

// could check that wighted sums of cvrAvg + poolAvgs = reportedMean.

fun verifyAllAssortAvg(
    contests: List<ContestUnderAudit>,
    cardIter: Iterator<Cvr>,
    ballotPools: List<BallotPool>,
    show: Boolean = false
) = buildString {
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    var totalCards = 0
    var poolCards = 0

    // sum all the assorters values in one pass across all the cvrs, including Pools
    while (cardIter.hasNext()) {
        val card: Cvr = cardIter.next()

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
    appendLine("totalCards=$totalCards poolCards=$poolCards")

    // compare the assortAverage with the contest's reportedMargin
    contests.forEach { contest ->
        val contestAssortAvg = assortAvg[contest.id]!!
        contest.pollingAssertions.forEach { assertion ->
            val passorter = assertion.assorter
            val assortAvg = contestAssortAvg[passorter]!!
            if (!doubleIsClose(passorter.reportedMargin(), assortAvg.margin())) {
                appendLine("**** margin does not agree for contest ${contest.id} assorter $passorter ")
                appendLine("     reportedMargin= ${passorter.reportedMargin()} assortAvg.margin= ${assortAvg.margin()} ")
            } else {
                appendLine("  margin agrees for contest ${contest.id} assorter $passorter ")
            }
        }
    }
}

fun verifyPoolAssortAvg(
    contests: List<ContestUnderAudit>,
    cardIter: Iterator<Cvr>,
    ballotPools: List<BallotPool>,
    show: Boolean = false
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
    appendLine("totalCards=$totalCards poolCards=$poolCards")

    val pool20 = ballotPools.find{ it.poolId == 20 && it.contestId == 16}
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
}