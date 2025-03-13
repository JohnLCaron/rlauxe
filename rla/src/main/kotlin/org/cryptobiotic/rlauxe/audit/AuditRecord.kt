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
    val mvrs: Set<CvrUnderAudit>,
) {
    val nrounds = rounds.size

    fun bcUA(): List<BallotOrCvr> {
        if (bcUA.isEmpty()) readCvrs()
        return bcUA
    }

    // in the original order
    fun cvrs(): List<Cvr> {
        if (cvrs.isEmpty()) readCvrs()
        return cvrs
    }

    private fun readCvrs() { // synchronized ?
        val publisher = Publisher(location)
        val cvrResult = readCvrsJsonFile(publisher.cvrsFile())
        val cvrsUA = if (cvrResult is Ok) cvrResult.unwrap() else emptyList()
        this.cvrs = cvrsUA.sortedBy { it.index() }.map { it.cvr }
        this.bcUA = cvrsUA
    }
    private var cvrs: List<Cvr> = emptyList()
    private var bcUA: List<BallotOrCvr> = emptyList()

    companion object {

        fun readFrom(location: String): AuditRecord {
            val publisher = Publisher(location)
            val auditConfigResult = readAuditConfigJsonFile(publisher.auditConfigFile())
            val auditConfig = auditConfigResult.unwrap()

            val contestsResults = readContestsJsonFile(publisher.contestsFile())
            val contests = if (contestsResults is Ok) contestsResults.unwrap()
                else throw RuntimeException("Cannot read contests from ${publisher.contestsFile()}")

            val mvrs = mutableSetOf<CvrUnderAudit>()

            val rounds = mutableListOf<AuditRound>()
            for (roundIdx in 1..publisher.rounds()) {
                val sampledIndices = readSampleIndicesJsonFile(publisher.sampleIndicesFile(roundIdx)).unwrap()

                val auditRound = readAuditRoundJsonFile(contests, sampledIndices, publisher.auditRoundFile(roundIdx)).unwrap()

                // may not exist yet
                val sampledMvrsResult = readCvrsJsonFile(publisher.sampleMvrsFile(roundIdx))
                val sampledMvrs = if (sampledMvrsResult is Ok) sampledMvrsResult.unwrap() else emptyList()
                mvrs.addAll(sampledMvrs) // cumulative

                rounds.add(auditRound)
            }
            return AuditRecord(location, auditConfig, rounds, mvrs)
        }
    }
}
