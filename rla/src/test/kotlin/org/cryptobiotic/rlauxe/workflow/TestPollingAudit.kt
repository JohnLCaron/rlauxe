package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.PollingConfig
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.df
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPollingAudit {

   // @Test
    fun testPollingNoStyleRepeat() {
        repeat(100) { testPollingNoStyle() }
    }

    @Test
    fun testPollingNoStyle() {
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles = false, nsimEst = 10)

        // each contest has a specific margin between the top two vote getters.
        val N = 100000
        val ncontests = 11
        val nbs = 4
        val marginRange= 0.05 .. 0.10
        val underVotePct= 0.02..0.02
        val phantomPct= 0.005..0.005
        val test = MultiContestTestData(
            ncontests,
            nbs,
            N,
            marginRange = marginRange,
            underVotePctRange = underVotePct,
            phantomPctRange = phantomPct
        )
        val contests: List<Contest> = test.contests

        println("Start testPollingNoStyle N=$N")
        contests.forEach{ println(" $it")}
        println()

        // Synthetic cvrs for testing reflecting the exact contest votes. In practice, we dont actually have the cvrs.
        val (testCvrs, ballots) = test.makeCvrsAndBallots(auditConfig.hasStyles)

        val workflow = PollingAuditTester(auditConfig, contests,
            MvrManagerPollingForTesting(ballots, testCvrs, auditConfig.seed)
        )
        runAudit("testPollingNoStyle", workflow)
    }

    // @Test
    fun testPollingWithStyleRepeat() {
        repeat(100) { testPollingWithStyle() }
    }

    @Test
    fun testPollingWithStyle() {
        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles = true, nsimEst = 10)

        // each contest has a specific margin between the top two vote getters.
        val N = 50000
        val ncontests = 11
        val nbs = 4
        val marginRange= 0.05 .. 0.10
        val underVotePct= 0.02 .. 0.02
        val phantomPct= 0.005 .. 0.005
        val test = MultiContestTestData(
            ncontests,
            nbs,
            N,
            marginRange = marginRange,
            underVotePctRange = underVotePct,
            phantomPctRange = phantomPct
        )

        val contests: List<Contest> = test.contests
        contests.forEachIndexed { idx, contest ->
            val nvotes = contest.votes.map{ it.value }.sum()
            val fcontest = test.fcontests[idx]
            println(" $contest")
            val Nc = contest.Nc.toDouble()
            print("    phantomCount=${fcontest.phantomCount} (${df(fcontest.phantomCount / Nc)})")
            print(" underCount=${fcontest.underCount} (${df(fcontest.underCount / Nc)})")
            print(" nvotes=${nvotes} (${df(nvotes / Nc)})")
            println()
            assertEquals(contest.Nc, fcontest.phantomCount + fcontest.underCount + nvotes)
        }
        println()
        // Synthetic cvrs for testing reflecting the exact contest votes. In production, we dont actually have the cvrs.
        val (testCvrs, ballots) = test.makeCvrsAndBallots(auditConfig.hasStyles)
        val testMvrs = testCvrs

        val workflow = PollingAuditTester(auditConfig, contests,
            MvrManagerPollingForTesting(ballots, testMvrs, auditConfig.seed)
        )
        runAudit("testPollingWithStyle", workflow)
    }

    @Test
    fun testPollingWithFuzz() {
        val mvrFuzzPct = .0123
        val auditConfig = AuditConfig(
            AuditType.POLLING, hasStyles = true, nsimEst = 10,
            pollingConfig = PollingConfig(simFuzzPct = mvrFuzzPct)
        )

        // each contest has a specific margin between the top two vote getters.
        val N = 50000
        val ncontests = 11
        val nbs = 4
        val marginRange= 0.05 .. 0.10
        val underVotePct= 0.02 .. 0.02
        val phantomPct= 0.005 .. 0.005
        val test = MultiContestTestData(
            ncontests,
            nbs,
            N,
            marginRange = marginRange,
            underVotePctRange = underVotePct,
            phantomPctRange = phantomPct
        )

        val contests: List<Contest> = test.contests

        // Synthetic cvrs for testing reflecting the exact contest votes. In production, we dont actually have the cvrs.
        val (testCvrs, ballots) = test.makeCvrsAndBallots(auditConfig.hasStyles)
        val testMvrs = makeFuzzedCvrsFrom(test.contests, testCvrs, mvrFuzzPct)

        val workflow = PollingAuditTester(auditConfig, contests,
            MvrManagerPollingForTesting(ballots, testMvrs, auditConfig.seed)
        )
        runAudit("testPollingWithStyle", workflow)
    }

    @Test
    fun testPollingOneContest() {
        val Nc = 50000
        val ncontests = 1
        val nbs = 1
        val marginRange= 0.05 .. 0.05
        val underVotePct= 0.05 .. 0.05
        val phantomPct= 0.005 .. 0.005
        val test = MultiContestTestData(
            ncontests,
            nbs,
            Nc,
            marginRange = marginRange,
            underVotePctRange = underVotePct,
            phantomPctRange = phantomPct
        )
        test.contests.forEachIndexed { idx, contest ->
            val nvotes = contest.votes.map{ it.value }.sum()
            val fcontest = test.fcontests[idx]
            println(" $contest")
            val Nc = contest.Nc.toDouble()
            print("    phantomCount=${fcontest.phantomCount} (${df(fcontest.phantomCount / Nc)})")
            print(" underCount=${fcontest.underCount} (${df(fcontest.underCount / Nc)})")
            print(" nvotes=${nvotes} (${df(nvotes / Nc)})")
            println()
            assertEquals(contest.Nc, fcontest.phantomCount + fcontest.underCount + nvotes)
        }

        val auditConfig = AuditConfig(AuditType.POLLING, hasStyles = true, nsimEst = 10)
        val (testCvrs, ballots) = test.makeCvrsAndBallots(auditConfig.hasStyles)
        val workflow = PollingAuditTester(auditConfig, test.contests,
            MvrManagerPollingForTesting(ballots, testCvrs, auditConfig.seed)
        )

        runAudit("testPollingOneContest", workflow)
    }
}