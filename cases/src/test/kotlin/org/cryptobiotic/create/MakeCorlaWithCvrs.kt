package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.resampleAndSaveResults
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.audit.runRoundResult
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
import org.cryptobiotic.rlauxe.auditcenter.allVotedatabaseCounties
import org.cryptobiotic.rlauxe.auditcenter.corlaCreationSettings
import org.cryptobiotic.rlauxe.auditcenter.corlaRoundSettings
import org.cryptobiotic.rlauxe.auditcenter.countyElectionWithCvrs
import org.cryptobiotic.rlauxe.auditcenter.writeCountyContestData
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.votedatabase.votedatabase2020

import kotlin.test.Test

class MakeElectionsWithCvrs {
    val show = false

    @Test
    fun makeColorado2020() {
        val topdir = "$cases/corla/withCvrs/Colorado2020"

        countyElectionWithCvrs(
            allVotedatabaseCounties(votedatabase2020),
            Colorado2020General(),
            topdir,
            corlaCreationSettings(2020),
            corlaRoundSettings(sampling = Sampling.consistent),
            name = "Colorado2020",
            startFirstRound = true,
            isUniform = false,
        )
    }

    @Test
    fun makeColorado2020uniform() {
        val topdir = "$cases/corla/withCvrs/Colorado2020uniform"

        countyElectionWithCvrs(
            allVotedatabaseCounties(votedatabase2020),
            Colorado2020General(),
            topdir,
            corlaCreationSettings(2020),
            corlaRoundSettings(sampling = Sampling.uniform),
            name = "Colorado2020uniform",
            startFirstRound = true,
            isUniform = true,
        )
    }

    @Test
    fun writeCountyContestData() {
        val topdir = "$cases/corla/withCvrs/Colorado2020"
        val auditRecord = AuditRecord.read(topdir)!!

        val coloradoInput = Colorado2020General()

        // writeCountyData(topdir, coloradoInput.strataMap.values.toList())

        val contestMap = auditRecord.contests.associate { it.contest.info().name to it }
        writeCountyContestData(topdir, contestMap, coloradoInput)
    }
}
