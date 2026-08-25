package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026PwithCvrs
import org.cryptobiotic.rlauxe.auditcenter.auditcenter
import org.cryptobiotic.rlauxe.auditcenter.auditcenter2026Counties
import org.cryptobiotic.rlauxe.auditcenter.votedatabase2020Counties
import org.cryptobiotic.rlauxe.auditcenter.corlaCreationSettings
import org.cryptobiotic.rlauxe.auditcenter.corlaRoundSettings
import org.cryptobiotic.rlauxe.auditcenter.countyElectionWithCvrs
import org.cryptobiotic.rlauxe.auditcenter.writeCountyContestData
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.votedatabase.votedatabase2020
import kotlin.test.Test

class MakeElectionsWithCvrs {
    val show = false

    @Test
    fun makeColorado2020() {
        val topdir = "$cases/corla/corla2020/withCvrs"

        countyElectionWithCvrs(
            votedatabase2020Counties(votedatabase2020),
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
    fun makeColorado2026P() {
        val topdir = "$cases/corla/corla2026/primaryCvrs2"

        countyElectionWithCvrs(
            auditcenter2026Counties("$auditcenter/2026/primary/observerfiles"),
            Colorado2026PwithCvrs(),
            topdir,
            corlaCreationSettings(2026),
            corlaRoundSettings(sampling = Sampling.consistent),
            name = "Colorado2026Pcvrs",
            startFirstRound = true,
            isUniform = false,
        )
    }

    // @Test
    fun makeColorado2020uniform() {
        val topdir = "$cases/corla/withCvrs/Colorado2020uniform"

        countyElectionWithCvrs(
            votedatabase2020Counties(votedatabase2020),
            Colorado2020General(),
            topdir,
            corlaCreationSettings(2020),
            corlaRoundSettings(sampling = Sampling.uniform),
            name = "Colorado2020uniform",
            startFirstRound = true,
            isUniform = true,
        )
    }

    // @Test
    fun writeCountyContestData() {
        val topdir = "$cases/corla/withCvrs/Colorado2020"
        val auditRecord = AuditRecord.read(topdir)!!

        val coloradoInput = Colorado2020General()

        // writeCountyData(topdir, coloradoInput.strataMap.values.toList())

        val contestMap = auditRecord.contests.associate { it.contest.info().name to it }
        writeCountyContestData(topdir, contestMap, coloradoInput)
    }
}
