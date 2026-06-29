package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.ClcaConfig
import org.cryptobiotic.rlauxe.audit.ContestSampleControl
import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.audit.SimulationControl
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.testdataDir

import kotlin.test.Test

class MakeElectionsSansCvrs {
    val show = false

    val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.03, )
    val round = AuditRoundConfig(
        SimulationControl(nsimTrials = 10, estPercentile = listOf(42, 55, 67)),
        ContestSampleControl(minRecountMargin = .005, minSize = 10, contestSampleCutoff = 10000,
            auditSampleCutoff = 200000, sampling = Sampling.consistent),
        ClcaConfig(), null)

    // @Test
    fun makeCounty2024OnlyTeller() {
        val topdir = "$testdataDir/cases/auditcenter/County2024OnlyTeller"

        createCountyElectionSansCvrs(topdir,  Colorado2024General(),
            creation, round, name = "County2024OnlyTeller", startFirstRound = true, onlyCounty="Teller")
    }

    @Test
    fun makeCounty2024General() {
        val topdir = "$cases/corla/sansCvrs/Colorado2024"

        createCountyElectionSansCvrs(topdir,  Colorado2024General(),
            creation, round, name = "County2024General", startFirstRound = true)
    }

    @Test
    fun makeColorado2022Primary() {
        val topdir = "$cases/corla/sansCvrs/Colorado2022Primary"

        createCountyElectionSansCvrs(topdir,  Colorado2022Primary(),
            creation, round, name = "Colorado2022Primary", startFirstRound = true)
    }

    @Test
    fun makeColorado2020General() {
        val topdir = "$cases/corla/sansCvrs/Colorado2020"
        val creation = AuditCreationConfig(AuditType.CLCA, riskLimit=.04, )

        createCountyElectionSansCvrs(topdir, Colorado2020General(),
            creation, round, name = "Colorado2020sans", startFirstRound = true)
    }

    @Test
    fun readCountyAuditRecord() {
        val topdir = "$cases/corla/sansCvrs/Colorado2020sans/"
        val auditRecord = AuditRecord.read(topdir)
        val countyRecord =  auditRecord as CountyAuditRecord

        println("countyRecord.countyData")
        countyRecord.countyData.forEach { println( "  $it") }
        val sum1 = countyRecord.countyData.filter { it.countyName != "Statewide"}.sumOf { it.npop }
        println("countyRecord.countyData total pop = $sum1}")
        println()

        //println("countyRecord.countyContestData")
        // countyRecord.countyContestData.forEach { println( it) }
        //println()

        val mvrCounts = countyRecord.countMvrsByCounty()
        println("countyRecord.countMvrsByCounty")
        mvrCounts.forEach { println( it ) }
        println("mvrCounts total nmvrs = ${mvrCounts.values.sumOf { it.nmvrs }}")
        println()
    }

    @Test
    fun writeCountyContestData() {
        val topdir = "$cases/corla/sansCvrs/Colorado2022Primary"
        val auditRecord = AuditRecord.read(topdir)!!

        val coloradoInput = Colorado2022Primary()
        val contestMap = auditRecord.contests.associate { it.contest.info().name to it }
        writeCountyContestData(topdir, contestMap, coloradoInput)
    }
}
