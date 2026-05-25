package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import org.junit.Test
import kotlin.test.assertTrue

class TestCands2 {
    val auditdir = "$testdataDir/cases/belgium/belgium2024/"
    val auditRecord = AuditRecord.read(auditdir)!! as CompositeAuditRecord
    val contests = auditRecord.contests
    val partyNames = auditRecord.readPartyNames()
    val lastRound = auditRecord.rounds.last()
    val config = auditRecord.config
    val sampleLimits = auditRecord.readSampleLimits()
    val sampleLimitMap = auditRecord.readSampleLimits().associateBy { it.id }

    @Test
    fun testOneFailure() {
        val contestRound = lastRound.contestRounds.find { it.id == 2 }!!
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        // interesting: the dcontest assorters didnt make it through the serialization.....
        val dcontest = contestRound.contestUA.contest as DHondtContest
        assertTrue(dcontest.assorters.isEmpty())
        val builder = CandSeatRangeBuilder2(contestRound)
        // builder.mergedRanges.candidates.forEach { println(it) }
        println(builder.mergedRanges.showSeatRanges())
    }

    @Test
    fun testThresholdFailure() {
        val contestRound = lastRound.contestRounds.find { it.id == 5 }!!
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        // interesting: the dcontest assorters didnt make it through the serialization.....
        val dcontest = contestRound.contestUA.contest as DHondtContest
        assertTrue(dcontest.assorters.isEmpty())
        val builder = CandSeatRangeBuilder2(contestRound)
        builder.mergedRanges.candidates.forEach { println(it) }
        println(builder.mergedRanges.showSeatRanges())
    }

    @Test
    fun testAll() {
        val all = makeContestAndCandidateSeats(lastRound, sampleLimits)
        println("contestSeats")
        all.contestSeats.forEach { println(it.showSeatRanges()) }
        println()
        println("candidateSums")
        all.candidateSums.forEach { println(it) }
    }

    @Test
    fun testCoalitionAll() {
        val all = makeContestAndCandidateSeats(lastRound, sampleLimits)
        val sumFail = all.candidateSums.sumOf{ it.failures.size }
        val allCands = all.contestSeats.map { it.candidates }.flatten()
        val allCandsFail = allCands.sumOf{ it.failures.size }

        val allContests = partyNames.map { it.key }.toSet()
        val coal = all.calcCoalition(allContests, partyNames)
        // if all are in teh coalition, there are no coalition failures
        println("sumFail = $sumFail; allCandsFail = $allCandsFail; coalAllFail = ${coal.nfailures}; coalFailures = ${coal.failures.size}; ")
    }

    @Test
    fun testOneCoalition() {
        val all = makeContestAndCandidateSeats(lastRound, sampleLimits)
        val coal = all.calcCoalition(setOf(15,24,14,28), partyNames)

        val sumFail = all.candidateSums.sumOf{ it.failures.size }
        val allCands = all.contestSeats.map { it.candidates }.flatten()
        val allCandsFail = allCands.sumOf{ it.failures.size }
        println("sumFail = $sumFail; allCandsFail = $allCandsFail; coalAllFail = ${coal.nfailures}; coalFailures = ${coal.failures.size}; ")

        println(coal)
    }

}