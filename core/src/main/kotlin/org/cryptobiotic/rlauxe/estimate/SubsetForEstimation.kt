package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TausErrorTable
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import kotlin.math.min

private val debug = false
private val logger = KotlinLogging.logger("ConsistentSampling")

data class CardSamples(val cards: List<AuditableCard>, val usedByContests: Map<Int, List<Int>>) {

    fun extractSubsetByIndex(contestId: Int): List<AuditableCard> {
        val extract = mutableListOf<AuditableCard>()
        val want = usedByContests[contestId]
        requireNotNull(want)
        var wantIdx = 0
        cards.forEachIndexed { idx, it ->
            if (wantIdx < want.size && idx == want[wantIdx]) {
                extract.add(it)
                wantIdx++
            }
        }
        return extract
    }

    fun extractSubsetByIndex(contestId: Int, pairs: List<Pair<CvrIF, AuditableCard>>): List<Pair<CvrIF, AuditableCard>> {
        val extract = mutableListOf<Pair<CvrIF, AuditableCard>>()
        val want = usedByContests[contestId]!!
        var wantIdx = 0
        pairs.forEachIndexed { idx, it ->
            if (wantIdx < want.size && idx == want[wantIdx]) {
                extract.add(it)
                wantIdx++
            }
        }
        return extract
    }

}

// bit simpler than consistentSampling, but tempting to try to combine the two
// cant use the "maxSampleIndex", because we need to run permutaiuons.
// so we have to send back the list of sample indices for each contest
fun getSubsetForEstimation(
    config: AuditConfig,
    contests: List<ContestRound>,
    cards: CloseableIterable<AuditableCard>,
    previousSamples: Set<Long>,  // TODO skip previous samples maybe?
): CardSamples
{
    val contestsIncluded = contests.filter { !it.done && it.included }
    if (contestsIncluded.isEmpty())
        return CardSamples(emptyList(), emptyMap())

    val allInfo = tabulateDebugInfo(cards.iterator(), contestsIncluded, null)

    // calculate how many samples are wanted for each contest.
    val wantSampleSize: Map<Int, Int> = contestsIncluded.associate { it.id to estSamplesNeeded(config, it) }
    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    val skippedContests = mutableSetOf<Int>()
    val usedByContests = mutableMapOf<Int, MutableList<Int>>()

    val sampledCards = mutableListOf<AuditableCard>()
    var cardIndex = 0  // track maximum index (not done yet)

    fun contestWantsMoreSamples(c: ContestRound): Boolean {
        return (haveSampleSize[c.id] ?: 0) < (wantSampleSize[c.id] ?: 0)
    }

    var countCardsLookedAt = 0
    val sortedCardIter = cards.iterator()
    while (sortedCardIter.hasNext()) {
        if (!contestsIncluded.any { contestWantsMoreSamples(it)} ) break

        // get the next card in sorted order
        val card = sortedCardIter.next()
        if (previousSamples.contains(card.prn)) continue
        countCardsLookedAt++

        // does anyone want this card ?
        var include = false
        contestsIncluded.forEach { contest ->
            // does this contest want this card ?
            if (card.hasContest(contest.id)) {
                if ((haveSampleSize[contest.id] ?: 0) < (wantSampleSize[contest.id] ?: 0)) {
                    include = true
                }
            }
        }

        if (include) {
            sampledCards.add(card)
        }

        // update the haveSampleSize count and maxSampleIndexAllowed
        contestsIncluded.forEach { contest ->
            if (card.hasContest(contest.id)) {
                if (include && !skippedContests.contains(contest.id)) {
                    haveSampleSize[contest.id] = haveSampleSize[contest.id]?.plus(1) ?: 1
                    // ok to use if we havent skipped any cards for this contest in its sequence
                    val usedByContest = usedByContests.getOrPut(contest.id) { mutableListOf() }
                    usedByContest.add(sampledCards.size-1)
                } else {
                    // if card has contest but its not included in the sample, then continuity has been broken
                    skippedContests.add(contest.id)
                }
            }
        }

        cardIndex++
    }

    logger.info{ "getSubsetForEstimation sampled cards ncards = ${sampledCards.size} countCardsLookedAt = $countCardsLookedAt" }
    if (debug) {
        val debugInfo = tabulateDebugInfo(Closer(sampledCards.iterator()), contestsIncluded, usedByContests)
        debugInfo.forEach { (contestId, debugInfo) ->
            val allInfo = allInfo[contestId]!!
            println("  $debugInfo allPct=${df(allInfo.pct())} wantSampleSize=${wantSampleSize[contestId]}")
        }
    }

    return CardSamples(sampledCards, usedByContests)
}

class ContestDebugInfo(val contestId: Int, var count: Int, var countNotPooled: Int) {
    fun pct() = countNotPooled/count.toDouble()
    override fun toString(): String {
        return "ContestDebugInfo(contestId=$contestId, count=$count, countNotPooled=$countNotPooled pct=${df(pct())}"
    }
}

fun tabulateDebugInfo(cards: CloseableIterator<AuditableCard>, contests: List<ContestRound>, usedByContests: Map<Int, List<Int>>? = null): Map<Int, ContestDebugInfo> {
    val tabs = mutableMapOf<Int, ContestDebugInfo>()
    var sampleIndex = 0
    cards.use { cardIter ->
        while (cardIter.hasNext()) {
            val card = cardIter.next()
            sampleIndex++
            contests.forEach { contest ->
                if (usedByContests != null && usedByContests[contest.id] == null)
                    print("how")
                val useCard = usedByContests == null || usedByContests[contest.id]!!.contains(sampleIndex)
                if (useCard && card.hasContest(contest.id)) {
                    val tab = tabs.getOrPut(contest.id) { ContestDebugInfo(contest.id, 0, 0) }
                    tab.count++
                    if (card.poolId == null)
                        tab.countNotPooled++
                }
            }
        }
    }
    return tabs.toSortedMap()
}

private val fac = 3 // TODO pass in

// CLCA and OneAudit TODO POLLING
// we dont use this for the actual estimation....
fun estSamplesNeeded(config: AuditConfig, contestRound: ContestRound): Int {
    val minAssertionRound = contestRound.minAssertion()
    if (minAssertionRound == null) {
        contestRound.minAssertion()
        throw RuntimeException()
    }

    val lastPvalue = minAssertionRound.auditResult?.plast ?: config.riskLimit
    val minAssertion = minAssertionRound.assertion
    val cassorter = (minAssertion as ClcaAssertion).cassorter

    val contest = contestRound.contestUA
    val assorter = cassorter.assorter

    // i think problem is that we arent adding the fuzzPct to estWithOptimalBet ??
    //val errorCounts = mutableMapOf<Double, Int>()
    //val taus = Taus(cassorter.assorter.upperBound())
    // ClcaErrorCounts(errorCounts, contest.Nc, cassorter.noerror(), cassorter.assorter.upperBound())
    // ClcaErrorTable.getErrorRates(contest.ncandidates, config.simFuzzPct)
    val clcaErrorCounts = if (config.simFuzzPct == null || config.simFuzzPct == 0.0) null else {
        TausErrorTable.makeErrorRates(
            contest.ncandidates,
            config.simFuzzPct,
            contest.Npop,
            cassorter.noerror(),
            cassorter.assorter.upperBound()
        )
    }

    val estAndBet = cassorter.estWithOptimalBet(contest, maxLoss = config.clcaConfig.maxLoss, lastPvalue, clcaErrorCounts)
    val dd = if (cassorter is OneAuditClcaAssorter) {
        val sum = cassorter.oaAssortRates.sumOneAuditTerm(estAndBet.second)
        val sumneg = if (sum < 0) "**" else ""
        "sumOneAuditTerm=${dfn(sum, 6)} $sumneg"
    } else ""

    var nsamples =  estAndBet.first
    val stddev = .586 * nsamples - 23.85 // see https://github.com/JohnLCaron/rlauxe?tab=readme-ov-file#clca-with-errors

    // Approximately 95.45% / 99.73% of the data in a normal distribution falls within two / three standard deviations of the mean.
    val needed = if (stddev > 0) roundUp(nsamples + fac * stddev) else fac * nsamples

    var est =  min( contest.Npop, needed)
    if (config.contestSampleCutoff != null) est = min(config.contestSampleCutoff, est)
    logger.info { "getSubsetForEstimation ${contest.id}-${assorter.winLose()} estSamplesNeeded=$est margin=${assorter.dilutedMargin()} " +
            "estAndBet=${estAndBet.first}, ${df(estAndBet.second)} stddev=$stddev; $dd" }

    if (est < 0) {
        // TODO what to do when estimate is negetive?? Perhaps fail ??
        //val wtf = cassorter.estWithOptimalBet(contest, maxLoss = config.clcaConfig.maxLoss, lastPvalue, clcaErrorCounts)
        //throw RuntimeException("est samples $est < 0") // TODO
        return cassorter.sampleSizeNoErrors(2 * config.clcaConfig.maxLoss, lastPvalue)
    }
    return est
}