package org.cryptobiotic.rlauxe.verify

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.AssortAvg
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.cryptobiotic.rlauxe.raire.RaireAssorter
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.mean2margin
import org.cryptobiotic.rlauxe.util.pfn
import org.cryptobiotic.rlauxe.util.sumContestTabulations
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.util.tabulateCardManifest
import org.cryptobiotic.rlauxe.workflow.readCardManifest
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.roundToInt
import kotlin.text.appendLine
import kotlin.use

// pre audit verifaction; no access to mvrs.
// for all audit types. Cards and CardPools must already be published, contests might not,
// but only if you call cerify with the contests' note only then do you get contestUA.preAuditStatus saved
class VerifyContests(val auditRecordLocation: String, val show: Boolean = false) {
    val config: AuditConfig
    val allContests: List<ContestUnderAudit>?
    val allInfos: Map<Int, ContestInfo>?
    val cardManifest: CardManifest
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

        cardManifest = readCardManifest(publisher)
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
        val contestSummary = verifyManifest(config, contests, cardManifest.cards, infos, results, show = show)

        // OA
        if (config.isOA) {
            verifyOAagainstCards(contests, contestSummary, cardManifest, infos, results, show = show)
            verifyOAassortAvg(contests, cardManifest.cards.iterator(), results, show = show)
            verifyOApools(contests, contestSummary, cardManifest, results, show = show)
        }

        // CLCA
        if (config.isClca) {
            verifyClcaAgainstCards(contests, contestSummary, results, show = show)
            verifyClcaAssortAvg(contests, cardManifest.cards.iterator(), results, show = show)
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

    val cardsTab = tabulateAuditableCards(cards.iterator(), infos)

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

            // the same as tabulateAuditableCards(), replicate so we can do allCvrVotes, nonpooled, pooled
            infos.forEach { (contestId, info) ->
                val allTab = allCvrVotes.getOrPut(contestId) { ContestTabulation(info) }
                if (card.hasContest(contestId)) { // TODO heres the problem, believing possibleContests()
                    if (card.votes != null && card.votes[contestId] != null) { // happens when cardStyle == all
                        val cands = card.votes[contestId]!!
                            allTab.addVotes(cands, card.phantom)
                    } else {
                        if (card.phantom) allTab.nphantoms++
                        allTab.ncards++
                    }

                    if (card.poolId == null) {
                        val nonpoolTab = nonpooled.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                        if (card.votes != null && card.votes[contestId] != null) { // happens when cardStyle == all
                            val cands = card.votes[contestId]!!
                                nonpoolTab.addVotes(cands, card.phantom)  // for IRV
                        } else {
                            if (card.phantom) nonpoolTab.nphantoms++
                            nonpoolTab.ncards++
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
        results.addMessage("  verify $count cards in the Manifest are ordered with no duplicate locations or indices")
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
        if (tab.ncards != it.Npop) {
            results.addError("contest ${it.id} Npop ${it.Npop} disagree with cards = ${tab.ncards}")
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
                if (contestUA.Nphantoms != contestTab.nphantoms) {
                    results.addError("contest ${contestUA.id} Nphantoms ${contestUA.Nphantoms} disagree with cards = ${contestTab.nphantoms}")
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
    cardManifest: CardManifest,
    infos: Map<Int, ContestInfo>,
    result: VerifyResults,
    show: Boolean = false
) {
    val nonpoolCvrVotes = contestSummary.nonpooled
    val poolSums = tabulateCardManifest(cardManifest, infos)
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
    // pooled + nonpooled = nPop
    // sumWithPools = nonpooled + poolSums = Nc

    // check non-IRV contest.votes == cvrTab.votes
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
                if (card.hasContest(contestUA.id)) {
                    val assorters = cardAssortAvgs.getOrPut(contestUA.id) { mutableMapOf() }
                    contestUA.clcaAssertions.forEach { cassertion ->
                        val passorter = cassertion.assorter
                        val assortAvg = assorters.getOrPut(passorter.hashcodeDesc()) { AssortAvg() }
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
            val dilutedMargin = passorter.dilutedMargin()
            if (!doubleIsClose(dilutedMargin, assortAvg.margin())) {
                result.addError("  verifyClcaAssortAvg dilutedMargin does not agree for contest ${contestUA.id} assorter='$passorter'")
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

    // sum all the assorter values in one pass across all the cards, usinmg PoolAverage when card is in a pool
    val cardAssortAvgs = mutableMapOf<Int, MutableMap<String, AssortAvg>>()  // contest -> assorter -> average
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()

            contestsUA.forEach { contestUA ->
                val avg = cardAssortAvgs.getOrPut(contestUA.id) { mutableMapOf() }
                contestUA.clcaAssertions.forEach { cassertion ->
                    if (cassertion.cassorter is ClcaAssorterOneAudit) { //  may be Raire
                        val oaCassorter = cassertion.cassorter
                        val passorter = oaCassorter.assorter
                        val assortAvg = avg.getOrPut(passorter.hashcodeDesc()) { AssortAvg() }
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

    // compare the assortAverage with the contest's dilutedMargin in passorter.
    contestsUA.forEach { contestUA ->
        val cardAssortAvg = cardAssortAvgs[contestUA.id]
        if (cardAssortAvg == null) {
            print("${contestUA.id}")
            throw RuntimeException()
        }
        contestUA.pollingAssertions.forEach { assertion ->
            val passorter = assertion.assorter
            if (cardAssortAvg[passorter.hashcodeDesc()] != null) {  //  may be Raire
                val assortAvg = cardAssortAvg[passorter.hashcodeDesc()]!!
                val dilutedMargin = passorter.dilutedMargin()
                if (!doubleIsClose(dilutedMargin, assortAvg.margin())) {
                    result.addError("  verifyOAassortAvg dilutedMargin does not agree for contest ${contestUA.id} assorter '$passorter'")
                    result.addError("     dilutedMargin= ${pfn(dilutedMargin)} cvrs.assortMargin= ${pfn(assortAvg.margin())} ncards=${assortAvg.ncards} Npop=${contestUA.Npop}")
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

//         var sumMarginInVotes = 0.0
//        cardManifest.populations.forEach { pop ->
//            val pool = pop as OneAuditPoolIF
//            val poolAvg = cassorter.poolAverages.assortAverage[pool.poolId]
//            if (poolAvg != null) {
//                val marginInVotes = mean2margin(poolAvg) * pool.ncards()
//                sumMarginInVotes += marginInVotes
//            }
//        }
//        println("sumMarginInVotes= ${sumMarginInVotes.roundToInt()}")
//        val poolMarginInVotes = sumMarginInVotes.roundToInt()
//
//        // whats the margin in votes for the cvrs ??
//        // the cards in the pools dont have votes
//        val cvrTab = tabulateAuditableCards(cardManifest.cards.iterator(), infos24).values.first()
//        val cvrVotes = cvrTab.irvVotes.makeVotes(rcontestUA.ncandidates)
//        println("  cvrVotes calcMarginInVotes= ${rassorter.calcMarginInVotes(cvrVotes)}")
//        val cvrMarginInVotes = rassorter.calcMarginInVotes(cvrVotes)

// verify assorter diluted margin from cvrs and cardPools
fun verifyOApools(
    contestsUA: List<ContestUnderAudit>,
    contestSummary: ContestSummary,
    cardManifest: CardManifest,
    result: VerifyResults,
    show: Boolean = false
): VerifyResults {
    result.addMessage("verifyOApools")
    var allOk = true

    val cvrTabs = contestSummary.nonpooled

    contestsUA.forEach { contestUA ->
        val contestId = contestUA.id

        // the cvrs
        contestUA.clcaAssertions.forEach { cassertion ->
            val cassorter = cassertion.cassorter as ClcaAssorterOneAudit
            val passorter = cassertion.assorter
            val assortAvg = AssortAvg()
            val cvrTab = cvrTabs[contestId]!!

            // the cvrs
            val cvrMargin = if (contestUA.isIrv) {
                val rassorter = passorter as RaireAssorter
                val cvrVotes = cvrTab.irvVotes.makeVotes(contestUA.ncandidates)
                rassorter.calcMargin(cvrVotes, cvrTab.ncards)
            } else {
                val regVotes = cvrTab.votes
                passorter.calcMarginFromRegVotes(regVotes, cvrTab.ncards)
            }
            val cvrMean = margin2mean(cvrMargin)
            assortAvg.ncards += cvrTab.ncards
            assortAvg.totalAssort += cvrTab.ncards * cvrMean

            // the pools
            cardManifest.populations.forEach { pop ->
                val pool = pop as OneAuditPoolIF
                val poolAvg = cassorter.poolAverages.assortAverage[pool.poolId]
                if (poolAvg != null) {
                    assortAvg.totalAssort += poolAvg * pool.ncards()
                    assortAvg.ncards += pool.ncards()
                }
            }

            val dilutedMargin = passorter.dilutedMargin()
            if (!doubleIsClose(dilutedMargin, assortAvg.margin())) {
                result.addError("  verifyOApools dilutedMargin does not agree for contest ${contestUA.id} assorter '$passorter'")
                result.addError("     dilutedMargin= ${pfn(dilutedMargin)} cardPools assortMargin= ${pfn(assortAvg.margin())} ncards=${assortAvg.ncards} Npop=${contestUA.Npop}")

                contestUA.preAuditStatus = TestH0Status.ContestMisformed
                allOk = false
            } else {
                if (show) result.addMessage("  dilutedMargin agrees with cvrs.assortMargin= ${pfn(assortAvg.margin())} for contest ${contestUA.id} assorter '$passorter'")
            }
        }
    }
    result.addMessage("  verifyOApools allOk = $allOk")
    return result
}

// ok if one has zero votes and the other doesnt
fun checkEquivilentVotes(votes1: Map<Int, Int>, votes2: Map<Int, Int>, ) : Boolean {
    if (votes1 == votes2) return true
    val votes1z = votes1.filter{ (_, vote) -> vote != 0 }
    val votes2z = votes2.filter{ (_, vote) -> vote != 0 }
    return votes1z == votes2z
}