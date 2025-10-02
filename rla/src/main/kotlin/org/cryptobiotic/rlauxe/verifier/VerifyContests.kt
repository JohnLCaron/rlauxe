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
        appendLine("verify audit in ${auditRecordLocation}")

        val contestSummary = verifyCvrs(CvrIteratorAdapter(cards.iterator()), ballotPools, infos)
        append(contestSummary.results)
        appendLine(verifyAllAssortAvg(contests, CvrIteratorAdapter(cards.iterator()), ballotPools = ballotPools))
    }

    fun verifyContest(contest: ContestUnderAudit) = buildString {
        appendLine("  verify contest = ${contest}")
        appendLine(
            checkContestsWithCvrs(
                listOf(contest),
                CvrIteratorAdapter(cards.iterator()),
                ballotPools = ballotPools,
                show = show
            )
        )
        appendLine(
            verifyPoolAssortAvg(
                listOf(contest),
                CvrIteratorAdapter(cards.iterator()),
                ballotPools = ballotPools,
                show = show
            )
        )
    }

    fun verifyCvrs(
        cardIter: Iterator<Cvr>,
        ballotPools: List<BallotPool>,
        infos: Map<Int, ContestInfo>,
    ): ContestSummary {
        val allVotes = mutableMapOf<Int, ContestTabulation>()
        val poolCvrVotes = mutableMapOf<Int, ContestTabulation>()

        while (cardIter.hasNext()) {
            val card = cardIter.next()
            card.votes.forEach { (contestId, cands) ->
                val allTab = allVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                allTab.addVotes(cands)
                if (card.poolId != null) {
                    val poolCvrTab = poolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                    poolCvrTab.addVotes(cands)
                }
            }
        }
        println("contest0 cvr.ncards= ${allVotes[0]!!.ncards}")
        println("contest0 cvr.undervotes= ${allVotes[0]!!.undervotes}")

        println("contest0 poolCvrTab.ncards= ${poolCvrVotes[0]!!.ncards}")
        println("contest0 poolCvrTab.undervotes= ${poolCvrVotes[0]!!.undervotes}") // when !completeCvrs, the undervotes == ncards

        // TODO leave the unpooled out of the ballotPool.csv ?
        val poolVotes = mutableMapOf<Int, ContestTabulation>()
        ballotPools.filter { it.name != unpooled }.forEach { ballotPool ->
            val poolTab = poolVotes.getOrPut(ballotPool.contestId) { ContestTabulation(infos[ballotPool.contestId]!!) }
            ballotPool.votes.forEach { (candId, nvotes) -> poolTab.addVote(candId, nvotes) }
            poolTab.ncards += ballotPool.ncards
        }
        println("contest0 poolVotes.ncards= ${poolVotes[0]!!.ncards}")
        println("contest0 poolVotes.undervotes= ${poolVotes[0]!!.undervotes}")

        val totalVotes = allVotes.values.sumOf { it.nvotes() }
        val totalCards = allVotes.values.sumOf { it.ncards }
        //println("totalVotes= $totalVotes totalCards= $totalCards")

        val totalPoolCvrVotes = poolCvrVotes.values.sumOf { it.nvotes() }
        val totalPoolCvrCards = poolCvrVotes.values.sumOf { it.ncards }
        //println("totalPoolCvrVotes= $totalPoolCvrVotes totalPoolCvrCards=$totalPoolCvrCards")

        val ballotPoolVotes = poolVotes.values.sumOf { it.nvotes() }
        val ballotPoolCards = poolVotes.values.sumOf { it.ncards }
        //println("ballotPoolVotes= $ballotPoolVotes ballotPoolCards=$ballotPoolCards ballotPools.size=${ballotPools.size}")

        // TODO ballot pools dont capture the IRV VoteConsolidator.
        val completeCvrs = (totalPoolCvrVotes > 0)

        val results = buildString {
            var allOk = true
            if (!completeCvrs) {
                // get the pool votes from the ballotPools
                allVotes.addJustVotes(poolVotes) // ncards are correct, just votes are missing; undervotes are not correct
                if (show) appendLine("add poolVotes to allVotes")
            } else {
                // check pool agreement for non-IRV
                val poolAgree = poolCvrVotes.size == poolVotes.size
                if (show) appendLine()
                if (show) appendLine("pool contest sizes agree= $poolAgree")
                contests.filter { !it.isIrv }.forEach { contest ->
                    val id = contest.id
                    val poolCvrs = poolCvrVotes[id]!!
                    val pool = poolVotes[id]!!
                    if (poolCvrs == pool) {
                        if (show) appendLine("pool contest ${id} agree")
                    } else {
                        appendLine("pool contest ${id}")
                        appendLine("     pool ${pool}")
                        appendLine(" poolCvrs ${poolCvrs}")
                        allOk = false
                    }
                }
            }

            // check Nc
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
                    if (completeCvrs && (contestUA.Nu != contestTab.undervotes)) {
                        appendLine("*** contest ${contestUA.id} undervotes ${contestUA.Nu} disagree with cvrs = ${contestTab.undervotes}")
                        allOk = false
                    }
                }
            }

            // move to CheckAudits?
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
                        if (show) appendLine("contest ${contestUA.id} contestVotes matches cvrTabulation")
                    }
                }
            }
            appendLine("verifyCvrs allOk = $allOk")
        }

        return ContestSummary(allVotes, completeCvrs, results)
    }

// could check that weighted sums of cvrAvg + poolAvgs = reportedMean ??

    fun verifyAllAssortAvg(
        contests: List<ContestUnderAudit>,
        cardIter: Iterator<Cvr>,
        ballotPools: List<BallotPool>,
    ) = buildString {
        var allOk = true
        val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average

        // sum all the assorters values in one pass across all the cvrs, including Pools
        while (cardIter.hasNext()) {
            val card: Cvr = cardIter.next()

            contests.forEach { contest ->
                val avg = assortAvg.getOrPut(contest.id) { mutableMapOf() }
                contest.pollingAssertions.forEach { assertion ->
                    val passorter = assertion.assorter
                    val assortAvg = avg.getOrPut(passorter) { AssortAvg() } // TODO could have a hash collision ?
                    if (card.hasContest(contest.id)) {
                        assortAvg.ncards++
                        assortAvg.totalAssort += passorter.assort(
                            card,
                            usePhantoms = false
                        ) // TODO usePhantoms correct ??
                    }
                }
            }
        }

        // compare the assortAverage with the contest's reportedMargin
        contests.forEach { contest ->
            val contestAssortAvg = assortAvg[contest.id]!!
            contest.pollingAssertions.forEach { assertion ->
                val passorter = assertion.assorter
                val assortAvg = contestAssortAvg[passorter]!!
                if (!doubleIsClose(passorter.reportedMargin(), assortAvg.margin())) {
                    appendLine("**** margin does not agree for contest ${contest.id} assorter $passorter ")
                    appendLine("     reportedMargin= ${passorter.reportedMargin()} assortAvg.margin= ${assortAvg.margin()} ")
                    allOk = false
                } else {
                    if (show) appendLine("  margin agrees for contest ${contest.id} assorter $passorter ")
                }
            }
        }
        appendLine("verifyAllAssortAvg allOk = $allOk")
    }
}

data class ContestSummary(val contestTabs: Map<Int, ContestTabulation>, val completeCvrs: Boolean, val results: String)

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

