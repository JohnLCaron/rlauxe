package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CvrsWithStylesToCardManifest
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardHeader
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsv
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.verifyOAassortAvg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

// when does (winner - loser) / N agree with AvgAssortValue?
class TestAvgAssortValues {
    val showCards = false
    @Test
    fun testAvgAssortValues() {
        val margin = .03
        val Nc = 100
        val (oaContest, mvrs, cards, cardPools) =
            makeOneAuditTest(margin, Nc, cvrFraction = 0.50, undervoteFraction = 0.0, phantomFraction = 0.0)
        println("oaContest = $oaContest")
        if (showCards) mvrs.forEach { println("  $it") }
        //testCvrs.subList(0, 10).forEach { println("  $it") }
        // assertEquals(margin, oaContest.minPollingAssertion().second)

        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            CvrsWithStylesToCardManifest(
                AuditType.ONEAUDIT, false, Closer(mvrs.iterator()),
                phantomCvrs = null, cardPools,
            )
        }

        if (showCards) {
            println("\n$AuditableCardHeader")
            var count = 0
            for (card in cardIterable.iterator()) {
                print(writeAuditableCardCsv(card))
                if (count++ > 100) break
            }
            println()
        }

        val contests = listOf(oaContest)
        val infos = contests.map { it.contest.info() }.associateBy { it.id }
        val manifestTabs = tabulateAuditableCards(cardIterable.iterator(), infos)
        val Nbs = manifestTabs.mapValues { it.value.ncards }
        println(Nbs)


        //     cards: CloseableIterator<AuditableCard>,
        //    result: VerifyResults,
        //    show: Boolean = false
        val results = VerifyResults()
        verifyOAassortAvg(listOf(oaContest), cardIterable.iterator(), results, show = true)
        println(results)
    }

    val showCvrs = false

    @Test
    fun testAvgAssortWithPhantoms() {
        val margin = .05
        val Nc = 1000
        val (oaContest, mvrs, cards, cardPools) =
            makeOneAuditTest(margin, Nc, cvrFraction = 0.80, undervoteFraction = 0.0, phantomFraction = 0.03)

        println("oaContest = $oaContest")
        if (showCvrs) mvrs.subList(0, 10).forEach { println("  $it") }
        assertEquals(margin, oaContest.minDilutedMargin())

        // class CvrsWithStylesToCards(
        //    val type: AuditType,
        //    val hasStyle: Boolean,
        //    val cvrs: CloseableIterator<Cvr>,
        //    val phantomCvrs : List<Cvr>?,
        //    styles: List<CardStyleIF>?,
        //): CloseableIterator<AuditableCard> {
        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            CvrsWithStylesToCardManifest(
                AuditType.ONEAUDIT, false, Closer(mvrs.iterator()),
                phantomCvrs = null, cardPools,
            )
        }

        if (showCvrs) {
            println("\n$AuditableCardHeader")
            var count = 0
            for (card in cardIterable.iterator()) {
                print(writeAuditableCardCsv(card))
                if (count++ > 10) break
            }
            println()
        }

        //     cards: CloseableIterator<AuditableCard>,
        //    result: VerifyResults,
        //    show: Boolean = false
        val results = VerifyResults()
        verifyOAassortAvg(listOf(oaContest), cardIterable.iterator(), results, show = true)
        println(results)
        if (results.hasErrors) fail()
    }

}
