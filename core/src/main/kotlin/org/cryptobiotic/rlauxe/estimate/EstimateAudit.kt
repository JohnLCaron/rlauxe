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
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.util.ConcurrentTaskRunner
import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.margin2mean
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.persist.SortedManifest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.Double
import kotlin.Int
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.abs
import kotlin.math.ln
import kotlin.use

private val logger = KotlinLogging.logger("EstimateAudit")
private val showWork = false

// TODO  round > 1 we want to incorporate the measured errors from previous rounds
//   cant use vunderPool to do so, that only uses fuzz
//   just change the assortValue randomly p percent of the time? can do the same for clca. (??)

// side effects:
//   contestRound.estMvrs = estMvrs
//   contestRound.estNewMvrs = newMvrs
//
//   useAssertionRound.estMvrs = estMvrs
//   useAssertionRound.estNewMvrs = newMvrs
//   assertionRound.estimationResult = estimationResult

// TODO fail if nsamples > contestSampleCutoff
class EstimateAudit(
    val topdir: String,
    val config: Config,
    val roundIdx: Int,
    val contests: List<ContestRound>,
    val pools: List<CardPool>?,
    val styles: List<StyleIF>?,
    val sortedManifest: SortedManifest,
    val nthreads: Int? = null,
    val contestOnly: Int? = null
) {
    val auditType = config.auditType

    // fun run(nthreads: Int? = null, contestOnly: Int? = null): Map<Int, List<Int>> {
    fun run(): Map<Int, List<Int>> {
        val contestsToAudit = if (contestOnly == null) contests.filter { !it.done && it.included } else
            listOf( contests.find { it.id == contestOnly}!! )

        if (contestsToAudit.isEmpty())
            return emptyMap()

        val stopwatch = Stopwatch()
        val tasks = mutableListOf<AuditTrialTask>()

        // TODO use more simulations when the margin is low or calcNewMvrs are high ??

        // each trial is running all the contests in the round (but only the minAssertion)
        // for OneAudit, cvr == mvr and the variation comes from which pool it comes from ??
        val ntrials = if (auditType.isClca()) 1 else config.round.simulation.nsimTrials
        repeat(ntrials) { run ->
            tasks.add(AuditTrialTask(topdir, roundIdx, run+1, config, contestsToAudit, pools, styles, sortedManifest))
        }

        val trialResults: List<List<AssertionTrialIF>> = ConcurrentTaskRunner<List<AssertionTrialIF>>().run(tasks, 1)

        val trackerResults = mutableMapOf<Int, MutableList<AssertionTrialIF>>() // contestId -> list(trial)
        contestsToAudit.forEach { trackerResults[it.id] = mutableListOf() }
        trialResults.forEach { result ->
            result.forEach { trackerResults[it.contest().id]!!.add(it) }
        }

        // transfer info to contestRound and minAssertion
        contestsToAudit.forEach { contestRound ->
            val contestResults: List<AssertionTrialIF> = trackerResults[contestRound.id]!!.filter { !it.wantsMore() }
            // seems like this should take into account the number of new mvrs wanted.
            // high pct when small, conservative when large ??
            // hard to get a one-size-fits-all. could try letting user have more control....
            val pct = config.round.simulation.percentile(roundIdx) // this is an integer

            //  TODO use quantile ?
            val distribution: List<Int> = contestResults.map { it.nmvrs() }.sorted()
            val newMvrs = if (distribution.isEmpty()) 0 else
                roundUp(percentiles().index(pct).compute(*distribution.toIntArray()))

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

                // TODO defer this calculation I think; remnant of experiment
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
                useAssertionRound.estimationResult = estimationResult

                val fakeEst = estimationResult.copy(
                    strategy = "estSamples",
                    estimatedDistribution=emptyList(),
                )

                // attach estimationResult to all the other assertions still to be done
                // TODO kludge
                contestRound.assertionRounds.filter { it != useAssertionRound }.forEach { round ->
                    val noerror = round.assertion.assorter.noerror(contestRound.contestUA.hasStyle)
                    // val nomargin = 2.0 * noerror - 1.0
                    // fun estSampleSize(Npop: Int, bet:Double, margin: Double, upper: Double, alpha: Double): Int {
                    val estMvrs = estSampleSizeStandardBet(contestRound.contestUA.Npop, noerror, config.creation.riskLimit)
                    val prevNmrs = round.prevAssertionRound?.auditResult?.samplesUsed ?: 0
                    val newMvrs = estMvrs - prevNmrs
                    round.estMvrs = estMvrs
                    round.estNewMvrs = newMvrs
                    round.estimationResult = fakeEst.copy(simNewMvrsNeeded=newMvrs, simMvrsNeeded=estMvrs)
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
    val topdir: String,
    val roundIdx: Int,
    val run: Int,
    val config: Config,
    val contestsToAudit: List<ContestRound>,
    val pools: List<CardPool>?,
    val styles: List<StyleIF>?,
    val sortedManifest: SortedManifest
) : ConcurrentTask<List<AssertionTrialIF>> {

    override fun name() = "roundIdx $roundIdx Run $run"

    override fun run(): List<AssertionTrialIF> {
        val stopwatch = Stopwatch()
        val simMvrs = mutableListOf<AuditableCard>()
        logger.debug { "roundIdx $roundIdx $run starting" }

        // TODO use VunderPoolsFuzzer when cvrsContainUndervotes = false
        // used for OA and Polling; different simulated pool data each run; TODO could use VunderPoolsFuzzer
        // TODO here is where we need the card.batch to point to the pool, not the batch. maybe dont write the batch if there are pools
        // used for OneAudit and Pools; given the manifest card, simulate an mvr from that pool whose vote distribution matches the pool total
        // this is what creates the variance in the distribution...
        // for CLCA, no need for multiple simulations if you dont have any apriori errors; but on rounds > 1, may have errors from previous rounds
        val vunderPools = if (pools != null && !config.isClca) VunderPools(pools) else null

        // Polling without pools, generate one VunderPool based on contest totals
        val onePool = if (vunderPools == null && config.isPolling) VunderPool.fromContests(contestsToAudit.map { it.contestUA }, 42) else null

        // Use Batches if available
        val vunderBatches = if (onePool != null && styles != null && config.election.pollingMode?.withBatches() == true)
            VunderBatches(styles, onePool) else null

        val contestTrials: List<AssertionTrialIF> = contestsToAudit.map {
            if (config.isPolling) ContestPollingTrial(run, config.creation.riskLimit, config.round.pollingConfig!!, it.contestUA, it.minAssertion()!!)
            else ContestClcaTrial(run, config.creation.riskLimit, config.sampleLimit, config.round.clcaConfig!!, config.isOA, it.contestUA, it.minAssertion()!!)
        }

        var cardSortedIndex = 1 // 1 based
        var countEstimatedCards = 0
        var countPoolCards = 0
        sortedManifest.cards.iterator().use { sortedCardIter ->
            while (sortedCardIter.hasNext()) {
                // does any contest need more cards ?
                if (!contestTrials.any { it.wantsMore() }) break

                // get the next card in sorted order
                val card = sortedCardIter.next()
                val mvr = when  {
                    (card.poolId() != null && vunderPools != null) -> vunderPools.simulatePooledCard(card)
                    (vunderBatches != null) -> vunderBatches.simulatePooledCard(card)
                    (onePool != null) -> onePool.simulatePooledCard(card)
                    else -> card // TODO was null; wtf ??
                }
                // feeding all the contests at once
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
                    if (card.poolId() != null) countPoolCards++
                    if (keepSimMvrs) simMvrs.add(mvr)
                }
                cardSortedIndex++

                if (showWork) {
                    if (cardSortedIndex % 1000 == 0)
                        print(" $cardSortedIndex")
                    if (cardSortedIndex % 10000 == 0)
                        println()
                }
            }
        }
        logger.debug { "roundIdx $roundIdx $run countEstimatedCards=$countEstimatedCards took $stopwatch" }

        if (keepSimMvrs) {
            // hmm on subsequent rounds, wont you get diffferent simulation on previous cards ?
            // yes but we skip cards already used using prevSamplesUsed ....
            val publisher = Publisher(topdir)
            writeCardCsvFile(simMvrs , publisher.estMvrsFile(roundIdx, run))
        }

        return contestTrials
    }
}

private val keepSimMvrs = false


// 1 trial, 1 Clca contest
class ContestClcaTrial(val run: Int,
                       val riskLimit: Double,
                       val sampleLimit: Int?,
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
    val oaAssorter: OneAuditClcaAssorter? = if (cassorter is OneAuditClcaAssorter) cassorter else null

    val errorTracker: ClcaErrorTracker
    val bettingFun : GeneralAdaptiveBetting
    val startingTestStatistic: Double
    val prevSamplesUsed: Int
    val oaAssortRates = if (isOA) (cassorter as OneAuditClcaAssorter).oaAssortRates else null

    // val seq = DebuggingSequences()

    init {
        val aprioriErrorRates = clcaConfig.apriori.makeErrorRates(cassorter.noerror, passorter.upperBound())

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
        errorTracker = assertionRound.previousErrorTracker()

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

    override fun wantsMore(): Boolean {
        if (maxIndex > 0) return false
        if (sampleLimit == null) return true
        // TODO
        //if (countUsed > sampleLimit) {
         //   logger.info{"sample limit exceeded - estimate terminated"}
         //   return false
        //}
        return true
    }

    override fun contest() = contest
    override fun assertionRound() = assertionRound
    override fun maxIndex() = maxIndex
    override fun nmvrs() = countUsed
    override fun startingTestStatistic() = startingTestStatistic

    // why not just use BettingMart ??
    override fun addCard(mvr: AuditableCard?, card: AuditableCard, cardSortedIndex: Int) {
        countUsed++

        val assortValue = if (mvr != null) {
            cassorter.bassort(mvr, card)
        } else {
            if (card.phantom()) phantomAssortValue * cassorter.noerror else cassorter.noerror
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
        var oaTerm = 0.0
        oaAssortRates?.rates?.forEach { (assortValue: Double, rate: Double) ->
            oaTerm += ln(1.0 + bet * (assortValue - mui)) * rate
        }

        if (countUsed % 1000 == 1) {
            logger.debug{"contest ${contest.id} run $run bet=$bet oaTerm=$oaTerm"}
        }

        val payoff = (1 + bet * (assortValue - mui))
        testStatistic *= payoff
        if (testStatistic > endingTestStatistic) {
            status = TestH0Status.StatRejectNull
            maxIndex = cardSortedIndex
        } // once we set maxUsed then wantsMore == false

        val wantId = -1
        if (run == 1 && contest.id == wantId) {
            val locWidth = 32
            if (countUsed == 1) {
                println("contest ${contest.id} run $run")
                println("id, idx, xs,              bet,   oaTerm,     payoff,       Tj, ${sfn("location", locWidth-3)}, mvr votes, card votes")
            }
            bettingFun.bet(errorTracker, show = true) // debugging

            val mvrVotes = mvr?.votes(wantId)?.contentToString() ?: "missing"
            val cardVotes = card.votes(wantId)?.contentToString() ?: "N/A"
            print("${contest.id}, ")
            print("$countUsed, ${dfn(assortValue, 8)}, ${dfn(bet, 8)}")
            print(", ${dfn(oaTerm, 8)}")
            print(", ${dfn(payoff, 8)}, ${dfn(testStatistic, 8)}, ${sfn(card.location(), locWidth)}, ${mvrVotes}")
            if (card.poolId() != null) print(", pool=${card.poolId()}, poolAvg=${df(oaAssorter?.poolAverage(card.poolId()))}")
            else print(", votes=${cardVotes}")
            println()
        }
        errorTracker.addSample(assortValue, card.poolId() == null)

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
    val welford = Welford() // use this as the Tracker

    init {
        val eta0 = margin2mean(assorter.margin(contest.hasStyle))

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

    override fun addCard(mvr: AuditableCard?, card: AuditableCard, cardSortedIndex: Int) {
        countUsed++

        val assortValue = if (card.phantom()) phantomAssortValue else {
            if (mvr == null)
                print("why")
            assorter.assort(mvr!!, usePhantoms = true)
        }

        val mui = populationMeanIfH0(contest.Npop, true, welford)
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

        val maxBet = bettingFn.bet(welford)

        val payoff = (1 + maxBet * (assortValue - mui))
        testStatistic *= payoff
        if (testStatistic > endingTestStatistic) {
            maxIndex = cardSortedIndex
        }

        val wantId = -1
        if (run == 1 && contest.id == wantId && countUsed < 1000) {
            val mvrVotes = mvr?.votes(wantId)?.contentToString() ?: "missing"
            val cardVotes = card.votes(wantId)?.contentToString() ?: "N/A"
            println("$countUsed, ${dfn(assortValue, 8)}, ${dfn(maxBet, 8)}, ${dfn(payoff, 8)}, ${dfn(testStatistic, 8)}, " +
                    "${card.location()}, ${cardSortedIndex}, mvr=${mvrVotes}, cvr=${cardVotes}")
            if (countUsed == 999)
                print("")
        }

        welford.update(assortValue)
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

/*

id, idx, xs,              bet,   oaTerm,     payoff,       Tj,                      location, mvr votes, card votes
2, 1, 0.52242400, 0.10180775, 0.16392264, 1.00228294, 1.00228294, HABERSHAM-AV-Location 1 ICP 2 - 0-673, [], pool=3925, poolAvg=0.4889
1, 2, 0.49174215, 0.96789419, 7.37257710, 0.99200726, 0.97054985, TROUP-AV-Elections Office ICP 2 - 0-722, [0], pool=5332, poolAvg=0.5275


 */