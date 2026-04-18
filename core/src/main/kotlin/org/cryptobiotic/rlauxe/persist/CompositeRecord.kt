package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
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

data class CompositeRecord(
    override val location: String,
    override val electionInfo: ElectionInfo,
    override val config: Config,
    override val contests: List<ContestWithAssertions>,
    override val rounds: List<AuditRoundIF>,
    val componentRecords: List<AuditRecord>,
): AuditRecordIF  {

    override fun readSortedManifest(batches: List<StyleIF>?): CardManifest {
        return componentRecords.first().readSortedManifest(batches)
    }
    override fun readSortedManifest(): CardManifest {
        return componentRecords.first().readSortedManifest() // barf
    }

    override fun readOneShotMvrs() = emptyMap<Int, Int>()

    override fun readCardStyles(): List<StyleIF> {
        val allBatches = mutableListOf<StyleIF>()
        for (component in componentRecords) {
            val cbatches = component.readCardStyles()
            if (cbatches != null) allBatches.addAll(cbatches)
        }
        return allBatches
    }

   fun findComponentWithContest(wantContestName: String): AuditRecord? {
        var want: AuditRecord? = null
        for (component in componentRecords) {
            if (component.contests.find { contestUA -> contestUA.name == wantContestName } != null) {
                want = component
                break
            }
        }
        return want
    }

    override fun toString() = buildString {
        append("CompositeRecord location='$location'\n$config")
        appendLine("components")
        componentRecords.forEach{ appendLine("  ${it.location}")}
        appendLine("contests")
        contests.forEach{ appendLine("  $it")}
        appendLine("rounds")
        rounds.forEach{ appendLine(it)}
    }

    companion object {
        private val logger = KotlinLogging.logger("CompositeRecord")

        // used by viewer
        fun checkExists(location: String): Boolean {
            val path = Path(location)
            if (path.isDirectory()) {
                path.listDirectoryEntries().sorted().forEach { entry ->
                    if (entry.isDirectory()) {
                        val auditDir = "${entry.toAbsolutePath()}/audit"
                        val publisher = Publisher(auditDir)
                        if (exists(publisher.electionInfoFile()) &&
                            exists(publisher.cardManifestFile()) &&
                            exists(publisher.contestsFile())) return true
                    }
                }
            }
            return false
        }

        // used by viewer
        fun readFrom(location: String): CompositeRecord? {
            val components = mutableListOf<AuditRecord>()
            val contests = mutableListOf<ContestWithAssertions>()
            var config: Config? = null
            var electionInfo: ElectionInfo? = null

            // find all subdirectories
            var componentId = 1
            val path = Path(location)
            if (path.isDirectory()) {
                path.listDirectoryEntries().sorted().forEach { entry ->
                    if (entry.isDirectory()) {
                        val auditDir = "${entry.toAbsolutePath()}/audit"
                        if (Path(auditDir).exists()) {
                            val result: Result<AuditRecordIF, ErrorMessages> = AuditRecord.readWithResult(auditDir)
                            if (result.isErr) {
                                logger.warn {"  Error: ${result.component2()}" }
                            } else {
                                val orgRecord: AuditRecord = result.unwrap() as AuditRecord
                                components.add(orgRecord)
                                contests.addAll(orgRecord.contests)
                                if (config == null) config = orgRecord.config // TODO all configs are the same ??
                                if (electionInfo == null) electionInfo = orgRecord.electionInfo // TODO all electionInfo are the same ??
                                componentId++
                            }
                        }
                    }
                }
            }
            return if (config != null) {
                // contests.sortBy { it.name }
                val auditRounds = makeAuditRounds(components)
                CompositeRecord(location, electionInfo!!, config, contests, auditRounds, components)
            } else {
                null
            }
        }
    }
}

fun makeAuditRounds(records: List<AuditRecord>) : List<AuditRoundIF> {
    val contestsAndRounds = mutableMapOf<Int, Pair<MutableList<ContestRound>, MutableList<AuditRound>>>() // roundIdx -> list<contestRound>

    // extract the contest rounds
    records.forEach { record ->
        record.rounds.forEach { auditRound ->
            auditRound.contestRounds.forEach { contestRound ->
                val (contestList, roundList) = contestsAndRounds.getOrPut(auditRound.roundIdx) { Pair(mutableListOf(), mutableListOf()) }
                contestList.add(contestRound)
                roundList.add(auditRound)
            }
        }
    }

    val result = contestsAndRounds.map { (roundIdx, pair) ->
        ProxyAuditRound(roundIdx, pair.first, pair.second, samplePrns = emptyList())
    }
    return result
}

//interface AuditRoundIF {
//    val roundIdx: Int
//    val contestRounds: List<ContestRound>
//
//    var auditWasDone: Boolean
//    var auditIsComplete: Boolean
//    var samplePrns: List<Long> // card prns to sample for just this round (complete, not just new)
//    var nmvrs: Int      // number of mvrs in round
//    var newmvrs: Int    // number of new mvrs in round
//    var mvrsUsed: Int
//    var mvrsUnused: Int
//
//    fun show(): String
//    fun createNextRound(): AuditRound
//}

// data class AuditRound(
//    override val roundIdx: Int,
//    override val contestRounds: List<ContestRound>,
//
//    override var auditWasDone: Boolean = false,
//    override var auditIsComplete: Boolean = false,
//    override var samplePrns: List<Long>, // card prns to sample for this round (complete, not just new).
//                                         // duplicates samplePrnsFile, so no need to serialze
//    override var nmvrs: Int = 0,    // mvrs in the round
//    override var newmvrs: Int = 0,  // new mvrs in the round
//    override var mvrsUnused: Int = 0,
//    override var mvrsUsed: Int = 0,
//) : AuditRoundIF

data class ProxyAuditRound(
    override val roundIdx: Int,
    override val contestRounds: List<ContestRound>,
    val auditRounds: List<AuditRound>,

    override var auditWasDone: Boolean = false,
    override var auditIsComplete: Boolean = false,

    override var samplePrns: List<Long> = emptyList(), // card prns to sample for this round (complete, not just new)
    override var nmvrs: Int = 0,
    override var newmvrs: Int = 0,
    override var mvrsUsed: Int = 0,
    override var mvrsUnused: Int = 0,
) : AuditRoundIF {
    override var auditorWantNewMvrs: Int? = null

    init {
        auditWasDone = auditRounds.all { it.auditWasDone }
        auditIsComplete = auditRounds.all { it.auditIsComplete }
        nmvrs = contestRounds.map { it.estMvrs }.sum()
        newmvrs = contestRounds.map { it.estNewMvrs }.sum()
        mvrsUsed = auditRounds.map { it.mvrsUsed }.sum()
        mvrsUnused = auditRounds.map { it.mvrsUnused }.sum()
    }

    // do it on all the rounds ??
    override fun createNextRound(): AuditRound {
        throw UnsupportedOperationException("CompositeAuditRound.createNextRound()")
    }

    //// called from viewer
    override fun show(): String {
        return toString()
    }

    override fun toString() = buildString {
        appendLine("  CompositeAuditRound roundIdx=${roundIdx}")
        appendLine("  contestRounds")
        contestRounds.forEach{
            appendLine("    ${it.name}")
        }
    }
}

