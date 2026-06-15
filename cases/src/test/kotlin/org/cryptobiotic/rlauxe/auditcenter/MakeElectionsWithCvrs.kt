package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.corla.countyElectionWithCvrs
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.votedatabase.colorado2020
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

import kotlin.test.Test

class MakeElectionsWithCvrs {
    val show = false

    /* this one is following CreateBoulderElection
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
    } */

    @Test
    fun makeColorado2020() {
        val topdir = "$cases/corla/withCvrs/Colorado2020debug"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 10000, auditSampleCutoff = 200000,
                sampling = Sampling.consistent),
            ClcaConfig(), null)

        // TODO topdir vs auditdir !!
        countyElectionWithCvrs(
            allColorado2020Counties(), Colorado2020General(), topdir,
            creation, round, name = "Colorado2020", startFirstRound = true
        )
    }

    @Test
    fun openColorado2020() {
        val auditdir = "$cases/corla/withCvrs/Colorado2020all/audit"
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

fun allColorado2020Counties(): Map<String, String> {
    val path = Path(colorado2020)
    val cvrdata = mutableListOf<Pair<String, String>>()
    path.listDirectoryEntries().sorted().filter { it.isDirectory() && !it.fileName.toString().startsWith("202")}.forEach { subdir ->
        val county = subdir.fileName.toString()
        // duplicates Huerfano && earlier format && missing contest tabulation && no such county in Colorado
        if (county !in listOf("Baca", "Garfield", "Gunnison", "Monroe", "Roosevelt")) {
            try {
                val filename = "${subdir}/cvr.csv" // entry.toString()
                cvrdata.add(Pair(county, filename))
            } catch (e: Exception) {
                println(e.message)
                throw e
            }
        }
    }
    return cvrdata.toMap()
}
