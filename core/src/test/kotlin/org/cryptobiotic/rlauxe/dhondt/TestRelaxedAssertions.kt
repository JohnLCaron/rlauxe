package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.persist.json.writeRelaxedAssertionProofs
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRelaxedAssertions {
    val topdir = "$cases/belgium/belgium2024/"
    val auditRecord = AuditRecord.read(topdir)!! as CompositeAuditRecord
    val contests = auditRecord.contests
    val lastRound = auditRecord.rounds.last()
    val config = auditRecord.config
    val sampleLimits: Map<Int, Int> = auditRecord.readSampleLimits().associate { it.id to it.limit }

    @Test
    fun testOneDHFailures() {
        val contestRound = lastRound.contestRounds.find { it.id == 4 }!! // FlandresEast
        val sampleLimit = sampleLimits[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit
        }
        val relax = makeRelaxedAssertions(contestRound, .05)
        println(relax.show())
        // println(relax.contestRanges().showSeatRanges())

        //           Vooruit/3-CD&V/3,          20,  0.5004,     3567,         800, 0.5112, 3567,
        val expected = mapOf(28 to -1, 4 to 1)
        checkExpectedContest(expected, relax.totalContestRange())
        println("-----------------------------------------------------------------------")
        //val candSeat: ContestSeatsRev = CandSeatRangeBuilderRev(contestRound).partyRanges
        //println(candSeat.showSeatRanges())
    }

    @Test
    fun testOneFailureVsV() {
        val oneFailures = listOf(1, 2, 6, 4)
        oneFailures.forEach { partyId ->
            val contestRound = lastRound.contestRounds.find { it.id == partyId }!!
            val sampleLimit = sampleLimits[contestRound.id]
            if (sampleLimit != null) {
                contestRound.haveSampleSize = sampleLimit
            }
            //println("==========================================================")
            val relax = makeRelaxedAssertions(contestRound, .05, sampleLimit)
            //println(relax.show())

            println("VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV")
            val relaxv = makeRelaxedAssertions(contestRound, .05, sampleLimit, version = "tooRelaxed")
            //println(relaxv.show())

            println("${contestRound.name} match = ${relax.totalContestRange().partyRanges() == relaxv.totalContestRange().partyRanges()}")

            compareAssorters(relax, relaxv, "tooRelaxed")
        }
    }

    fun compareAssorters(r: RelaxedAssertionsIF, v: RelaxedAssertionsIF, version: String) {
        val Npop = r.orgContest.Nc

        val mapr = r.assortersForProof().associateBy { it.shortName() }.toSortedMap()
        val mapv = v.assortersForProof().associateBy { it.shortName() }.toSortedMap()

        r.failures().forEach { println(it) }
        println("assertions in R:")
        mapr.forEach { (name, dh) ->
            if (mapv[name] == null) print("****") else print("    ")
            println(" ${dh} ${estSampleSizeStandardBet(Npop, dh.noerror(true), 0.05)} samples")
            //println(" ${dh}")
        }
        val minerror = mapr.values.minOf{ it.noerror(true) }
        println(" minerror = ${minerror} ${estSampleSizeStandardBet(Npop, minerror, 0.05)} samples")

        println()
        v.failures().forEach { println(it) }

        println("assertions in $version:")
        mapv.forEach { (name, dh) ->
            if (mapr[name] == null) print("****") else print("    ")
            println(" ${dh}, ${estSampleSizeStandardBet(Npop, dh.noerror(true), 0.05)} samples")
            //println(" ${dh}")
        }
        val minerrorv = mapv.values.minOf{ it.noerror(true) }
        println(" minerror = ${minerrorv} ${estSampleSizeStandardBet(Npop, minerrorv, 0.05)} samples")
    }

    @Test
    fun testMultipleDHFailures() {
        val contestRound = lastRound.contestRounds.find { it.id == 1 }!! // Anvers
        val sampleLimit = sampleLimits[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit
        }
        val relax = makeRelaxedAssertions(contestRound, .05, sampleLimit)
        println(relax.show())

        /* var idx = 0
        relax.assortersForProof().sortedBy { it.desc() }.forEach {
            println("$idx   $it")
            idx++
        } */

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
        checkExpectedContest(expected, relax.totalContestRange())
        println("VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV")
        //val relaxv = makeRelaxedAssertions(contestRound, .05, sampleLimit?.limit, useV = true)
        //println(relaxv.show())
        //println("match = ${relax.contestRange().partyRanges() == relaxv.contestRange().partyRanges()}")
        //compareAssorters(relax, relaxv)

    }

    @Test
    fun testWriteOneContestsJson() {
        val contestRound = lastRound.contestRounds.find { it.id == 5 }!!
        val filename = "/home/stormy/rla/temp/assertionsSingleBT.v3.json"
        writeRelaxedAssertionProofs(filename, listOf(contestRound), config.riskLimit, sampleLimits)
    }

    @Test
    fun testWriteAllContestsJson() {
        val filename = "/home/stormy/rla/temp/assertions.v5.json"
        val contestRounds = lastRound.contestRounds
        writeRelaxedAssertionProofs(filename, contestRounds, config.riskLimit, sampleLimits)
    }

    @Test
    fun testBruxelleFailures() {
        // val contestRound = lastRound.contestRounds.find { it.id == 5 }!! // Hainut
        val contestRound = lastRound.contestRounds.find { it.id == 2 }!! // Bruxelles now has 1 tfailure and 2 dh failures.
        val sampleLimit = sampleLimits[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit
        }
        val relax = makeRelaxedAssertions(contestRound, .05)
        println(relax.show())
        // println(relax.contestRange().showSeatRanges())

        // val expected = mapOf(24 to 1, 28 to -1, 19 to 1, 4 to -1)
        //checkExpectedContest(expected, relax.contestRanges())
        println("-----------------------------------------------------------------------")
        //val candSeat: ContestSeatsRev = CandSeatRangeBuilderRev(contestRound).partyRanges
        //println(candSeat.showSeatRanges())

        val filename = "/home/stormy/rla/temp/assertionsBruxelle.v5.json"
        writeRelaxedAssertionProofs(filename, listOf(contestRound), config.riskLimit, sampleLimits)
    }

    @Test
    fun testHainutFailures() {
        val contestRound = lastRound.contestRounds.find { it.id == 5 }!! // Hainut has 1 threshold failue and its alt has DH failures
        val sampleLimit = sampleLimits[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit
        }
        val relax = makeRelaxedAssertions(contestRound, .05)
        println(relax.show())
        // println(relax.contestRange().showSeatRanges())

        // val expected = mapOf(24 to 1, 28 to -1, 19 to 1, 4 to -1)
        //checkExpectedContest(expected, relax.contestRanges())
        println("-----------------------------------------------------------------------")
        //val candSeat: ContestSeatsRev = CandSeatRangeBuilderRev(contestRound).partyRanges
        //println(candSeat.showSeatRanges())

        val filename = "/home/stormy/rla/temp/assertionsHainut.v5.json"
        writeRelaxedAssertionProofs(filename, listOf(contestRound), config.riskLimit, sampleLimits)
    }
}

fun checkExpectedContest(expected: Map<Int, Int>, actual: ContestRange) {
    val ar = mutableMapOf<Int, Int>()
    actual.partyRanges().forEach {
        if ((it.maxSeats - it.reportedSeats) > 0) ar[it.partyId] = (it.maxSeats - it.reportedSeats)
        else if ((it.minSeats - it.reportedSeats) < 0) ar[it.partyId] = (it.minSeats - it.reportedSeats)
    }
    println(ar)
    assertEquals(expected, ar)

    actual.partyRanges().forEach { range ->
        val expect = expected[range.partyId]
        if (expect == null) {
            assertEquals(range.maxSeats, range.reportedSeats, range.toString())
            assertEquals(range.minSeats, range.reportedSeats, range.toString())
        } else {
            if (expect > 0)
                assertEquals(range.maxSeats, range.reportedSeats + expect, range.toString())
            else
                assertEquals(range.minSeats, range.reportedSeats + expect, range.toString())
        }
    }
}