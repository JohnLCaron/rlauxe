package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TausRateTable
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.workflow.CardManifest
import kotlin.math.min

private val debug = false
private val logger = KotlinLogging.logger("ConsistentSampling")

// TODO not needed I think
// cant use the "maxSampleIndex", because we need to run permutations.
// so we have to send back the list of sample indices for each contest
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
}

// cardManifest may not fit into memory, so extract in-memory subset of cardManifest to use for the estimations.
fun getSubsetForEstimation(
    config: AuditConfig,
    contests: List<ContestRound>,
    cardManifest: CardManifest,
    previousSamples: Set<Long>,
): CardSamples  {
    val contestsIncluded = contests.filter { !it.done && it.included }
    if (contestsIncluded.isEmpty())
        return CardSamples(emptyList(), emptyMap())

    val allInfos = if (debug) tabulateDebugInfo(cardManifest.cards.iterator(), contestsIncluded, null) else null

    // calculate how many samples are wanted for each contest.
    val wantSampleSize: Map<Int, Int> = contestsIncluded.associate { it.id to estSamplesNeeded(config, it, cardManifest.ncards) }
    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    val skippedContests = mutableSetOf<Int>()
    val usedByContests = mutableMapOf<Int, MutableList<Int>>()

    val sampledCards = mutableListOf<AuditableCard>()
    var cardIndex = 0  // track maximum index (not done yet)

    fun contestWantsMoreSamples(c: ContestRound): Boolean {
        return (haveSampleSize[c.id] ?: 0) < (wantSampleSize[c.id] ?: 0)
    }

    var countPhantoms = 0
    var countCardsLookedAt = 0
    val sortedCardIter = cardManifest.cards.iterator()
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
            if (card.isPhantom()) countPhantoms++
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

    if (sampledCards.size == 0)
        logger.warn { "sampledCards.size == 0" }

    // you could just count the damn number of phantoms you are going to see....
    // you could run a mini audit to track the phantoms and just keep adding cards until you pass the risk limit.
    // is that kosher? It might solve the "variance due to phantoms" problem.
    // maybe you should not do incremental, just start over each time ???
    // maybe its bogus to have so many phantoms - they should be undervotes....
    // corla contest 116 - margin .133, noerror is .536 (payoff 1.0685) and phantom pct is 6% (payoff .5531)
    //  (1.0684)^n * (.5531) = 1
    // n = -ln(.5531) / ln(1.0684) = 9
    // n_payoffRisk = ln (.03) ÷ ln(1.0685) = 52
    // n_payoffPhantoms = nphantoms * n
    // nphantoms = phantomPct * sampleSize
    // sampleSize = n_payoffRisk + phantomPct * sampleSize * 9
    // sampleSize = n_payoffRisk / (1 - phantomPct * 9)
    // phantomPct = .06, sampleSize = 113,
    // phantomPct = .08, sampleSize = 185,
    // phantomPct = .09, sampleSize = 274,
    // phantomPct = .10, sampleSize = 520 (!) close to the margin
    println("nphantoms = $countPhantoms = ${countPhantoms/sampledCards.size.toDouble()}")

    if (debug) logger.info{ "getSubsetForEstimation sampled cards ncards = ${sampledCards.size} countCardsLookedAt = $countCardsLookedAt" }
    if (debug && allInfos != null) {
        val debugInfo = tabulateDebugInfo(Closer(sampledCards.iterator()), contestsIncluded, usedByContests)
        debugInfo.forEach { (contestId, debugInfo) ->
            val allInfo = allInfos[contestId]!!
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

private val fac = 10 // TODO pass in? check cardManifest, just use all if not too big, because this
                     //     algorithm isnt so great for small samples.... this sucks

// TODO feels wrong
// CLCA and OneAudit, not needed by Polling
// we dont use this for the actual estimation....
fun estSamplesNeeded(config: AuditConfig, contestRound: ContestRound, ncards: Int): Int {
    val minAssertionRound = contestRound.minAssertion()
    if (minAssertionRound == null) {
        contestRound.minAssertion()
        return 0
    }

    if (contestRound.contestUA.id == 15)
        print("")

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
        TausRateTable.makeErrorRates(
            contest.ncandidates,
            config.simFuzzPct,
            contest.Npop,
            cassorter.noerror(),
            cassorter.assorter.upperBound()
        )
    }

    val nsamples = minAssertionRound.calcNewMvrsNeeded(contest, config) // TODO NEXT

    // TODO underestimates when nsamples is low ?
    val stddev = .586 * nsamples - 23.85 // see https://github.com/JohnLCaron/rlauxe?tab=readme-ov-file#clca-with-errors

    // Approximately 95.45% / 99.73% of the data in a normal distribution falls within two / three standard deviations of the mean.
    val needed = if (stddev > 0) roundUp(nsamples + fac * stddev) else fac * nsamples

    // TODO using contestSampleCutoff as maximum
    var est =  min( contest.Npop, needed)
    if (config.contestSampleCutoff != null) est = min(config.contestSampleCutoff, est)

    if (est < 0) {
        // TODO what to do when estimate is negetive?? Perhaps fail ??
        //val wtf = cassorter.estWithOptimalBet(contest, maxLoss = config.clcaConfig.maxLoss, lastPvalue, clcaErrorCounts)
        //throw RuntimeException("est samples $est < 0")
        logger.warn { " *** getSubsetForEstimation ${contest.id}-${assorter.winLose()} estSamplesNeeded=$est margin=${assorter.dilutedMargin()} " +
                "nsamples=${nsamples}, stddev=$stddev" }

        val lastPvalue = minAssertionRound.auditResult?.plast ?: config.riskLimit
        est =  cassorter.sampleSizeNoErrors(2 * config.clcaConfig.maxLoss, lastPvalue)
        if (est < 0) { // barf
            est = 0
            contestRound.status = TestH0Status.FailMaxSamplesAllowed
            contestRound.included = false
            contestRound.done = true
            logger.info{"*** removeMaxContests contest ${contestRound.id} with status FailMaxSamplesAllowed"}
        }
    } else {
        logger.info { "getSubsetForEstimation ${contest.id}-${assorter.winLose()} estSamplesNeeded=$est margin=${assorter.dilutedMargin()} " +
                "nsamples=${nsamples}, stddev=$stddev" }
    }
    return est
}