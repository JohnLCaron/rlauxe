package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import java.io.BufferedReader
import java.io.File
import kotlin.text.split

class CountyAudit(
        location: String,
        config: Config,
        contests: List<ContestWithAssertions>,
        rounds: List<AuditRound>,
        nmvrs: Int, // number of mvrs already sampled
        val countyData: List<CountyData>,
): AuditRecord(location, config, contests, rounds, nmvrs)  {

    fun countyDataMap(): Map<String, CountyData> {
        return countyData.associateBy { it. countyName }
    }

    companion object {
        private val logger = KotlinLogging.logger("CountyAudit")
        val countyDataFile = "countyData.csv"

        fun fromStateAndCounties(stateRecord: AuditRecord, countyRecords: List<AuditRecord>, countyData: List<CountyData>): CountyComposite {
            return CountyComposite(stateRecord.location, stateRecord.config, stateRecord.contests, stateRecord.rounds,
                stateRecord.nmvrs, countyRecords, countyData)
        }

        // check CountyComposite exists
        fun checkExists(location: String?): Boolean {
            if (location == null) return false
            if (!exists("$location/$countyDataFile")) return false
            val publisher = Publisher("$location/audit")
            return (exists(publisher.electionInfoFile()) &&
                    exists(publisher.auditCreationConfigFile()) &&
                    exists(publisher.auditRoundProtoFile()) &&
                    exists(publisher.contestsFile()))
        }

        // used by viewer
        fun readFrom(location: String): CountyAudit? {
            val stateLevelResult = AuditRecord.readWithResult("$location/audit")
            val stateRecord = if (stateLevelResult.isOk) stateLevelResult.unwrap() else {
                logger.warn { stateLevelResult.unwrapError() }
                return null
            }

            val countyData = readCountyData("$location/$countyDataFile")

            return CountyAudit(stateRecord.location, stateRecord.config, stateRecord.contests, stateRecord.rounds,
                stateRecord.nmvrs, countyData)
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