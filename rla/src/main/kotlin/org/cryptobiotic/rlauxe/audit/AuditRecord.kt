package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.readCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.json.*
import java.nio.file.Files
import java.nio.file.Path

class AuditRecord(
    val location: String,
    val auditConfig: AuditConfig,
    val contests: List<ContestUnderAudit>,
    val rounds: List<AuditRound>,
    mvrs: List<CvrUnderAudit> // mvrs already sampled
) {
    val previousMvrs = mutableMapOf<Long, CvrUnderAudit>()

    init {
        mvrs.forEach { previousMvrs[it.sampleNum] = it } // cumulative
    }

    // TODO new mvrs vs mvrs. Build interfacce to manage this process
    fun enterMvrs(mvrFile: String): Boolean {
        val mvrs = readCvrsCsvFile(mvrFile)
        val mvrMap = mvrs.associateBy { it.sampleNum }.toMap()

        val publisher = Publisher(location)
        val lastRound = rounds.last()
        val lastRoundIdx = lastRound.roundIdx

        // get complete match with sampleNums
        var missing = false
        val sampledNumbers = readSampleNumbersJsonFile(publisher.sampleNumbersFile(lastRoundIdx)).unwrap()
        sampledNumbers.forEach { sampleNumber ->
            var mvr = previousMvrs[sampleNumber]
            if (mvr == null) {
                mvr = mvrMap[sampleNumber]
                if (mvr == null) {
                    println("Missing MVR for sampleNumber $sampleNumber")
                    missing = true
                } else {
                    previousMvrs[sampleNumber] = mvr
                }
            }
        }
        if (missing) return false

        val sampledMvrs = sampledNumbers.map{ sampleNumber -> previousMvrs[sampleNumber]!! }
        writeCvrsCsvFile(sampledMvrs , publisher.sampleMvrsFile(lastRoundIdx))
        println("    write sampledMvrs to '${publisher.sampleMvrsFile(lastRoundIdx)}' for round $lastRoundIdx")
        return true
    }

    companion object {

        fun readFrom(location: String): AuditRecord {
            val publisher = Publisher(location)
            val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
            val auditConfig = auditConfigResult.unwrap()

            val contestsResults = readContestsJsonFile(publisher.contestsFile())
            val contests = if (contestsResults is Ok) contestsResults.unwrap()
                else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()}")

            val sampledMvrsAll = mutableListOf<CvrUnderAudit>()

            val rounds = mutableListOf<AuditRound>()
            for (roundIdx in 1..publisher.rounds()) {
                val sampledNumbers = readSampleNumbersJsonFile(publisher.sampleNumbersFile(roundIdx)).unwrap()

                val auditRound = readAuditRoundJsonFile(contests, sampledNumbers, publisher.auditRoundFile(roundIdx)).unwrap()

                // may not exist yet
                val mvrsForRoundFile = Path.of(publisher.sampleMvrsFile(roundIdx))
                if (Files.exists(mvrsForRoundFile)) {
                    val sampledMvrs = readCvrsCsvFile(publisher.sampleMvrsFile(roundIdx))
                    sampledMvrsAll.addAll(sampledMvrs) // cumulative
                }

                rounds.add(auditRound)
            }
            return AuditRecord(location, auditConfig, contests, rounds, sampledMvrsAll)
        }
    }
}


// TODO fix this; used by viewer
fun makeMvrManager(auditDir: String, auditConfig: AuditConfig): MvrManager {
    // TODO TIMING taking 15%
    return if (auditConfig.isClca) {
        MvrManagerClca(auditDir)
    } else {
        MvrManagerPolling(emptyList(), auditConfig.seed) // TODO
    }
}
