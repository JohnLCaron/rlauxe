package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.estimate.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.cvr.RedactionBoulder
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrsFromFile
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBoulder2025Cvrs {
    val datadir = "$testdataDir/cases/boulder2025"
    val cvrFilename = "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv"
    val sovoFilename = "src/test/data/Boulder2025/2025C-Boulder-County-Official-Statement-of-Votes.csv"

    @Test
    fun parseBoulder25cvrs() {
        val stopwatch = Stopwatch()
        // redaction lines are present
        val export: BoulderCvrExportCsv = readBoulderCvrExportCsv(cvrFilename, "Boulder")
        // println(export.summary())
        println("took = $stopwatch")

        assertEquals("Boulder", export.countyId)
        assertEquals(
            cvrFilename,
            export.filename
        )
        assertEquals("Boulder County 2025 Coordinated", export.electionName)
        assertEquals("5.17.17.1", export.versionName)
        assertEquals(32, export.schema.contests.size)
        assertEquals(120529, export.cvrs.size)


        val exportCvrs: List<Cvr> = export.cvrs.map { it.convertToCvr() }
        val votes = tabulateVotesFromCvrs(exportCvrs.iterator()).toSortedMap()
        votes.forEach { (contestId, votes) ->
            println("${contestId}: ${votes.toSortedMap()}")
        }

        println("\nBallot Types")
        export.ballotTypes.forEach { println("  ${it}") }
        println("Total count=${export.ballotTypes.sumOf { it.count }}")

        println("\nRedacted Groups")
        export.redacted.forEach { println("  ${it}") }
        println("Total ncards=${export.redacted.sumOf { it.ncards }}")
        println("Total votes=${export.redacted.sumOf { it.totalVotes() }}")
    }

    @Test
    fun testCreateBoulderElection() {
        val corlaCvrs = readCorlaCvrsFromFile(cvrFilename, redaction = RedactionBoulder())

        val sovo: BoulderStatementOfVotes = readBoulderStatementOfVotes(sovoFilename, "Boulder2024")
        //     val auditType: AuditType,
        //    val export: BoulderCvrExportCsv,
        //    val sovo: BoulderStatementOfVotes,
        //    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
        //    val hasStyle: Boolean,
        val election = CreateBoulderElection("boulder25", AuditType.ONEAUDIT, corlaCvrs, sovo, hasStyle = true)
        println("ncontests with info = ${election.infos.size}")

        val contests = election.contests

        // some random sanity check
        val selectedContest = contests.find { it.name.startsWith("City of Boulder Council")}!!
        val candidatesById = selectedContest.info().candidateNames.map { (name, id) -> id to name }.toMap()

        assertEquals(11, candidatesById.size)
    }

}
