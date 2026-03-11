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

private val logger = KotlinLogging.logger("EstimateOneAudit")

// TODO  round > 1 we want to incorporate the measured errors from previous rounds
//   cant we use vunderPool to do so, that only uses fuzz
//   just change the assortValue randomly p percent of the time. can do the same for clca.
class EstimateOneAudit(
    val config: AuditConfig,
    val roundIdx: Int,
    val contests: List<ContestRound>,
    val pools: List<OneAuditPool>,
    val cardManifest: CardManifest,
) {
    val contestsToAudit = contests.filter { !it.done && it.included }

    fun run(): List<RunRepeatedResult> {
        if (contestsToAudit.isEmpty())
            return emptyList()

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<OneAuditTrialTask>()

        // TODO use more simulations when the margin is low or calcNewMvrs are high ??
        repeat(config.nsimEst) { run ->
            tasks.add(OneAuditTrialTask(roundIdx, run+1, config, contestsToAudit, pools, cardManifest))
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

            val useTrial = findQuantileTrial(contestResults, .01 * pct)
            val distribution: List<Int> = contestResults.map { it.nmvrs() }.sorted()

            // TODO only have minAssertion; is that ok ?
            //   minAssertion can change when some assertions succeed in previous rounds
            val useAssertionRound = useTrial.assertionRound()

            val newMvrs = roundUp(percentiles().index(pct).compute(*distribution.toIntArray()))
            val prevNmrs = useAssertionRound.prevAssertionRound?.auditResult?.samplesUsed ?: 0
            val estMvrs = prevNmrs + newMvrs

            contestRound.estMvrs = estMvrs
            contestRound.estNewMvrs = newMvrs

            useAssertionRound.estMvrs = estMvrs
            useAssertionRound.estNewMvrs = newMvrs

            val estimatiomResult = EstimationRoundResult(
                roundIdx,
                "EstimateOneAudit",
                calcNewMvrsNeeded = useAssertionRound.calcNewMvrsNeeded(contestRound.contestUA, config),
                startingTestStatistic = useTrial.startingTestStatistic(), // TODO
                startingErrorRates = emptyMap(), // TODO capture nphantoms ?
                estimatedDistribution = distribution,
                lastIndex = useTrial.maxIndex(),
                quantile = pct,
                ntrials = config.nsimEst,
                simNewMvrsNeeded = newMvrs,
                simMvrsNeeded = estMvrs,
            )

            // attach estimatiomResult to all the assertions still to be done
            contestRound.assertionRounds.forEach { assertion ->
                assertion.estimationResult = estimatiomResult
            }

            println("  ${contestRound.id} quantile = $pct uses $newMvrs from ${distribution} ")
        }
        logger.info { "EstimateOneAudit ntrials=${config.nsimEst} ncontests=${contestsToAudit.size} took $stopwatch" }

        return emptyList() // bogus
    }
}

// 1 trial, all contests
class OneAuditTrialTask(
    val roundIdx: Int,
    val run: Int,
    val config: AuditConfig,
    val contestsToAudit: List<ContestRound>,
    val pools: List<OneAuditPool>,
    val cardManifest: CardManifest) : ConcurrentTaskG<List<ContestTrial>> {

    override fun name() = "roundIdx $roundIdx Run $run"

    override fun run(): List<ContestTrial> {
        val stopwatch = Stopwatch()
        val vunderPools = VunderPools(pools) // different simulated pool data each run

        val contestTrials = contestsToAudit.map {
            ContestTrial(config, it)
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
                    vunderPools.simulatePooledCard(card) // simulate differently each trial to get a distribution
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
        logger.info { "roundIdx $roundIdx $run countCardsIncluded=$countCardsIncluded countPoolCards=$countPoolCards took $stopwatch" }

        return contestTrials
    }
}

// 1 trial, 1 contest
class ContestTrial(val config: AuditConfig, val contest: ContestRound): ContestTrialIF {
    val endingTestStatistic = 1 / config.riskLimit

    // TODO always uses "min noerror" assertion. but not taking into account startingTestStatistic for round > 1
    val assertionRound = contest.minAssertion()!!// minimum noerror
    val cassertion = assertionRound.assertion as ClcaAssertion // minimum noerror
    val minAssorter: OneAuditClcaAssorter = cassertion.cassorter as OneAuditClcaAssorter
    val passorter = minAssorter.assorter
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
        val aprioriErrorRates = config.clcaConfig.apriori.makeErrorRates(minAssorter.noerror, passorter.upperBound())

        bettingFun = GeneralAdaptiveBetting(
            contest.Npop, // population size for this contest
            aprioriErrorRates = aprioriErrorRates, // apriori rates not counting phantoms, non-null so we always have noerror and upper
            contest.contestUA.Nphantoms,
            config.clcaConfig.maxLoss,
            oaAssortRates=minAssorter.oaAssortRates,
        )

        errorTracker = ClcaErrorTracker(minAssorter.noerror, passorter.upperBound())

        //// extra stuff for continuation
        val previousErrorCounts = assertionRound.previousErrorCounts()
        if (previousErrorCounts != null) {
            errorTracker.setFromPreviousCounts(previousErrorCounts)
        }
        val prevAuditResult = assertionRound.prevAssertionRound?.auditResult
        prevSamplesUsed = prevAuditResult?.samplesUsed ?: 0

        val plast = prevAuditResult?.plast
        startingTestStatistic = if (plast == null) 1.0 else 1.0 / plast
    }

    var testStatistic = startingTestStatistic // aka T
    var maxIndex = 0
    var countSkip = 0
    var countUsed = 0

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

        val assortValue = if (mvr != null) {
            minAssorter.bassort(mvr, card, hasStyle = false) // hasStyle??
        } else {
            if (card.isPhantom()) phantomAssortValue * minAssorter.noerror else minAssorter.noerror
        }

        // TODO errorTracker will have prevSampleCount, which I think is right.
        val mui = populationMeanIfH0(contest.Npop, true, errorTracker)
        val maxBet = bettingFun.bet(errorTracker)

        testStatistic *= (1 + maxBet * (assortValue - mui))
        if (testStatistic > endingTestStatistic) {
            maxIndex = cardSortedIndex
        } // once we set maxUsed then wantsMore == false

        // welford.update(assortValue) // error tracker has a welford...
        errorTracker.addSample(assortValue)
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
