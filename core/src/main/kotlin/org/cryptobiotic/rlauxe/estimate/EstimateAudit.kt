package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.EstimationRoundResult
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.Taus
import org.cryptobiotic.rlauxe.betting.populationMeanIfH0
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPool
import org.cryptobiotic.rlauxe.oneaudit.VunderPools
import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.workflow.CardManifest
import kotlin.Double
import kotlin.Int
import kotlin.collections.sortedBy
import kotlin.math.min
import kotlin.use

private val logger = KotlinLogging.logger("EstimateAudit")

// TODO  round > 1 we want to incorporate the measured errors from previous rounds
//   cant we use vunderPool to do so, that only uses fuzz
//   just change the assortValue randomly p percent of the time. can do the same for clca.
class EstimateAudit(
    val config: AuditConfig,
    val roundIdx: Int,
    val contests: List<ContestRound>,
    val pools: List<OneAuditPool>?,
    val cardManifest: CardManifest,
) {

    fun run(contestOnly: Int? = null): List<RunRepeatedResult> {
        val contestsToAudit = if (contestOnly == null) contests.filter { !it.done && it.included } else
            listOf( contests.find { it.id == contestOnly}!! )

        if (contestsToAudit.isEmpty())
            return emptyList()

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<AuditTrialTask>()

        // TODO use more simulations when the margin is low or calcNewMvrs are high ??
        // TODO use 1 when CLCA with no errors
        val ntrials = if (config.isClca) 1 else config.nsimEst
        repeat(ntrials) { run ->
            tasks.add(AuditTrialTask(roundIdx, run+1, config, contestsToAudit, pools, cardManifest))
        }
        val trialResults: List<List<ContestTrial>> = ConcurrentTaskRunnerG<List<ContestTrial>>().run(tasks)

        val trackerResults = mutableMapOf<Int, MutableList<ContestTrial>>()
        contestsToAudit.forEach { trackerResults[it.id] = mutableListOf() }
        trialResults.forEach { result ->
            result.forEach { trackerResults[it.contest.id]!!.add(it) }
        }

        // transfer info to contestRound and minAssertion
        contestsToAudit.forEach { contestRound ->
            val contestResults: List<ContestTrial> = trackerResults[contestRound.id]!!
            val pct = when (roundIdx) {
                1 -> 50
                2 -> 80
                else -> 96
            }

            contestResults.forEach {
                if (it.wantsMore()) {
                    println(" wantsMore $it")
                }
                require( !contestResults.any { it.wantsMore() })
            }

            val useTrial = findQuantileTrial(contestResults, .01 * pct)
            val distribution: List<Int> = contestResults.map { it.nmvrs() }.sorted()

            // TODO only have minAssertion; is that ok ?
            //   minAssertion can change when some assertions succeed in previous rounds
            val useAssertionRound = useTrial.assertionRound()

            val newMvrs = roundUp(percentiles().index(pct).compute(*distribution.toIntArray()))
            val prevNmrs = useAssertionRound.prevAssertionRound?.auditResult?.samplesUsed ?: 0
            val estMvrs = prevNmrs + newMvrs

            // testOnly means dont modify the AuditRecord
            if (contestOnly == null) {
                contestRound.estMvrs = estMvrs
                contestRound.estNewMvrs = newMvrs

                useAssertionRound.estMvrs = estMvrs
                useAssertionRound.estNewMvrs = newMvrs

                val estimatiomResult = EstimationRoundResult(
                    roundIdx,
                    "EstimateAudit",
                    calcNewMvrsNeeded = useAssertionRound.calcNewMvrsNeeded(contestRound.contestUA, config),
                    startingTestStatistic = useTrial.startingTestStatistic(), // TODO
                    startingErrorRates = emptyMap(), // TODO capture nphantoms ?
                    estimatedDistribution = distribution,
                    lastIndex = useTrial.maxIndex(),
                    quantile = pct,
                    ntrials = ntrials,
                    simNewMvrsNeeded = newMvrs,
                    simMvrsNeeded = estMvrs,
                )

                // attach estimatiomResult to all the assertions still to be done
                contestRound.assertionRounds.forEach { assertion ->
                    assertion.estimationResult = estimatiomResult
                }
            }

            println("  ${contestRound.id} quantile = $pct uses $newMvrs from ${distribution} lastIndex= ${useTrial.maxIndex()}")
        }
        logger.info { "EstimateAudit ntrials=${ntrials} ncontests=${contestsToAudit.size} took $stopwatch" }

        return emptyList() // bogus
    }
}

// 1 trial, all contests
class AuditTrialTask(
    val roundIdx: Int,
    val run: Int,
    val config: AuditConfig,
    val contestsToAudit: List<ContestRound>,
    val pools: List<OneAuditPool>?,
    val cardManifest: CardManifest) : ConcurrentTaskG<List<ContestTrial>> {

    override fun name() = "roundIdx $roundIdx Run $run"

    override fun run(): List<ContestTrial> {
        val stopwatch = Stopwatch()
        val vunderPools = if (pools != null) VunderPools(pools) else null // different simulated pool data each run

        val contestTrials = contestsToAudit.map {
            ContestTrial(run, config, it)
        }

        var cardSortedIndex = 1 // 1 based

        var countCardsIncluded = 0
        var countPoolCards = 0
        cardManifest.cards.iterator().use { sortedCardIter ->
            while (sortedCardIter.hasNext()) {
                // does any contest need more cards ?
                if (!contestTrials.any { it.wantsMore() }) break

                // get the next card in sorted order
                val card = sortedCardIter.next()
                val mvr = if (card.poolId == null) null else {
                    vunderPools!!.simulatePooledCard(card) // simulate differently each trial to get a distribution
                }

                var include = false
                contestTrials.forEach { contestTrial ->
                    // does this contest want this card ?
                    if (contestTrial.wantsMore() && card.hasContest(contestTrial.contest.id) && !contestTrial.skip()) {
                        include = true
                        contestTrial.addCard(mvr, card, cardSortedIndex)
                    }
                }

                if (include) {
                    // sampledCards.add(card)
                    countCardsIncluded++
                    if (card.poolId != null) countPoolCards++
                }
                cardSortedIndex++
            }
        }
        logger.info { "roundIdx $roundIdx $run countCardsIncluded=$countCardsIncluded took $stopwatch" }

        return contestTrials
    }
}

// 1 trial, 1 contest
class ContestTrial(val run: Int, val config: AuditConfig, val contest: ContestRound): ContestTrialIF {
    val endingTestStatistic = 1 / config.riskLimit

    // TODO always uses "min noerror" assertion. but not taking into account startingTestStatistic for round > 1
    val assertionRound = contest.minAssertion()!!// minimum noerror
    val cassertion = assertionRound.assertion as ClcaAssertion // minimum noerror
    val cassorter = cassertion.cassorter
    val passorter = cassorter.assorter
    val phantomAssortValue: Double = Taus(passorter.upperBound()).phantomTausValue()

    val errorTracker: ClcaErrorTracker
    val bettingFun : GeneralAdaptiveBetting
    val startingTestStatistic: Double
    val prevSamplesUsed: Int

    init {
        //     val Npop: Int, // population size for this contest
        //    val aprioriCounts: ClcaErrorRates, // apriori rates not counting phantoms, non-null so we always have noerror and upper
        //    val nphantoms: Int, // number of phantoms in the population
        //    val maxLoss: Double, // between 0 and 1; this bounds how close lam can get to 2.0; maxBet = maxLoss / mui
        //
        //    val oaAssortRates: OneAuditAssortValueRates? = null, // non-null for OneAudit
        //    val d: Int = 100,  // trunc weight
        //    val debug: Boolean = false,
        val aprioriErrorRates = config.clcaConfig.apriori.makeErrorRates(cassorter.noerror, passorter.upperBound())
        val oaAssortRates = if (config.isOA) (cassorter as OneAuditClcaAssorter).oaAssortRates else null

        bettingFun = GeneralAdaptiveBetting(
            contest.Npop, // population size for this contest
            aprioriErrorRates = aprioriErrorRates, // apriori rates not counting phantoms, non-null so we always have noerror and upper
            contest.contestUA.Nphantoms,
            config.clcaConfig.maxLoss,
            oaAssortRates=oaAssortRates,
        )

        val prevAuditResult = assertionRound.prevAssertionRound?.auditResult
        prevSamplesUsed = prevAuditResult?.samplesUsed ?: 0
        errorTracker = prevAuditResult?.clcaErrorTracker ?: ClcaErrorTracker(cassorter.noerror, passorter.upperBound())

        val plast = prevAuditResult?.plast
        startingTestStatistic = if (plast == null) 1.0 else 1.0 / plast
    }

    var testStatistic = startingTestStatistic // aka T
    var maxIndex = 0
    var countSkip = 0
    var countUsed = 0
    var firstUse: Int? = null

    fun skip(): Boolean {
        countSkip++
        return countSkip <= prevSamplesUsed
    }

    fun wantsMore() = maxIndex == 0

    override fun assertionRound() = assertionRound
    override fun maxIndex() = maxIndex
    override fun nmvrs() = countUsed
    override fun startingTestStatistic() = startingTestStatistic

    fun addCard(mvr: AuditableCard?, card: AuditableCard, cardSortedIndex: Int) {
        countUsed++

        if (firstUse == null) {
            firstUse = cardSortedIndex
            // println(" ${contest.id} firstUse=$firstUse")
        }

        val assortValue = if (mvr != null) {
            cassorter.bassort(mvr, card, hasStyle = false) // hasStyle??
        } else {
            if (card.isPhantom()) phantomAssortValue * cassorter.noerror else cassorter.noerror
        }

        // TODO errorTracker will have prevSampleCount, which I think is right.
        val mui = populationMeanIfH0(contest.Npop, true, errorTracker)
        val maxBet = bettingFun.bet(errorTracker)

        val payoff = (1 + maxBet * (assortValue - mui))
        testStatistic *= payoff
        if (testStatistic > endingTestStatistic) {
            maxIndex = cardSortedIndex
        } // once we set maxUsed then wantsMore == false

        // welford.update(assortValue) // error tracker has a welford...
        errorTracker.addSample(assortValue, card.poolId == null)

        /*val wantId = 28
        if (run == 1 && contest.id == wantId && countUsed < 1000) {
            val mvrVotes = mvr?.votes(wantId)?.contentToString() ?: "missing"
            val cardVotes = card.votes(wantId)?.contentToString() ?: "N/A"
            println("$countUsed, ${dfn(assortValue, 8)}, ${dfn(maxBet, 8)}, ${dfn(payoff, 8)}, ${dfn(testStatistic, 8)}, " +
                    "${card.location}, ${mvrVotes}, ${cardVotes}")
        } */
    }

    override fun toString(): String {
        return "ContestTracker(contest=${contest.id} nmvrs=${nmvrs()} maxIndex=${maxIndex} testStatistic=${testStatistic} )"
    }
}

interface ContestTrialIF {
    fun assertionRound(): AssertionRound
    fun maxIndex(): Int
    fun nmvrs(): Int
    fun startingTestStatistic(): Double
}

fun findQuantileTrial(data: List<ContestTrialIF>, quantile: Double): ContestTrialIF {
    require(data.isNotEmpty())

    val sortedData: List<ContestTrialIF> = data.sortedBy { it.nmvrs() }
    val deciles = mutableListOf<Int>()
    val n = sortedData.size
    repeat(9) {
        val quantile = .10 * (it + 1)
        val p = min((quantile * n).toInt(), n - 1)
        deciles.add(sortedData[p].nmvrs())
    }
    deciles.add(sortedData.last().nmvrs() + 1)

    require(quantile in 0.0..1.0)

    // edge cases
    if (quantile == 0.0) return sortedData.first()
    if (quantile == 100.0) return sortedData.last()

    // rounding down. TODO interpolate; see Deciles ??
    val p = min((quantile * data.size).toInt(), data.size-1)
    return sortedData[p]
}
