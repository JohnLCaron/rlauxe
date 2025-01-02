package org.cryptobiotic.rlauxe.verifier

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.readCvrsJsonFile
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.Publisher

class Verifier(val publish: Publisher) {

    fun verify(): Boolean {
        return verifyCvrSampleNumbers()
    }

    fun verifyCvrSampleNumbers(): Boolean {
        val auditConfig = readAuditConfigJsonFile(publish.auditConfigFile()).unwrap()
        val cvrs: List<CvrUnderAudit> = readCvrsJsonFile(publish.cvrsFile()).unwrap()
        val prng = Prng(auditConfig.seed)
        var countBad = 0
        cvrs.forEach{
            if (it.sampleNum != prng.next()) {
                countBad++
                if (countBad > 10) throw RuntimeException()
            }
        }
        println("verifyCvrSampleNumbers ${cvrs.size} bad=${countBad} ")
        return countBad == 0
    }

    fun verifySampleIndices(): Boolean {
        val auditConfig = readAuditConfigJsonFile(publish.auditConfigFile()).unwrap()
        val cvrs: List<CvrUnderAudit> = readCvrsJsonFile(publish.cvrsFile()).unwrap()
        val prng = Prng(auditConfig.seed)
        var countBad = 0
        cvrs.forEach{
            if (it.sampleNum != prng.next()) {
                countBad++
                if (countBad > 10) throw RuntimeException()
            }
        }
        println("verifyCvrSampleNumbers ${cvrs.size} bad=${countBad} ")
        return countBad == 0
    }
}