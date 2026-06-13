package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corla.CanonicalContest
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.corla.CountyContestBuilder
import org.cryptobiotic.rlauxe.dominion.DominionCvrExport
import org.cryptobiotic.rlauxe.dominion.makeContestInfo
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import kotlin.test.Test
import kotlin.test.assertEquals

class TestElectionSchema {
    val show = false
    val colorado2020 = Colorado2020AuditCenterInput()
    val colorado2022 = Colorado2022Primary()

    @Test
    fun test2020CvrSchema() {
        testCvrSchema("/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/Boulder CO.csv", colorado2020)
        testCvrSchema("/home/stormy/datadrive/votedatabase/cvr/Colorado/Otero/cvr.csv", colorado2020)
    }

    @Test
    fun test2022CvrSchema() {
        testCvrSchema("/home/stormy/datadrive/votedatabase/cvr/2022Primaries/Colorado/Boulder CO '22 Primary.csv", colorado2022)
    }

    fun testCvrSchema(filename: String, coloradoInput: ColoradoInput) {
        println("========================================================================================")
        println(filename)
        compareCvrSchemaVsCanonical(filename, coloradoInput)
        //println("------")
        compareCvrSchemaVsContestBuilder(filename, coloradoInput)
    }
}

fun compareCvrSchemaVsCanonical(filename: String, coloradoInput: ColoradoInput) {
    val export: DominionCvrExport = readDominionCvrExportCsv(filename, "")
    val contestInfos: List<ContestInfo> = export.makeContestInfo()

    var count = 0
    val canonicalMap: Map<String, CanonicalContest> = coloradoInput.canonicalContests()
    contestInfos.forEach { info ->
        val schemaName = coloradoInput.contestNameCleanup(info.name)
        val can = canonicalMap[schemaName]
        if (can == null) {
            println("canonical doesnt have contest '${schemaName}' that is in the cvr schema")
            count++
        } else {
            val schemaCands = info.candidateNames.keys.map{ coloradoInput.candidateNameCleanup(it) }.toSortedSet()
            if (schemaCands != can.choices.toSet()) {
                println("   ${can.choices.toSortedSet()}")
                println("   ${schemaCands}")
            }
        }
    }
    println("find ${contestInfos.size} export contests in ${canonicalMap.size} canonical contests: $count are missing")

}

fun compareCvrSchemaVsContestBuilder(filename: String, coloradoInput: ColoradoInput) {
    val export: DominionCvrExport = readDominionCvrExportCsv(filename, "")
    val exportContestInfos: List<ContestInfo> = export.makeContestInfo()

    // CountyContestBuilder only uses coloradoInput
    val contestBuilder = CountyContestBuilder(coloradoInput)
    val contests = contestBuilder.contests

    var count = 0
    val contestMap: Map<String, ContestIF> = contests.associateBy { it.name }
    exportContestInfos.forEach { schemaInfo ->
        val schemaName = coloradoInput.contestNameCleanup(schemaInfo.name)
        val can = contestMap[schemaName]
        if (can == null) {
            println("contestBuilder doesnt have contest '${schemaName}' that is in the cvr schema")
            count++
        } else {
            val schemaCands = schemaInfo.candidateNames.keys.map { coloradoInput.candidateNameCleanup(it) }.toSortedSet()
            val contestCands = can.info().candidateNames.keys.toSortedSet()
            if (schemaCands != contestCands) {
                println("   ${contestCands}")
                println("   ${schemaCands}")
            }
            val info = can.info()
            assertEquals(info.name, schemaName)
            assertEquals(contestCands, schemaCands)
            assertEquals(info.nwinners, schemaInfo.nwinners)
            assertEquals(info.voteForN, schemaInfo.voteForN)
            assertEquals(info.isIrv, schemaInfo.isIrv)
        }
    }
    println("find ${exportContestInfos.size} exportContestInfos contests in ${contests.size} contestBuilder contests: $count are missing")
}