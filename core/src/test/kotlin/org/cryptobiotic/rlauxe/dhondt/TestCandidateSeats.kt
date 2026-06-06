package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestCandidateSeats {
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
        val contestRound = lastRound.contestRounds.find { it.id == 6 }!!
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        // interesting: the dcontest assorters didnt make it through the serialization (inside contestRound.contestUA).....
        val dcontest = contestRound.contestUA.contest as DHondtContest
        assertTrue(dcontest.assorters.isEmpty()) // wtf ??
        val builder = CandSeatRangeBuilder(contestRound)
        // builder.mergedRanges.candidates.forEach { println(it) }
        println(builder.mergedRanges.showSeatRanges())
    }

    @Test
    fun testDHondtFailure() {
        val contestRound = lastRound.contestRounds.find { it.id == 5 }!!
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        // interesting: the dcontest assorters didnt make it through the serialization..... TODO ??
        val dcontest = contestRound.contestUA.contest as DHondtContest
        assertTrue(dcontest.assorters.isEmpty())

        // works anyway because it gets assorters from AssertionRound
        val builder = CandSeatRangeBuilder(contestRound)
        builder.mergedRanges.candidates.forEach { println(it) }
        println(builder.mergedRanges.showSeatRanges())
    }

    @Test
    fun testMakeAltContest() {
        val contestRound = lastRound.contestRounds.find { it.id == 6 }!!
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

        /* cands.failureNodes.forEach {
            val altContest = cands.makeAltContest(dcontest, it)
            println(altContest.alt)
            println(relax.showAltFailureContest(altContest))
        } */
    }

    @Test
    fun testShowRelaxedAssertion() {
        val contestRound = lastRound.contestRounds.find { it.id == 6 }!!
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        // interesting: the dcontest assorters didnt make it through the serialization..... TODO ??
        val dcontest = contestRound.contestUA.contest as DHondtContest

        val cassertion = contestRound.contestUA.clcaAssertions.find { it.assorter.shortName() == "DHondt w/l=LES ENGAGÉS-3/PS-4" }!!
        println( "Contest ${contestRound.contestUA.id} assertion ${cassertion.assorter.shortName()}")
        println( dcontest.showRelaxedAssertion(contestRound, cassertion) )

    }

    @Test
    fun testThresholdFailure() {
        val contestRound = lastRound.contestRounds.find { it.id == 5 }!!
        val sampleLimit = sampleLimitMap[contestRound.id]
        if (sampleLimit != null) {
            contestRound.haveSampleSize = sampleLimit.limit
        }
        // interesting: the dcontest assorters didnt make it through the serialization..... TODO ??
        val dcontest = contestRound.contestUA.contest as DHondtContest
        assertTrue(dcontest.assorters.isEmpty())
        val builder = CandSeatRangeBuilder(contestRound)
        builder.mergedRanges.candidates.forEach { println(it) }
        println(builder.mergedRanges.showSeatRanges())
    }

    @Test
    fun testShowRelaxedAssertions() {
        val contestRound = lastRound.contestRounds.find { it.id == 1 }!!
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
    fun testCountContestedSeats() {
        var totalContests = 0
        lastRound.contestRounds.forEach { contestRound ->
            val sampleLimit = sampleLimitMap[contestRound.id]
            if (sampleLimit != null) {
                contestRound.haveSampleSize = sampleLimit.limit
            }
            val dcontest = contestRound.contestUA.contest as DHondtContest
            val n = dcontest.countContestedSeats(contestRound)
            println("contest ${dcontest.id} has $n contested seats")
            totalContests += n
        }
        println("total = $totalContests")
    }

    @Test
    fun testAll() {
        val all = makeAllSeats(lastRound, sampleLimits)
        println("contestSeats")
        all.contestSeats.forEach { println(it.showSeatRanges()) }
        println()
        println("candidateSums")
        all.candidateSums.forEach { println(it) }
    }

    @Test
    fun testShowAllPartySeats() {
        val all = makeAllSeats(lastRound, sampleLimits)
        println(all.showAllPartySeats())
    }

    @Test
    fun testCoalitionAll() {
        val all = makeAllSeats(lastRound, sampleLimits)
        val sumFail = all.candidateSums.sumOf{ it.failures.size }
        val allCands = all.contestSeats.map { it.candidates }.flatten()
        val allCandsFail = allCands.sumOf{ it.failures.size }

        val allContests = partyNames.map { it.key }.toSet()
        val coal = all.calcCoalition(allContests, partyNames)
        // if all are in the coalition, there are no coalition failures
        println("sumFail = $sumFail; allCandsFail = $allCandsFail; coalAllFail = ${coal.nfailures}; coalFailures = ${coal.failures.size}; ")
        assertEquals(0, coal.failures.size)
    }

    @Test
    fun testOneCoalition() {
        val all = makeAllSeats(lastRound, sampleLimits)
        val coal = all.calcCoalition(setOf(15,24,14,28), partyNames)

        val sumFail = all.candidateSums.sumOf{ it.failures.size }
        val allCands = all.contestSeats.map { it.candidates }.flatten()
        val allCandsFail = allCands.sumOf{ it.failures.size }
        println("sumFail = $sumFail; allCandsFail = $allCandsFail; coalAllFail = ${coal.nfailures}; coalFailures = ${coal.failures.size}; ")

        println(coal)
    }

}