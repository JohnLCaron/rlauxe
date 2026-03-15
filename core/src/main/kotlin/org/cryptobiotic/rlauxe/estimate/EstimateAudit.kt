package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.EstimationRoundResult
import org.cryptobiotic.rlauxe.betting.ClcaErrorTracker
import org.cryptobiotic.rlauxe.betting.EstimAdapter
import org.cryptobiotic.rlauxe.betting.GeneralAdaptiveBetting
import org.cryptobiotic.rlauxe.betting.Taus
import org.cryptobiotic.rlauxe.betting.TruncShrinkage
import org.cryptobiotic.rlauxe.betting.populationMeanIfH0
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.workflow.CardManifest
import kotlin.Double
import kotlin.Int
import kotlin.collections.sortedBy
import kotlin.math.min
import kotlin.use

private val logger = KotlinLogging.logger("EstimateAudit")
private val showWork = false

// TODO  round > 1 we want to incorporate the measured errors from previous rounds
//   cant we use vunderPool to do so, that only uses fuzz
//   just change the assortValue randomly p percent of the time. can do the same for clca.
class EstimateAudit(
    val config: AuditConfig,
    val roundIdx: Int,
    val contests: List<ContestRound>,
    val pools: List<CardPool>?,
    val batches: List<BatchIF>?,
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
            tasks.add(AuditTrialTask(roundIdx, run+1, config, contestsToAudit, pools, batches, cardManifest))
        }
        val trialResults: List<List<AssertionTrialIF>> = ConcurrentTaskRunnerG<List<AssertionTrialIF>>().run(tasks, nthreads=1)

        val trackerResults = mutableMapOf<Int, MutableList<AssertionTrialIF>>()
        contestsToAudit.forEach { trackerResults[it.id] = mutableListOf() }
        trialResults.forEach { result ->
            result.forEach { trackerResults[it.contest().id]!!.add(it) }
        }

        // transfer info to contestRound and minAssertion
        contestsToAudit.forEach { contestRound ->
            val contestResults: List<AssertionTrialIF> = trackerResults[contestRound.id]!!
            // seems like this should take into account the number of new mvrs wanted.
            // high pct when small, conservative when large ??
            // hard to get a one-size-fits-all. could try letting userr have control....
            val pct = when (roundIdx) {
                1 -> 50
                2 -> 80
                else -> 96
            }

            contestResults.forEach {
                if (it.wantsMore()) {
                    if (showWork) println(" wantsMore $it")
                }
                // require( !contestResults.any { it.wantsMore() })
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

                val calcNewMvrsNeeded = if (config.isPolling) 0 else useAssertionRound.calcNewMvrsNeeded(contestRound.contestUA, config)

                val estimatiomResult = EstimationRoundResult(
                    roundIdx,
                    "EstimateAudit",
                    calcNewMvrsNeeded = calcNewMvrsNeeded,
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

            if (showWork) println("  ${contestRound.id} quantile = $pct uses $newMvrs from ${distribution} lastIndex= ${useTrial.maxIndex()}")
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
    val pools: List<CardPool>?,
    val batches: List<BatchIF>?,
    val cardManifest: CardManifest) : ConcurrentTaskG<List<AssertionTrialIF>> {

    override fun name() = "roundIdx $roundIdx Run $run"

    override fun run(): List<AssertionTrialIF> {
        val stopwatch = Stopwatch()

        // used for OA and Polling; different simulated pool data each run; TODO could use Fuzzer
        val vunderPools = if (pools != null && !config.isClca) VunderPools(pools) else null

        // Polling without pools, auto generate OnePool based on contest totals
        // Can we do better with Batches ?? Seems like if card has a batch, we can use possible contests to be closer to mvr....
        val vunderPool = if (vunderPools == null && config.isPolling) VunderPool.fromContests(contestsToAudit.map { it.contestUA }, 42) else null

        val contestTrials: List<AssertionTrialIF> = contestsToAudit.map {
            if (config.isPolling) ContestPollingTrial(run, config, it.contestUA, it.minAssertion()!!)
            else ContestClcaTrial(run, config, it.contestUA, it.minAssertion()!!)
        }

        var cardSortedIndex = 1 // 1 based

        var countEstimatedCards = 0
        var countPoolCards = 0
        cardManifest.cards.iterator().use { sortedCardIter ->
            while (sortedCardIter.hasNext()) {
                // does any contest need more cards ?
                if (!contestTrials.any { it.wantsMore() }) break

                // get the next card in sorted order
                val card = sortedCardIter.next()
                val mvr = when  {
                    (card.poolId != null && vunderPools != null) -> vunderPools.simulatePooledCard(card)
                    (vunderPool != null) -> vunderPool.simulatePooledCard(card)
                    else -> null
                }

                var include = false
                contestTrials.forEach { contestTrial ->
                    // does this contest want this card ?
                    if (contestTrial.wantsMore() && card.hasContest(contestTrial.contest().id) && !contestTrial.skip()) {
                        include = true
                        contestTrial.addCard(mvr, card, cardSortedIndex)
                    }
                }

                if (include) {
                    // sampledCards.add(card)
                    countEstimatedCards++
                    if (card.poolId != null) countPoolCards++
                }
                cardSortedIndex++
            }
        }
        logger.info { "roundIdx $roundIdx $run countEstimatedCards=$countEstimatedCards took $stopwatch" }

        return contestTrials
    }
}

// 1 trial, 1 Clca contest
class ContestClcaTrial(val run: Int,
                       val config: AuditConfig,
                       val contest: ContestWithAssertions,
                       val assertionRound: AssertionRound,
): AssertionTrialIF {
    val endingTestStatistic = 1 / config.riskLimit

    val cassertion = assertionRound.assertion as ClcaAssertion // minimum noerror
    val cassorter = cassertion.cassorter
    val passorter = cassorter.assorter
    val phantomAssortValue: Double = Taus(passorter.upperBound()).phantomTausValue()

    val errorTracker: ClcaErrorTracker
    val bettingFun : GeneralAdaptiveBetting
    val startingTestStatistic: Double
    val prevSamplesUsed: Int

    init {
        val aprioriErrorRates = config.clcaConfig.apriori.makeErrorRates(cassorter.noerror, passorter.upperBound())
        val oaAssortRates = if (config.isOA) (cassorter as OneAuditClcaAssorter).oaAssortRates else null

        bettingFun = GeneralAdaptiveBetting(
            contest.Npop, // population size for this contest
            aprioriErrorRates = aprioriErrorRates, // apriori rates not counting phantoms, non-null so we always have noerror and upper
            contest.Nphantoms,
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

    override fun skip(): Boolean {
        countSkip++
        return countSkip <= prevSamplesUsed
    }

    override fun wantsMore() = maxIndex == 0

    override fun contest() = contest
    override fun assertionRound() = assertionRound
    override fun maxIndex() = maxIndex
    override fun nmvrs() = countUsed
    override fun startingTestStatistic() = startingTestStatistic

    override fun addCard(mvr: AuditableCard?, card: AuditableCard, cardSortedIndex: Int) {
        countUsed++

        val assortValue = if (mvr != null) {
            cassorter.bassort(mvr, card, hasStyle = false) // hasStyle??
        } else {
            if (card.isPhantom()) phantomAssortValue * cassorter.noerror else cassorter.noerror
        }

        val mui = populationMeanIfH0(contest.Npop, true, errorTracker)
        val maxBet = bettingFun.bet(errorTracker)

        val payoff = (1 + maxBet * (assortValue - mui))
        testStatistic *= payoff
        if (testStatistic > endingTestStatistic) {
            maxIndex = cardSortedIndex
        } // once we set maxUsed then wantsMore == false

        // welford.update(assortValue) // error tracker has a welford...
        errorTracker.addSample(assortValue, card.poolId == null)

        /*
        val wantId = 0
        if (run == 1 && contest.id == wantId && countUsed < 1000) {
            val mvrVotes = mvr?.votes(wantId)?.contentToString() ?: "missing"
            val cardVotes = card.votes(wantId)?.contentToString() ?: "N/A"
            println("$countUsed, ${dfn(assortValue, 8)}, ${dfn(maxBet, 8)}, ${dfn(payoff, 8)}, ${dfn(testStatistic, 8)}, " +
                    "${card.location}, ${mvrVotes}, ${cardVotes}")
        } */
    }

    override fun toString(): String {
        return "ContestClcaTrial(contest=${contest.id} nmvrs=${nmvrs()} maxIndex=${maxIndex} testStatistic=${testStatistic} )"
    }
}

// 1 trial, 1 Polling contest
class ContestPollingTrial(val run: Int,
                          val config: AuditConfig,
                          val contest: ContestWithAssertions,
                          val assertionRound: AssertionRound): AssertionTrialIF {
    val endingTestStatistic = 1 / config.riskLimit

    val assertion = assertionRound.assertion
    val assorter = assertion.assorter
    val phantomAssortValue: Double = 0.0

    val startingTestStatistic: Double
    val prevSamplesUsed: Int

    val bettingFn: EstimAdapter
    val errorTracker: ClcaErrorTracker

    init {
        val eta0 = margin2mean(assorter.dilutedMargin())

        val estimFn = TruncShrinkage(
            N = contest.Npop,
            withoutReplacement = true,
            upperBound = assorter.upperBound(),
            d = config.pollingConfig.d,
            eta0 = eta0,
        )

        bettingFn = EstimAdapter(contest.Npop, withoutReplacement=true, assorter.upperBound(), estimFn)

        val prevAuditResult = assertionRound.prevAssertionRound?.auditResult
        prevSamplesUsed = prevAuditResult?.samplesUsed ?: 0
        errorTracker = prevAuditResult?.clcaErrorTracker ?: ClcaErrorTracker(0.0, assorter.upperBound())

        val plast = prevAuditResult?.plast
        startingTestStatistic = if (plast == null) 1.0 else 1.0 / plast
    }

    var testStatistic = startingTestStatistic // aka T
    var maxIndex = 0
    var countSkip = 0
    var countUsed = 0

    override fun skip(): Boolean {
        countSkip++
        return countSkip <= prevSamplesUsed
    }

    override fun wantsMore() = maxIndex == 0

    override fun contest() = contest
    override fun assertionRound() = assertionRound
    override fun maxIndex() = maxIndex
    override fun nmvrs() = countUsed
    override fun startingTestStatistic() = startingTestStatistic

    override fun addCard(cvr: AuditableCard?, card: AuditableCard, cardSortedIndex: Int) {
        countUsed++

        val assortValue = if (card.isPhantom()) phantomAssortValue else {
            if (cvr == null)
                print("why")
            assorter.assort(cvr!!, usePhantoms = true)
        }

        val mui = populationMeanIfH0(contest.Npop, true, errorTracker)
        val maxBet = bettingFn.bet(errorTracker)

        val payoff = (1 + maxBet * (assortValue - mui))
        testStatistic *= payoff
        if (testStatistic > endingTestStatistic) {
            maxIndex = cardSortedIndex
        }

        errorTracker.addSample(assortValue)

        /* val wantId = 120
        if (run == 1 && contest.id == wantId && countUsed < 1000) {
            val mvrVotes = cvr?.votes(wantId)?.contentToString() ?: "missing"
            val cardVotes = card.votes(wantId)?.contentToString() ?: "N/A"
            println("$countUsed, ${dfn(assortValue, 8)}, ${dfn(maxBet, 8)}, ${dfn(payoff, 8)}, ${dfn(testStatistic, 8)}, " +
                    "${card.location}, ${cardSortedIndex}, mvr=${mvrVotes}, cvr=${cardVotes}")
            if (countUsed == 999)
                print("")
        } */
    }

    override fun toString(): String {
        return "ContestPollingTrial(contest=${contest.id} nmvrs=${nmvrs()} maxIndex=${maxIndex} testStatistic=${testStatistic} )"
    }
}


interface AssertionTrialIF {
    fun id() = contest().id
    fun contest(): ContestWithAssertions
    fun assertionRound(): AssertionRound
    fun maxIndex(): Int
    fun nmvrs(): Int
    fun startingTestStatistic(): Double

    fun skip(): Boolean
    fun wantsMore(): Boolean
    fun addCard(mvr: AuditableCard?, card: AuditableCard, cardSortedIndex: Int)
}

fun findQuantileTrial(data: List<AssertionTrialIF>, quantile: Double): AssertionTrialIF {
    require(data.isNotEmpty())

    val sortedData: List<AssertionTrialIF> = data.sortedBy { it.nmvrs() }
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
