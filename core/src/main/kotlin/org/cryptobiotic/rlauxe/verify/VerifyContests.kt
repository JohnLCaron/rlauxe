package org.cryptobiotic.rlauxe.verify

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
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
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.RegVotes
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateCardPools
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.text.appendLine

// for all audit types. Cards and CardPools must already be published, contests might not,
// but only is you call cerify with the contests' note only then do you get contestUA.preAuditStatus saved
class VerifyContests(val auditRecordLocation: String, val show: Boolean = false) {
    val auditConfig: AuditConfig
    val allContests: List<ContestUnderAudit>?
    val allInfos: Map<Int, ContestInfo>?
    val cards: CloseableIterable<AuditableCard>
    val mvrs: CloseableIterable<AuditableCard>?
    val publisher: Publisher

    init {
        publisher = Publisher(auditRecordLocation)
        val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
        auditConfig = auditConfigResult.unwrap()

        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        allContests = if (contestsResults is Ok) contestsResults.unwrap().sortedBy { it.id } else null
        allInfos = allContests?.map{ it.contest.info() }?.associateBy { it.id }

        cards = AuditableCardCsvReader(publisher.cardsCsvFile())
        mvrs = if (existsOrZip(publisher.testMvrsFile())) AuditableCardCsvReader(publisher.testMvrsFile()) else null
    }

    fun verify() = verify( allContests!!, show = show)

    fun verifyContest(contest: ContestUnderAudit) = verify(listOf(contest), show = true)

    fun verify(contests: List<ContestUnderAudit>, show: Boolean): VerifyResults {
        val results = VerifyResults()
        results.addMessage("---RunVerifyContests on $auditRecordLocation ")
        if (contests.size == 1) results.addMessage("  ${contests.first()} ")

        // all
        val infos = allInfos ?: contests.associate { it.id to it.contest.info() }
        checkContestsCorrectlyFormed(auditConfig, contests, results)
        val contestSummary = verifyManifest(auditConfig, contests, cards, infos, results, show = show)

        // OA
        if (auditConfig.isOA && contestSummary != null) {
            val cardPools = readCardPoolsJsonFile(publisher.cardPoolsFile(), infos).unwrap()
            verifyOAagainstCards(contests, contestSummary, cardPools, infos, results, show = show)
        }

        // CLCA
        if (auditConfig.isClca) {
            if (contestSummary != null) verifyClcaAgainstCards(contests, contestSummary, results, show = show)
            verifyAssortAvg(contests, cards.iterator(), results, show = show)
        } else if (auditConfig.isOA) {
            results.addMessage("Cant run verify assorters with OneAudit because cards from pools dont contain votes")
        }

        /*
        if (mvrs != null) {
            result.addMessage("---RunVerifyContests on testMvrs")
            verifyCardCounts(auditConfig, contests, cards, infos, result, show = show)
            verifyCardsWithPools(auditConfig, contests, mvrs, cardPools, infos, result, show = show)
            verifyAssortAvg(contests, mvrs.iterator(), result, show = show)
        } */

        return results
    }
}

class VerifyResults() {
    private val messes = mutableListOf<String>()
    var hasErrors = false

    constructor(error: String): this() {
        addError(error)
    }
    
    fun addMessage(mess: String) = messes.add(mess)
    fun addError(mess: String) {
        addMessage(" *** $mess")
        hasErrors = true
    }

    override fun toString() = buildString {
        messes.forEach { appendLine(it) }
    }
}

data class ContestSummary(
    val allVotes: Map<Int, ContestTabulation>,
    val nonpoolCvrVotes: Map<Int, ContestTabulation>,
    val poolCvrVotes: Map<Int, ContestTabulation>,
)

// tryimg to do it all in one iteration
fun verifyManifest(
    config: AuditConfig,
    contests: List<ContestUnderAudit>,
    cards: CloseableIterable<AuditableCard>,
    infos: Map<Int, ContestInfo>,
    results: VerifyResults,
    show: Boolean = false
): ContestSummary? {

    results.addMessage("VerifyManifest")

    val allCvrVotes = mutableMapOf<Int, ContestTabulation>()
    val nonpoolCvrVotes = mutableMapOf<Int, ContestTabulation>()
    val poolCvrVotes = mutableMapOf<Int, ContestTabulation>()

    val locationSet = mutableSetOf<String>()
    val indexSet = mutableSetOf<Int>()
    val indexList = mutableListOf<Pair<Int, Long>>()

    var count = 0
    var lastCard: AuditableCard? = null
    cards.iterator().use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()

            // 1. Check that all card locations and indices are unique, and the card prns are in ascending order
            if (!locationSet.add(card.location)) {
                results.addError("$count duplicate card.location ${card.location}")
            }

            if (!indexSet.add(card.index)) {
                results.addError("$count duplicate card.index ${card.index}")
            }

            if (lastCard != null) {
                if (card.prn <= lastCard.prn) {
                    results.addError("$count prn out of order lastCard = $lastCard card = ${card}")
                }
            }

            indexList.add(Pair(card.index, card.prn))
            lastCard = card
            count++

            if (config.hasStyles) {
                card.contests.forEachIndexed { idx, contestId ->
                    val info = infos[contestId]
                    if (info != null) {
                        val cands = if (card.votes != null) card.votes[idx] else intArrayOf()
                        val allTab = allCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                        allTab.addVotes(cands, card.phantom)
                        if (card.poolId == null) {
                            val nonpoolCvrTab =
                                nonpoolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                            nonpoolCvrTab.addVotes(cands, card.phantom)
                        } else {
                            val poolCvrTab = poolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                            poolCvrTab.addVotes(cands, card.phantom)
                        }
                    }
                }
            }
        }
    }

    // 2. Given the seed and the PRNG, check that the PRNs are correct and are assigned sequentially by index.
    val indexSorted = indexList.sortedBy { it.first }
    val prng = Prng(config.seed)
    indexSorted.forEach {
        val prn = prng.next()
        require(it.second == prn) // TODO dont allow to barf, but return null maybe
    }
    results.addMessage("  verify $count cards in the Ballot Manifest")

    // 3. If hasStyle, check that the count of phantom cards containing a contest = Contest.Nc - Contest.Ncast.
    // 4. If hasStyle, check that the count of non-phantom cards containing a contest = Contest.Ncast.
    if (config.hasStyles) {
        var allOk = true
        contests.forEach { contestUA ->
            val contestTab = allCvrVotes[contestUA.id]
            if (contestTab == null) {
                results.addError("contest ${contestUA.id} not found in tabulated cards")
                allOk = false

            } else {
                // 3. If hasStyle, check that the count of cards containing a contest = Contest.Nc.
                if (contestUA.Nc != contestTab.ncards) {
                    results.addError("contest ${contestUA.id} Nc ${contestUA.Nc} disagree with cards = ${contestTab.ncards}")
                    contestUA.preAuditStatus = TestH0Status.ContestMisformed
                    allOk = false
                }
                // 4. If hasStyle, check that the count of phantom cards containing a contest = Contest.Nc - Contest.Ncast.
                if (contestUA.Np != contestTab.nphantoms) {
                    results.addError("contest ${contestUA.id} Np ${contestUA.Np} disagree with cards = ${contestTab.nphantoms}")
                    contestUA.preAuditStatus = TestH0Status.ContestMisformed
                    allOk = false
                }
            }
        }
        if (allOk) results.addMessage("  verify contest.Nc and Np agree with manifest\n")
    }
    return ContestSummary(allCvrVotes, nonpoolCvrVotes, poolCvrVotes)
}

fun verifyOAagainstCards(
    contests: List<ContestUnderAudit>,
    contestSummary: ContestSummary,
    cardPools: List<CardPoolIF>,
    infos: Map<Int, ContestInfo>,
    result: VerifyResults,
    show: Boolean = false
) {
    val allCvrVotes = contestSummary.allVotes
    val nonpoolCvrVotes = contestSummary.nonpoolCvrVotes
    val poolCvrVotes = contestSummary.poolCvrVotes

    val poolSums = tabulateCardPools(cardPools, infos)

    /* val poolSums = infos.mapValues { ContestTabulation(it.value) }
    cardPools.forEach { cardPool ->
        cardPool.regVotes().forEach { (contestId, regVotes: RegVotes) ->
            val poolSum = poolSums[contestId]!!
            regVotes.votes.forEach { (candId, nvotes) -> poolSum.addVote(candId, nvotes) }
            poolSum.ncards += regVotes.ncards()
            poolSum.undervotes += regVotes.undervotes()
        }
    } */

    val sumWithPools = mutableMapOf<Int, ContestTabulation>()
    sumWithPools.sumContestTabulations(nonpoolCvrVotes)
    sumWithPools.sumContestTabulations(poolSums)

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
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
                allOk = false
            } else {
                if (show) result.addMessage("  contest ${contestUA.id} contest.votes matches cvrTabulation")
            }
        }
    }
    result.addMessage("  verifyCvrs allOk = $allOk")
}

fun verifyClcaAgainstCards(
    contests: List<ContestUnderAudit>,
    contestSummary: ContestSummary,
    result: VerifyResults,
    show: Boolean = false
) {

    val allCvrVotes = contestSummary.allVotes

    // check contest.votes == cvrTab.votes (non-IRV)
    var allOk = true
    contests.filter { it.preAuditStatus == TestH0Status.InProgress && !it.isIrv }.forEach { contestUA ->
        val contestVotes = contestUA.contest.votes()!!
        val contestTab = allCvrVotes[contestUA.id]
        if (contestTab == null) {
            result.addError("contest ${contestUA.id} not found in tabulated Cvrs")
            allOk = false
        } else {
            if (!checkEquivilentVotes(contestVotes, contestTab.votes)) {
                result.addError("contest ${contestUA.id} votes disagree with cvrs")
                result.addError("    contestVotes = $contestVotes")
                result.addError("    sumWithPools = ${contestTab.votes}")
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
                allOk = false
            } else {
                if (show) result.addMessage("  contest ${contestUA.id} contest.votes matches cvrTabulation")
            }
        }
    }
    result.addMessage("  verifyCvrs allOk = $allOk")
}


// problem is that the cvrs dont always have the votes on them, which is what passort needs to assort.
// how to detect ??
fun verifyAssortAvg(
    contestsUA: List<ContestUnderAudit>,
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

            contestsUA.forEach { contestUA ->
                val avg = assortAvg.getOrPut(contestUA.id) { mutableMapOf() }
                contestUA.pollingAssertions.forEach { assertion ->
                    val passorter = assertion.assorter
                    val assortAvg = avg.getOrPut(passorter) { AssortAvg() } // TODO could have a hash collision ?
                    if (card.hasContest(contestUA.id)) {
                        assortAvg.ncards++
                        assortAvg.totalAssort += passorter.assort(card.cvr(), usePhantoms = false)
                    }
                }
            }
        }
    }

    // compare the assortAverage with the contest's reportedMargin in passorter.
    contestsUA.forEach { contestUA ->
        val contestAssortAvg = assortAvg[contestUA.id]!!
        contestUA.pollingAssertions.forEach { assertion ->
            val passorter = assertion.assorter
            val assortAvg = contestAssortAvg[passorter]!!
            if (!doubleIsClose(passorter.reportedMargin(), assortAvg.margin())) {
                result.addError("  **** margin does not agree for contest ${contestUA.id} assorter '$passorter'")
                result.addError("     reportedMean= ${passorter.reportedMean()} cvrs.assortAvg= ${assortAvg.avg()} ")
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
                allOk = false
            } else {
                if (show) result.addMessage("  margin agrees with assort avg ${assortAvg.margin()} for contest ${contestUA.id} assorter '$passorter'")
            }
        }
    }
    result.addMessage("  verifyAllAssortAvg allOk = $allOk")
    return result
}

// ok if one has zero votes and the other doesnt
fun checkEquivilentVotes(votes1: Map<Int, Int>, votes2: Map<Int, Int>, ) : Boolean {
    if (votes1 == votes2) return true
    val votes1z = votes1.filter{ (_, vote) -> vote != 0 }
    val votes2z = votes2.filter{ (_, vote) -> vote != 0 }
    return votes1z == votes2z
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

fun verifyCardCounts(
    auditConfig: AuditConfig,
    contests: List<ContestUnderAudit>,
    cards: CloseableIterable<AuditableCard>,
    infos: Map<Int, ContestInfo>,
    result: VerifyResults,
    show: Boolean = false
): ContestSummary? {
    if (auditConfig.isPolling) {
        result.addMessage("Cant verifyCards on Polling Audit")
        return null
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
                allTab.addVotes(cands, card.phantom)
                if (card.poolId == null) {
                    val nonpoolCvrTab = nonpoolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                    nonpoolCvrTab.addVotes(cands, card.phantom)
                } else {
                    val poolCvrTab = poolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                    poolCvrTab.addVotes(cands, card.phantom)
                }
            }
        }
    }
    result.addMessage("verifyCardCounts on $count cards from AuditableCardCsvReader")

    var allOk = true
    contests.forEach { contestUA ->
        val contestTab = allCvrVotes[contestUA.id]
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
    return ContestSummary(allCvrVotes, nonpoolCvrVotes, poolCvrVotes)
}


 */

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

