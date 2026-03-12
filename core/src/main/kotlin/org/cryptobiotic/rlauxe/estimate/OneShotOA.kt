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
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.util.Quantiles.percentiles
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.workflow.CardManifest
import kotlin.Double
import kotlin.Int
import kotlin.collections.sortedBy
import kotlin.math.max
import kotlin.math.min
import kotlin.use

private val logger = KotlinLogging.logger("OneShotOA")

class OneShotOA(
    val auditdir: String,
) {
    val record = AuditRecord.readFrom(auditdir) as AuditRecord
    val config = record.config
    val cardManifest = record.readCardManifest()
    val cardPools = record.readCardPools()
    val mvrs = readCardsCsvIterator(Publisher(auditdir).privateMvrsFile())

    fun run(skipContests: List<Int>) {
        println("exclude $skipContests")

        val stopwatch = Stopwatch()
        val mvrsIter = mvrs.iterator()
        val contestsUAs = record.contests.filter { it.id !in skipContests }

        val assertionAudits = mutableListOf<AssertionAudit>()
        contestsUAs.forEach { contestUA ->
            contestUA.clcaAssertions.forEach {
                assertionAudits.add( AssertionAudit(contestUA, it))
            }
        }
        val naudits = assertionAudits.size

        var countCards = 1 // 1 based
        var countCardsIncluded = 0
        var countPoolCards = 0
        cardManifest.cards.iterator().use { sortedCardIter ->
            while (sortedCardIter.hasNext()) {
                // does any contest need more cards ?
                if (!assertionAudits.any { it.wantsMore() }) break

                // get the next card in sorted order
                val card = sortedCardIter.next()
                val mvr = mvrsIter.next()
                require( card.prn == mvr.prn )

                var include = false
                assertionAudits.forEach { assertionAudit ->
                    // does this contest want this card ?
                    if (assertionAudit.wantsMore() && card.hasContest(assertionAudit.id)) {
                        include = true
                        assertionAudit.addCard(mvr, card, countCards)
                    }
                }

                if (include) {
                    countCardsIncluded++
                    if (card.poolId != null) countPoolCards++
                }
                countCards++

                if (countCards % 100 == 0 && countCards < 1000) {
                    val more = assertionAudits.filter { it.wantsMore() }.map { it.id }
                    println(" $countCards ${more.size}/$naudits = $more")

                } else if (countCards % 1000 == 0) {
                    val more = assertionAudits.filter { it.wantsMore() }.map { it.id }
                    println(" $countCards ${more.size}/$naudits = $more")
                }
            }
        }
        println("\ncountCards=$countCards countIncludedCards=$countCardsIncluded took $stopwatch\n")

        val maxAssertions = mutableMapOf<Int, Int>()
        assertionAudits.forEach {
            println(it)
            val maxSamples = maxAssertions.getOrPut(it.id) { 0 }
            maxAssertions[it.id] = max( maxSamples, it.countUsed )
        }
        println()
        maxAssertions.toSortedMap().forEach { (id, count) -> println("$id: $count") }
    }

    inner class AssertionAudit(val contestUA: ContestWithAssertions, val cassertion: ClcaAssertion) {
        val id = contestUA.id
        val endingTestStatistic = 1 / config.riskLimit
        val cassorter: OneAuditClcaAssorter = cassertion.cassorter as OneAuditClcaAssorter
        val passorter = cassorter.assorter

        val errorTracker: ClcaErrorTracker
        val bettingFun : GeneralAdaptiveBetting

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

            bettingFun = GeneralAdaptiveBetting(
                contestUA.Npop, // population size for this contest
                aprioriErrorRates = aprioriErrorRates, // apriori rates not counting phantoms, non-null so we always have noerror and upper
                contestUA.Nphantoms,
                config.clcaConfig.maxLoss,
                oaAssortRates=cassorter.oaAssortRates,
            )

            errorTracker = ClcaErrorTracker(cassorter.noerror, passorter.upperBound())
        }

        var testStatistic = 1.0 // aka T
        var maxIndex = 0
        var countUsed = 0

        fun wantsMore() = maxIndex == 0

        fun addCard(mvr: AuditableCard, card: AuditableCard, cardSortedIndex: Int) {
            countUsed++

            val assortValue = cassorter.bassort(mvr, card, hasStyle = false) // hasStyle??

            if (id == 15 && passorter.shortName() == "49/42" && countUsed == 82) {
            }
                // TODO errorTracker will have prevSampleCount, which I think is right.
            val mui = populationMeanIfH0(contestUA.Npop, true, errorTracker)
            val maxBet = bettingFun.bet(errorTracker)

            if (id == 15 && passorter.shortName() == "49/42" && countUsed == 83) {
                bettingFun.debug = true
                bettingFun.bet(errorTracker)
                bettingFun.debug = false
            }

            val payoff = (1 + maxBet * (assortValue - mui))
            testStatistic *= payoff
            if (testStatistic > endingTestStatistic) {
                maxIndex = cardSortedIndex
            } // once we set maxUsed then wantsMore == false

            if (id == 15 && passorter.shortName() == "49/42") {
                val mvrVotes = mvr.votes(15)?.contentToString() ?: "missing"
                val cardVotes = card.votes(15)?.contentToString() ?: "N/A"
                println("$countUsed, ${dfn(assortValue, 8)}, ${dfn(maxBet, 8)}, ${dfn(payoff, 8)}, ${dfn(testStatistic, 8)}, " +
                        "${mvr.location}, ${mvrVotes}, ${cardVotes}")
            }

            // welford.update(assortValue) // error tracker has a welford...
            errorTracker.addSample(assortValue, card.poolId == null)
        }

        override fun toString(): String {
            return "AssertionAudit(contest=${contestUA.id} assertion=${passorter.shortName()} countUsed=${countUsed} maxIndex=${maxIndex} testStatistic=${testStatistic} )"
        }
    }
}
