package org.cryptobiotic.rlauxe.datadrive

import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corla.CanonicalContest
import org.cryptobiotic.rlauxe.corla.CountyContestBuilder
import org.cryptobiotic.rlauxe.dominion.CastVoteRecord
import org.cryptobiotic.rlauxe.dominion.DominionCvrExport
import org.cryptobiotic.rlauxe.dominion.makeContestInfo
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import kotlin.test.Test
import kotlin.test.assertEquals

class TestElectionSchema {
    val show = false

    @Test
    fun testCvrSchema() {
        testCvrSchema("/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/Boulder CO.csv")
        testCvrSchema("/home/stormy/datadrive/votedatabase/cvr/Colorado/Otero/cvr.csv")
    }

    fun testCvrSchema(filename: String) {
        println("========================================================================================")
        println(filename)
        compareCvrSchemaVsCanonical(filename)
        println("------")
        compareCvrSchemaVsContestBuilder(filename)
    }

    fun compareCvrSchemaVsCanonical(filename: String) {
        println(CastVoteRecord.header)
        val export: DominionCvrExport = readDominionCvrExportCsv(filename, "", 10)

        val coloradoInput = Colorado2020Input
        val schemaInfos: List<ContestInfo> = export.makeContestInfo()

        var count = 0
        val canonicalMap: Map<String, CanonicalContest> = coloradoInput.canonicalContests
        schemaInfos.forEach { info ->
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
        println("there are ${canonicalMap.size} canonical contests and ${schemaInfos.size} schema contests, $count are missing")
    }

    fun compareCvrSchemaVsContestBuilder(filename: String) {
        val export: DominionCvrExport = readDominionCvrExportCsv(filename, "")

        val coloradoInput = Colorado2020Input
        val contestBuilder = CountyContestBuilder(coloradoInput)
        val schemaInfos: List<ContestInfo> = export.makeContestInfo()
        val contests = contestBuilder.contests

        var count = 0
        val contestMap: Map<String, ContestIF> = contests.associateBy { it.name }
        schemaInfos.forEach { schemaInfo ->
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
        println("there are ${contests.size} contests and ${schemaInfos.size} schema contests, $count are missing")
    }
}