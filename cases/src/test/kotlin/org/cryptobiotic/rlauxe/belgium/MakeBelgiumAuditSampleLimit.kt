package org.cryptobiotic.rlauxe.belgium

import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.dhondt.CandSeatRanges
import org.cryptobiotic.rlauxe.dhondt.DHondtContest
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

class MakeBelgiumAuditSampleLimit {
    val topdirLimited = "$testdataDir/cases/belgium/2024limited"

    @Test
    fun createOneElectionLimited() {
        val electionName = "Limbourg"
        println("createBelgiumElectionLimited with electionName $electionName")
        val filename = belgianElectionMap[electionName]!!
        createAndRunBelgiumElection(electionName, filename, topdirLimited, contestId=7, riskMeasuringSampleLimit=1000, runRounds=false)
    }

    @Test
    fun createAllElectionsLimited() {
        belgianElectionMap.keys.forEachIndexed { idx, electionName ->
            val filename = belgianElectionMap[electionName]!!
            createAndRunBelgiumElection(electionName, filename, topdirLimited, contestId=idx+1, riskMeasuringSampleLimit=1000, runRounds=false)
        }
    }

    @Test
    fun runAllElectionsLimited() {
        belgianElectionMap.keys.forEach { electionName ->
            val auditdir = "$topdirLimited/$electionName/audit"
            runBelgiumElection(electionName, auditdir, stopRound=1)
        }
    }

    @Test
    fun showCandRangeAcrossContests() {
        val compositeDir = topdirLimited
        val compositeRecord = AuditRecord.readFrom(compositeDir)!!
        val candRanges = mutableListOf<CandSeatRanges>()
        compositeRecord.contests.forEach { contestUA ->
            val dcontest = contestUA.contest as DHondtContest
            candRanges.add(dcontest.makeSeatRanges(compositeRecord.rounds))
        }
        println("Sum of candidate ranges across all contests")
        val sum = CandSeatRanges.sumRanges(candRanges)
        print(sum.showSeatRanges())
    }
}

private fun runBelgiumElection(electionName: String, auditdir: String, stopRound:Int=0): Int {
    var done = false
    var finalRound: AuditRoundIF? = null
    while (!done) {
        val lastRound = runRound(inputDir = auditdir)
        if (lastRound != null) finalRound = lastRound
        done = lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5 || lastRound.roundIdx >= stopRound
    }

    return if (finalRound != null) {
        println("runBelgiumElection $electionName lastRound: ${finalRound.show()}")
        finalRound.nmvrs
    } else 0
}

