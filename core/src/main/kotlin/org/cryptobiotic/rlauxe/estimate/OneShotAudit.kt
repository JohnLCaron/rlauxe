package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.util.Stopwatch
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.Int
import kotlin.math.max
import kotlin.use

// AuditRecord must have privateMvrs; run actual audit to compare to estimation
class OneShotAudit(
    val auditdir: String,
) {
    val record = AuditRecord.readFrom(auditdir) as AuditRecord
    val config = record.config
    val cardManifest = record.readSortedManifest()
    val cardPools = record.readCardPools()
    val mvrs = readCardsCsvIterator(Publisher(auditdir).sortedMvrsFile())

    fun run(skipContests: List<Int>, writeFile: String? = null, show:Boolean = false) {
        println("OneShotAudit exclude $skipContests on $auditdir")

        val stopwatch = Stopwatch()
        val mvrsIter = mvrs.iterator()
        val contestsUAs = record.contests.filter { it.id !in skipContests }

        val assertionAudits = mutableListOf<AssertionTrialIF>()
        contestsUAs.forEach { contestUA ->
            contestUA.assertions().forEach { assertion ->
                val assertionRound = AssertionRound(assertion, 1, null)
                val aa = if (config.isPolling) ContestPollingTrial(1, config.creation.riskLimit, config.round.pollingConfig!!, contestUA, assertionRound)
                    else ContestClcaTrial(1, config.creation.riskLimit, config.round.clcaConfig!!, config.isOA, contestUA, assertionRound)
                assertionAudits.add( aa)
            }
        }

        /* val assertionAuditsOld = mutableListOf<AssertionAudit>()
        contestsUAs.forEach { contestUA ->
            contestUA.clcaAssertions.forEach {
                assertionAuditsOld.add( AssertionAudit(contestUA, it, show))
            }
        } */

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
                    if (assertionAudit.wantsMore() && card.hasContest(assertionAudit.id())) {
                        include = true
                        assertionAudit.addCard(mvr, card, countCards)
                    }
                }

                if (include) {
                    countCardsIncluded++
                    if (card.poolId != null) countPoolCards++
                }
                countCards++

                if (show) {
                    if (countCards % 100 == 0 && countCards < 1000) {
                        val more = assertionAudits.filter { it.wantsMore() }.map { it.id() }
                        println(" $countCards ${more.size}/$naudits = $more")

                    } else if (countCards % 1000 == 0) {
                        val more = assertionAudits.filter { it.wantsMore() }.map { it.id() }
                        println(" $countCards ${more.size}/$naudits = $more")
                    }
                }
            }
        }
        println("\ncountCards=$countCards countIncludedCards=$countCardsIncluded took $stopwatch\n")

        val maxAssertions = mutableMapOf<Int, Int>()
        assertionAudits.forEach {
            println(it)
            val maxSamples = maxAssertions.getOrPut(it.id()) { 0 }
            maxAssertions[it.id()] = max( maxSamples, it.nmvrs() )
        }
        println()
        maxAssertions.toSortedMap().forEach { (id, count) -> println("$id: $count") }

        if (writeFile != null) {
            val writer: OutputStreamWriter = FileOutputStream(writeFile).writer()
            maxAssertions.toSortedMap().forEach { (id, count) -> writer.write("$id: $count\n") }
            writer.close()
        }
    }

    companion object {
        private val logger = KotlinLogging.logger("OneShotAudit")
    }

    /*
    inner class AssertionAudit(val contest: ContestWithAssertions, val cassertion: ClcaAssertion, val show: Boolean = false) {
        val id = contest.id
        val endingTestStatistic = 1 / config.riskLimit
        val cassorter = cassertion.cassorter
        val passorter = cassorter.assorter

        val errorTracker: ClcaErrorTracker
        val bettingFun : GeneralAdaptiveBetting

        init {
            val aprioriErrorRates = config.clcaConfig.apriori.makeErrorRates(cassorter.noerror, passorter.upperBound())
            val oaAssortRates = if (config.isOA) (cassorter as OneAuditClcaAssorter).oaAssortRates else null

            bettingFun = GeneralAdaptiveBetting(
                contest.Npop, // population size for this contest
                aprioriErrorRates = aprioriErrorRates, // apriori rates not counting phantoms, non-null so we always have noerror and upper
                this@AssertionAudit.contest.Nphantoms,
                config.clcaConfig.maxLoss,
                oaAssortRates=oaAssortRates,
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

            // TODO errorTracker will have prevSampleCount, which I think is right.
            val mui = populationMeanIfH0(contest.Npop, true, errorTracker)
            val maxBet = bettingFun.bet(errorTracker)

            val payoff = (1 + maxBet * (assortValue - mui))
            testStatistic *= payoff
            if (testStatistic > endingTestStatistic) maxIndex = cardSortedIndex // once we set maxUsed then wantsMore == false

            val wantId = 253
            if (id == wantId) { // && passorter.shortName() == "NEN 107/102") {
                val mvrVotes = mvr.votes(wantId)?.contentToString() ?: "missing"
                val cardVotes = card.votes(wantId)?.contentToString() ?: "N/A"
                println("$countUsed, ${dfn(assortValue, 8)}, ${dfn(maxBet, 8)}, ${dfn(payoff, 8)}, ${dfn(testStatistic, 8)}, " +
                        "${mvr.location}, ${mvrVotes}, ${cardVotes}")
            }

            // welford.update(assortValue) // error tracker has a welford...
            errorTracker.addSample(assortValue, card.poolId == null)
        }

        override fun toString(): String {
            return "AssertionAudit(contest=${contest.id} assertion=${passorter.shortName()} countUsed=${countUsed} maxIndex=${maxIndex} testStatistic=${testStatistic} )"
        }
    } */
}
