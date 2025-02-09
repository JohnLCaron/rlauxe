package org.cryptobiotic.rlauxe.verifier

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.workflow.AuditConfig
import org.cryptobiotic.rlauxe.workflow.AuditType

class Verifier(val publish: Publisher) {
    var auditConfig : AuditConfig = readAuditConfigJsonFile(publish.auditConfigFile()).unwrap()

    init {
        println("auditConfig = $auditConfig")
    }

    fun verify(): Boolean {
        var allOk = true

        if (auditConfig.auditType == AuditType.CARD_COMPARISON) {
            allOk = allOk && verifyCvrSampleNumbers()
        } else {
            allOk = allOk && verifyBallotManifest()
        }

        for (roundIdx in 1..publish.rounds()) {
            allOk = allOk && verifyRound(roundIdx)
        }
        println("verify = $allOk")
        return allOk
    }

    fun verifyCvrSampleNumbers(): Boolean {
        val cvrs: List<CvrUnderAudit> = readCvrsJsonFile(publish.cvrsFile()).unwrap()
        val prng = Prng(auditConfig.seed)
        var countBad = 0
        cvrs.forEach{
            if (it.sampleNum != prng.next()) {
                countBad++
                if (countBad > 10) throw RuntimeException()
            }
        }
        println("  verifyCvrSampleNumbers ${cvrs.size} bad=${countBad} ")
        return countBad == 0
    }

    fun verifyBallotManifest(): Boolean {
        val ballotManifest = readBallotManifestJsonFile(publish.ballotManifestFile()).unwrap()
        val prng = Prng(auditConfig.seed)
        var countBad = 0
        ballotManifest.ballots.forEach{
            if (it.sampleNum != prng.next()) {
                countBad++
                if (countBad > 10) throw RuntimeException()
            }
        }
        println("  verifyBallotManifest ${ballotManifest.ballots.size} bad=${countBad} ")
        return countBad == 0
    }

    fun verifyRound(roundIdx: Int): Boolean {
        var result = true
        val state = readAuditStateJsonFile(publish.auditRoundFile(roundIdx)).unwrap()
        if (roundIdx != state.roundIdx) {
            println("*** roundIdx = ${state.roundIdx} should be = $roundIdx")
            result = false
        }
        val indices = readSampleIndicesJsonFile(publish.sampleIndicesFile(roundIdx)).unwrap()
        println("    verifyRound $roundIdx '${state.name}' auditWasDone=${state.auditWasDone} auditIsComplete=${state.auditIsComplete} indices = ${indices.size}")

        if (state.auditWasDone) {
            val mvrs = readCvrsJsonFile(publish.sampleMvrsFile(roundIdx)).unwrap()
            if (indices.size != mvrs.size) {
                println("*** indices = ${indices.size} NOT EQUAL mvrs = ${mvrs.size}")
                result = false
            }
        }

        state.contests.forEach { contest ->
            print(contest.show())
        }
        return result
    }
}