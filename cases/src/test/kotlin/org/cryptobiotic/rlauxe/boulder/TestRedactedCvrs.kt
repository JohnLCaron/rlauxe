package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.estimate.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.readCvrExportsFromFile
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.contentToString
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRedactedCvrs {

    @Test
    fun test2025RedactedCvrs() {
        val datadir = "$testdataDir/cases/boulder2025"
        val cvrFilename = "$datadir/Redacted-CVR-PUBLIC.utf8.csv"
        val sovoFilename = "$datadir/2025C-Boulder-County-Official-Statement-of-Votes.utf8.csv"
        val sovo: BoulderStatementOfVotes = readBoulderStatementOfVotes(sovoFilename, "Boulder2024")

        val export: DominionCvrExportCsv = readCvrExportsFromFile(cvrFilename)

        val electionSimCvrs = CreateBoulderElection(AuditType.CLCA, export, sovo, distributeOvervotes = emptyList())
        testRedactedCvrTabulation(export, electionSimCvrs)
    }

    @Test
    fun test2024RedactedCvrs() {
        // redaction lines are present
        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
        val export: DominionCvrExportCsv = readCvrExportsFromFile(filename)

        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            "Boulder2024")

        val electionSimCvrs = CreateBoulderElection(AuditType.CLCA,  export, sovo)
        val infos = electionSimCvrs.makeContestInfo()
        println("ncontests with info = ${infos.size}")

        testRedactedCvrTabulation(export, electionSimCvrs)
    }

    fun testRedactedCvrTabulation(export: DominionCvrExportCsv, electionSimCvrs: CreateBoulderElection) {
        val redactedCvrs = electionSimCvrs.makeRedactedCvrs()
        println("nredacted cvrs = ${redactedCvrs.size}")

        val redactedCvrVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(redactedCvrs.iterator())

        val redactedDirect = mutableMapOf<Int, MutableMap<Int, Int>>()
        export.redactedGroups.forEach { redacted ->
            redacted.contestVotes.forEach { (contestId, conVotes) ->
                val accumVotes = redactedDirect.getOrPut(contestId) { mutableMapOf() }
                conVotes.forEach { (cand, nvotes) ->
                    if (nvotes > 0) {
                        val accum = accumVotes.getOrPut(cand) { 0 }
                        accumVotes[cand] = accum + nvotes
                    }
                }
            }
        }
        // println(compareRedactions(redactedCvrVotes, redactedDirect))
        assertEquals(redactedCvrVotes, redactedDirect)
        println("redactedCvrVotes agrees with redactedDirect")
    }

    /* code is currently specialized to 2025 general election TODO
    @Test
    fun testIrvRedactedCvrs() {
        val stopwatch = Stopwatch()
        // redaction lines are present
        val filename = "src/test/data/Boulder2023/Redacted-2023Coordinated-CVR.csv"
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(filename, "Boulder")

        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes.csv", "Boulder2023")
        // println("sovo = ${sovo.show()}")

        val sovoRcv = readBoulderStatementOfVotes(
            "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes-RCV.csv", "Boulder2023Rcv")
        // println("sovoRcv = ${sovoRcv.show()}")
        val irvContest: BoulderContestVotes = sovoRcv.contests.first()
        // println("irvContest = ${irvContest}")

        val combined = BoulderStatementOfVotes.combine(listOf(sovoRcv, sovo))

        val electionSimCvrs = BoulderElectionOAnew(export, combined)
        val contestUA = electionSimCvrs.makeContestsUA(true)
        val irv = contestUA.find { it.isIrv }
        if (irv != null) {
            val countIrvCvrs = electionSimCvrs.cvrs.count { it.hasContest(irv.id) }
            println("countIrvCvrs = $countIrvCvrs")
            assertEquals(irvContest.totalBallots, countIrvCvrs)
        }
    }
    */

}