package org.cryptobiotic.rlauxe.persist

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.audit.AuditConfig
import org.cryptobiotic.rlauxe.audit.AuditRound
import org.cryptobiotic.rlauxe.audit.AuditableCard
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
    fun enterMvrs(mvrs: List<AuditableCard>): Boolean {
        val mvrMap = mvrs.associateBy { it.prn }.toMap()

        val publisher = Publisher(location)
        val lastRoundIdx = if (rounds.isEmpty()) 1 else rounds.last().roundIdx

        // get complete match with sampleNums in last round
        var missing = false
        val sampledPrnsResult = readSamplePrnsJsonFile(publisher.samplePrnsFile(lastRoundIdx))
        if (sampledPrnsResult is Err) {
            println(sampledPrnsResult)
        }
        val sampledPrns = sampledPrnsResult.unwrap()
        sampledPrns.forEach { sampleNumber ->
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

        val sampledMvrs = sampledPrns.map{ sampleNumber -> previousMvrs[sampleNumber]!! }
        writeAuditableCardCsvFile(sampledMvrs , publisher.sampleMvrsFile(lastRoundIdx))
        println("    write sampledMvrs to '${publisher.sampleMvrsFile(lastRoundIdx)}' for round $lastRoundIdx")

        sampledMvrs.forEach { previousMvrs[it.prn] = it } // cumulative
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
                val sampledNumbers = readSamplePrnsJsonFile(publisher.samplePrnsFile(roundIdx)).unwrap()

                // may not exist yet
                val mvrsForRoundFile = publisher.sampleMvrsFile(roundIdx)
                val sampledMvrs = if (Files.exists(Path.of(mvrsForRoundFile))) {
                    readAuditableCardCsvFile(mvrsForRoundFile)
                } else {
                    emptyList()
                }
                sampledMvrsAll.addAll(sampledMvrs) // cumulative

                // may not exist yet
                val auditRoundFile = publisher.auditRoundFile(roundIdx)
                if (Files.exists(Path.of(auditRoundFile))) {
                    val auditRound = readAuditRoundJsonFile(
                        auditRoundFile,
                        contests,
                        sampledNumbers,
                        sampledMvrs
                    )
                    rounds.add(auditRound.unwrap())
                }
            }
            return AuditRecord(location, auditConfig, contests, rounds, sampledMvrsAll)
        }
    }
}
