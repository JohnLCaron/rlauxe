package org.cryptobiotic.rlauxe.corlaInput

import com.jsoizo.kotlincsv.csvReader
import com.jsoizo.kotlincsv.reader.read
import com.jsoizo.kotlincsv.reader.withHeader
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.collections.forEach
import kotlin.text.split
import kotlin.text.toInt

private val logger = KotlinLogging.logger("ColoradoInput")

interface ColoradoInput {
    fun name(): String
    fun ncontests(): Int
    fun nuncontested(): Int
}

//////////////////////////////////////////////////////////////////////////////
// see WriteCountyInputData

data class CountyInputData(val county: String,
                           val manifestCount: Int,
                           val unredactedCvrs: Int,
                           val redactedCvrs: Int,
                           val cvrInManifest:Int,
                           val cvrNoManifest:Int,
                           val manifestNoCvr: Int,
                           val minCards: Int,
                           val countBlankPrecincts: Int,
                           val ballotStylesUnique: Boolean,
                           val ballotStyleMin: Int,
                           val precinctStyleMin: Int,
)  {
    companion object {
        fun readFromResource(resource: String): List<CountyInputData> {
            val inputStream = object {}.javaClass.getResourceAsStream(resource)
            val result= mutableListOf<CountyInputData>()
            csvReader().read(inputStream) { rows ->
                rows.withHeader().forEach { untrimmedRow ->
                    val row = untrimmedRow.mapKeys { (key, _) -> key.trim() }
                    result.add(CountyInputData(
                        county = row["county"]!!.trim(),
                        manifestCount = parseColAsInt(row["manifestCount"]!!),
                        unredactedCvrs = parseColAsInt(row["unredactedCvrs"]!!),
                        redactedCvrs = parseColAsInt(row["redactedCvrs"]!!),
                        cvrInManifest = parseColAsInt(row["cvrInManifest"]!!),
                        cvrNoManifest = parseColAsInt(row["cvrNoManifest"]!!),
                        manifestNoCvr = parseColAsInt(row["manifestNoCvr"]!!),
                        minCards = parseColAsInt(row["minCards"]!!),
                        countBlankPrecincts = parseColAsInt(row["countBlankPrecincts"]!!),
                        ballotStylesUnique = row["ballotStylesUnique"]!!.trim() == "true",
                        ballotStyleMin = parseColAsInt(row["ballotStyleMin"]!!),
                        precinctStyleMin = parseColAsInt(row["precinctStyleMin"]!!),
                    ))
                }
            }
            return result
        }
    }
}

fun parseColAsInt(col:String) = col.trim().toInt()


fun writeCountyInputData(outputFilename: String, data: List<CountyInputData>) {
    // misc data by county
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("county, manifestCount, unredactedCvrs, redactedCvrs, cvrInManifest, cvrNoManifest, manifestNoCvr, " +
            "minCards, countBlankPrecincts, ballotStylesUnique, ballotStyleMin, precinctStyleMin\n")
    data.sortedBy { it.county }.forEach {
        writer.write(
            "${sfn(it.county, 20)}, ${nfn(it.manifestCount, 7)}, ${nfn(it.unredactedCvrs, 7)}, ${nfn(it.redactedCvrs, 7)}, ${nfn(it.cvrInManifest, 7)}, " +
                    "${nfn(it.cvrNoManifest, 7)}, ${nfn(it.manifestNoCvr, 5)}, ${nfn(it.minCards, 5)}, " +
            "${it.countBlankPrecincts}, ${it.ballotStylesUnique}, ${it.ballotStyleMin}, ${it.precinctStyleMin}\n"
        )
    }
    writer.close()
    println("wrote ${data.size} countyData to $outputFilename")
}

/////////////////////////////////////////////////////////////////////////////////////////

// county, pythonRedactions, rlauxeRedactions
data class RedactionCount(val pythonNredact: Int, val rlauxeNredact: Int, val fail: Boolean) {
    companion object {
        fun readFromResource(resource: String): Map<String, RedactionCount> {
            val inputStream = object {}.javaClass.getResourceAsStream(resource)
            val result= mutableMapOf<String, RedactionCount>()
            csvReader().read(inputStream) { rows ->
                rows.withHeader().forEach { untrimmedRow ->
                    val row = untrimmedRow.mapKeys { (key, _) -> key.trim() }
                    val county = row["county"]!!
                    result[county] = RedactionCount(
                        pythonNredact = parseColAsInt(row["pythonRedactions"]!!),
                        rlauxeNredact = parseColAsInt(row["rlauxeRedactions"]!!),
                        fail = false,
                    )
                }
            }
            return result
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////////////

fun writeCountyData(topdir: String, strataInfo: List<StrataInfo>) {
    // misc data by county
    val outputFilename = "$topdir/${CountyAuditRecord.countyDataFile}"
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("county,   nmvrs, ballotCardCount\n")
    strataInfo.sortedBy { it.strataName }.forEach {
        writer.write("${it.strataName}, ${nfn(it.nmvrs, 5)}, ${nfn(it.ballotCardCount, 5)}\n")
    }
    writer.close()
    logger.info{"wrote ${strataInfo.size} countyData to $outputFilename"}
}

// data class CountyContestTab(val countyName: String) {
//    val contests = mutableMapOf<String, ContestTab>()
// data class ContestTab(val contestName: String) {
//    val choices = mutableMapOf<String, Int>()

fun writeCountyContestData(topdir: String, contestMap: Map<String, ContestWithAssertions>, statewideInput: StatewideInput) {
    val countyTabs = statewideInput.countyTabsAllContests()
    // misc data by county
    val outputFilename = "$topdir/${CountyAuditRecord.countyContestDataFile}"
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("county, contest, id, voteDiff, votes,\n")

    var count = 0
    countyTabs.values.forEach { countyTab ->
        countyTab.contests.values.forEach { ccv: CountyContestVotes ->
            val contestUA = contestMap[ccv.contestName]
            val canonicalContest = statewideInput.canonicalContests()[ccv.contestName]
            if (contestUA != null && canonicalContest != null) {
                val contest = contestUA.contest
                val info = contest.info()
                val candidateVotes: Map<String, Int> = ccv.canonicalChoices(canonicalContest)
                val votesByCandId = candidateVotes.mapKeys { info.candidateNames[it.key]!! }

                // calculate the vote difference for the minimum assorter
                val minAssertion = contestUA.minAssertion()
                val voteDiff = if (minAssertion == null) 0
                else minAssertion.assorter.calcMarginFromRegVotes(votesByCandId, 1).toInt()

                writer.write("${countyTab.countyName}, ${ccv.contestName}, ${info.id}, $voteDiff, ")
                votesByCandId.forEach { (id, vote) ->
                    writer.write("$id:$vote, ")
                }
                writer.write("\n")
                count++
            }
        }
    }
    writer.close()
    logger.info{"wrote ${count}  countyContestData to $outputFilename"}
}