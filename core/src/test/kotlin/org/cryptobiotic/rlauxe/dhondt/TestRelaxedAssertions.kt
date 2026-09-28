package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRelaxedAssertions {
    val topdir = "$cases/belgium/belgium2024/"
    val auditRecord = AuditRecord.read(topdir)!! as CompositeAuditRecord
    val contests = auditRecord.contests
    val lastRound = auditRecord.rounds.last()
    val config = auditRecord.config
    val sampleLimitMap = auditRecord.readSampleLimits().associateBy { it.id }

    @Test
    fun testOneHFailures() {
        val contestRound = lastRound.contestRounds.find { it.id == 4 }!! // FlandresEast
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val relax = makeRelaxedAssertions(contestRound, .05)
        println(relax.show())
        println(relax.contestRanges().showSeatRanges())

        //          Vooruit/3-CD&V/3,          20,  0.5004,     3567,         800, 0.5112,
        val expected = mapOf(28 to -1, 4 to 1)
        checkExpectedContest(expected, relax.contestRanges())
        println("-----------------------------------------------------------------------")
        val candSeat: ContestSeatsRev = CandSeatRangeBuilderRev(contestRound).partyRanges
        println(candSeat.showSeatRanges())
    }

    @Test
    fun testMultipleDHFailures() {
        val contestRound = lastRound.contestRounds.find { it.id == 1 }!! // Anvers
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val relax = makeRelaxedAssertions(contestRound, .05)
        println(relax.show())
        println(relax.contestRanges().showSeatRanges())

        // I think the answer should be
        // ContestId=1
        //|                party     | min | reported | max | nfailures |
        //|--------------------------|-----|----------|-----|-----------|
        //|                  N-VA 15 |  7  |     8    |  9  |       3   |
        //|         VLAAMS BELANG 24 |  4  |     5    |  6  |       4   | could gain 1
        //|               Vooruit 28 |  2  |     3    |  4  |       4   | could lose 1
        //|                  PVDA 19 |  1  |     2    |  3  |       4   | could gain 1
        //|                  CD&V  4 |  2  |     3    |  3  |       3   | could lose 1

        val expected = mapOf(24 to 1, 28 to -1, 19 to 1, 4 to -1)
        checkExpectedContest(expected, relax.contestRanges())
        println("-----------------------------------------------------------------------")
        val candSeat: ContestSeatsRev = CandSeatRangeBuilderRev(contestRound).partyRanges
        println(candSeat.showSeatRanges())
    }

    @Test
    fun testThresholdFailure() {
        val contestRound = lastRound.contestRounds.find { it.id == 5 }!! // Hainut
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val relax = makeRelaxedAssertions(contestRound, .05)
        println(relax.show())
        println(relax.contestRanges().showSeatRanges())

        val expected = mapOf(24 to 1, 28 to -1, 19 to 1, 4 to -1)
        //checkExpectedContest(expected, relax.contestRanges())
        println("-----------------------------------------------------------------------")
        val candSeat: ContestSeatsRev = CandSeatRangeBuilderRev(contestRound).partyRanges
        println(candSeat.showSeatRanges())
    }
}

fun checkExpectedContest(expected: Map<Int, Int>, actual: ContestRanges) {
    actual.partyRanges.values.forEach { range ->
        val expect = expected[range.partyId]
        if (expect == null) {
            assertEquals(range.maxSeats, range.reportedSeats)
            assertEquals(range.minSeats, range.reportedSeats)
        } else {
            if (expect > 0)
                assertEquals(range.maxSeats, range.reportedSeats + expect)
            else
                assertEquals(range.minSeats, range.reportedSeats + expect)
        }
    }
}