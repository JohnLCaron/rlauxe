package org.cryptobiotic.rlauxe.ga

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.collections.forEach
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

data class GaCounty(val countyName: String, val batches: List<CountyBatch>) {
    fun ncards() = batches.sumOf { it.nballots }
}
data class GaContest(val contestName: String) {
    val candCount = mutableMapOf<Candidate, Int>()
    fun addCandidateCount(cand: Candidate, voteCount: Int) {
        val accum = candCount.getOrPut(cand) { 0 }
        candCount[cand] = accum + voteCount
    }

    override fun toString() = buildString {
        appendLine("Contest '$contestName'")
        candCount.forEach { appendLine("    $it") }
    }
}

data class Candidate(val contest: String, val candName: String)

data class CountyBatch(val type: String, val name: String, val nballots: Int) {
    val candCount = mutableMapOf<Candidate, Int>()
    fun addCandidateCount(cand: Candidate, voteCount: Int) {
        candCount[cand] = voteCount
    }
    override fun toString() = buildString {
        appendLine("CountyBatch(type='$type', name='$name', nballots=$nballots")
        candCount.forEach { appendLine("  $it") }
    }
}

///////////////////////////////////////////////////////////////////////////////////////////


fun readCountyManifest(filename: String): List<CountyBatch> {
    val isBurke = filename.contains("Burke")
    val parser = CSVParser.parse(File(filename), StandardCharsets.UTF_8, CSVFormat.DEFAULT)
    val records = parser.iterator()

    records.next() // skip header line

    val countyBatches = mutableListOf<CountyBatch>()
    while (records.hasNext()) {
        val line = records.next()
        if (line.all { it.isEmpty() }) continue
        try {
            val countyBatch = if (isBurke) {
                val name = cleanup(line.get(0))
                val nballots = line.get(1).trim().toInt()
                val type = line.get(2).trim()
                CountyBatch(type, name, nballots)

            } else {
                val type = line.get(0).trim()
                val name = cleanup(line.get(1))
                val nballots = line.get(2).trim().toInt()
                CountyBatch(type, name, nballots)
            }
            countyBatches.add(countyBatch)
        } catch (e: Exception) {
            println("*** ${e.message} $line")
        }
    }
    return countyBatches
}


fun readCandidateTotals(filename: String, batches: List<CountyBatch>) {
    val parser = CSVParser.parse(File(filename), StandardCharsets.UTF_8, CSVFormat.DEFAULT)
    val records = parser.iterator()

    // read the header
    val headerLine = records.next()

    val candidates = mutableListOf<Candidate>()
    var idx = 1
    while (idx < headerLine.size()) {
        if (headerLine[idx].isNotEmpty()) {   // trailing comma
            val contestCandidateHead = cleanup(headerLine[idx])
            val split = contestCandidateHead.lastIndexOf("-")
            if (split < 0)
                println("WRONG $idx $split")
            val contestName = contestCandidateHead.take(split - 1).trim()
            val candName = contestCandidateHead.substring(split + 1).trim()
            candidates.add(Candidate(contestName, candName))
        }
        idx++
    }

    val batchMap = batches.associateBy { it.name }

    // read the body
    while (records.hasNext()) {
        val line = records.next()
        if (line.all { it.isEmpty() }) continue

        try {
            val canditer = candidates.iterator()
            val batchName = cleanup(line[0])
            val batch = batchMap[batchName]
            if (batch == null) {
                println("  cant find batch name '$batchName'")
            } else {
                var idx = 1
                while (idx < line.size()) {
                    if (line[idx].isNotEmpty()) {   // trailing comma
                        val voteCount = line[idx].trim().toInt()
                        batch.addCandidateCount(canditer.next(), voteCount)
                    }
                    idx++
                }
            }
        } catch (e: Exception) {
            println("*** ${e.message}")
        }
    }
}

fun cleanup(s: String): String {
    val updated = s.replace("\"\"", "'")
    val updated2 = updated.replace(",", "")
    return updated2.replace("\"", "").trim()
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////

// read manifests and candidte_totals
fun readGaCountyInputCsv(topdir: String): Pair<List<GaContest>, List<GaCounty>> {
    val contests = mutableMapOf<String, GaContest>()
    val counties = mutableListOf<GaCounty>()

    var count = 0
    val manifests = "$topdir/manifests"
    Path(manifests).listDirectoryEntries().sorted().forEach { countyPath ->
        val countyName = countyPath.name
        // if (countyName !in listOf("BURKE", "CHATHAM", "FULTON")) {
        try {
            println("Read County $countyName")
            val manifests = "$topdir/manifests/$countyName"
            val manifestData = Path(manifests).listDirectoryEntries().first()
            val batches = readCountyManifest(manifestData.toString())

            val candidate_totals = "$topdir/candidate_totals/$countyName"
            val candData = Path(candidate_totals).listDirectoryEntries().first()
            readCandidateTotals(candData.toString(), batches)

            batches.forEach { batch ->
                batch.candCount.forEach { (candidate, votes) ->
                    val contest = contests.getOrPut(candidate.contest) { GaContest(candidate.contest) }
                    contest.addCandidateCount(candidate, votes)
                }
            }
            counties.add(GaCounty(countyName, batches))
        } catch (e: Exception) {
            println("*** ${e.message}")
        }
        // }
        count++
    }
    println("  $count counties")
    println()
    println("Contests:")
    contests.values.forEach { println(it) }

    return Pair(contests.values.toList(), counties)
}