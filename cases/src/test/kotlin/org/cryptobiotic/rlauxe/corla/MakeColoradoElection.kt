package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.PollingConfig
import org.cryptobiotic.rlauxe.audit.PollingMode
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.cli.RunVerifyAuditRecord.runVerifyAuditRecord
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardCsvFile
import org.cryptobiotic.rlauxe.util.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class MakeColoradoElection {

    // @Test
    fun makeCountyAudits25() {
        val topdir = "$testdataDir/cases/corla25/county"
        val detailXmlFile = "src/test/data/corla2025/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"
        val wantCounties = listOf("Summit") // listOf("Boulder",  "El Paso", "La Plata", "Weld",)

        val creationConfig = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
        val roundConfig = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 10000, auditSampleCutoff = 20000),
            ClcaConfig(), null)

        createCountyAudits(topdir,
            detailXmlFile, contestRoundFile, precinctFile,
            wantCounties, creationConfig, roundConfig,
            startFirstRound = false
        )
    }

    @Test
    fun makeCountyAudits24() {
        val topdir = "$testdataDir/cases/corla/county"
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"
        val wantCounties = listOf("Boulder",  "El Paso", "La Plata", "Weld",)

        val creationConfig = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
        val roundConfig = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 10000, auditSampleCutoff = 20000),
            ClcaConfig(), null)

        createCountyAudits(topdir,
            detailXmlFile, contestRoundFile, precinctFile,
            wantCounties, creationConfig, roundConfig,
            startFirstRound = true
            )
    }

    @Test
    fun makeColoradoClca() {
        val topdir = "$testdataDir/cases/corla/clca"
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 10000, auditSampleCutoff = 20000),
            ClcaConfig(), null)

        createColoradoElection(topdir, "$topdir/audit",
            detailXmlFile, contestRoundFile, precinctFile,
            null, creation, round)
    }

    @Test
    fun makeColoradoPollingPools() {
        val topdir = "$testdataDir/cases/corla/polling"
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 200000, auditSampleCutoff = 100000),
            null, PollingConfig())

        createColoradoElection(topdir, "$topdir/audit",
            detailXmlFile, contestRoundFile, precinctFile,
            pollingMode=PollingMode.withPools, creation, round)
    }

    @Test
    fun makeColoradoPollingBatches() {
        val topdir = "$testdataDir/cases/corla/polling2"
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 200000, auditSampleCutoff = 100000),
            null, PollingConfig())

        createColoradoElection(topdir, "$topdir/audit",
            detailXmlFile, contestRoundFile, precinctFile,
            pollingMode=PollingMode.withBatches, creation, round)
    }

    @Test
    fun makeColoradoPollingWithoutBatches() {
        val topdir = "$testdataDir/cases/corla/polling3"
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

        val creation = AuditCreationConfig(AuditType.POLLING, riskLimit=.03, )
        val round = AuditRoundConfig(
            SimulationControl(nsimTrials = 10, estPercentile = listOf(50, 80)),
            ContestSampleControl(minRecountMargin = .005, contestSampleCutoff = 200000, auditSampleCutoff = 100000),
            null, PollingConfig())

        createColoradoElection(topdir, "$topdir/audit",
            detailXmlFile, contestRoundFile, precinctFile,
            pollingMode=PollingMode.withoutBatches, creation, round)
    }

    // @Test
    fun testReadColoradoElectionDetail() {
        val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
        val electionResultXml: ElectionResult = readColoradoElectionDetail(detailXmlFile)
        println(electionResultXml)
        println("  number of contests = ${electionResultXml.contests.size}")
    }

    // @Test
    fun testRunVerifyPolling() {
        val auditdir = "$testdataDir/cases/corla/polling/audit"
        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
        println()
        print(results)

        val results2 = runVerifyAuditRecord(auditdir)
        println()
        print(results2)
        if (results.hasErrors) fail()
        if (results2.hasErrors) fail()
    }

    //// files were lost
    // @Test
    fun testPrecinctTree() {
        val cvrsDir = "$testdataDir/cases/corla/old/cvrexport" // lost?
        val tour = TreeReaderTour(cvrsDir, silent = false) { path -> precinctLine(path) }
        println("county, precinct")
        val total = tour.tourFiles()
        assertEquals(3199, total)
    }

    fun precinctLine(path: Path, silent: Boolean = true): CountyAndPrecinct {
        val last = path.nameCount - 1
        val county = path.getName(last-1)
        val filename = path.getName(last).toString()
        val precinct = filename.substring(0, filename.length-4)
        if (!silent) println("$county, $precinct")
        return CountyAndPrecinct(county.toString(), precinct)
    }

    // @Test
    fun testCountySampleLists() {
        val countyPrecincts = mutableListOf<CountyAndPrecinct>()

        val topDir = "$testdataDir/cases/corla/old" // lost?
        val tour = TreeReaderTour("$topDir/cvrexport") { path -> countyPrecincts.add(precinctLine(path)) }
        tour.tourFiles()

        val precinctMap = countyPrecincts.associate { it.precinct to it.county }
        val countySamples = countyPrecincts.associate { it.county to mutableMapOf<String, PrecinctSamples>() }

        // fake: reading the mvrs instead of the cvrs
        val publisher = Publisher("$topDir/audit")
        val sampledMvrs = readCardCsvFile(publisher.sampleMvrsFile(1))
        println("number of samples = ${sampledMvrs.size}")

        sampledMvrs.forEach{ mvr ->
            val precinct = mvr.id.split("-").first()
            val county = precinctMap[precinct] ?: error("no county for precinct $precinct")
            val countySampleMap = countySamples[county]!!
            val precinctSamples = countySampleMap.getOrPut(precinct) { PrecinctSamples(precinct) }
            precinctSamples.sampleIds.add(mvr.id)
        }

        println("============================================================")
        countySamples.forEach { (county, sampleMap) ->
            val countySamplesTotal = sampleMap.map { it.value.sampleIds.size }.sum()
            println("County $county ($countySamplesTotal)")
        }

        println("============================================================")
        countySamples.forEach { (county, sampleMap) ->
            val countySamplesTotal = sampleMap.map { it.value.sampleIds.size }.sum()
            println("County $county ($countySamplesTotal)")
            sampleMap.toSortedMap().forEach { (precinct, samples) ->
                println("   Precinct $precinct (${samples.sampleIds.size}) ")
                samples.sampleIds.sorted().forEach {
                    println("      $it")
                }
            }
        }
    }

    data class CountyAndPrecinct(val county: String, val precinct: String)

    data class PrecinctSamples(val precinct: String) {
        val sampleIds = mutableListOf<String>()
    }

}