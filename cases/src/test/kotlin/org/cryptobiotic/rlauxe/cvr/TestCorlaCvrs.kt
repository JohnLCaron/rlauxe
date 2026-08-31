package org.cryptobiotic.rlauxe.cvr

import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.Schema
import org.cryptobiotic.rlauxe.dominion.readCvrExportsFromFile
import kotlin.test.Test
import kotlin.test.assertTrue

class TestCorlaCvrs {

    @Test
    fun test2026Weld() {
        val inputFile = "/home/stormy/datadrive/github/nealmcb/auditcenter/2026/primary/observerfiles/Weld_CVR_Export_20260709140755.csv"
        val corlaCvrs = readCorlaCvrsFromFile(inputFile, showHeaders = false, showSchema = false)
        val cvrExports = readCvrExportsFromFile(inputFile, showHeaders = false)
        assertTrue(compareCvr(corlaCvrs, cvrExports))
    }

    @Test
    fun testBoulderIRV() {
        val inputFile = "/home/stormy/dev/github/rla/rlauxe/cases/src/test/data/Boulder2023/Redacted-2023Coordinated-CVR.csv"
        val corlaCvrs = readCorlaCvrsFromFile(inputFile, showHeaders = false, showSchema = false)
        val cvrExports = readCvrExportsFromFile(inputFile, showHeaders = false)
        assertTrue(compareCvr(corlaCvrs, cvrExports))
    }

    ///////////////////////////////////////////////////////////////////////////////////

    fun compareCvr(corlaCvrs: CorlaCvrs, cvrExports: DominionCvrExportCsv): Boolean {
        println("Compare corlaCvrs ${corlaCvrs.inputSource}\n    to cvrExports ${cvrExports.filename}")
        var same = true
        same = same && compareSchema(corlaCvrs.schema, cvrExports.schema)
        if (corlaCvrs.cvrs.size != cvrExports.cvrs.size) {
            println("number of cvrs differ")
            same = false
        }
        return same
    }

    fun compareSchema(corlaSchema: CvrSchema, domSchema: Schema): Boolean {
        var same = true
        if (corlaSchema.nheaders != domSchema.nheaders) {
            println("number of headers differ")
            same = false
        }
        if (corlaSchema.contests.size != domSchema.contests.size) {
            println("number of contests differ")
            same = false
        }
        val corlaContests = corlaSchema.contests.associateBy { it.contestName }
        val domContests = domSchema.contests.associateBy { it.contestName }
        corlaContests.forEach { (contestName, corlaContest) ->
            val domContest = domContests[contestName]
            if (domContest == null) {
                println("domSchema missing contest '$contestName")
                same = false
            } else {
                compareContest(corlaSchema, corlaContest, domSchema, domContest)
            }
        }

        return same
    }

    fun compareContest(corlaSchema: CvrSchema, corlaContest: SchemaContestInfo,
                       domSchema: Schema, domContest: org.cryptobiotic.rlauxe.dominion.SchemaContestInfo): Boolean {
        var same = true
        if (corlaContest.ncols != domContest.ncols) {
            println("number of choices differ")
            same = false
        }
        if (corlaContest.isIRV != domContest.isIRV) {
            println("isIRV differ")
            same = false
            if (corlaContest.isIRV)
                print("")
        }
        for (colidx in corlaContest.startCol+corlaContest.ncols..corlaContest.ncols) {
            compareChoice(corlaSchema, corlaSchema.columns[colidx], domSchema, domSchema.columns[colidx], )
        }

        return same
    }

    fun compareChoice(corlaSchema: CvrSchema, corlaChoice: SchemaColumnInfo,
                       domSchema: Schema, domChoice: org.cryptobiotic.rlauxe.dominion.SchemaColumnInfo): Boolean {
        var same = true
        if (corlaChoice.choice != domChoice.choice) {
            println("names differ: ${corlaChoice.choice} != ${domChoice.choice}")
            same = false
        }
        if (corlaChoice.contest != domChoice.contest) {
            println("contest differ: ${corlaChoice.contest} != ${domChoice.contest}")
            same = false
        }

        return same
    }
}