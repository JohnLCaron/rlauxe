package org.cryptobiotic.rlauxe.estimate

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.workflow.PersistedMvrManager
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.math.max

// AuditRecord must have privateMvrs; run actual audit to compare to estimation
class OneShotAudit(
    val auditdir: String,
) {
    val record = AuditRecord.read(auditdir) as AuditRecord
    val config = record.config

    val mvrManager = PersistedMvrManager(record, false)
    val cardManifest = mvrManager.sortedManifest()
    val cardPools = mvrManager.pools()
    val mvrs = mvrManager.readCardsAndMerge(Publisher(auditdir).sortedMvrsFile())

    fun run(skipContests: List<Int>? = null, writeFile: String? = null, show: Boolean = false): Int {

        val stopwatch = Stopwatch()
        val mvrsIter = mvrs.iterator()

        // put last round for each contest
        val contestStatus = mutableMapOf<Int, Boolean>()
        for (round in record.rounds) {
            for (contestRound in round.contestRounds) {
                contestStatus[contestRound.id] =
                    contestRound.status == TestH0Status.StatRejectNull || contestRound.status == TestH0Status.SampleSumRejectNull
            }
        }

        val useSkipContests: List<Int> = skipContests ?: contestStatus.filter { !it.value }.map { it.key }
        val contestsUAs = record.contests.filter { it.id !in useSkipContests }
        println("OneShotAudit exclude $useSkipContests on $auditdir")

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
                    if (card.poolId() != null) countPoolCards++
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
            if (show) println(it)
            val maxSamples = maxAssertions.getOrPut(it.id()) { 0 }
            maxAssertions[it.id()] = max( maxSamples, it.nmvrs() )
        }
        println()
        if (show) maxAssertions.toSortedMap().forEach { (id, count) -> println("$id: $count") }

        if (writeFile != null) {
            val writer: OutputStreamWriter = FileOutputStream(writeFile).writer()
            maxAssertions.toSortedMap().forEach { (id, count) -> writer.write("$id: $count\n") }
            writer.write("9999: $countCardsIncluded\n")
            writer.close()
        }
        return countCardsIncluded
    }

    companion object {
        private val logger = KotlinLogging.logger("OneShotAudit")
    }
}
