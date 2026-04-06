package org.cryptobiotic.rlauxe.verify

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.MergeBatchesIntoCardManifestIterator
import org.cryptobiotic.rlauxe.audit.mvrsToAuditableCardsTest
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.simulateCvrsFromMargin
import org.cryptobiotic.rlauxe.persist.csv.CardHeader
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsv
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

// when does (winner - loser) / N agree with AvgAssortValue?
class TestAvgAssortValues {
    val showCvrs = true

    @Test
    fun testAvgAssortValues() {
        val margin = .03
        val Nc = 1000
        val phantomPercent = 0.0
        val underVotePct = .087

        val (cu, testCvrs) = simulateCvrsFromMargin(Nc = Nc, margin, undervotePct = underVotePct, phantomPct = phantomPercent)

        val contest = cu.contest
        println("contest = $contest")
        if (showCvrs) testCvrs.subList(0, 10).forEach { println("  $it") }

        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            mvrsToAuditableCardsTest( AuditType.CLCA, testCvrs, null).iterator()
        }

        if (showCvrs) {
            println("\n$CardHeader")
            var count = 0
            for (card in cardIterable.iterator()) {
                print(writeCardCsv(card))
                if (count++ > 10) break
            }
            println()
        }

        val contestsUA = ContestWithAssertions.make(listOf(contest), cardIterable.iterator(), isClca=true)
        val contestUA= contestsUA.first()
        println("contestUA = ${contestUA.show()}")

        val minassert = contestUA.minPollingAssertion()
        println(minassert)
        assertEquals(minassert!!.assorter.dilutedMargin(), contestUA.minDilutedMargin())

        //     cards: CloseableIterator<AuditableCard>,
        //    result: VerifyResults,
        //    show: Boolean = false
        val results = VerifyResults()
        verifyClcaAssortAvg(contestsUA, cardIterable.iterator(), results, show = true)
        println(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testAvgAssortWithPhantoms() {
        val margin = .03
        val Nc = 1000
        val phantomPercent = 0.0221
        val underVotePct = .087

        val (cu, testCvrs) = simulateCvrsFromMargin(Nc = Nc, margin, undervotePct = underVotePct, phantomPct = phantomPercent)
        val contest = cu.contest
        println("contest = $contest")

        if (showCvrs) testCvrs.subList(0, 10).forEach { println("  $it") }

        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            mvrsToAuditableCardsTest( AuditType.CLCA, testCvrs, null).iterator()
        }

        if (showCvrs) {
            println("\n$CardHeader")
            var count = 0
            for (card in cardIterable.iterator()) {
                print(writeCardCsv(card))
                if (count++ > 10) break
            }
            println()
        }

        val contestsUA = ContestWithAssertions.make(listOf(contest), cardIterable.iterator(), isClca=true)
        val contestUA= contestsUA.first()
        println("contestUA = ${contestUA.show()}")

        val minassert = contestUA.minPollingAssertion()
        println(minassert)
        assertEquals(minassert!!.assorter.dilutedMargin(), contestUA.minDilutedMargin())

        val results = VerifyResults()
        verifyClcaAssortAvg(contestsUA, cardIterable.iterator(), results, show = true)
        println(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testAvgAssortMultiContest() {
        val N = 50000
        val ncontests = 40
        val nbs = 11
        val marginRange = 0.01..0.04
        val underVotePct = 0.234..0.345
        val phantomRange = 0.001..0.01

        val test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct, phantomRange)
        val testCvrs = test.makeCvrsFromContests()

        // TODO this looks wrong. use makeCardsFromContests ?
        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            mvrsToAuditableCardsTest( AuditType.CLCA, testCvrs, null).iterator()
        }

        if (showCvrs) {
            println("\n$CardHeader")
            var count = 0
            for (card in cardIterable.iterator()) {
                print(writeCardCsv(card))
                if (count++ > 10) break
            }
            println()
        }

        val contestsUA = ContestWithAssertions.make(test.contests, cardIterable.iterator(), isClca=true)

        val results = VerifyResults()
        verifyClcaAssortAvg(contestsUA, cardIterable.iterator(), results, show = false)
        println(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testAvgAssortNoStyle() {
        val N = 100
        val ncontests = 5
        val nbs = 3
        val marginRange = 0.01..0.04
        val underVotePct = 0.034..0.0345
        val phantomRange = 0.001..0.005

        val test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct, phantomRange)

        println()
        test.cardStyleWithNcards.forEach { println(it) }

        val testCards = test.makeCardsFromContests()
        if (showCvrs) testCards.subList(0, 10).forEach { print("  ${writeCardCsv(it)}") }

        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            MergeBatchesIntoCardManifestIterator(
                Closer(testCards.iterator()),
                batches = test.cardStyleWithNcards,
            )
        }

        if (showCvrs) {
            println("\n$CardHeader")
            var count = 0
            for (card in cardIterable.iterator()) {
                if (card.possibleContests().toSet() != card.votes?.keys) print("*** ")
                print(writeCardCsv(card))
                if (count++ > 100) break
            }
            println()
        }

        val contestsUA = ContestWithAssertions.make(test.contests, cardIterable.iterator(), isClca=true)
        contestsUA.forEach {
            println("$it : Npop diff = ${it.Npop != it.Nc}")
        }

        val results = VerifyResults()
        verifyClcaAssortAvg(contestsUA, cardIterable.iterator(), results, show = true)
        println(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testAvgAssortNoStyleAll() {
        val N = 100
        val ncontests = 5
        val nbs = 3
        val marginRange = 0.01..0.04
        val underVotePct = 0.034..0.0345
        val phantomRange = 0.001..0.005

        val test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct, phantomRange)

        println()
        test.cardStyleWithNcards.forEach { println(it) }

        val modStyles = test.cardStyleWithNcards.map { it.copy(contests=intArrayOf(0,1,2,3,4)) }

        val testCards = test.makeCardsFromContests()
        if (showCvrs) testCards.subList(0, 10).forEach { print("  ${writeCardCsv(it)}") }

        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            MergeBatchesIntoCardManifestIterator(
                Closer(testCards.iterator()),
                batches = modStyles,
            )
        }

        if (showCvrs) {
            println("\n$CardHeader")
            var count = 0
            for (card in cardIterable.iterator()) {
                if (card.possibleContests().toSet() != card.votes?.keys) print("*** ")
                print(writeCardCsv(card))
                if (count++ > 100) break
            }
            println()
        }

        val contestsUA = ContestWithAssertions.make(test.contests, cardIterable.iterator(), isClca=true)
        contestsUA.forEach {
            println("$it : Nb diff = ${it.Npop != it.Nc}")
        }

        val results = VerifyResults()
        verifyClcaAssortAvg(contestsUA, cardIterable.iterator(), results, show = true)
        println(results)
        if (results.hasErrors) fail()
    }


}
