package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.csv.IteratorCvrsCsvStream
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test

class TestCreateElectionFromAudit {

    @Test
    fun testReadColoradoElectionDetail() {
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val electionResultXml: ElectionDetailXml = readColoradoElectionDetail(detailXmlFile)
    }

    // use tabulate.csv for contests and votes, and round1/contests.csv (Nc)
    @Test
    fun createElectionFromAudit() {
        val auditDir = "/home/stormy/temp/corla/election"
        val tabulateFile = "src/test/data/2024audit/tabulate.csv"
        val contestRoundFile = "src/test/data/2024audit/round1/contest.csv"
        val detailXmlFile = "src/test/data/2024election/detail.xml"
        val precinctFile = "src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"

        createElectionFromAudit(auditDir, detailXmlFile, contestRoundFile, precinctFile)
    }

    @Test
    fun sortCvrs() {
        val auditDir = "/home/stormy/temp/corla/electio"
        sortCvrs(auditDir, "$auditDir/cvrs.zip", "$auditDir/sortChunks")
    }

    @Test
    fun mergeCvrs() {
        val auditDir = "/home/stormy/temp/corla/election"
        mergeCvrs(auditDir, "$auditDir/sortChunks")
    }

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

}