package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.ErrorMessages
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.isDirectory

// interface AuditRecordIF {
//    val location: String
//    val electionInfo: ElectionInfo
//    val config: Config
//    val contests: List<ContestWithAssertions>
//    val rounds: List<AuditRoundIF>
//
//    fun readSortedManifest(): CardManifest
//    fun readSortedManifest(batches: List<StyleIF>?): CardManifest
//    fun readOneShotMvrs(): Map<Int, Int>
//    fun readCardStyles(): List<StyleIF>?
//}
// class AuditRecord(
//    override val location: String,
//    override val electionInfo: ElectionInfo,
//    val auditCreationConfig: AuditCreationConfig,
//    val auditRoundConfig: AuditRoundConfig,
//    override val contests: List<ContestWithAssertions>,
//    override val rounds: List<AuditRound>,
//    val nmvrs: Int // number of mvrs already sampled
//)

class CountyComposite(
    location: String,
    config: Config,
    contests: List<ContestWithAssertions>,
    rounds: List<AuditRound>,
    nmvrs: Int,
    override val componentRecords: List<AuditRecord>,
): AuditRecord(location, config, contests, rounds, nmvrs), CompositeRecordIF  {

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

    companion object {
        private val logger = KotlinLogging.logger("CountyComposite")

        fun fromStateAndCounties(stateRecord: AuditRecord, countyRecords: List<AuditRecord>): CountyComposite {
            return CountyComposite(stateRecord.location, stateRecord.config, stateRecord.contests, stateRecord.rounds, stateRecord.nmvrs, countyRecords)
        }

        // check CountyComposite exists
        fun checkExists(location: String?): Boolean {
            if (location == null) return false
            if (!CompositeRecord.checkExists(location)) return false
            return checkAuditRecordExists("$location/audit")
        }

        // used by viewer
        fun readFrom(location: String): CountyComposite? {
            val stateLevelResult = AuditRecord.readWithResult("$location/audit")
            val stateLevel = if (stateLevelResult.isOk) stateLevelResult.unwrap() else {
                logger.warn { stateLevelResult.unwrapError() }
                return null
            }

            val counties = CompositeRecord.readFrom(location)
            if (counties == null) return null

            return fromStateAndCounties(stateLevel, counties.componentRecords)
        }
    }
}
