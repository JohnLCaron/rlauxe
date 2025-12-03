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
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardCsvReader
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.readCardPoolsJsonFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateCardPools
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.text.appendLine

// for all audit types. Cards and CardPools must already be published, contests might not,
// but only is you call cerify with the contests' note only then do you get contestUA.preAuditStatus saved
class VerifyContests(val auditRecordLocation: String, val show: Boolean = false) {
    val config: AuditConfig
    val allContests: List<ContestUnderAudit>?
    val allInfos: Map<Int, ContestInfo>?
    val cards: CloseableIterable<AuditableCard>
    val publisher: Publisher

    init {
        publisher = Publisher(auditRecordLocation)
        val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
        config = auditConfigResult.unwrap()

        val contestsResults = readContestsJsonFile(publisher.contestsFile())
        allContests = if (contestsResults is Ok) contestsResults.unwrap().sortedBy { it.id } else {
            println(contestsResults)
            null
        }
        allInfos = allContests?.map{ it.contest.info() }?.associateBy { it.id }

        cards = AuditableCardCsvReader(publisher.sortedCardsFile())
        // mvrs = if (existsOrZip(publisher.sortedMvrsFile())) AuditableCardCsvReader(publisher.sortedMvrsFile()) else null
    }

    fun verify() = verify( allContests!!, show = show)

    fun verifyContest(contest: ContestUnderAudit) = verify(listOf(contest), show = true)

    fun verify(contests: List<ContestUnderAudit>, show: Boolean): VerifyResults {
        val results = VerifyResults()
        results.addMessage("---RunVerifyContests on $auditRecordLocation ")
        if (contests.size == 1) results.addMessage("  ${contests.first()} ")

        // all
        val infos = allInfos ?: contests.associate { it.id to it.contest.info() }
        checkContestsCorrectlyFormed(config, contests, results)
        val contestSummary = verifyManifest(config, contests, cards, infos, results, show = show)

        // OA
        if (config.isOA) {
            val cardPools = readCardPoolsJsonFile(publisher.cardPoolsFile(), infos).unwrap()
            verifyOAagainstCards(contests, contestSummary, cardPools, infos, results, show = show)
            verifyOAassortAvg(contests, cards.iterator(), results, show = show)
            verifyOApools(contests, contestSummary, cardPools, results, show = show)
        }

        // CLCA
        if (config.isClca) {
            verifyClcaAgainstCards(contests, contestSummary, results, show = show)
            verifyClcaAssortAvg(contests, cards.iterator(), results, show = show)
        }
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
    val nonpooled: Map<Int, ContestTabulation>,
    val pooled: Map<Int, ContestTabulation>,
)

// all audits, including polling
fun verifyManifest(
    config: AuditConfig,
    contestsUA: List<ContestUnderAudit>,
    cards: CloseableIterable<AuditableCard>,
    infos: Map<Int, ContestInfo>,
    results: VerifyResults,
    show: Boolean = false
): ContestSummary {
    results.addMessage("VerifyManifest")

    val allCvrVotes = mutableMapOf<Int, ContestTabulation>()
    val nonpooled = mutableMapOf<Int, ContestTabulation>()
    val pooled = mutableMapOf<Int, ContestTabulation>()

    val locationSet = mutableSetOf<String>()
    val indexSet = mutableSetOf<Int>()
    val indexList = mutableListOf<Pair<Int, Long>>()

    var count = 0
    var lastCard: AuditableCard? = null
    cards.iterator().use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()
            //if (card.index in 100..150)
            //    print("")

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

            // the same as tabulateAuditableCards()
            infos.forEach { (contestId, info) ->
                val allTab = allCvrVotes.getOrPut(contestId) { ContestTabulation(info) }
                if (card.hasContest(contestId)) { // TODO heres the problem, believing possibleContests()
                    allTab.ncards++ // how many cards are in the sample population?

                    if (card.phantom) allTab.nphantoms++
                    if (card.votes != null) {
                        val cands = card.votes[contestId]
                        if (cands == null) {
                            allTab.undervotes++
                        } else {
                            cands.forEach { cand -> allTab.addVote(cand, 1) }
                        }
                    }

                    if (card.poolId == null) {
                        val nonpoolTab = nonpooled.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                        nonpoolTab.ncards++
                        if (card.phantom) nonpoolTab.nphantoms++
                        if (card.votes != null) { // I  think this is always true
                            val cands = card.votes[contestId]
                            if (cands == null) {
                                nonpoolTab.undervotes++
                            } else {
                                cands.forEach { cand -> nonpoolTab.addVote(cand, 1) }
                            }
                        }
                    } else {
                        val poolTab = pooled.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                        poolTab.ncards++
                    }
                }
            }
        }
    }
    if (!results.hasErrors) {
        results.addMessage("  verify $count cards in the Manifest are ordered with no duplicates")
    }

    // 2. Given the seed and the PRNG, check that the PRNs are correct and are assigned sequentially by index.
    var countErrs = 0
    val indexSorted = indexList.sortedBy { it.first }
    val prng = Prng(config.seed)
    indexSorted.forEach {
        val prn = prng.next()
        if(it.second != prn) countErrs++
    }
    if (countErrs > 0)
        results.addError("  verify $count cards in the Manifest have correct prn: there are $countErrs errors")
    else
      results.addMessage("  verify $count cards in the Manifest have correct prn")

    // check if tabulation agrees with diluted count
    contestsUA.forEach {
        val tab = allCvrVotes[it.id]!!
        if (tab.ncards != it.Nb) {
            results.addError("contest ${it.id} Nb ${it.Nb} disagree with cards = ${tab.ncards}")
        }
    }

    // 3. If hasStyle, check that the count of phantom cards containing a contest = Contest.Nc - Contest.Ncast.
    // 4. If hasStyle, check that the count of non-phantom cards containing a contest = Contest.Ncast.
    if (config.isClca) {
        var allOk = true
        contestsUA.forEach { contestUA ->
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
        if (allOk) results.addMessage("  verify contest.Nc and Np agree with manifest")
    }
    return ContestSummary(allCvrVotes, nonpooled, pooled)
}

fun verifyOAagainstCards(
    contests: List<ContestUnderAudit>,
    contestSummary: ContestSummary,
    cardPools: List<CardPoolIF>,
    infos: Map<Int, ContestInfo>,
    result: VerifyResults,
    show: Boolean = false
) {
    val nonpoolCvrVotes = contestSummary.nonpooled
    val poolSums = tabulateCardPools(cardPools, infos)
    val sumWithPools = mutableMapOf<Int, ContestTabulation>()
    sumWithPools.sumContestTabulations(nonpoolCvrVotes)
    sumWithPools.sumContestTabulations(poolSums)

    result.addMessage("verifyOAagainstCards")
    if (show) {
        contests.forEach { contest ->
            val id = contest.id
            result.addMessage("  contest ${id}")
            result.addMessage("               all = ${contestSummary.allVotes[id]}")
            result.addMessage("            pooled = ${contestSummary.pooled[id]}")
            result.addMessage("         nonpooled = ${contestSummary.nonpooled[id]}")
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
                result.addError("contest ${contestUA.id} votes disagree with sumWithPool = $sumWithPool")
                result.addError("    contestVotes = $contestVotes")
                result.addError("    sumWithPools = ${sumWithPool.votes}")
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
                allOk = false
            } else {
                if (show) result.addMessage("  contest ${contestUA.id} contest.votes matches sumWithPool")
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
    result.addMessage("verifyClcaAgainstCards")

    val allCvrVotes = contestSummary.allVotes

    // check contest.votes == cvrTab.votes (non-IRV)
    var allOk = true
    contests.filter { it.preAuditStatus == TestH0Status.InProgress && !it.isIrv }.forEach { contestUA ->
        val contestVotes = contestUA.contest.votes()!!
        val cvrTab = allCvrVotes[contestUA.id]
        if (cvrTab == null) {
            result.addError("contest ${contestUA.id} not found in tabulated Cvrs")
            allOk = false
        } else {
            if (!checkEquivilentVotes(contestVotes, cvrTab.votes)) {
                result.addError("contest ${contestUA.id} votes disagree with cvrs")
                result.addError("    contestVotes = $contestVotes")
                result.addError("          cvrTab = ${cvrTab.votes}")
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
                allOk = false
            } else {
                if (show) result.addMessage("  contest ${contestUA.id} contest.votes matches cvrTabulation")
            }
        }
    }
    result.addMessage("  verifyCvrs allOk = $allOk")
}

fun verifyClcaAssortAvg(
    contestsUA: List<ContestUnderAudit>,
    cards: CloseableIterator<AuditableCard>,
    result: VerifyResults,
    show: Boolean = false
): VerifyResults {
    result.addMessage("verifyClcaAssortAvg")

    var allOk = true

    // sum all the assorter values in one pass across all the cvrs, including Pools
    val cardAssortAvgs = mutableMapOf<Int, MutableMap<String, AssortAvg>>()  // contest -> assorter -> average
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()

            contestsUA.forEach { contestUA ->
                val avg = cardAssortAvgs.getOrPut(contestUA.id) { mutableMapOf() }
                contestUA.clcaAssertions.forEach { cassertion ->
                    val passorter = cassertion.assorter
                    val assortAvg = avg.getOrPut(passorter.hashcodeDesc()) { AssortAvg() }
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
        val cardAssortAvg = cardAssortAvgs[contestUA.id]!!
        contestUA.pollingAssertions.forEach { assertion ->
            val passorter = assertion.assorter
            val assortAvg = cardAssortAvg[passorter.hashcodeDesc()]!!
            val dilutedMargin = contestUA.makeDilutedMargin(passorter)
            if (!doubleIsClose(dilutedMargin, assortAvg.margin())) {
                result.addError("  dilutedMargin does not agree for contest ${contestUA.id} assorter='$passorter'")
                result.addError("     dilutedMargin= ${pfn(dilutedMargin)} cvrs.assortMargin= ${pfn(assortAvg.margin())} ncards=${assortAvg.ncards}")
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
                allOk = false
            } else {
                if (show) result.addMessage("  dilutedMargin agrees with cvrs.assortMargin= ${pfn(assortAvg.margin())} for contest ${contestUA.id} assorter='$passorter'")
            }
        }
    }
    result.addMessage("  verifyClcaAssortAvg allOk = $allOk")
    return result
}

// calculate diluted margin from assort values and poolAverages
fun verifyOAassortAvg(
    contestsUA: List<ContestUnderAudit>,
    cards: CloseableIterator<AuditableCard>,
    result: VerifyResults,
    show: Boolean = false
): VerifyResults {
    result.addMessage("verifyOAassortAvg")

    var allOk = true

    // sum all the assorter values in one pass across all the cvrs, including Pools
    val cardAssortAvgs = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()

            contestsUA.forEach { contestUA ->
                val avg = cardAssortAvgs.getOrPut(contestUA.id) { mutableMapOf() }
                contestUA.clcaAssertions.forEach { cassertion ->
                    if (cassertion.cassorter is ClcaAssorterOneAudit) { //  may be Raire
                        val oaCassorter = cassertion.cassorter as ClcaAssorterOneAudit
                        val passorter = oaCassorter.assorter
                        val assortAvg = avg.getOrPut(passorter) { AssortAvg() }
                        if (card.hasContest(contestUA.id)) {
                            val assortVal = if (card.poolId != null)
                                oaCassorter.poolAverages.assortAverage[card.poolId]!!
                            else
                                passorter.assort(card.cvr(), usePhantoms = false)
                            assortAvg.totalAssort += assortVal
                            assortAvg.ncards++
                        }
                    }
                }
            }
        }
    }

    // compare the assortAverage with the contest's reportedMargin in passorter.
    contestsUA.forEach { contestUA ->
        val cardAssortAvg = cardAssortAvgs[contestUA.id]!!
        contestUA.pollingAssertions.forEach { assertion ->
            val passorter = assertion.assorter
            if (cardAssortAvg[passorter] != null) {  //  may be Raire
                val assortAvg = cardAssortAvg[passorter]!!
                val dilutedMargin = contestUA.makeDilutedMargin(passorter)
                if (!doubleIsClose(dilutedMargin, assortAvg.margin())) {
                    result.addError("  dilutedMargin does not agree for contest ${contestUA.id} assorter '$passorter'")
                    result.addError("     dilutedMargin= ${pfn(dilutedMargin)} cvrs.assortMargin= ${pfn(assortAvg.margin())} ncards=${assortAvg.ncards}")
                    contestUA.preAuditStatus = TestH0Status.ContestMisformed
                    allOk = false
                } else {
                    if (show) result.addMessage("  dilutedMargin agrees with cvrs.assortMargin= ${pfn(assortAvg.margin())} for contest ${contestUA.id} assorter '$passorter'")
                }
            }
        }
    }
    result.addMessage("  verifyOAassortAvg allOk = $allOk")
    return result
}

// calculate diluted margin from cardPools
fun verifyOApools(
    contestsUA: List<ContestUnderAudit>,
    contestSummary: ContestSummary,
    pools: List<CardPoolIF>,
    result: VerifyResults,
    show: Boolean = false
): VerifyResults {
    result.addMessage("verifyOApools")
    var allOk = true

    contestsUA.forEach { contestUA ->
        val contestId = contestUA.id

        contestUA.pollingAssertions.forEach { assertion ->
            val passorter = assertion.assorter
            val assortAvg = AssortAvg()
            pools.filter { it.name() != "unpooled" }.forEach { cardPool ->
                if (cardPool.hasContest(contestId)) {
                    val regVotes = cardPool.regVotes()[contestId]!!
                    if (cardPool.ncards() > 0) {
                        // note: use cardPool.ncards(), this is the diluted count
                        val poolMargin = assertion.assorter.calcMargin(regVotes.votes, cardPool.ncards())
                        val poolAvg = margin2mean(poolMargin)
                        assortAvg.ncards += cardPool.ncards()
                        assortAvg.totalAssort += cardPool.ncards() * poolAvg
                    }
                }
            }
            // result.addMessage("  cardPools assortAvg = ${assortAvg.avg()} assortMargin = ${assortAvg.margin()} ncards = ${assortAvg.ncards}")

            /* this part may not be needed
            if (contestSummary.pooled[contestId] != null) {
                val pooled = contestSummary.pooled[contestId]!!
                val extra = pooled.ncards() - assortAvg.ncards
                assortAvg.ncards += extra
                assortAvg.totalAssort += extra * 0.5 // this says that the extra get counted as 1/2; but i think they get counted as assortAverage[card.poolId]
                // result.addMessage("  diluted extra votes = ${extra}")
            } */

            if (contestSummary.nonpooled[contestId] != null) {
                val nonpoolTab = contestSummary.nonpooled[contestId]!!
                // result.addMessage("  nonpoolCvrVotes = ${nonpoolTab}")
                val poolMargin = assertion.assorter.calcMargin(nonpoolTab.votes, nonpoolTab.ncards())
                val poolAvg = margin2mean(poolMargin)
                assortAvg.ncards += nonpoolTab.ncards()
                assortAvg.totalAssort += nonpoolTab.ncards() * poolAvg
                // result.addMessage("  contest assortAvg = ${assortAvg.avg()} assortMargin = ${assortAvg.margin()} ncards = ${assortAvg.ncards}")
            }

            val dilutedMargin = contestUA.makeDilutedMargin(passorter)

            if (!doubleIsClose(dilutedMargin, assortAvg.margin())) {
                result.addError("  dilutedMargin does not agree for contest ${contestUA.id} assorter '$passorter'")
                result.addError("     dilutedMargin= ${pfn(dilutedMargin)} cardPools assortMargin= ${pfn(assortAvg.margin())} ncards=${assortAvg.ncards}")
                contestUA.preAuditStatus = TestH0Status.ContestMisformed
                allOk = false
            } else {
                if (show) result.addMessage("  dilutedMargin agrees with cvrs.assortMargin= ${pfn(assortAvg.margin())} for contest ${contestUA.id} assorter '$passorter'")
            }
        }
    }
    result.addMessage("  verifyOAassortAvg allOk = $allOk")
    return result
}

// ok if one has zero votes and the other doesnt
fun checkEquivilentVotes(votes1: Map<Int, Int>, votes2: Map<Int, Int>, ) : Boolean {
    if (votes1 == votes2) return true
    val votes1z = votes1.filter{ (_, vote) -> vote != 0 }
    val votes2z = votes2.filter{ (_, vote) -> vote != 0 }
    return votes1z == votes2z
}