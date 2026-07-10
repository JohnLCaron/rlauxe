package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.estimate.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.readCvrExportsFromFile
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBoulder2025Cvrs {
    val datadir = "$testdataDir/cases/boulder2025"
    val cvrFilename = "$datadir/Redacted-CVR-PUBLIC.utf8.csv"
    val sovoFilename = "$datadir/2025C-Boulder-County-Official-Statement-of-Votes.utf8.csv"
    val sovo: BoulderStatementOfVotes = readBoulderStatementOfVotes(sovoFilename, "Boulder2024")

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
    }

    @Test
    fun testCreateBoulderElection() {
        val stopwatch = Stopwatch()
        // redaction lines are present
        val export: DominionCvrExportCsv = readCvrExportsFromFile(cvrFilename)
        // println(export.summary())
        println("took = $stopwatch")

        assertEquals(
            cvrFilename,
            export.filename
        )
        assertEquals("Boulder County 2025 Coordinated", export.electionName)
        assertEquals("5.17.17.1", export.versionName)
        assertEquals(32, export.schema.contests.size)
        assertEquals(120529, export.cvrs.size)

        val maker = CreateBoulderElection(AuditType.CLCA, export, sovo, distributeOvervotes=emptyList())
        val infos = maker.makeContestInfo()
        println("ncontests with info = ${infos.size}")

        val countVotes = maker.countVotes()
        val contests = infos.map { info ->
            val contestTab = countVotes[info.id]!!
            contestTab.votes.forEach {
                if (!info.candidateIds.contains(it.key)) {
                    "contestCount ${info.id } has candidate '${it.key}' not found in contestInfo candidateIds ${info.candidateIds}"
                }
            }
            val inputVotes = contestTab.votes.filter{ info.candidateIds.contains(it.key) }
            Contest(info, inputVotes, contestTab.ncardsTabulated, contestTab.ncardsTabulated)
        }

        // some random sanity check
        val selectedContest = contests.find { it.name.startsWith("City of Boulder Council")}!!
        val candidatesById = selectedContest.info.candidateNames.map { (name, id) -> id to name }.toMap()

        assertEquals(11, candidatesById.size)
    }

}
