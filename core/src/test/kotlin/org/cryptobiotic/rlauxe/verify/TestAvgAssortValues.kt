package org.cryptobiotic.rlauxe.verify

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CvrsWithStylesToCards
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardHeader
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsv
import org.cryptobiotic.rlauxe.util.CloseableIterable
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.verifyOAassortAvg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

// when does (winner - loser) / N agree with AvgAssortValue?
class TestAvgAssortValues {
    val showCvrs = false

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

        val contestUA = ContestUnderAudit(contest, isClca = true).addStandardAssertions()
        println("contestUA = ${contestUA.show()}")

        val (minassert, minMargin) = contestUA.minPollingAssertion()
        println(minassert)

        assertEquals(minMargin, contestUA.minPollingAssertion().second)

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

        //     cards: CloseableIterator<AuditableCard>,
        //    result: VerifyResults,
        //    show: Boolean = false
        val results = VerifyResults()
        verifyClcaAssortAvg(listOf(contestUA), cardIterable.iterator(), results, show = true)
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

        val contestUA = ContestUnderAudit(contest, isClca = true).addStandardAssertions()
        println("contestUA = ${contestUA.show()}")

        val (minassert, minMargin) = contestUA.minPollingAssertion()
        println(minassert)

        assertEquals(minMargin, contestUA.minPollingAssertion().second)

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

        //     cards: CloseableIterator<AuditableCard>,
        //    result: VerifyResults,
        //    show: Boolean = false
        val results = VerifyResults()
        verifyClcaAssortAvg(listOf(contestUA), cardIterable.iterator(), results, show = true)
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

        val test = MultiContestTestData(ncontests, nbs, N, hasStyle=true, marginRange, underVotePct, phantomRange)
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

        val contestsOA = test.contests.map { ContestUnderAudit(it, isClca = true, hasStyle=true).addStandardAssertions() }
        val results = VerifyResults()
        verifyClcaAssortAvg(contestsOA, cardIterable.iterator(), results, show = false)
        println(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testAvgAssortNoStyle() {
        val N = 100
        val ncontests = 3
        val nbs = 11
        val marginRange = 0.01..0.04
        val underVotePct = 0.034..0.0345
        val phantomRange = 0.001..0.005

        val test = MultiContestTestData(ncontests, nbs, N, hasStyle=false, marginRange, underVotePct, phantomRange)
        val testCvrs = test.makeCvrsFromContests()

        val cardIterable: CloseableIterable<AuditableCard> = CloseableIterable {
            CvrsWithStylesToCards(
                AuditType.CLCA, false, Closer(testCvrs.iterator()),
                phantomCvrs=null, styles = test.ballotStyles,
            )
        }

        if (true) {
            println("\n$AuditableCardHeader")
            var count = 0
            for (card in cardIterable.iterator()) {
                print(writeAuditableCardCsv(card))
                if (count++ > 10) break
            }
            println()
        }

        val contestsOA = test.contests.map { ContestUnderAudit(it, isClca = true, hasStyle=false).addStandardAssertions() }
        val results = VerifyResults()
        verifyClcaAssortAvg(contestsOA, cardIterable.iterator(), results, show = true)
        println(results)
        if (results.hasErrors) fail()
    }


}
