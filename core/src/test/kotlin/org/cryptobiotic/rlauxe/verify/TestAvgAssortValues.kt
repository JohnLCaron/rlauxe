package org.cryptobiotic.rlauxe.verify

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardsWithStylesToCards
import org.cryptobiotic.rlauxe.audit.CvrsWithStylesToCards
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardHeader
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsv
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
        val simContest = ContestSimulation.make2wayTestContest(Nc, margin, underVotePct, phantomPercent)

        val contest = simContest.contest
        println("contest = $contest")

        val testCvrs =  simContest.makeCvrs()
        if (showCvrs) testCvrs.subList(0, 10).forEach { println("  $it") }

        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            CvrsWithStylesToCards(
                AuditType.CLCA, false, Closer(testCvrs.iterator()),
                phantomCvrs=null, styles = null,
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

        val contestsUA = ContestUnderAudit.make(listOf(contest), cardIterable.iterator(), isClca=true, hasStyle=true)
        val contestUA= contestsUA.first()
        println("contestUA = ${contestUA.show()}")

        val minassert = contestUA.minPollingAssertion()
        println(minassert)
        assertEquals(contestUA.makeDilutedMargin(minassert!!.assorter), contestUA.minDilutedMargin())

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
        val simContest = ContestSimulation.make2wayTestContest(Nc, margin, underVotePct, phantomPercent)

        val contest = simContest.contest
        println("contest = $contest")

        val testCvrs =  simContest.makeCvrs()
        if (showCvrs) testCvrs.subList(0, 10).forEach { println("  $it") }

        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            CvrsWithStylesToCards(
                AuditType.CLCA, false, Closer(testCvrs.iterator()),
                phantomCvrs=null, styles = null,
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

        val contestsUA = ContestUnderAudit.make(listOf(contest), cardIterable.iterator(), isClca=true, hasStyle=true)
        val contestUA= contestsUA.first()
        println("contestUA = ${contestUA.show()}")

        val minassert = contestUA.minPollingAssertion()
        println(minassert)
        assertEquals(contestUA.makeDilutedMargin(minassert!!.assorter), contestUA.minDilutedMargin())

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

        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            CvrsWithStylesToCards(
                AuditType.CLCA, false, Closer(testCvrs.iterator()),
                phantomCvrs=null, styles = null,
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

        val contestsUA = ContestUnderAudit.make(test.contests, cardIterable.iterator(), isClca=true, hasStyle=true)

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

        val test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct, phantomRange,
            addPoolId = true)

        println()
        test.cardStyles.forEach { println(it) }

        val testCards = test.makeCardsFromContests()
        if (showCvrs) testCards.subList(0, 10).forEach { print("  ${writeAuditableCardCsv(it)}") }

        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            CardsWithStylesToCards(
                AuditType.CLCA, false, Closer(testCards.iterator()),
                phantomCards = null, styles = test.cardStyles,
            )
        }

        if (showCvrs) {
            println("\n$AuditableCardHeader")
            var count = 0
            for (card in cardIterable.iterator()) {
                if (card.contests().toSet() != card.votes?.keys) print("*** ")
                print(writeAuditableCardCsv(card))
                if (count++ > 100) break
            }
            println()
        }

        val contestsUA = ContestUnderAudit.make(test.contests, cardIterable.iterator(), isClca=true, hasStyle=false)
        contestsUA.forEach {
            println("$it : Nb diff = ${it.Nb != it.Nc}")
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

        val test = MultiContestTestData(ncontests, nbs, N, marginRange, underVotePct, phantomRange,
            addPoolId = true)

        println()
        test.cardStyles.forEach { println(it) }

        val modStyles = test.cardStyles.map { it.copy(contestIds=listOf(0,1,2,3,4)) }

        val testCards = test.makeCardsFromContests()
        if (showCvrs) testCards.subList(0, 10).forEach { print("  ${writeAuditableCardCsv(it)}") }

        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            CardsWithStylesToCards(
                AuditType.CLCA, false, Closer(testCards.iterator()),
                phantomCards = null, styles = modStyles,
            )
        }

        if (showCvrs) {
            println("\n$AuditableCardHeader")
            var count = 0
            for (card in cardIterable.iterator()) {
                if (card.contests().toSet() != card.votes?.keys) print("*** ")
                print(writeAuditableCardCsv(card))
                if (count++ > 100) break
            }
            println()
        }

        val contestsUA = ContestUnderAudit.make(test.contests, cardIterable.iterator(), isClca=true, hasStyle=false)
        contestsUA.forEach {
            println("$it : Nb diff = ${it.Nb != it.Nc}")
        }

        val results = VerifyResults()
        verifyClcaAssortAvg(contestsUA, cardIterable.iterator(), results, show = true)
        println(results)
        if (results.hasErrors) fail()
    }


}
