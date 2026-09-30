package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.persist.SampleLimit
import org.cryptobiotic.rlauxe.persist.json.writeAllContestsToJsonFile
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRelaxedAssertions {
    val topdir = "$cases/belgium/belgium2024/"
    val auditRecord = AuditRecord.read(topdir)!! as CompositeAuditRecord
    val contests = auditRecord.contests
    val lastRound = auditRecord.rounds.last()
    val config = auditRecord.config
    val sampleLimitMap: Map<Int, SampleLimit> = auditRecord.readSampleLimits().associateBy { it.id }

    @Test
    fun testOneDHFailures() {
        val contestRound = lastRound.contestRounds.find { it.id == 1 }!! // Anders
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val relax = makeRelaxedAssertions(contestRound, .05)
        println(relax.show())
        // println(relax.contestRanges().showSeatRanges())

        //          Vooruit/3-CD&V/3,          20,  0.5004,     3567,         800, 0.5112,
        val expected = mapOf(28 to -1, 4 to 1)
        checkExpectedContest(expected, relax.contestRanges())
        println("-----------------------------------------------------------------------")
        //val candSeat: ContestSeatsRev = CandSeatRangeBuilderRev(contestRound).partyRanges
        //println(candSeat.showSeatRanges())
    }

    @Test
    fun testOneFailureVsV() {
        val oneFailures = listOf(2, 6, 4)
        oneFailures.forEach { partyId ->
            val contestRound = lastRound.contestRounds.find { it.id == partyId }!!
            val sampleLimit = sampleLimitMap[contestRound.id]
            if (sampleLimit != null) {
                contestRound.haveSampleSize = sampleLimit.limit
            }
            println("==========================================================")
            val relax = makeRelaxedAssertions(contestRound, .05, sampleLimit?.limit)
            //println(relax.show())
            println(relax.contestRanges().showSeatRanges())
            println("VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV")
            val relaxv = makeRelaxedAssertions(contestRound, .05, sampleLimit?.limit, useV = true)
            //println(relaxv.show())
            println(relaxv.contestRanges().showSeatRanges())

            println("match = ${relax.contestRanges().partyRanges == relaxv.contestRanges().partyRanges}")
        }
    }

    @Test
    fun testMultipleDHFailures() {
        val contestRound = lastRound.contestRounds.find { it.id == 1 }!! // Anvers
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        val relax = makeRelaxedAssertions(contestRound, .05, sampleLimit?.limit, useV = false)
        println(relax.show())

        var idx = 0
        relax.assortersForProof().sortedBy { it.desc() }.forEach {
            println("$idx   $it")
            idx++
        }

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
        println("VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV")
        val relaxv = makeRelaxedAssertions(contestRound, .05, sampleLimit?.limit, useV = true)
        println(relaxv.show())
        println("match = ${relax.contestRanges().partyRanges == relaxv.contestRanges().partyRanges}")
    }

    @Test
    fun testWriteOneContestsJson() {
        val contestRound = lastRound.contestRounds.find { it.id == 2 }!!

        val filename = "/home/stormy/rla/temp/assertionsBruxelles.json"
        writeAllContestsToJsonFile(listOf(contestRound), filename, config.riskLimit, pretty = false, sampleLimitMap, useV = false)
    }

    @Test
    fun testWriteAllContestsJson() {
        val filename = "/home/stormy/rla/temp/assertionsv.json"
        val contestRounds = lastRound.contestRounds
        writeAllContestsToJsonFile(contestRounds, filename, config.riskLimit, pretty = false, sampleLimitMap, useV = true)
    }

    @Test
    fun testThresholdFailure() {
        val contestRound = lastRound.contestRounds.find { it.id == 1 }!! // Hainut
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
        //val candSeat: ContestSeatsRev = CandSeatRangeBuilderRev(contestRound).partyRanges
        //println(candSeat.showSeatRanges())
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