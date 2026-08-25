package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.Sampling
import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
import org.cryptobiotic.rlauxe.auditcenter.Colorado2022Primary
import org.cryptobiotic.rlauxe.auditcenter.Colorado2024General
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026PMerged
import org.cryptobiotic.rlauxe.auditcenter.Colorado2026Primary
import org.cryptobiotic.rlauxe.auditcenter.corlaCreationSettings
import org.cryptobiotic.rlauxe.auditcenter.corlaRoundSettings
import org.cryptobiotic.rlauxe.auditcenter.createCountyElectionSansCvrs
import org.cryptobiotic.rlauxe.auditcenter.createCountyElectionSimCvrs
import org.cryptobiotic.rlauxe.auditcenter.writeCountyContestData
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.testdataDir

import kotlin.test.Test

class MakeCorlaSimCvrs {
    val show = false

    @Test
    fun makeColorado2026PMerged() {
        val topdir = "$cases/corla/corla2026/primaryMerged"

        createCountyElectionSimCvrs(
            topdir, Colorado2026PMerged(),
            corlaCreationSettings(2026),
            corlaRoundSettings(sampling = Sampling.consistent),
            name = "Colorado2026PrimaryMerged", startFirstRound = true
        )
    }

    @Test
    fun makeColorado2026Primary() {
        val topdir = "$cases/corla/corla2026/primary"

        createCountyElectionSimCvrs(
            topdir, Colorado2026Primary(),
            corlaCreationSettings(2026),
            corlaRoundSettings(sampling = Sampling.consistent),
            name = "Colorado2026Primary", startFirstRound = true
        )
    }

    @Test
    fun makeCounty2024General() {
        val topdir = "$cases/corla/corla2024"

        createCountyElectionSimCvrs(
            topdir, Colorado2024General(),
            corlaCreationSettings(2024),
            corlaRoundSettings(sampling = Sampling.consistent),
            name = "County2024General", startFirstRound = true
        )
    }

    @Test
    fun makeColorado2022Primary() {
        val topdir = "$cases/corla/corla2022Primary"

        createCountyElectionSimCvrs(
            topdir, Colorado2022Primary(),
            corlaCreationSettings(2022),
            corlaRoundSettings(sampling = Sampling.consistent),
            name = "Colorado2022Primary", startFirstRound = true
        )
    }

    @Test
    fun makeColorado2020sim() {
        val topdir = "$cases/corla/corla2020/sim"

        createCountyElectionSimCvrs(
            topdir, Colorado2020General(),
            corlaCreationSettings(2020),
            corlaRoundSettings(sampling = Sampling.consistent),
            name = "Colorado2020clca", startFirstRound = true
        )
    }

    @Test
    fun makeColorado2020sans() {
        val topdir = "$cases/corla/corla2020/sans"

        createCountyElectionSansCvrs(
            topdir, Colorado2020General(),
            corlaCreationSettings(2020),
            corlaRoundSettings(sampling = Sampling.consistent),
            name = "Colorado2020clca", startFirstRound = true
        )
    }

    @Test
    fun readCountyAuditRecord() {
        val topdir = "$cases/corla/corla2020/sim"
        val auditRecord = AuditRecord.read(topdir)
        val countyRecord =  auditRecord as CountyAuditRecord

        println("countyRecord.countyData")
        countyRecord.countyData.forEach { println( "  $it") }
        val sum1 = countyRecord.countyData.filter { it.strataName != "Statewide"}.sumOf { it.population }
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

    // @Test
    fun writeCountyContestData() {
        val topdir = "$cases/corla/corla2020/clca"
        val auditRecord = AuditRecord.read(topdir)!!

        val coloradoInput = Colorado2022Primary()
        val contestMap = auditRecord.contests.associate { it.contest.info().name to it }
        writeCountyContestData(topdir, contestMap, coloradoInput)
    }


    // @Test
    fun startFirstRound() {
        val topdir = "$cases/corla/withCvrs/Colorado2020uniform"
        val result = org.cryptobiotic.rlauxe.audit.startFirstRound(topdir)
        println(result)
    }

    // @Test
    fun runRound() {
        val topdir = "$cases/corla/withCvrs/Colorado2020uniform"
        org.cryptobiotic.rlauxe.audit.runRound(topdir)
    }

    // @Test
    fun resampleAndSaveResults() {
        val topdir = "$cases/corla/withCvrs/Colorado2020uniform"
        org.cryptobiotic.rlauxe.audit.resampleAndSaveResults(topdir)
    }

    @Test
    fun readRecord() {
        val topdir = "$cases/corla/withCvrs/Colorado2020uniform"
        val countyRecord = AuditRecord.read(topdir) as CountyAuditRecord

        println("countyRecord.countyData")
        // countyRecord.countyData.forEach { println( it) }
        println()

        println("countyRecord.countyContestData")
        // countyRecord.countyContestData.forEach { println( it) }

        val mvrCounts = countyRecord.countMvrsByCounty()
        println("countyRecord.countMvrsByCounty")
        mvrCounts.forEach { println(it) }
        println()
    }
}
