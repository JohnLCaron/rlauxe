package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.auditcenter.Colorado2020General
import org.cryptobiotic.rlauxe.auditcenter.Colorado2022Primary
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.auditcenter.ColoradoInput
import org.cryptobiotic.rlauxe.auditcenter.CountyContestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

// compare exportSchema with auditcenter input
class TestExportSchema {
    val show = false

    @Test
    fun test2020CvrSchema() {
        testCvrSchema("Boulder", "/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/Boulder CO.csv", Colorado2020General())
        // testCvrSchema("/home/stormy/datadrive/votedatabase/cvr/Colorado/Otero/cvr.csv", colorado2020)
    }

    @Test
    fun test2022CvrSchema() {
        testCvrSchema(
            "Boulder","/home/stormy/datadrive/votedatabase/cvr/2022Primaries/Colorado/Boulder CO '22 Primary.csv",
            Colorado2022Primary()
        )
    }

    @Test
    fun testArapahoe20cvrs() {
        testCvrSchema("Arapahoe", "/home/stormy/datadrive/votedatabase/cvr/Colorado/Arapahoe/CVR_EDITED.csv", Colorado2020General())
    }

    @Test
    fun testEagle20cvrs() {
        testCvrSchema("Eagle", "/home/stormy/datadrive/votedatabase/cvr/Colorado/Eagle/cvr.csv", Colorado2020General())
    }

}

fun testCvrSchema(county: String, filename: String, coloradoInput: ColoradoInput): Int {
    val export: DominionCvrCsvSummary = DominionCvrExportCsvReader(filename).read()
    var errs = 0

    println("========================================================================================")
    println(filename)
    errs += compareExportSchemaVsAuditCenter(county, export, coloradoInput)
    println("------")
    errs += compareCvrSchemaVsContestBuilder(county, export, coloradoInput)

    return errs
}

fun compareExportSchemaVsAuditCenter(county: String, export: DominionCvrCsvSummary, coloradoInput: ColoradoInput): Int {
    val sinfos = export.makeContestInfo()

    var countErrs = 0
    sinfos.forEach { sinfo ->
        val canonicalContest = coloradoInput.matchCanonicalContest(county, sinfo.name)
        if (canonicalContest == null) {
            println("canonical doesnt have contest '${sinfo.name}' that is in the cvr schema")
            countErrs++
        } else {
            val schemaCands = sinfo.candidateNames.keys.map{
                // coloradoInput.candidateNameCleanup(it)
                coloradoInput.matchCanonicalCandidate(county, canonicalContest, it)!!
            }.toSortedSet()
            if (schemaCands != canonicalContest.choices.toSet()) {
                println("   ${canonicalContest.choices.toSortedSet()}")
                println("   ${schemaCands}")
            }
        }
    }
    println("find ${sinfos.size} export contests in canonical contests: $countErrs are missing")
    return countErrs
}

fun compareCvrSchemaVsContestBuilder(county: String, export: DominionCvrCsvSummary, coloradoInput: ColoradoInput): Int {
    val exportContestInfos = export.makeContestInfo()

    // CountyContestBuilder only uses coloradoInput
    val contestBuilder = CountyContestBuilder(coloradoInput)
    val contests = contestBuilder.contests

    var countErrs = 0
    val contestMap: Map<String, ContestIF> = contests.associateBy { it.name }
    exportContestInfos.forEach { schemaInfo ->
        // val schemaName = coloradoInput.contestNameCleanup(schemaInfo.name)
        val canonicalContest = coloradoInput.matchCanonicalContest(county, schemaInfo.name)!!
        val contest = contestMap[canonicalContest.contestName]
        if (contest == null) {
            println("contestBuilder doesnt have contest '${canonicalContest.contestName}' that is in the cvr schema")
            countErrs++
        } else {
            val schemaCands = schemaInfo.candidateNames.keys.map {
                // coloradoInput.candidateNameCleanup(it)
                coloradoInput.matchCanonicalCandidate(county, canonicalContest, it)!!
            }.toSortedSet()

            val contestCands = contest.info().candidateNames.keys.toSortedSet()
            if (schemaCands != contestCands) {
                println("   ${contestCands}")
                println("   ${schemaCands}")
            }
            val info = contest.info()
            assertEquals(info.name, canonicalContest.contestName)
            assertEquals(contestCands, schemaCands)
            assertEquals(info.nwinners, schemaInfo.nwinners)
            assertEquals(info.voteForN, schemaInfo.nwinners)
            assertEquals(info.isIrv, schemaInfo.isIrv)
        }
    }
    println("find ${exportContestInfos.size} exportContestInfos contests in ${contests.size} contestBuilder contests: $countErrs are missing")
    return countErrs
}