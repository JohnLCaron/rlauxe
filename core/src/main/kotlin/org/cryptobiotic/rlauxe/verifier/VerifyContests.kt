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
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardCsvReader
import org.cryptobiotic.rlauxe.persist.existsOrZip
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.readCardPoolsJsonFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.doubleIsClose
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

// only for CLCA
class VerifyContests(val auditRecordLocation: String, val show: Boolean = false) {
    val auditConfig: AuditConfig
    val contests: List<ContestUnderAudit>
    val infos: Map<Int, ContestInfo>
    val cards: CloseableIterable<AuditableCard>
    val mvrs: CloseableIterable<AuditableCard>?
    val cardPools: List<CardPoolIF>?

    init {
        val publisher = Publisher(auditRecordLocation)
        val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
        auditConfig = auditConfigResult.unwrap()

        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        contests = if (contestsResults is Ok) contestsResults.unwrap().sortedBy { it.id }
        else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()} err = $contestsResults")

        infos = contests.associate { it.id to it.contest.info() }
        cards = AuditableCardCsvReader(publisher.cardsCsvFile())
        mvrs = if (existsOrZip(publisher.testMvrsFile())) AuditableCardCsvReader(publisher.testMvrsFile()) else null

        cardPools = if (auditConfig.isOA) readCardPoolsJsonFile(publisher.cardPoolsFile(), infos).unwrap() else null
    }

    fun verify() = verify(contests, show = show)
    fun verifyContest(contest: ContestUnderAudit) = verify(listOf(contest), show = true)

    fun verify(contests: List<ContestUnderAudit>, show: Boolean): VerifyResults {
        val result = VerifyResults()
        result.addMessage("---RunVerifyContests on $auditRecordLocation ")
        if (contests.size == 1) result.addMessage("  ${contests.first()} ")
        verifyCardCounts(auditConfig, contests, cards, infos, result, show = show)
        if (auditConfig.isOA) {
            verifyCardsWithPools(auditConfig, contests, cards, cardPools, infos, result, show = show)
        }
        if (auditConfig.isClca) {
            verifyAssortAvg(contests, cards.iterator(), result, show = show)
        } else {
            result.addMessage("Cant run verifyAssortAvg because cvrs from pools dont contain votes")
        }

        /*
        if (mvrs != null) {
            result.addMessage("---RunVerifyContests on testMvrs")
            verifyCardCounts(auditConfig, contests, cards, infos, result, show = show)
            verifyCardsWithPools(auditConfig, contests, mvrs, cardPools, infos, result, show = show)
            verifyAssortAvg(contests, mvrs.iterator(), result, show = show)
        } */

        return result
    }
}

class VerifyResults() {
    private val messes = mutableListOf<String>()
    var fail = false

    constructor(error: String): this() {
        addError(error)
    }
    
    fun addMessage(mess: String) = messes.add(mess)
    fun addError(mess: String) {
        addMessage(" *** $mess")
        fail = true
    }

    override fun toString() = buildString {
        appendLine("Messages")
        messes.forEach { appendLine(it) }
    }
}

data class ContestSummary(
    val allVotes: Map<Int, ContestTabulation>,
    val cardPoolVotes: Map<Int, ContestTabulation>,
    val nonPoolTabs: Map<Int, ContestTabulation>,
)

fun verifyCardCounts(
    auditConfig: AuditConfig,
    contests: List<ContestUnderAudit>,
    cards: CloseableIterable<AuditableCard>,
    infos: Map<Int, ContestInfo>,
    result: VerifyResults,
    show: Boolean = false
) {
    if (auditConfig.isPolling) {
        result.addMessage("Cant verifyCards on Polling Audit")
        return
    }
    val allCardVotes = mutableMapOf<Int, ContestTabulation>()
    val nonpoolCvrVotes = mutableMapOf<Int, ContestTabulation>()
    val poolCvrVotes = mutableMapOf<Int, ContestTabulation>()

    var count = 0
    cards.iterator().use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()
            count++
            card.contests.forEachIndexed { idx, contestId ->
                val cands = if (card.votes != null) card.votes[idx] else intArrayOf()
                val allTab = allCardVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                allTab.addVotes(cands)
                if (card.poolId == null) {
                    val nonpoolCvrTab = nonpoolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                    nonpoolCvrTab.addVotes(cands)
                } else {
                    val poolCvrTab = poolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                    poolCvrTab.addVotes(cands)
                }
            }
        }
    }
    result.addMessage("verifyCardCounts on $count cards from AuditableCardCsvReader")

    val cardTabs = tabulateAuditableCards(cards.iterator(), infos)
    if (show) {
        result.addMessage("      cardTabs = ${cardTabs}")
        result.addMessage("  allCardVotes = ${allCardVotes}")
    }

    var allOk = true
    contests.forEach { contestUA ->
        val contestTab = allCardVotes[contestUA.id]
        if (contestTab == null) {
            result.addError("contest ${contestUA.id} not found in tabulated Cvrs")
            allOk = false

        } else {
            if (contestUA.Nc != contestTab.ncards) {
                result.addError("contest ${contestUA.id} Nc ${contestUA.Nc} disagree with cvrs = ${contestTab.ncards}")
                allOk = false
            }
            if (!contestUA.isIrv) { // TODO
                // cvr phantoms look like undervotes. Cant calculate from BallotPools.
                val expectedUndervotes = contestUA.Nu + contestUA.Np * contestUA.contest.info().voteForN
                if ((auditConfig.isClca) && (expectedUndervotes != contestTab.undervotes)) {
                    result.addError("contest ${contestUA.id} expectedUndervotes ${expectedUndervotes} disagree with cvrs = ${contestTab.undervotes}")
                    allOk = false
                }
            }
        }
    }
    if (allOk) result.addMessage("  contest.Ncast == contestTab.ncards for all contests\n")
}


fun verifyCardsWithPools(
    auditConfig: AuditConfig,
    contests: List<ContestUnderAudit>,
    cards: CloseableIterable<AuditableCard>,
    cardPools: List<CardPoolIF>?,
    infos: Map<Int, ContestInfo>,
    result: VerifyResults,
    show: Boolean = false
) {
    if (auditConfig.isPolling) {
        result.addMessage("Cant verifyCards on Polling Audit")
        return
    }
    val allCvrVotes = mutableMapOf<Int, ContestTabulation>()
    val nonpoolCvrVotes = mutableMapOf<Int, ContestTabulation>()
    val poolCvrVotes = mutableMapOf<Int, ContestTabulation>()

    var count = 0
    cards.iterator().use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()
            count++
            card.contests.forEachIndexed { idx, contestId ->
                val cands = if (card.votes != null) card.votes[idx] else intArrayOf()
                val allTab = allCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                allTab.addVotes(cands)
                if (card.poolId == null) {
                    val nonpoolCvrTab = nonpoolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                    nonpoolCvrTab.addVotes(cands)
                } else {
                    val poolCvrTab = poolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                    poolCvrTab.addVotes(cands)
                }
            }
        }
    }
    result.addMessage("verifyCardsWithPools on $count cards from AuditableCardCsvReader; ${cardPools?.size} cardPools")

    val poolSums = infos.mapValues { ContestTabulation(it.value) }
    cardPools!!.forEach { cardPool ->
        cardPool.regVotes().forEach { (contestId, regVotes: RegVotes) ->
            val poolSum = poolSums[contestId]!!
            regVotes.votes.forEach { (candId, nvotes) -> poolSum.addVote(candId, nvotes) }
            poolSum.ncards += regVotes.ncards()
            poolSum.undervotes += regVotes.undervotes()
        }
    }

    val sumWithPools = mutableMapOf<Int, ContestTabulation>()
    sumWithPools.sumContestTabulations(nonpoolCvrVotes)
    sumWithPools.sumContestTabulations(poolSums)

    var ncOk = true
    contests.forEach { contestUA ->
        val sumWithPool = sumWithPools[contestUA.id]
        if (sumWithPool == null) {
            result.addError("contest ${contestUA.id} not found in tabulated Cvrs")
            ncOk = false

        } else {
            if (contestUA.Nc != sumWithPool.ncards) {
                result.addError("contest ${contestUA.id} Nc ${contestUA.Nc} disagree with sumWithPool ncards = ${sumWithPool.ncards}")
                ncOk = false
            }
            val Nup = contestUA.Nu + contestUA.Np
            if (Nup != sumWithPool.undervotes) { // phantoms look like undervotes
                result.addError("contest ${contestUA.id} Nu+Np $Nup disagree with sumWithPools undervotes ${sumWithPool.undervotes}")
                ncOk = false
            }
        }
    }
    if (ncOk) result.addMessage("  sumWithPool and undervotes agree for all contests\n")

    if (show) {
        contests.forEach { contest ->
            val id = contest.id
            result.addMessage("  contest ${id}")
            result.addMessage("       allCvrVotes = ${allCvrVotes[id]}")
            result.addMessage("   nonpoolCvrVotes = ${nonpoolCvrVotes[id]}")
            result.addMessage("      poolCvrVotes = ${poolCvrVotes[id]}")
            result.addMessage("          poolSums = ${poolSums[id]}")
            result.addMessage("      sumWithPools = ${sumWithPools[id]}")
        }
    }

    // check contest.votes == cvrTab.votes (non-IRV)
    var allOk = true
    contests.filter { it.preAuditStatus == TestH0Status.InProgress && !it.isIrv }.forEach { contestUA ->
        val contestVotes = contestUA.contest.votes()!!
        val sumWithPool = sumWithPools[contestUA.id]
        if (sumWithPool == null) {
            result.addError("contest ${contestUA.id} not found in tabulated Cvrs")
            allOk = false
        } else {
            if (!checkEquivilentVotes(contestVotes, sumWithPool.votes)) {
                result.addError("contest ${contestUA.id} votes disagree with cvrs = $sumWithPool")
                result.addError("    contestVotes = $contestVotes")
                result.addError("    sumWithPools = ${sumWithPool.votes}")
                allOk = false
            } else {
                if (show) result.addMessage("  contest ${contestUA.id} contest.votes matches cvrTabulation")
            }
        }
    }
    result.addMessage("  verifyCvrs allOk = $allOk")

    // return ContestSummary(sumWithPools, poolCvrVotes, nonpoolCvrVotes)
}

// problem is that the cvrs dont always have the votes on them, which is what passort needs to assort.
// how to detect ??
fun verifyAssortAvg(
    contests: List<ContestUnderAudit>,
    cards: CloseableIterator<AuditableCard>,
    result: VerifyResults,
    show: Boolean = false
): VerifyResults {
    var allOk = true

    // sum all the assorter values in one pass across all the cvrs, including Pools
    val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()

            contests.forEach { contest ->
                val avg = assortAvg.getOrPut(contest.id) { mutableMapOf() }
                contest.pollingAssertions.forEach { assertion ->
                    val passorter = assertion.assorter
                    val assortAvg = avg.getOrPut(passorter) { AssortAvg() } // TODO could have a hash collision ?
                    if (card.hasContest(contest.id)) {
                        assortAvg.ncards++
                        assortAvg.totalAssort += passorter.assort(
                            card.cvr(),
                            usePhantoms = false
                        )
                    }
                }
            }
        }
    }

    // compare the assortAverage with the contest's reportedMargin in passorter.
    contests.forEach { contest ->
        val contestAssortAvg = assortAvg[contest.id]!!
        contest.pollingAssertions.forEach { assertion ->
            val passorter = assertion.assorter
            val assortAvg = contestAssortAvg[passorter]!!
            if (!doubleIsClose(passorter.reportedMargin(), assortAvg.margin())) {
                result.addError("  **** margin does not agree for contest ${contest.id} assorter '$passorter'")
                result.addError("     reportedMean= ${passorter.reportedMean()} cvrs.assortAvg= ${assortAvg.avg()} ")
                allOk = false
            } else {
                if (show) result.addMessage("  margin agrees with assort avg ${assortAvg.margin()} for contest ${contest.id} assorter '$passorter'")
            }
        }
    }
    result.addMessage("  verifyAllAssortAvg allOk = $allOk")
    return result
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
    result: VerifyResults,
    show: Boolean = false,
): VerifyResults {
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
            result.addMessage("  assortAvg ${assortAvg.avg()} for contest ${contest.id} assorter '$passorter'")
            require(doubleIsClose(1.0, assortAvg.avg()))
        }
    }
    return result
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

