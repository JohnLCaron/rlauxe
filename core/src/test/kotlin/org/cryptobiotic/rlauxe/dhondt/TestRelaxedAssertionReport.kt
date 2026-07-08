package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.dhondt.CandSeatRanges.Companion.showMergedSeatRanges
import org.cryptobiotic.rlauxe.dhondt.CandSeatRanges.Companion.showSeatRange
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import kotlin.test.Test
import kotlin.test.assertTrue

class TestRelaxedAssertionReport {
    val topdir = "$cases/belgium/belgium2024/"
    val auditRecord = AuditRecord.read(topdir)!! as CompositeAuditRecord
    val contests = auditRecord.contests
    val partyNames = auditRecord.readPartyNames()
    val lastRound = auditRecord.rounds.last()
    val config = auditRecord.config
    val sampleLimits = auditRecord.readSampleLimits()
    val sampleLimitMap = auditRecord.readSampleLimits().associateBy { it.id }

    @Test
    fun testShowRelaxedAssertions() {
        val contestRound = lastRound.contestRounds.find { it.id == 4 }!!
        println("for ${contestRound.name}")
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        // interesting: the dcontest assorters didnt make it through the serialization..... TODO ??
        val dcontest = contestRound.contestUA.contest as DHondtContest
        assertTrue(dcontest.assorters.isEmpty())
        val builder = CandSeatRangeBuilder(contestRound)

        val ra = RelaxedAssertionReport(builder)
        println(ra.showRelaxedAssertions())
    }

    @Test
    fun testShowDhondtRiskFailures() {
        val contestRound = lastRound.contestRounds.find { it.id == 4 }!!
        println("for ${contestRound.name}")
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val builder = CandSeatRangeBuilder(contestRound)

        val ra = RelaxedAssertionReport(builder)
        println(ra.showDhondtRiskFailures())
    }

    @Test
    fun testMakeAltContest() {
        val contestRound = lastRound.contestRounds.find { it.id == 4 }!!
        println("for ${contestRound.name}")

        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        // interesting: the dcontest assorters didnt make it through the serialization..... TODO ??
        val dcontest = contestRound.contestUA.contest as DHondtContest
        assertTrue(dcontest.assorters.isEmpty())

        // works anyway because ??
        val cands = CandSeatRangeBuilder(contestRound)
        val relax = RelaxedAssertionReport(cands)

        cands.failureNodes.forEach { altFailure ->
            println(altFailure.altContest.alt)
            println(relax.showAltFailureContest(altFailure.altContest))
        }
    }

    @Test
    fun testShowRelaxedAssertion() {
        val contestRound = lastRound.contestRounds.find { it.id == 5 }!!
        println("for ${contestRound.name}")
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        // interesting: the dcontest assorters didnt make it through the serialization..... TODO ??
        val dcontest = contestRound.contestUA.contest as DHondtContest

        val cassertion = contestRound.contestUA.clcaAssertions.find { it.assorter.shortName().contains("BelowThreshold for 'ECOLO'") }!!
        println( "Contest ${contestRound.contestUA.id} assertion ${cassertion.assorter.shortName()}")
        println( dcontest.showRelaxedAssertion(contestRound, cassertion) )
    }

    @Test
    fun testAltThrasherAssertions() {
        val contestRound = lastRound.contestRounds.find { it.id == 5 }!! // Hainut with threshold failure
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val cassertion = contestRound.contestUA.clcaAssertions.find { it.assorter is BelowThreshold && it.assorter.candId == 9}!!

        // interesting: the dcontest assorters didnt make it through the serialization..... TODO ??
        val dcontest = contestRound.contestUA.contest as DHondtContest
        assertTrue(dcontest.assorters.isEmpty())
        println( dcontest.showRelaxedAssertion(contestRound, cassertion) )
    }

    @Test
    fun testCoalitionReport() {
        print( showCoalitionReport(auditRecord as CompositeAuditRecord))
    }

    @Test
    fun testMergeSeatRanges() {
        print( showMergedSeatRanges(lastRound))
    }

    @Test
    fun testAltContest() {
        val contestRound = lastRound.contestRounds.find { it.id == 5 }!!
        print( showSeatRange(contestRound))
    }

    @Test
    fun testShowAllRelaxedAssertions() {
        contests.forEach{ contestUA ->
            val contestRound = lastRound.contestRounds.find { it.contestUA.id == contestUA.id }
            if (contestRound != null) {
                val result = (contestUA.contest as DHondtContest).showRelaxedAssertions(contestRound)
                println(result)
                println("=======================================================================================================")
            }
        }
    }
}