package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContest
import org.cryptobiotic.rlauxe.oneaudit.OneAuditIrvContest
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
import org.cryptobiotic.rlauxe.raire.RaireAssertion
import org.cryptobiotic.rlauxe.raire.RaireContest
import kotlin.test.Test
import kotlin.test.assertTrue

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestOneAuditJson {

    @Test
    fun testAssortorRoundtrip() {
        val contestUA = makeTestContestOA()
        val target = contestUA.assertions().first().assorter

        val json = target.publishJson()
        val roundtrip = json.import(contestUA.contest.info())
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testAssertionRoundtrip() {
        val contestUA = makeTestContestOA()
        val target = contestUA.assertions().first()
        assert(target is ClcaAssertion)

        val json = target.publishIFJson()
        val roundtrip = json.import(contestUA.contest)
        assertNotNull(roundtrip)
        assert(roundtrip is ClcaAssertion)
        println((target as ClcaAssertion).checkEquals(roundtrip as ClcaAssertion))
        assertEquals((target.cassorter as OneAuditClcaAssorter).contestOA, (roundtrip.cassorter as OneAuditClcaAssorter).contestOA)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testContestRoundtrip() {
        val contestUA = makeTestContestOA()
        val target = contestUA.contestOA

        val json = target.publishJson()
        val roundtrip = json.import(target.contest.info())
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testContestUARoundtrip() {
        val target: OAContestUnderAudit = makeTestContestOA()

        val json = target.publishOAJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    fun makeTestContestOA(): OAContestUnderAudit {
        val contest = makeContestOA(23000, 21000, cvrPercent = .70, undervotePercent=.01, phantomPercent=.01)
        val contestOA = contest.makeContestUnderAudit()
        contestOA.preAuditStatus = TestH0Status.ContestMisformed
        val minAllAsserter = contestOA.minClcaAssertion()
        assertNotNull(minAllAsserter)

        return contestOA
    }

    @Test
    fun testContestOAIrvRoundtrip() {
        val target = makeTestContestOAIrv()

        val json = target.publishOAIrvJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    fun makeTestContestOAIrv(): OneAuditIrvContest {
        val info = ContestInfo(
            "TestOneAuditIrvContest",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2, "cand3" to 3, "cand4" to 4, "cand42" to 42),
            SocialChoiceFunction.IRV,
            voteForN = 6,
        )
        val Nc = 212
        val Np = 1
        val contest = RaireContest(info, winners=listOf(1), iNc=Nc, Np=Np)

        // val contestOA = OneAuditContest.make(contest, cvrVotes, cvrPercent = cvrPercent, undervotePercent = undervotePercent, phantomPercent = phantomPercent)
        val cvrVotes = mapOf(0 to 100, 1 to 200, 2 to 42, 3 to 7, 4 to 0) // worthless?
        val cvrNc = 200

        val pool = BallotPool("swim", 42, 0, 11, mapOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0))
        val contestOA = OneAuditContest.make(contest, cvrVotes, cvrNc, listOf(pool))

        val contestOAIrv =  OneAuditIrvContest(contestOA, true, emptyList<RaireAssertion>())
        contestOAIrv.makeClcaAssertionsFromReportedMargin()

        return contestOAIrv
    }

}