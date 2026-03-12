package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.EstimationRoundResult
import org.cryptobiotic.rlauxe.betting.Taus
import org.cryptobiotic.rlauxe.betting.populationMeanIfH0
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.Welford
import org.cryptobiotic.rlauxe.workflow.CardManifest

private val logger = KotlinLogging.logger("EstimateClcaNoErrors")

// not needed
class EstimateClcaNoErrors(
    val config: AuditConfig,
    val contests: List<ContestRound>,
    val cardManifest: CardManifest,
) {
    val minTracker = 1 / config.riskLimit
    val maxLoss = config.clcaConfig.maxLoss

    fun run(): List<RunRepeatedResult> {
        val contestsToAudit = contests.filter { !it.done && it.included }
        if (contestsToAudit.isEmpty())
            return emptyList()

        // track minAssertion testStatistic (T) using actual cards to see where phantoms are, assume noerror otherwise.
        val contestTrackers = contestsToAudit.map {
            val cassertion = it.minAssertion()!!.assertion as ClcaAssertion // minimum noerror
            ContestTracker(it, cassertion.cassorter)
        }

        // val sampledCards = mutableListOf<AuditableCard>() // dont actually need this ??
        var cardSortedIndex = 1  // 1 based

        var countCardsIncluded = 0
        var countPhantoms = 0
        val sortedCardIter = cardManifest.cards.iterator()
        while (sortedCardIter.hasNext()) {
            // does anyone want this card ?
            if (!contestTrackers.any { it.wantsMore() }) break

            // get the next card in sorted order
            val card = sortedCardIter.next()

            var include = false
            contestTrackers.forEach { contestTracker ->
                // does this contest want this card ?
                if (contestTracker.wantsMore() && card.hasContest(contestTracker.contest.id)) {
                    include = true
                    contestTracker.assort(card, cardSortedIndex)
                }
            }

            if (include) {
                // sampledCards.add(card)
                countCardsIncluded++
                if (card.isPhantom()) countPhantoms++
            }
            cardSortedIndex++
        }
        logger.info { "countCardsIncluded=$countCardsIncluded countPhantoms = $countPhantoms" }

        // transfer info to contestRound and minAssertion
        contestTrackers.forEach { tracker ->
            tracker.contest.estMvrs = tracker.welford.count
            tracker.contest.estNewMvrs = tracker.welford.count
            // tracker.contest.maxSampleAllowed = useContestTrial.maxIndex + 1  // wait, ConsistentSamnpling sets this
            tracker.assertionRound.estMvrs = tracker.welford.count
            tracker.assertionRound.estNewMvrs = tracker.welford.count
            tracker.assertionRound.estimationResult = EstimationRoundResult(
                1,
                "EstimateClcaNoErrors",
                calcNewMvrsNeeded = tracker.assertionRound.calcNewMvrsNeeded(tracker.contest.contestUA, config),
                startingTestStatistic = 1.0,
                startingErrorRates = emptyMap(), // TODO capture nphantoms ?
                estimatedDistribution = emptyList(),
                quantile=0,
                ntrials = 1,
                simNewMvrsNeeded = tracker.welford.count,
                lastIndex = tracker.maxIndex,
            )
            println("${tracker.contest.id} needs ${tracker.welford.count} maxIndex=${tracker.maxIndex} T=${tracker.testStatistic} ")
        }

        return emptyList()
    }

    inner class ContestTracker(val contest: ContestRound, val minAssorter: ClcaAssorter): ContestTrialIF {
        val assertionRound = contest.minAssertion()!!// minimum noerror
        val cassertion = assertionRound.assertion as ClcaAssertion // minimum noerror
        val phantomAssortValue: Double = Taus(minAssorter.assorter.upperBound()).phantomTausValue()

        var testStatistic = 1.0  // aka T
        var maxIndex = 0
        val welford = Welford()

        fun wantsMore() = maxIndex == 0

        override fun assertionRound() = assertionRound
        override fun nmvrs() = welford.count
        override fun maxIndex() = maxIndex
        override fun startingTestStatistic() = 1.0 // TODO

        fun assort(card: AuditableCard, cardSortedIndex: Int) {
            val assortValue = if (card.isPhantom()) phantomAssortValue * minAssorter.noerror else minAssorter.noerror

            // TODO just use GeneralAdaptiveBetting, so we have exact match with audit
            val mui = populationMeanIfH0(contest.Npop, true, welford)
            val maxBet = maxLoss / mui
            val payoff = (1 + maxBet * (assortValue - mui))
            testStatistic *= payoff

            if (testStatistic > minTracker) maxIndex = cardSortedIndex
            // if (contest.id == 116) println("   ${welford.count} assort=$assortValue payoff=$payoff maxBet=${maxBet} T=${testStatistic} ")

            welford.update(assortValue)
        }
    }
}
