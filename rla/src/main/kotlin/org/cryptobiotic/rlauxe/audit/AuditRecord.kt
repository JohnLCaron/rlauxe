package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.*
import java.nio.file.Files
import java.nio.file.Path

class AuditRecord(
    val location: String,
    val auditConfig: AuditConfig,
    val contests: List<ContestUnderAudit>,
    val rounds: List<AuditRound>,
    mvrs: List<AuditableCard> // mvrs already sampled
) {
    val previousMvrs = mutableMapOf<Long, AuditableCard>()

    init {
        mvrs.forEach { previousMvrs[it.prn] = it } // cumulative
    }

    // TODO new mvrs vs mvrs. Build interface to manage this process
    fun enterMvrs(mvrFile: String): Boolean {
        val mvrs = readAuditableCardCsvFile(mvrFile)
        val mvrMap = mvrs.associateBy { it.prn }.toMap()

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
        writeAuditableCardCsvFile(sampledMvrs , publisher.sampleMvrsFile(lastRoundIdx))
        println("    write sampledMvrs to '${publisher.sampleMvrsFile(lastRoundIdx)}' for round $lastRoundIdx")

        // TODO
        //   mvrManager.setMvrsForRound(sampledMvrs)
        return true
    }

    companion object {

        fun readFrom(location: String): AuditRecord {
            val publisher = Publisher(location)
            val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
            val auditConfig = auditConfigResult.unwrap()

            val contestsResults = readContestsJsonFile(publisher.contestsFile())
            val contests = if (contestsResults is Ok) contestsResults.unwrap()
                else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()} err = $contestsResults")

            val sampledMvrsAll = mutableListOf<AuditableCard>()

            val rounds = mutableListOf<AuditRound>()
            for (roundIdx in 1..publisher.rounds()) {
                val sampledNumbers = readSampleNumbersJsonFile(publisher.sampleNumbersFile(roundIdx)).unwrap()

                // may not exist yet
                val mvrsForRoundFile = Path.of(publisher.sampleMvrsFile(roundIdx))
                val sampledMvrs = if (Files.exists(mvrsForRoundFile)) {
                    readAuditableCardCsvFile(publisher.sampleMvrsFile(roundIdx))
                } else {
                    emptyList()
                }
                sampledMvrsAll.addAll(sampledMvrs) // cumulative

                val auditRound = readAuditRoundJsonFile(publisher.auditRoundFile(roundIdx), contests, sampledNumbers, sampledMvrs).unwrap()
                rounds.add(auditRound)
            }
            return AuditRecord(location, auditConfig, contests, rounds, sampledMvrsAll)
        }
    }
}

fun makeMvrManager(auditDir: String) = MvrManagerFromRecord(auditDir)
