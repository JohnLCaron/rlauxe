package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.core.*

// not used right now, but Corla may use it in the future
// the idea is to have County Audits as subdirectories, to make a CompositeRecord
// but corla/consistent audits the entire state, not by county.
// corla/uniform doesnt run an audit, just shows the results of the colorado-rla audit.
// so may not be needed

class CountyComposite(
    location: String,
    config: Config,
    contests: List<ContestWithAssertions>,
    rounds: List<AuditRound>,
    nmvrs: Int,
    override val componentRecords: List<AuditRecord>, // optional
    val countyData: List<CountyData>,
): AuditRecord(location, config, contests, rounds, nmvrs), CompositeRecordIF  {

    override fun auditdir() = "$location/audit"

    override fun findComponentWithName(name: String): AuditRecord? {
        return componentRecords.find {
            name == it.name()
        }
    }

    override fun toString() = buildString {
        append("CountyComposite location='$location'\n$config")
        appendLine("components")
        componentRecords.forEach{ appendLine("  ${it.name()} at ${it.location}")}
        appendLine("contests")
        contests.forEach{ appendLine("  $it")}
        appendLine("rounds")
        rounds.forEach{ appendLine(it)}
    }

    fun countyData(): Map<String, CountyData> {
        return countyData.associateBy { it. countyName }
    }

    companion object {
        private val logger = KotlinLogging.logger("CountyComposite")
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
                // exists(publisher.cardManifestFile()) &&
                exists(publisher.contestsFile()))
        }

        // used by viewer
        fun readFrom(location: String): CountyComposite? {
            val stateLevelResult = AuditRecord.readWithResult("$location/audit")
            val stateLevel = if (stateLevelResult.isOk) stateLevelResult.unwrap() else {
                logger.warn { stateLevelResult.unwrapError() }
                return null
            }

            val components = if (CompositeAuditRecord.checkExists(location)) {
                CompositeAuditRecord.readFrom(location)!!.componentRecords
            } else emptyList()

            val countyData = readCountyData("$location/$countyDataFile")

            return fromStateAndCounties(stateLevel, components, countyData)
        }
    }
}
