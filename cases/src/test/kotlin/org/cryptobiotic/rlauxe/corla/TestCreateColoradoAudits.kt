package org.cryptobiotic.rlauxe.corla

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.writeSortedCardsInternalSort
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.util.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCreateColoradoAudits {

    @Test
    fun testReadColoradoElectionDetail() {
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val electionResultXml: ElectionDetailXml = readColoradoElectionDetail(detailXmlFile)
        println(electionResultXml)
        println("  number of contests = ${electionResultXml.contests.size}")
    }

    @Test
    fun testCreateColoradoOneAudit() {
        val topdir = "/home/stormy/rla/cases/corla/oneaudit"
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val contestRoundFile = "src/test/data/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"

        createColoradoOneAudit(topdir, detailXmlFile, contestRoundFile, precinctFile, isClca=false, clear=true)

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun testCreateColoradoClca() {
        val topdir = "/home/stormy/rla/cases/corla/clca"
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val contestRoundFile = "src/test/data/2024audit/round1/contest.csv"
        val precinctFile = "src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"

        createColoradoOneAudit(topdir, detailXmlFile, contestRoundFile, precinctFile, isClca=true, clear=true)

        val publisher = Publisher("$topdir/audit")
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsInternalSort(publisher, config.seed)
    }

    @Test
    fun testPrecinctTree() {
        val cvrsDir = "/home/stormy/rla/cases/corla/old/cvrexport"
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

        val topDir = "/home/stormy/rla/cases/corla/old"
        val tour = TreeReaderTour("$topDir/cvrexport") { path -> countyPrecincts.add(precinctLine(path)) }
        tour.tourFiles()

        val precinctMap = countyPrecincts.associate { it.precinct to it.county }
        val countySamples = countyPrecincts.associate { it.county to mutableMapOf<String, PrecinctSamples>() }

        // fake: reading the mvrs instead of the cvrs
        val publisher = Publisher("$topDir/audit")
        val sampledMvrs = readAuditableCardCsvFile(publisher.sampleMvrsFile(1))
        println("number of samples = ${sampledMvrs.size}")

        sampledMvrs.forEach{ mvr ->
            val precinct = mvr.location.split("-").first()
            val county = precinctMap[precinct] ?: error("no county for precinct $precinct")
            val countySampleMap = countySamples[county]!!
            val precinctSamples = countySampleMap.getOrPut(precinct) { PrecinctSamples(precinct) }
            precinctSamples.sampleIds.add(mvr.location)
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