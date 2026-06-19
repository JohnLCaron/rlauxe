package org.cryptobiotic.rlauxe.belgium

import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

// obsolete I think
class MakeBelgiumAuditSampleLimit {
    val topdirLimited = "$testdataDir/cases/belgium/belgium2024limited"

    fun sampleLimitFun(contestId: Int): Int? {
        return when (contestId) {
            1 -> 1884
            2 -> 1759
            4 -> 800
            5 -> 550
            6 -> 640
            else -> null
        }
    }

    @Test
    fun createOneElectionLimited() {
        val electionName = "Liège"
        println("createBelgiumElectionLimited with electionName $electionName")
        val filename = belgianElectionMap[electionName]!!
        createAndRunBelgiumElection(electionName, filename, topdirLimited, contestId=6, runRounds=false) { sampleLimitFun(it) }
    }

    @Test
    fun createAllElectionsLimited() {
        belgianElectionMap.keys.forEachIndexed { idx, electionName ->
            val filename = belgianElectionMap[electionName]!!
            createAndRunBelgiumElection(electionName, filename, topdirLimited, contestId=idx+1, runRounds=false) { sampleLimitFun(it) }
        }
    }

    @Test
    fun runAllElectionsLimited() {
        belgianElectionMap.keys.forEach { electionName ->
            val auditdir = "$topdirLimited/$electionName/audit"
            runRound(auditdir)
        }
    }

}


