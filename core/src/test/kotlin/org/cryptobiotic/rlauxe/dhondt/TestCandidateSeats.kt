package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.persist.json.RelaxedAssertionsResultJson
import org.cryptobiotic.rlauxe.persist.json.readRelaxedAssertionProofsUnwrapped
import org.cryptobiotic.rlauxe.persist.json.writeRelaxedAssertionProofs
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestCandidateSeats {
    val topdir = "$cases/belgium/belgium2024/"
    val auditRecord = AuditRecord.read(topdir)!! as CompositeAuditRecord
    val contests = auditRecord.contests
    val partyNames = auditRecord.readPartyNames()
    val lastRound = auditRecord.rounds.last()
    val config = auditRecord.config
    val sampleLimits = auditRecord.readSampleLimits().associate { it.id to it.limit }

    @Test
    fun testOneFailure() {
        val contestRound = lastRound.contestRounds.find { it.id == 6 }!!
        val sampleLimit = sampleLimits[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit
        }
        // interesting: the dcontest assorters didnt make it through the serialization (inside contestRound.contestUA).....
        val relax = makeRelaxedAssertions(contestRound, .05)
        val contestRange = relax.totalContestRange()
        println(contestRange.showSeatRanges())
    }

    @Test
    fun testWriteAssertionProof() {
        val contestRound = lastRound.contestRounds.find { it.id == 6 }!!
        val sampleLimit = sampleLimits[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit
        }
        // this seems to be the workaround
        val workaround = contestRound.contestUA.clcaAssertions.map { it.assorter }

        // builder.mergedRanges.candidates.forEach { println(it) }
        val relax = makeRelaxedAssertions(contestRound, .05)
        val contestRange = relax.totalContestRange()
        println(contestRange.showSeatRanges())

        //val failedAssorters = relax.failures().map { it.assorter }
        //val assorters = contestRound.contestUA.clcaAssertions.map { it.assorter }.filter { !failedAssorters.contains(it) }

        val scratchFile = createTempFile().toString()

        val org : RelaxedAssertionsResultJson = writeRelaxedAssertionProofs(scratchFile, listOf(contestRound), .05, sampleLimits = sampleLimits)

        val roundtrip = readRelaxedAssertionProofsUnwrapped(scratchFile)
        assertNotNull(roundtrip)
        assertEquals(org, roundtrip)
    }

    @Test
    fun testDHondtFailure() {
        val contestRound = lastRound.contestRounds.find { it.id == 6 }!!
        val sampleLimit = sampleLimits[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit
        }

        // works anyway because it gets assorters from AssertionRound
        val relax = makeRelaxedAssertions(contestRound, .05)
        relax.totalContestRange().partyRanges().forEach { println(it) }
        println(relax.totalContestRange().showSeatRanges())
    }

    @Test
    fun testThresholdFailure() {
        val contestRound = lastRound.contestRounds.find { it.id == 5 }!! // Hainut with threshold failure
        val sampleLimit = sampleLimits[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit
        }
        val relax = makeRelaxedAssertions(contestRound, .05)
        relax.totalContestRange().partyRanges().forEach { println(it) }
        println(relax.totalContestRange().showSeatRanges())
    }

    /* @Test
    fun testCountContestedSeats() {
        var totalContests = 0
        lastRound.contestRounds.forEach { contestRound ->
            val sampleLimit = sampleLimitMap[contestRound.id]
            if (sampleLimit != null) {
                contestRound.haveSampleSize = sampleLimit.limit
            }
            val dcontest = contestRound.contestUA.contest as DhondtContest
            val n = dcontest.countContestedSeats(contestRound)
            println("contest ${dcontest.id} has $n contested seats")
            totalContests += n
        }
        println("total = $totalContests")
    } */

    /* @Test
    fun testAll() {
        val all = makeAllSeats(lastRound, sampleLimits, .05)
        println("contestSeats")
        all.contestRanges.forEach { println(it.showSeatRanges()) }
        println()
        println("candidateSums")
        all.candidateSums.forEach { println(it) }
    } */

    /*
    @Test
    fun testAllWrite() {
        val allSeats = makeAllSeats(lastRound, sampleLimits, .05)

        val scratchFile = "/home/stormy/rla/temp/assertions.json" // createTempFile().toString()
        val org = writeAllContestsToJsonFile(lastRound.contestRounds, allSeats, filename = scratchFile, alpha = .05)

        val roundtrip = readDHondtAssertionContestsJsonUnwrapped(scratchFile)
        println("--------------------------------------------------------------------------")
        println(roundtrip)
        assertEquals(org, roundtrip)
    } */

    @Test
    fun testShowAllPartySeats() {
        val partyNames = auditRecord.readPartyNames()
        val all = makeAllSeatsFromRound(lastRound, sampleLimits, alpha = .05)
        println(all.showAllPartySeats(partyNames))
    }

    /*
    @Test
    fun testCoalitionAll() {
        val all = makeAllSeats(lastRound, sampleLimits, alpha = .05)
        // val sumFail = all.candidateSums.sumOf{ it.failures.size }
        val allCands = all.contestSeats.map { it.candidates }.flatten()
        val allCandsFail = allCands.sumOf{ it.failures.size }

        val allContests = partyNames.map { it.key }.toSet()
        val coal = all.calcCoalition(allContests, partyNames)
        // if all are in the coalition, there are no coalition failures
        println("sumFail = $sumFail; allCandsFail = $allCandsFail; coalAllFail = ${coal.nfailures}; coalFailures = ${coal.all().size}; ")
        assertEquals(32, coal.all().size)
    }

    @Test
    fun testOneCoalition() {
        val all = makeAllSeats(lastRound, sampleLimits, .05)
        val coal = all.calcCoalition(setOf(15,24,14,28), partyNames)

        val sumFail = all.candidateSums.sumOf{ it.failures.size }
        val allCands = all.contestSeats.map { it.candidates }.flatten()
        val allCandsFail = allCands.sumOf{ it.failures.size }
        println("sumFail = $sumFail; allCandsFail = $allCandsFail; coalAllFail = ${coal.nfailures}; coalFailures = ${coal.all().size}; ")

        println(coal)
    } */

}