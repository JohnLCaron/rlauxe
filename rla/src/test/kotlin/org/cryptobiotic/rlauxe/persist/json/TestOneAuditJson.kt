package org.cryptobiotic.rlauxe.persist.json

import org.cryptobiotic.rlauxe.core.ClcaAssertion
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.core.TestH0Status
import org.cryptobiotic.rlauxe.oneaudit.AssortAvgsInPools
import org.cryptobiotic.rlauxe.oneaudit.BallotPool
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OAIrvContestUA
import org.cryptobiotic.rlauxe.oneaudit.OneAuditClcaAssorter
import org.cryptobiotic.rlauxe.oneaudit.makeOneContestUA
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
        val roundtrip = json.import(contestUA.contest)
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

    fun makeTestContestOAIrv(): OAIrvContestUA {

        val info = ContestInfo(
            "TestOneAuditIrvContest",
            0,
            mapOf("cand0" to 0, "cand1" to 1, "cand2" to 2, "cand3" to 3, "cand4" to 4, "cand42" to 42),
            SocialChoiceFunction.IRV,
            voteForN = 6,
        )
        val Nc = 212
        val Np = 1
        val rcontest = RaireContest(info, winners=listOf(1), Nc=Nc, Ncast=Nc-Np)

        val assert1 = RaireAssertion(1, 0, 42, RaireAssertionType.winner_only)
        val assert2 = RaireAssertion(1, 2, 422, RaireAssertionType.irv_elimination,
            listOf(2), mapOf(1 to 1, 2 to 2, 3 to 3))

        val oaIrv =  OAIrvContestUA(rcontest, true, listOf(assert1, assert2))

        // add pools

        // val contestOA = OneAuditContest.make(contest, cvrVotes, cvrPercent = cvrPercent, undervotePercent = undervotePercent, phantomPercent = phantomPercent)
        //val cvrVotes = mapOf(0 to 100, 1 to 200, 2 to 42, 3 to 7, 4 to 0) // worthless?
        //val cvrNc = 200
        val pool = BallotPool("swim", 42, 0, 11, mapOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0))
        val pools = listOf(pool)

        val clcaAssertions = oaIrv.pollingAssertions.map { assertion ->
            val passort = assertion.assorter
            val pairs = pools.map { pool ->
                Pair(pool.poolId, 0.55)
            }
            val poolAvgs = AssortAvgsInPools(assertion.info.id, pairs.toMap())
            val clcaAssertion = OneAuditClcaAssorter(assertion.info, passort, true, poolAvgs)
            ClcaAssertion(assertion.info, clcaAssertion)
        }
        oaIrv.clcaAssertions = clcaAssertions

        return oaIrv
    }

}