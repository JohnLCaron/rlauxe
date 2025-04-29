package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.makeContestOA
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

        val json = target.publishIFJson()
        val roundtrip = json.import(contestUA.contest.info())
        assertNotNull(roundtrip)
        assert(roundtrip is ClcaAssertion)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testContestRoundtrip() {
        val contestUA = makeTestContestOA()
        val target = contestUA.contestOA

        val json = target.publishOAJson()
        val roundtrip = json.import(target.info)
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
        val minAllAsserter = contestOA.minClcaAssertion()
        assertNotNull(minAllAsserter)

        return contestOA
    }

}