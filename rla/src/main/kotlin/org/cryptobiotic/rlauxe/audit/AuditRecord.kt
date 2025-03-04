package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.workflow.*

class AuditRecord(
    val location: String,
    val auditConfig: AuditConfig,
    val rounds: List<AuditRound>,
    val cvrs: List<CvrUnderAudit>,
    val mvrs: Set<CvrUnderAudit>,
) {
    val nrounds = rounds.size

    companion object {

        fun readFrom(location: String): AuditRecord {
            val publisher = Publisher(location)
            val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
            val auditConfig = auditConfigResult.unwrap()
            val cvrResult = readCvrsJsonFile(publisher.cvrsFile())
            val cvrs = if (cvrResult is Ok) cvrResult.unwrap() else emptyList()

            val mvrs = mutableSetOf<CvrUnderAudit>()
            val previousSamples = mutableSetOf<Int>()
            var previousRound: AuditRound? = null

            val rounds = mutableListOf<AuditRound>()
            for (roundIdx in 1..publisher.rounds()) {
                val auditRound = readAuditRoundJsonFile(publisher.auditRoundFile(roundIdx)).unwrap()

                val sampledIndices = readSampleIndicesJsonFile(publisher.sampleIndicesFile(roundIdx)).unwrap()

                // may not exist yet
                val sampledMvrsResult = readCvrsJsonFile(publisher.sampleMvrsFile(roundIdx))
                val sampledMvrs = if (sampledMvrsResult is Ok) sampledMvrsResult.unwrap() else emptyList()
                mvrs.addAll(sampledMvrs) // cumulative

                auditRound.calcNewSamples(previousSamples)
                if (auditConfig.auditType == AuditType.CLCA) auditRound.calcContestMvrs(cvrs)
                previousSamples.addAll(sampledIndices) // cumulative

                /* i htink you dont need this because the serialization store it
                if (previousRound != null) {
                    auditRound.setPreviousRound(previousRound)
                } */

                rounds.add(auditRound)
                previousRound = auditRound
            }
            return AuditRecord(location, auditConfig, rounds, cvrs, mvrs)
        }
    }
}
