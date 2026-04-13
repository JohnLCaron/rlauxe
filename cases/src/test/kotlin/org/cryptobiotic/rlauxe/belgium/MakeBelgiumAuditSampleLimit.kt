package org.cryptobiotic.rlauxe.belgium

import org.cryptobiotic.rlauxe.testdataDir
import kotlin.test.Test

class MakeBelgiumAuditSampleLimit {
    val topdirLimited = "$testdataDir/cases/belgium/2024limited"

    @Test
    fun createOneElectionLimited() {
        val electionName = "Hainaut"
        val topdir = "$topdirLimited/${electionName}"
        println("createBelgiumElectionLimited with electionName $electionName")
        val filename = belgianElectionMap[electionName]!!
        createAndRunBelgiumElection(electionName, filename, toptopdir, contestId=5, riskMeasuringSampleLimit=1000)
    }

    @Test
    fun createAllElectionsLimited() {
        belgianElectionMap.keys.forEachIndexed { idx, electionName ->
            val filename = belgianElectionMap[electionName]!!
            createAndRunBelgiumElection(electionName, filename, toptopdir, contestId=idx+1, riskMeasuringSampleLimit=1000)
        }
    }
}

