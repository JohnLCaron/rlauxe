package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.corla.Colorado2024Input
import org.cryptobiotic.rlauxe.corla.createCorlaElection
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.testdataDir

import kotlin.test.Test

class MakeElectionsWithoutCvrs {
    val show = false

    @Test
    fun makeColorado2022Primary() {
        val topdir = "$testdataDir/cases/auditcenter/Colorado2022Primary"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 10000, auditSampleCutoff = 200000,
                sampling = Sampling.consistent),
            ClcaConfig(), null)

        createCorlaElection(topdir, "$topdir/audit", Colorado2022Primary(),
            null, creation, round, name = "Colorado2022Primary", startFirstRound = true)
    }

    @Test
    fun openColorado2022Primary() {
        val topdir = "$testdataDir/cases/auditcenter/Colorado2022Primary"
        val countyRecord = AuditRecord.read("$topdir/audit") as CountyAudit

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
