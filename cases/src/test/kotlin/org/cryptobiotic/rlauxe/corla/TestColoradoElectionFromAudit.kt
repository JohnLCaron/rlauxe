package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.csv.IteratorCvrsCsvStream
import org.cryptobiotic.rlauxe.persist.csv.readCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.json.Publisher
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
        val auditDir = "/home/stormy/temp/corla/election"
        val tabulateFile = "src/test/data/2024audit/tabulate.csv"
        val contestRoundFile = "src/test/data/2024audit/round1/contest.csv"
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val precinctFile = "src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"

        coloradoElectionFromAudit(auditDir, detailXmlFile, contestRoundFile, precinctFile)

        // out of memory sort by sampleNum()
        sortMergeCvrs(auditDir, null)
    }

    //// zip cvrs directory to cvrs.zip

    // out of memory sort by sampleNum()
    @Test
    fun testSortMergeCvrs() {
        val auditDir = "/home/stormy/temp/corla/election"
        // sortCvrs(auditDir, "$auditDir/cvrs.zip", "$auditDir/sortChunks")
        sortCvrs(auditDir, null, "$auditDir/sortChunks")
        mergeCvrs(auditDir, "$auditDir/sortChunks")
    }

    //// zip sortedCvs.csv directory to sortedCvs.zip

    @Test
    fun testPrecintReader() {
        val stopwatch = Stopwatch()
        val auditDir = "/home/stormy/temp/corla/election"
        val precinctReader = PrecinctReader("$auditDir/cvrs/")
        var count = 0
        while (precinctReader.hasNext()) {
            count++
            precinctReader.next()
        }
        println("count = $count took = $stopwatch")
    }

    ///////////////////////////////////////////////////////////////////////////
    // looking for where we lose contests > 260
    @Test
    fun testMergedCvrs() {
        val auditDir = "/home/stormy/temp/corla/election"
        val cvrZipFile = "$auditDir/sortedCvrs.zip"

        val reader = ZipReader(cvrZipFile)
        val input = reader.inputStream("sortedCvrs.csv")
        val iter = IteratorCvrsCsvStream(input)
        var lastCvr : CvrUnderAudit? = null
        var count = 0

        val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
        while (iter.hasNext()) {
            val cvr = iter.next()
            cvr.votes.forEach { (key, value) ->
                haveSampleSize[key] = haveSampleSize[key]?.plus(1) ?: 1
            }

            if (lastCvr != null) {
                require(cvr.sampleNum > lastCvr.sampleNum)
            }
            lastCvr = cvr
            count++
            if (count % 100000 == 0) println("$count ")
        }

        println("count = $count")
        haveSampleSize.toSortedMap().forEach {
            println("${it.key} : ${it.value}")
        }
    }

    @Test
    fun testCvrsSortedZip() {
        val auditDir = "/home/stormy/temp/corla/election"
        val cvrZipFile = "$auditDir/sortedCvrs.zip"

        val reader = ZipReader(cvrZipFile)
        val input = reader.inputStream("sortedCvrs.csv")
        val iter = IteratorCvrsCsvStream(input)
        var lastCvr : CvrUnderAudit? = null
        var count = 0

        val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
        while (iter.hasNext()) {
            val cvr = iter.next()
            cvr.votes.forEach { (key, value) ->
                haveSampleSize[key] = haveSampleSize[key]?.plus(1) ?: 1
            }

            if (lastCvr != null) {
                require(cvr.sampleNum > lastCvr.sampleNum)
            }
            lastCvr = cvr
            count++
            if (count % 100000 == 0) println("$count ")
        }

        println("count = $count")
        haveSampleSize.toSortedMap().forEach {
            println("${it.key} : ${it.value}")
        }
    }

    @Test
    fun testCvrsZip() {
        val auditDir = "/home/stormy/temp/corla/election"
        val cvrZipFile = "$auditDir/cvrs.zip"
        val process = TreeReaderZip(cvrZipFile)
        process.processCvrs()
    }

    @Test
    fun testCvrsTree() {
        val cvrsDir = "/home/stormy/temp/corla/election/cvrs"
        val process = TreeReader(cvrsDir)
        process.processCvrs()
    }

    @Test
    fun makePrecinctTree() {
        val cvrsDir = "/home/stormy/temp/corla/election/cvrs"
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

    @Test
    fun makeCountySampleLists() {
        val countyPrecincts = mutableListOf<CountyAndPrecinct>()
        val auditDir = "/home/stormy/temp/corla/election"
        val tour = TreeReaderTour("$auditDir/cvrs") { path -> countyPrecincts.add(precinctLine(path)) }
        tour.tourFiles()

        val precinctMap = countyPrecincts.associate { it.precinct to it.county }
        val countySamples = countyPrecincts.associate { it.county to mutableMapOf<String, PrecinctSamples>() }

        // fake: reading the mvrs instead of the cvrs
        val publisher = Publisher(auditDir)
        val sampledMvrs = readCvrsCsvFile(publisher.sampleMvrsFile(1))
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