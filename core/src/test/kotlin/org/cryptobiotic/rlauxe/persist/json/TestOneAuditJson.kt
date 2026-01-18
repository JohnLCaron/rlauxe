package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.betting.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.ClcaAssorterOneAudit
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditTest
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
        val target: ContestWithAssertions = makeTestContestOA()

        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    fun makeTestContestOA(): ContestWithAssertions {
        val (contestOA, _, _) = makeOneAuditTest(23000, 21000, cvrFraction = .70, undervoteFraction=.01, phantomFraction=.01)
        contestOA.preAuditStatus = TestH0Status.ContestMisformed
        val minAllAsserter = contestOA.minClcaAssertion()
        assertNotNull(minAllAsserter)

        return contestOA
    }

}