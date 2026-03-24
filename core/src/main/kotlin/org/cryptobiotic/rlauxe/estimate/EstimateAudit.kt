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
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.ConcurrentTaskRunner
import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.workflow.CardManifest
import kotlin.Double
import kotlin.Int
import kotlin.math.abs
import kotlin.use

private val logger = KotlinLogging.logger("EstimateAudit")
private val showWork = false

// TODO  round > 1 we want to incorporate the measured errors from previous rounds
//   cant use vunderPool to do so, that only uses fuzz
//   just change the assortValue randomly p percent of the time? can do the same for clca.

// side effects:
//   contestRound.estMvrs = estMvrs
//   contestRound.estNewMvrs = newMvrs
//
//   useAssertionRound.estMvrs = estMvrs
//   useAssertionRound.estNewMvrs = newMvrs
//   assertionRound.estimationResult = estimationResult

class EstimateAudit(
    val config: Config,
    val roundIdx: Int,
    val contests: List<ContestRound>,
    val pools: List<CardPool>?,
    val batches: List<BatchIF>?,
    val cardManifest: CardManifest,
) {
    val auditType = config.auditType

    fun run(nthreads: Int? = null, contestOnly: Int? = null): Map<Int, List<Int>> {
        val contestsToAudit = if (contestOnly == null) contests.filter { !it.done && it.included } else
            listOf( contests.find { it.id == contestOnly}!! )

        if (contestsToAudit.isEmpty())
            return emptyMap()

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<AuditTrialTask>()

        // TODO use more simulations when the margin is low or calcNewMvrs are high ??

        // each trial is running all the contests in the round (but only the minAssertion)
        val ntrials = if (auditType.isClca()) 1 else config.round.simulation.nsimTrials
        repeat(ntrials) { run ->
            tasks.add(AuditTrialTask(roundIdx, run+1, config, contestsToAudit, pools, batches, cardManifest))
        }
        val trialResults: List<List<AssertionTrialIF>> = ConcurrentTaskRunner<List<AssertionTrialIF>>().run(tasks, nthreads)

        val trackerResults = mutableMapOf<Int, MutableList<AssertionTrialIF>>() // contestId -> list(trial)
        contestsToAudit.forEach { trackerResults[it.id] = mutableListOf() }
        trialResults.forEach { result ->
            result.forEach { trackerResults[it.contest().id]!!.add(it) }
        }

        // transfer info to contestRound and minAssertion
        contestsToAudit.forEach { contestRound ->
            val contestResults: List<AssertionTrialIF> = trackerResults[contestRound.id]!!
            // seems like this should take into account the number of new mvrs wanted.
            // high pct when small, conservative when large ??
            // hard to get a one-size-fits-all. could try letting user have more control....
            val pct = config.round.simulation.percentile(roundIdx) // this is an integer

            contestResults.forEach {
                if (it.wantsMore()) {
                    if (showWork) println(" wantsMore $it")
                }
                // require( !contestResults.any { it.wantsMore() })
            }

            val distribution: List<Int> = contestResults.map { it.nmvrs() }.sorted()
            val newMvrs = roundUp(percentiles().index(pct).compute(*distribution.toIntArray()))

            // TODO only estimating minAssertion; is that ok ?
            //   minAssertion can change when some assertions succeed in previous rounds

            // which trial is closest to nmvrs?
            val useTrial = findClosestTrial(contestResults, newMvrs)
            val useAssertionRound = useTrial.assertionRound()

            val prevNmrs = useAssertionRound.prevAssertionRound?.auditResult?.samplesUsed ?: 0
            val estMvrs = prevNmrs + newMvrs

            // contestOnly means dont modify the AuditRecord
            if (contestOnly == null) {
                contestRound.estMvrs = estMvrs
                contestRound.estNewMvrs = newMvrs

                useAssertionRound.estMvrs = estMvrs
                useAssertionRound.estNewMvrs = newMvrs

                val calcNewMvrsNeeded = if (auditType.isPolling()) 0 else useAssertionRound.calcNewMvrsNeeded(contestRound.contestUA, config)

                val estimationResult = EstimationRoundResult(
                    roundIdx,
                    "EstimateAudit",
                    calcNewMvrsNeeded = calcNewMvrsNeeded,
                    startingTestStatistic = useTrial.startingTestStatistic(), // TODO
                    startingErrorRates = emptyMap(), // TODO capture nphantoms ?
                    estimatedDistribution = distribution,
                    lastIndex = useTrial.maxIndex(),
                    percentile = pct,
                    ntrials = ntrials,
                    simNewMvrsNeeded = newMvrs,
                    simMvrsNeeded = estMvrs,
                )

                // attach estimationResult to all the assertions still to be done
                contestRound.assertionRounds.forEach { assertion ->
                    assertion.estimationResult = estimationResult
                }
            }

            if (showWork) println("  ${contestRound.id} quantile = $pct uses $newMvrs from ${distribution} lastIndex= ${useTrial.maxIndex()}")
        }
        logger.info { "EstimateAudit ntrials=${ntrials} ncontests=${contestsToAudit.size} took $stopwatch" }

        // return contestId -> List<nmvrs>
        return trackerResults.mapValues { it.value.map{ it.nmvrs() } }
    }
}

// 1 trial, all contests
class AuditTrialTask(
    val roundIdx: Int,
    val run: Int,
    val config: Config,
    val contestsToAudit: List<ContestRound>,
    val pools: List<CardPool>?,
    val batches: List<BatchIF>?,
    val cardManifest: CardManifest) : ConcurrentTask<List<AssertionTrialIF>> {

    override fun name() = "roundIdx $roundIdx Run $run"

    override fun run(): List<AssertionTrialIF> {
        val stopwatch = Stopwatch()

        // used for OA and Polling; different simulated pool data each run; TODO could use Fuzzer
        val vunderPools = if (pools != null && !config.isClca) VunderPools(pools) else null

        // Polling without pools, generate one VunderPool based on contest totals
        val onePool = if (vunderPools == null && config.isPolling) VunderPool.fromContests(contestsToAudit.map { it.contestUA }, 42) else null

        // Use Batches if available
        val vunderBatches = if (onePool != null && batches != null && config.election.pollingMode?.withBatches() == true)
            VunderBatches(batches, onePool) else null

        val contestTrials: List<AssertionTrialIF> = contestsToAudit.map {
            if (config.isPolling) ContestPollingTrial(run, config.creation.riskLimit, config.round.pollingConfig!!, it.contestUA, it.minAssertion()!!)
            else ContestClcaTrial(run, config.creation.riskLimit, config.round.clcaConfig!!, config.isOA, it.contestUA, it.minAssertion()!!)
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
                    (vunderBatches != null) -> vunderBatches.simulatePooledCard(card)
                    (onePool != null) -> onePool.simulatePooledCard(card)
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
        logger.debug { "roundIdx $roundIdx $run countEstimatedCards=$countEstimatedCards took $stopwatch" }

        return contestTrials
    }
}

// 1 trial, 1 Clca contest
class ContestClcaTrial(val run: Int,
                       val riskLimit: Double,
                       val clcaConfig: ClcaConfig,
                       val isOA: Boolean,
                       val contest: ContestWithAssertions,
                       val assertionRound: AssertionRound,
): AssertionTrialIF {
    val endingTestStatistic = 1 / riskLimit

    val cassertion = assertionRound.assertion as ClcaAssertion // minimum noerror
    val cassorter = cassertion.cassorter
    val passorter = cassorter.assorter
    val phantomAssortValue: Double = Taus(passorter.upperBound()).phantomTausValue()

    val errorTracker: ClcaErrorTracker
    val bettingFun : GeneralAdaptiveBetting
    val startingTestStatistic: Double
    val prevSamplesUsed: Int
    // val seq = DebuggingSequences()

    init {
        val aprioriErrorRates = clcaConfig.apriori.makeErrorRates(cassorter.noerror, passorter.upperBound())
        val oaAssortRates = if (isOA) (cassorter as OneAuditClcaAssorter).oaAssortRates else null

        // use the same betting function as the real audit
        bettingFun = GeneralAdaptiveBetting(
            contest.Npop, // population size for this contest
            aprioriErrorRates = aprioriErrorRates, // apriori rates not counting phantoms; non-null so we always have noerror and upper
            contest.Nphantoms,
            clcaConfig.maxLoss,
            oaAssortRates=oaAssortRates,
        )

        val prevAuditResult = assertionRound.prevAssertionRound?.auditResult
        prevSamplesUsed = prevAuditResult?.samplesUsed ?: 0
        errorTracker = prevAuditResult?.clcaErrorTracker?.copyAll() ?: ClcaErrorTracker(cassorter.noerror, passorter.upperBound())

        val plast = prevAuditResult?.plast
        startingTestStatistic = if (plast == null) 1.0 else 1.0 / plast
    }
    var status = TestH0Status.InProgress
    var testStatistic = startingTestStatistic // aka T
    var maxIndex = 0
    var countSkip = 0
    var countUsed = 0
    var firstDebug = true

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

    // why not just use BettingMart ??
    override fun addCard(mvr: AuditableCard?, card: AuditableCard, cardSortedIndex: Int) {
        countUsed++

        val assortValue = if (mvr != null) {
            cassorter.bassort(mvr, card, hasStyle = false) // hasStyle??
        } else {
            if (card.isPhantom()) phantomAssortValue * cassorter.noerror else cassorter.noerror
        }

        val mui = populationMeanIfH0(contest.Npop, true, errorTracker)
        if (mui > cassorter.upperBound) { // 1  # true mean is certainly less than 1/2
            status = TestH0Status.AcceptNull
            maxIndex = cardSortedIndex
            return
        }
        if (mui < 0.0) { // 5 # true mean certainly greater than 1/2
            status = TestH0Status.SampleSumRejectNull
            maxIndex = cardSortedIndex
            return
        }

        val bet = bettingFun.bet(errorTracker)

        val payoff = (1 + bet * (assortValue - mui))
        testStatistic *= payoff
        if (testStatistic > endingTestStatistic) {
            status = TestH0Status.StatRejectNull
            maxIndex = cardSortedIndex
        } // once we set maxUsed then wantsMore == false

        val wantId = -1
        if (run == 1 && contest.id == wantId) {
            if (firstDebug) {
                println("idx, xs,            bet,           payoff,       Tj,             location, mvr votes, card votes")
                firstDebug = false
            }
            bettingFun.bet(errorTracker, show = true) // debugging

            val mvrVotes = mvr?.votes(wantId)?.contentToString() ?: "missing"
            val cardVotes = card.votes(wantId)?.contentToString() ?: "N/A"
            println("$countUsed, ${dfn(assortValue, 8)}, ${dfn(bet, 8)}, ${dfn(payoff, 8)}, ${dfn(testStatistic, 8)}, " +
                    "${card.location}, ${mvrVotes}, ${cardVotes}")
        }
        errorTracker.addSample(assortValue, card.poolId == null)

        //     fun add(x: Double, bet: Double, mj: Double, tj: Double, testStatistic: Double) {
        // seq.add(assortValue, bet, mui, payoff, testStatistic)
    }

    override fun toString(): String {
        return "ContestClcaTrial(contest=${contest.id} nmvrs=${nmvrs()} maxIndex=${maxIndex} testStatistic=${testStatistic} )"
    }
}

// 1 trial, 1 Polling contest
class ContestPollingTrial(val run: Int,
                          val riskLimit: Double,
                          val pollingConfig: PollingConfig,
                          val contest: ContestWithAssertions,
                          val assertionRound: AssertionRound): AssertionTrialIF {
    val endingTestStatistic = 1 / riskLimit

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
            d = pollingConfig.d,
            eta0 = eta0,
        )

        bettingFn = EstimAdapter(contest.Npop, withoutReplacement=true, assorter.upperBound(), estimFn)

        val prevAuditResult = assertionRound.prevAssertionRound?.auditResult
        prevSamplesUsed = prevAuditResult?.samplesUsed ?: 0
        errorTracker = prevAuditResult?.clcaErrorTracker?.copyAll() ?: ClcaErrorTracker(0.0, assorter.upperBound())

        val plast = prevAuditResult?.plast
        startingTestStatistic = if (plast == null) 1.0 else 1.0 / plast
    }

    var status = TestH0Status.InProgress
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

    // why not just use BettingMart ??
    override fun addCard(cvr: AuditableCard?, card: AuditableCard, cardSortedIndex: Int) {
        countUsed++

        val assortValue = if (card.isPhantom()) phantomAssortValue else {
            if (cvr == null)
                print("why")
            assorter.assort(cvr!!, usePhantoms = true)
        }

        val mui = populationMeanIfH0(contest.Npop, true, errorTracker)
        if (mui > assorter.upperBound()) { // 1  # true mean is certainly less than 1/2
            status = TestH0Status.AcceptNull
            maxIndex = cardSortedIndex
            return
        }
        if (mui < 0.0) { // 5 # true mean certainly greater than 1/2
            status = TestH0Status.SampleSumRejectNull
            maxIndex = cardSortedIndex
            return
        }

        val maxBet = bettingFn.bet(errorTracker)

        val payoff = (1 + maxBet * (assortValue - mui))
        testStatistic *= payoff
        if (testStatistic > endingTestStatistic) {
            maxIndex = cardSortedIndex
        }

        val wantId = -1
        if (run == 1 && contest.id == wantId && countUsed < 1000) {
            val mvrVotes = cvr?.votes(wantId)?.contentToString() ?: "missing"
            val cardVotes = card.votes(wantId)?.contentToString() ?: "N/A"
            println("$countUsed, ${dfn(assortValue, 8)}, ${dfn(maxBet, 8)}, ${dfn(payoff, 8)}, ${dfn(testStatistic, 8)}, " +
                    "${card.location}, ${cardSortedIndex}, mvr=${mvrVotes}, cvr=${cardVotes}")
            if (countUsed == 999)
                print("")
        }

        errorTracker.addSample(assortValue)
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

fun findClosestTrial(data: List<AssertionTrialIF>, nmvrs: Int): AssertionTrialIF {
    require(data.isNotEmpty())

    var closestValue = Int.MAX_VALUE
    var closestTrial: AssertionTrialIF? = null

    data.forEach { trial ->
        val diff = abs(trial.nmvrs() - nmvrs)
        if (diff < closestValue) {
            closestTrial = trial
            closestValue = diff
        }
    }
    return closestTrial!!
}
