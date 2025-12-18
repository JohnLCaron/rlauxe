package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTestP
import org.cryptobiotic.rlauxe.oneaudit.makeTestContestOAIrv
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
        val roundtrip = json.import(contestUA.contest.info())
        assertNotNull(roundtrip)
        assert(roundtrip is ClcaAssertion)
        println((target as ClcaAssertion).checkEquals(roundtrip as ClcaAssertion))
        assertEquals((target.cassorter as ClcaAssorterOneAudit).info, (roundtrip.cassorter as ClcaAssorterOneAudit).info)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testContestRoundtrip() {
        val contestUA = makeTestContestOA()
        val target = contestUA.contest

        val json = target.publishJson()
        val roundtrip = json.import(target.info())
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testContestUARoundtrip() {
        val target: ContestUnderAudit = makeTestContestOA()

        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    fun makeTestContestOA(): ContestUnderAudit {
        val (contestOA, _, _) = makeOneAuditTestP(23000, 21000, cvrFraction = .70, undervoteFraction=.01, phantomFraction=.01)
        contestOA.preAuditStatus = TestH0Status.ContestMisformed
        val minAllAsserter = contestOA.minClcaAssertion()
        assertNotNull(minAllAsserter)

        return contestOA
    }

    //////////////////////////////////////////////////////////////////////////////

    // TODO OAIrv
    @Test
    fun testOAIrvRoundtrip() {
        val target = makeTestContestOAIrv()

        val json = target.publishRaireJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

}