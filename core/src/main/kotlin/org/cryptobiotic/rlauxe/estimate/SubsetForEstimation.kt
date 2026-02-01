package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
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
        val want = usedByContests[contestId]!!
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
    if (contestsIncluded.isEmpty()) return CardSamples(emptyList(), emptyMap())

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

    val estAndBet = cassorter.estWithOptimalBet(contest, maxLoss = config.clcaConfig.maxLoss, lastPvalue)
    val dd = if (cassorter is OneAuditClcaAssorter) {
        val sum = cassorter.oaAssortRates.sumOneAuditTerm(estAndBet.second)
        val sumneg = if (sum < 0) "**" else ""
        "sumOneAuditTerm=${dfn(sum, 6)} $sumneg"
    } else ""

    var nsamples =  estAndBet.first
    val stddev = .586 * nsamples - 23.85 // see https://github.com/JohnLCaron/rlauxe?tab=readme-ov-file#clca-with-errors
    // Approximately 95.45% / 99.73% of the data in a normal distribution falls within two / three standard deviations of the mean.
    val needed = roundUp(nsamples + 3 * stddev)
    var est =  min( contest.Npop, needed)
    if (config.contestSampleCutoff != null) est = min(config.contestSampleCutoff, est)
    logger.info { "getSubsetForEstimation ${contest.id}-${assorter.winLose()} estSamplesNeeded=$est margin=${assorter.dilutedMargin()} " +
            "estAndBet=${estAndBet.first}, ${df(estAndBet.second)} stddev=$stddev; $dd" }
    return est
}

//////////////////////////////////////////////////////////////////////////////////
// the idea is that the errorRates are proportional to fuzzPct
// Then p1 = fuzzPct * r1, p2 = fuzzPct * r2, p3 = fuzzPct * r3, p4 = fuzzPct * r4.
// margin doesnt matter (TODO show this)

/*
object ClcaErrorTable {
    val rrates = mutableMapOf<Int, List<Double>>() // errorRates / FuzzPct
    val standard = ClcaErrorCounts(.01, 1.0e-4, 0.01, 1.0e-4)

    fun getErrorRates(nc: Int, ncandidates: Int, fuzzPct: Double?): ClcaErrorCounts {
        if (fuzzPct == null) return standard

        val useCand = when  {
            ncandidates < 2 -> 2
            ncandidates > 10 -> 10
            else -> ncandidates
        }
        val rr = rrates[useCand]!!.map { it * fuzzPct }
        return ClcaErrorCounts(rr[0], rr[1], rr[2], rr[3])
    }

    fun calcErrorRates(contestId: Int,
                       cassorter: ClcaAssorter,
                       cvrPairs: List<Pair<Cvr, Cvr>>, // (mvr, cvr)
    ) : ClcaErrorCounts {
        require(cvrPairs.size > 0)
        val samples = PluralityErrorTracker(cassorter.noerror()) // accumulate error counts here
        cvrPairs.filter { it.first.hasContest(contestId) }.forEach { samples.addSample(cassorter.bassort(it.first, it.second, true)) }
        // require( samples.errorCounts().sum() ==  cvrPairs.size)
        return samples.pluralityErrorRates()
    }

    // given an error rate, what fuzz pct does it corresond to ?
    fun calcFuzzPct(ncandidates: Int, errorRates: PluralityErrorRates ) : List<Double> {
        val useCand = when  {
            ncandidates < 2 -> 2
            ncandidates > 10 -> 10
            else -> ncandidates
        }
        val rr = rrates[useCand]!!
        // p1 = fuzzPct * r1
        // fuzzPct = p1 / r1
        val p2o = errorRates.p2o / rr[0]
        val p1o = errorRates.p1o / rr[1]
        val p1u = errorRates.p1u / rr[2]
        val p2u = errorRates.p2u / rr[3]
        return listOf(p2o, p1o, p1u, p2u)
    }

    init {
        // GenerateClcaErrorTable.generateErrorTable()
        // N=100000 ntrials = 200
        // generated 1/26/2025
        rrates[2] = listOf(0.2623686, 0.2625469, 0.2371862, 0.2370315,)
        rrates[3] = listOf(0.1400744, 0.3492912, 0.3168304, 0.1245060,)
        rrates[4] = listOf(0.1277999, 0.3913025, 0.3519773, 0.1157800,)
        rrates[5] = listOf(0.0692904, 0.3496153, 0.3077332, 0.0600383,)
        rrates[6] = listOf(0.0553841, 0.3398728, 0.2993941, 0.0473467,)
        rrates[7] = listOf(0.0334778, 0.2815991, 0.2397504, 0.0259392,)
        rrates[8] = listOf(0.0351272, 0.3031122, 0.2591883, 0.0280541,)
        rrates[9] = listOf(0.0308620, 0.3042787, 0.2585768, 0.0254916,)
        rrates[10] = listOf(0.0276966, 0.2946918, 0.2517076, 0.0225628,)
    }
}

 */

