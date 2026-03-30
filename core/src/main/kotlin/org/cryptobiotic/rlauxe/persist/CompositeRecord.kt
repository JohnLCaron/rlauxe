package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.BatchIF
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.ErrorMessages
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.isDirectory

data class CompositeRecord(
    override val location: String,
    override val electionInfo: ElectionInfo,
    override val config: Config,
    override val contests: List<ContestWithAssertions>,
    override val rounds: List<AuditRoundIF>,
    val componentRecords: List<AuditRecord>,
): AuditRecordIF  {

    override fun readSortedManifest(batches: List<BatchIF>?): CardManifest {
        return componentRecords.first().readSortedManifest(batches)
    }
    override fun readSortedManifest(): CardManifest {
        return componentRecords.first().readSortedManifest() // barf
    }

    override fun readOneShotMvrs() = emptyMap<Int, Int>()

    override fun readBatches(): List<BatchIF> {
        val allBatches = mutableListOf<BatchIF>()
        for (component in componentRecords) {
            val cbatches = component.readBatches()
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
                            val result: Result<AuditRecordIF, ErrorMessages> = AuditRecord.readFromResult(auditDir)
                            if (result.isErr) {
                                println("  Error: ${result.component2()}")
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
        CompositeAuditRound(roundIdx, pair.first, pair.second, samplePrns = emptyList())
    }
    return result
}

// interface AuditRoundIF {
//    val roundIdx: Int
//    val contestRounds: List<ContestRound>
//
//    val auditWasDone: Boolean
//    val auditIsComplete: Boolean
//    val samplePrns: List<Long> // card prns to sample for this round (complete, not just new)
//    val nmvrs: Int
//    val newmvrs: Int
//    val auditorWantNewMvrs: Int
//}

data class CompositeAuditRound(
    override val roundIdx: Int,
    override val contestRounds: List<ContestRound>,
    val auditRounds: List<AuditRound>,

    override var auditWasDone: Boolean = false,
    override var auditIsComplete: Boolean = false,
    override var samplePrns: List<Long>, // card prns to sample for this round (complete, not just new)
    override var nmvrs: Int = 0,
    override var newmvrs: Int = 0,
    override var mvrsUsed: Int = 0,
    override var mvrsUnused: Int = 0,
) : AuditRoundIF {

    init {
        auditWasDone = auditRounds.all { it.auditWasDone }
        auditIsComplete = auditRounds.all { it.auditIsComplete }
        nmvrs = contestRounds.map { it.estMvrs }.sum()
        newmvrs = contestRounds.map { it.estNewMvrs }.sum()
        mvrsUsed = auditRounds.map { it.mvrsUsed }.sum()
        mvrsUnused = auditRounds.map { it.mvrsUnused }.sum()
    }

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

