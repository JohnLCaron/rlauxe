package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.testdataDir

import kotlin.test.Test

class MakeElectionsWithCvrs {
    val show = false

    // this one is following CreateBoulderElection
    @Test
    fun createCountyElection() { // simulate CVRs
        val exportFile = "/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/Boulder CO.csv"

        val auditdir = "$testdataDir/cases/datadrive/boulder/clca/audit"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 20, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 5000, auditSampleCutoff = 20000),
            ClcaConfig(fuzzMvrs = .001), null // TOFO is fuzz implemented ??
        )

        createCountyElection("Boulder", Colorado2020General(), exportFile, auditdir, creation, round)
    }

    @Test
    fun makeColorado2020() {
        val topdir = "$cases/corla/Colorado2020-4counties/"
        val counties = mapOf (
            "Adams" to "/home/stormy/datadrive/votedatabase/cvr/Colorado/Adams/Adams_2020G_CVR_REDACTED.csv", // or cvr.csv
            "Arapahoe" to "/home/stormy/datadrive/votedatabase/cvr/Colorado/Arapahoe/cvr.csv",
            "Boulder" to "/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/Boulder CO.csv", // or cvr.csv
            "Eagle" to "/home/stormy/datadrive/votedatabase/cvr/Colorado/Eagle/cvr.csv",
        )

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 10000, auditSampleCutoff = 200000,
                sampling = Sampling.consistent),
            ClcaConfig(), null)

        // TODO topdir vs auditdir !!
        countyElectionWithCvrs(counties, Colorado2020General(), topdir,
             creation, round, name = "Colorado2020", startFirstRound = true)
    }

    @Test
    fun openColorado2020() {
        val auditdir = "$cases/corla/Colorado2020/audit"
        val countyRecord = AuditRecord.read(auditdir) as CountyAudit

        println("countyRecord.countyData")
        countyRecord.countyData.forEach { println( it) }
        println()

        println("countyRecord.countyContestData")
        countyRecord.countyContestData.forEach { println( it) }

        val mvrCounts = countyRecord.countMvrsByCounty()
        println("countyRecord.countMvrsByCounty")
        mvrCounts.forEach { println( it) }
        println()

    }
}
