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
    val config: AuditConfig
    val allContests: List<ContestUnderAudit>?
    val allInfos: Map<Int, ContestInfo>?
    val cards: CloseableIterable<AuditableCard>
    val mvrs: CloseableIterable<AuditableCard>?
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
        mvrs = if (existsOrZip(publisher.sortedMvrsFile())) AuditableCardCsvReader(publisher.sortedMvrsFile()) else null
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
        }

        // CLCA
        if (config.isClca) {
            verifyClcaAgainstCards(contests, contestSummary, results, show = show)
            verifyAssortAvg(contests, cards.iterator(), results, show = show)
        }

        /*
        if (mvrs != null) {
            result.addMessage("---RunVerifyContests on testMvrs")
            verifyCardCounts(config, contests, cards, infos, result, show = show)
            verifyCardsWithPools(config, contests, mvrs, cardPools, infos, result, show = show)
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

// all audits, including polling
fun verifyManifest(
    config: AuditConfig,
    contests: List<ContestUnderAudit>,
    cards: CloseableIterable<AuditableCard>,
    infos: Map<Int, ContestInfo>,
    results: VerifyResults,
    show: Boolean = false
): ContestSummary {

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

            if (card.votes != null) {
                card.votes.forEach { (contestId, cands) ->
                    val info = infos[contestId]
                    if (info != null) {
                        val allTab = allCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                        allTab.addVotes(cands, card.phantom)
                        if (card.poolId == null) {
                            val nonpoolCvrTab = nonpoolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                            nonpoolCvrTab.addVotes(cands, card.phantom)
                        } else {
                            // TODO seems like this never happen? have a poolId and votes ??
                            val poolCvrTab = poolCvrVotes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                            poolCvrTab.addVotes(cands, card.phantom)
                        }
                    }
                }
            }
        }
    }
    if (!results.hasErrors) {
        results.addMessage("  verify $count cards in the Manifest are ordered with no duplicates")
    }

    // 2. Given the seed and the PRNG, check that the PRNs are correct and are assigned sequentially by index.
    val indexSorted = indexList.sortedBy { it.first }
    val prng = Prng(config.seed)
    indexSorted.forEach {
        val prn = prng.next()
        require(it.second == prn) // TODO dont allow to barf, but return null maybe
    }
    results.addMessage("  verify $count cards in the Manifest have correct prn")

    // 3. If hasStyle, check that the count of phantom cards containing a contest = Contest.Nc - Contest.Ncast.
    // 4. If hasStyle, check that the count of non-phantom cards containing a contest = Contest.Ncast.
    if (config.isClca) {
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
        if (allOk) results.addMessage("  verify contest.Nc and Np agree with manifest")
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
    val cardAssortAvgs = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()

            contestsUA.forEach { contestUA ->
                val avg = cardAssortAvgs.getOrPut(contestUA.id) { mutableMapOf() }
                contestUA.pollingAssertions.forEach { assertion ->
                    val passorter = assertion.assorter
                    val assortAvg = avg.getOrPut(passorter) { AssortAvg() } // TODO could we have a hash collision ?
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
            val assortAvg = cardAssortAvg[passorter]!!
            val dilutedMargin = contestUA.makeDilutedMargin(passorter)
            val cardMargin = assortAvg.margin()
            if (!doubleIsClose(dilutedMargin, cardMargin)) {
                result.addError("  margin does not agree for contest ${contestUA.id} assorter '$passorter'")
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