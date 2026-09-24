package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.strata.Strata
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.tabulateAuditableCards
import java.io.BufferedReader
import java.io.File
import kotlin.collections.forEach
import kotlin.text.split

private val logger = KotlinLogging.logger("CountyAuditRecord")

// CountyAudit assume existence of countyDataFile and countyContestDataFile. does not use nested county directories (yet)
// Used by Corla
class CountyAuditRecord(
    topdir: String,
    config: Config,
    contests: List<ContestWithAssertions>,
    rounds: List<AuditRound>,
    nmvrs: Int, // number of mvrs already sampled
    val countyData: List<Strata>,
    val countyContestData: List<CountyContestData>, // used by viewer
): AuditRecord(topdir, config, contests, rounds, nmvrs)  {

    private val styles by lazy { readCardStyles() ?: readCardPools() } // styles are preferred

    // for viewer
    // TODO assumes that we can see what county an mvr is from its styleName
    fun countMvrsByCounty(): Map<String, Strata> {
        if (rounds.isEmpty()) return emptyMap()
        val lastRound = rounds.last() // TODO last round that has results

        val mvrCount = mutableMapOf<String, Int>()
        val mvrs = publisher.sampleMvrsFile(lastRound.roundIdx)
        val mvrCardIter = readCardsCsvIterator(mvrs, styles=styles)
        var count = 0
        mvrCardIter.forEach { mvr ->
            val location = mvr.location()
            var countyName = location
            if (location.indexOf(":") > 0) countyName = location.substring(0, location.indexOf(":"))
                else if (location.indexOf("-") > 0) countyName = location.substring(0, location.indexOf("-"))
            val accum = mvrCount.getOrPut(countyName) { 0 }
            mvrCount[countyName] = accum + 1
            count++
        }
        val countyData = mvrCount.mapValues {
            Strata(it.key, it.value, 0) // hijack CountyData
        }
        logger.info{ "countMvrsByCounty mvrs=$count sumCounties = ${ countyData.values.sumOf { it.nmvrs } }"}

        return countyData.toSortedMap()
    }

    // for viewer
    fun readCountyMvrsAndTabulate(countyName: String) : Map<Int, ContestTabulation> {
        val countyMvrFile = "${publisher.unsortedMvrsDirectory()}/$countyName.csv"
        logger.debug { "readCountyMvrsAndTabulate on $countyMvrFile (exists=${exists(countyMvrFile)}"}
        if (!exists(countyMvrFile)) return emptyMap()
        val mvrs = readCardsCsvIterator(countyMvrFile, styles = styles)
        val infos = contests.associate{ it.id to it.contest.info() }
        val tabs =  tabulateAuditableCards(mvrs, infos)
        logger.debug { "got ${tabs.size} tabulations"}
        return tabs
    }

    // for viewer
    fun readCountyCvrsAndTabulate(countyName: String) : Map<Int, ContestTabulation> {
        val countyCvrFile = "${publisher.unsortedCountyCvrDirectory()}/$countyName.csv"
        logger.debug { "readCountyCvrsAndTabulate on $countyCvrFile (exists=${exists(countyCvrFile)}"}
        if (!exists(countyCvrFile)) return emptyMap()

        val mvrs = readCardsCsvIterator(countyCvrFile, styles = styles)
        val infos = contests.associate{ it.id to it.contest.info() }
        val tabs =  tabulateAuditableCards(mvrs, infos)
        logger.debug { "got ${tabs.size} tabulations"}
        return tabs
    }

    fun readCountyCvrs(countyName: String, limit: Int? = null) : MutableList<AuditableCard> {
        val countyCvrFile = "${publisher.unsortedCountyCvrDirectory()}/$countyName.csv"
        logger.debug { "readCountyCvrs on $countyCvrFile (exists=${exists(countyCvrFile)} limit = $limit"}
        if (!exists(countyCvrFile)) return mutableListOf()

        var useLimit = limit ?: Int.MAX_VALUE
        val result = mutableListOf<AuditableCard>()
        var count = 0
        readCardsCsvIterator(countyCvrFile, styles = styles).use { iter ->
            while (iter.hasNext() && count < useLimit) {
                result.add(iter.next())
                count++
            }
        }
        return result
    }

    companion object {
        private val logger = KotlinLogging.logger("CountyAudit")
        val countyDataFile = "countyData.csv"
        val countyContestDataFile = "countyContestData.csv"

        // check CountyComposite exists
        fun checkExists(topdir: String?): Boolean {
            if (topdir == null) return false
            if (!exists("$topdir/$countyDataFile")) return false
            if (!exists("$topdir/$countyContestDataFile")) return false
            val publisher = Publisher(topdir)
            return (exists(publisher.electionInfoFile()) &&
                    exists(publisher.auditCreationConfigFile()) &&
                    exists(publisher.auditRoundProtoFile()) &&
                    exists(publisher.contestsFile()))
        }

        // used by viewer
        fun readFrom(topdir: String): CountyAuditRecord? {
            val auditResult = AuditRecord.readWithResult(topdir)
            val auditRecord = if (auditResult.isOk) auditResult.unwrap() else {
                logger.warn { auditResult.unwrapError() }
                return null
            }

            val countyData = readCountyData("$topdir/$countyDataFile")
            val countyContestData = readCountyContestData("$topdir/$countyContestDataFile")

            return CountyAuditRecord(auditRecord.topdir, auditRecord.config, auditRecord.contests, auditRecord.rounds,
                auditRecord.nmvrs, countyData, countyContestData)
        }
    }
}

// data class CountyData(val countyName: String, val nmvrs: Int, val npop: Int)

fun readCountyData(filename: String): List<Strata> {
    val reader: BufferedReader = File(filename).bufferedReader()
    reader.readLine() // skip header line

    val countyData = mutableListOf<Strata>()
    while (true) {
        val line = reader.readLine()
        if (line == null) break

        val tokens = line.split(",")
        val countyName = tokens[0]
        val nmvrs = tokens[1].trim().toInt()
        val npop = tokens[2].trim().toInt()
        countyData.add( Strata(countyName, nmvrs, npop))
    }
    reader.close()

    return countyData
}

data class CountyContestData(val countyName: String, val contestName: String, val id: Int, val voteDiff: Int, val votes: Map<Int, Int>)

fun readCountyContestData(filename: String): List<CountyContestData> {
    try {
        val reader: BufferedReader = File(filename).bufferedReader()
        reader.readLine() // skip header line

        val countyData = mutableListOf<CountyContestData>()
        while (true) {
            val line = reader.readLine()
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
            countyData.add(CountyContestData(countyName, contestName, id, voteDiff, votes))
        }
        reader.close()
        return countyData
    } catch (e: Exception) {
        logger.error { e.message }
        return emptyList()
    }
}