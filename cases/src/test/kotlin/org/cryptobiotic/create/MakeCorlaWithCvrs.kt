package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.audit.resampleAndSaveResults
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.audit.runRoundResult
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
import org.cryptobiotic.rlauxe.auditcenter.countyElectionWithCvrs
import org.cryptobiotic.rlauxe.auditcenter.writeCountyContestData
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.votedatabase.colorado2020
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

import kotlin.test.Test

class MakeElectionsWithCvrs {
    val show = false

    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
        ContestSampleControl(
            minRecountMargin = .005,
            minMargin = .005,
            minSize = 10,
            contestSampleCutoff = 10000,
            auditSampleCutoff = 200000,
            sampling = Sampling.consistent),
        ClcaConfig(), null)

    @Test
    fun makeColorado2020uniform() {
        val topdir = "$cases/corla/withCvrs/Colorado2020uniform"
        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.04, ) // TODO LOOK
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(
                minRecountMargin = .005,
                minMargin = .005,
                minSize = 10,
                contestSampleCutoff = 10000,
                auditSampleCutoff = 200000,
                sampling = Sampling.uniform),
            ClcaConfig(), null)

        countyElectionWithCvrs(
            allColorado2020Counties(),
            Colorado2020General(),
            topdir,
            creation,
            round,
            name = "Colorado2020uniform",
            startFirstRound = true,
            isUniform = true,
        )
    }

    @Test
    fun makeColorado2020() {
        val topdir = "$cases/corla/withCvrs/Colorado2020"
        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.04, ) // TODO LOOK

        countyElectionWithCvrs(
            allColorado2020Counties(),
            Colorado2020General(),
            topdir,
            creation,
            round,
            name = "Colorado2020",
            startFirstRound = true,
            isUniform = false,
        )
    }

    @Test
    fun writeCountyContestData() {
        val topdir = "$cases/corla/withCvrs/Colorado2020"
        val auditRecord = AuditRecord.read(topdir)!!

        val coloradoInput = Colorado2020General()

        // writeCountyData(topdir, coloradoInput.strataMap.values.toList())

        val contestMap = auditRecord.contests.associate { it.contest.info().name to it }
        writeCountyContestData(topdir, contestMap, coloradoInput)
    }

    @Test
    fun startColorado2020() {
        val topdir = "$cases/corla/withCvrs/Colorado2020uniform"
        startFirstRound(topdir)
    }

    @Test
    fun runColorado2020() {
        val topdir = "$cases/corla/withCvrs/Colorado2020uniform"
        runRound(topdir)
    }
    @Test
    fun resampleColorado2020() {
        val topdir = "$cases/corla/withCvrs/Colorado2020uniform"
        resampleAndSaveResults(topdir)
    }

    @Test
    fun openColorado2020() {
        val topdir = "$cases/corla/withCvrs/Colorado2020uniform"
        val countyRecord = AuditRecord.read(topdir) as CountyAuditRecord

        val what = runRoundResult(topdir)
        println(what)

        println("countyRecord.countyData")
        // countyRecord.countyData.forEach { println( it) }
        println()

        println("countyRecord.countyContestData")
        // countyRecord.countyContestData.forEach { println( it) }

        val mvrCounts = countyRecord.countMvrsByCounty()
        println("countyRecord.countMvrsByCounty")
        mvrCounts.forEach { println(it) }
        println()
    }
}

fun allColorado2020Counties(): Map<String, String> {
    val path = Path(colorado2020)
    val cvrdata = mutableListOf<Pair<String, String>>()
    path.listDirectoryEntries().sorted().filter { it.isDirectory() && !it.fileName.toString().startsWith("202")}.forEach { subdir ->
        val county = subdir.fileName.toString()
        // Baca duplicates Huerfano
        // Gunnison is missing contest tabulation
        // Las Animas has only 120 of 8000 cvrs
        // San Juan is missing
        // Monroe, Rooselvelt: no such county in Colorado
        if (county !in listOf("Baca", "Gunnison", "Las Animas", "San Juan", "Monroe", "Roosevelt")) {
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
