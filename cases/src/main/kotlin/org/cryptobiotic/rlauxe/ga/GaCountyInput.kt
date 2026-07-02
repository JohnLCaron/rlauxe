package org.cryptobiotic.rlauxe.ga

import java.io.BufferedReader
import java.io.File
import kotlin.collections.forEach
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.text.split

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

fun readCountyManifest(filename: String): List<CountyBatch> {
    val reader: BufferedReader = File(filename).bufferedReader()
    reader.readLine() // skip header line

    val countyBatches = mutableListOf<CountyBatch>()
    while (true) {
        val line = reader.readLine()
        if (line == null) break

        val tokens = line.split(",") // TODO look for quote, or use commons.csv ??
        val type = tokens[0].trim()
        val name = tokens[1].trim()
        val nballots = tokens[2].trim().toInt()
        countyBatches.add( CountyBatch(type, name, nballots))
    }
    reader.close()

    return countyBatches
}

// class Contest(val name: String, val candidates: MutableList<Candidate> = mutableListOf<Candidate>())

fun readCandidateTotals(filename: String, batches: List<CountyBatch>) {
    val candidates = mutableListOf<Candidate>()

    val reader: BufferedReader = File(filename).bufferedReader()

    // read the header
    val headerLine = reader.readLine()
    val tokens = headerLine.split(",")
    // println(headerLine)
    var idx = 1
    while (idx < tokens.size) {
        val contestCandidateHead = cleanup(tokens[idx])
        val split = contestCandidateHead.lastIndexOf("-")
        val contestName = contestCandidateHead.take(split-1).trim()
        // val contest = contests.getOrPut(contestName) { Contest(contestName) }

        val candName = contestCandidateHead.substring(split + 1).trim()
        candidates.add(Candidate(contestName, candName))
        idx++
    }
    // candidates.forEach { println(it) }

    val batchMap = batches.associateBy { it.name }

    // read the body
    while (true) {
        val line = reader.readLine()
        if (line == null) break

        val canditer = candidates.iterator()
        val tokens = line.split(",")
        val batchName = tokens[0].trim()
        val batch = batchMap[batchName]
        if (batch == null) {
            println("  cant find batch name '$batchName'")
        } else {
            var idx = 1
            while (idx < tokens.size) {
                val voteCount = tokens[idx].trim().toInt()
                batch.addCandidateCount(canditer.next(), voteCount)
                idx++
            }
        }
    }
    reader.close()
}

fun cleanup(s: String): String {
    val updated = s.replace("\"\"", "'")
    return updated.replace("\"", "").trim()
}

fun readGaCountyInputCsv(topdir: String): Pair<List<GaContest>, List<GaCounty>> {
    val contests = mutableMapOf<String, GaContest>()
    val counties = mutableListOf<GaCounty>()

    val candidates  = mutableSetOf<String>()

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