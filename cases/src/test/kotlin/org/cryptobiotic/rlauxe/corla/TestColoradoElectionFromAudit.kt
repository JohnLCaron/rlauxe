package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.util.*
import java.nio.file.Path
import kotlin.test.Test

class TestColoradoElectionFromAudit {

    @Test
    fun testReadColoradoElectionDetail() {
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val electionResultXml: ElectionDetailXml = readColoradoElectionDetail(detailXmlFile)
    }

    // use detailXmlFile for contests and votes, and round1/contests.csv (Nc)
    // and precinctFile for cvrs
    @Test
    fun createElectionFromAudit() {
        val auditDir = "/home/stormy/temp/cases/corla"
        val tabulateFile = "src/test/data/2024audit/tabulate.csv"
        val contestRoundFile = "src/test/data/2024audit/round1/contest.csv"
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val precinctFile = "src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"

        coloradoElectionFromAudit(auditDir, detailXmlFile, contestRoundFile, precinctFile)

        // out of memory sort by sampleNum()
        sortCardsInDirectoryTree(auditDir, "$auditDir/cards/", "$auditDir/sortChunks")
        mergeCards(auditDir, "$auditDir/sortChunks")

        // other tests depend on this one
        testTreeReader()
        makeCountySampleLists()
    }

    //// zip cvrs directory to cvrs.zip

    // out of memory sort by sampleNum()
    // @Test
    fun testSortMergeCvrs() {
        val auditDir = "/home/stormy/temp/cases/corla"
        // out of memory sort by sampleNum()
        sortCardsInDirectoryTree(auditDir, "$auditDir/cards/", "$auditDir/sortChunks")
        mergeCards(auditDir, "$auditDir/sortChunks")
    }

    // class TreeReaderIterator <T> (
    //    topDir: String,
    //    val fileFilter: (Path) -> Boolean,
    //    val reader: (Path) -> Iterator<T>
    //)
    // @Test
    fun testTreeReader() {
        val stopwatch = Stopwatch()
        val auditDir = "/home/stormy/temp/cases/corla"
        val precinctReader = TreeReaderIterator(
            "$auditDir/cards/",
            fileFilter = { true },
            reader = { path -> readCardsCsvIterator(path.toString()) }
        )

        var count = 0
        while (precinctReader.hasNext()) {
            count++
            precinctReader.next()
        }
        println("count = $count took = $stopwatch")
    }

    @Test
    fun makePrecinctTree() {
        val cvrsDir = "/home/stormy/temp/cases/corla/cards"
        val tour = TreeReaderTour(cvrsDir) { path -> precinctLine(path) }
        println("county, precinct")
        tour.tourFiles()
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
    fun makeCountySampleLists() {
        val countyPrecincts = mutableListOf<CountyAndPrecinct>()

        val auditDir = "/home/stormy/temp/cases/corla"
        val precinctReader = TreeReaderIterator(
            "$auditDir/cards/",
            fileFilter = { true },
            reader = { path -> readCardsCsvIterator(path.toString()) }
        )

        val tour = TreeReaderTour("$auditDir/cards") { path -> countyPrecincts.add(precinctLine(path)) }
        tour.tourFiles()

        val precinctMap = countyPrecincts.associate { it.precinct to it.county }
        val countySamples = countyPrecincts.associate { it.county to mutableMapOf<String, PrecinctSamples>() }

        // fake: reading the mvrs instead of the cvrs
        val publisher = Publisher(auditDir)
        val sampledMvrs = readAuditableCardCsvFile(publisher.sampleMvrsFile(1))
        println("number of samples = ${sampledMvrs.size}")

        sampledMvrs.forEach{ mvr ->
            val precinct = mvr.desc.split("-").first()
            val county = precinctMap[precinct] ?: error("no county for precinct $precinct")
            val countySampleMap = countySamples[county]!!
            val precinctSamples = countySampleMap.getOrPut(precinct) { PrecinctSamples(precinct) }
            precinctSamples.sampleIds.add(mvr.desc)
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