package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import kotlin.test.Test

class TestRelaxedAssertionsOld {
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
        // Anvers has 4 DH failures. Do we need to try all combinations ?? = 2^4 = 16 ??
        val contestRound = lastRound.contestRounds.find { it.id == 1 }!!
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val orgContest = contestRound.contestUA.contest as DhondtContest
        val orgAssorters = contestRound.contestUA.clcaAssertions.map { it.assorter }

        print(showRelaxedAssertions(orgContest, orgAssorters, sampleLimit?.limit ?: -1, .05))
    }

    @Test
    fun testOneFailure() {
        // Anvers has 4 DH failures. Do we need to try all combinations ?? = 2^4 = 16 ??
        val contestRound = lastRound.contestRounds.find { it.id == 1 }!!
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        print(RelaxedAssertionsOld(contestRound, .05).show())
    }

    @Test
    fun testAllFailures() {
        val contestRound = lastRound.contestRounds.forEach { contestRound ->
            println("========================================================================")
            val sampleLimit = sampleLimitMap[contestRound.id]
            if (sampleLimit != null) {
                contestRound.haveSampleSize = sampleLimit.limit
            }
            print(RelaxedAssertionsOld(contestRound, .05).show())
        }
    }

}