package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIteratorM
import java.io.BufferedReader
import java.io.File
import kotlin.collections.forEach
import kotlin.text.split

// CountyAudit assume existence of countyDataFile and countyContestDataFile. does not use nested county directories (yet)
// Used by Corla
class CountyAudit(
        location: String,
        config: Config,
        contests: List<ContestWithAssertions>,
        rounds: List<AuditRound>,
        nmvrs: Int, // number of mvrs already sampled
        val countyData: List<CountyData>,
        val countyContestData: List<CountyContestData>, // used by viewer
): AuditRecord(location, config, contests, rounds, nmvrs)  {

    override fun auditdir() = "$location/audit"

    // for viewer
    // TODO assumes that we can see what county an mvr is from its styleName
    fun countMvrsByCounty(): Map<String, CountyData> {
        if (rounds.isEmpty()) return emptyMap()
        val lastRound = rounds.last() // TODO last round that has results

        val mvrCount = mutableMapOf<String, Int>()
        // styleNames should already be added
        val mvrCardIter = readCardsCsvIteratorM(publisher.sampleMvrsFile(lastRound.roundIdx), styles=null)
        var count = 0
        mvrCardIter.forEach { mvr ->
            val split = mvr.styleName.split("-",".") // county is the first token of the style name HAHAHAHAH
            val countyName = split[0]
            val accum = mvrCount.getOrPut(countyName) { 0 }
            mvrCount[countyName] = accum + 1
            count++
        }
        val countyData = mvrCount.mapValues {
            CountyData(it.key, it.value, 0) // hijack CountyData
        }
        logger.info{ "countMvrsByCounty mvrs=$count sumCounties = ${ countyData.values.sumOf { it.nmvrs } }"}

        return countyData.toSortedMap()
    }

    companion object {
        private val logger = KotlinLogging.logger("CountyAudit")
        val countyDataFile = "countyData.csv"
        val countyContestDataFile = "countyContestData.csv"

        // check CountyComposite exists
        fun checkExists(location: String?): Boolean {
            if (location == null) return false
            if (!exists("$location/$countyDataFile")) return false
            if (!exists("$location/$countyContestDataFile")) return false
            val publisher = Publisher("$location/audit")
            return (exists(publisher.electionInfoFile()) &&
                    exists(publisher.auditCreationConfigFile()) &&
                    exists(publisher.auditRoundProtoFile()) &&
                    exists(publisher.contestsFile()))
        }

        // used by viewer
        fun readFrom(location: String): CountyAudit? {
            val auditResult = AuditRecord.readWithResult("$location/audit")
            val auditRecord = if (auditResult.isOk) auditResult.unwrap() else {
                logger.warn { auditResult.unwrapError() }
                return null
            }

            val countyData = readCountyData("$location/$countyDataFile")
            val countyContestData = readCountyContestData("$location/$countyContestDataFile")

            return CountyAudit(auditRecord.location, auditRecord.config, auditRecord.contests, auditRecord.rounds,
                auditRecord.nmvrs, countyData, countyContestData)
        }
    }
}

data class CountyData(val countyName: String, val nmvrs: Int, val npop: Int)

fun readCountyData(filename: String): List<CountyData> {
    val reader: BufferedReader = File(filename).bufferedReader()
    reader.readLine() // skip header line

    val countyData = mutableListOf<CountyData>()
    while (true) {
        var line = reader.readLine()
        if (line == null) break

        val tokens = line.split(",")
        val countyName = tokens[0]
        val nmvrs = tokens[1].trim().toInt()
        val npop = tokens[2].trim().toInt()
        countyData.add( CountyData(countyName, nmvrs, npop))
    }
    reader.close()

    return countyData
}

data class CountyContestData(val countyName: String, val contestName: String, val id: Int, val voteDiff: Int, val votes: Map<Int, Int>)

fun readCountyContestData(filename: String): List<CountyContestData> {
    val reader: BufferedReader = File(filename).bufferedReader()
    reader.readLine() // skip header line

    val countyData = mutableListOf<CountyContestData>()
    while (true) {
        var line = reader.readLine()
        if (line == null) break

        val tokens = line.split(",")
        var idx = 0
        val countyName = tokens[idx++].trim()
        val contestName = tokens[idx++].trim()
        val id = tokens[idx++].trim().toInt()
        val voteDiff = tokens[idx++].trim().toInt()
        val votes = mutableMapOf<Int, Int>()
        while (idx < line.length && tokens[idx].trim().isNotEmpty()) {
            val inner = tokens[idx++].split(":")
            val id = inner[0].trim().toInt()
            val vote = inner[1].trim().toInt()
            votes[id] = vote
        }
        countyData.add( CountyContestData(countyName, contestName, id, voteDiff, votes))
    }
    reader.close()

    return countyData
}