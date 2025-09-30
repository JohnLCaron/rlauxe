package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.AssortAvgsInPools
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OAIrvContestUA
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.makeTestContestOAIrv
import org.cryptobiotic.rlauxe.workflow.makeOneContestUA
import org.cryptobiotic.rlauxe.raire.RaireAssertion
import org.cryptobiotic.rlauxe.raire.RaireAssertionType
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
        val roundtrip = json.import(contestUA.contest.info())
        assertNotNull(roundtrip)
        assert(roundtrip is ClcaAssertion)
        println((target as ClcaAssertion).checkEquals(roundtrip as ClcaAssertion))
        assertEquals((target.cassorter as OneAuditClcaAssorter).info, (roundtrip.cassorter as OneAuditClcaAssorter).info)
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
        val target: OAContestUnderAudit = makeTestContestOA()

        val json = target.publishJson()
        val roundtrip = json.import(isOA = true)
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    fun makeTestContestOA(): OAContestUnderAudit {
        val (contestOA, testCvrs) = makeOneContestUA(23000, 21000, cvrPercent = .70, undervotePercent=.01, phantomPercent=.01)
        contestOA.preAuditStatus = TestH0Status.ContestMisformed
        val minAllAsserter = contestOA.minClcaAssertion()
        assertNotNull(minAllAsserter)

        return contestOA
    }

    //////////////////////////////////////////////////////////////////////////////


    @Test
    fun testOAIrvRoundtrip() {
        val raireContestUA = makeTestContestOAIrv()
        val target = OAIrvContestUA(raireContestUA.contest as RaireContest, true,  raireContestUA.rassertions)

        val json = target.publishOAIrvJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }

}