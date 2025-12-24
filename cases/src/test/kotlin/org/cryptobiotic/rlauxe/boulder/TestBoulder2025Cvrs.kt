package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.util.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
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
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrFilename, "Boulder")
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
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrFilename, "Boulder")
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

        val maker = CreateBoulderElection(export, sovo, isClca=true, distributeOvervotes=emptyList(),  poolsHaveOneCardStyle=true)
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
            Contest(info, inputVotes, contestTab.ncards, contestTab.ncards)
        }

        /* from https://assets.bouldercounty.gov/wp-content/uploads/2024/11/2024G-Boulder-County-Official-Summary-of-Votes.pdf
        val expected = mapOf(
            "Kamala D. Harris / Tim Walz" to 150149,
            "Donald J. Trump / JD Vance" to 40758,
            "Blake Huber / Andrea Denault" to 123,
            "Chase Russell Oliver / Mike ter Maat" to 1263,
            "Jill Stein / Rudolph Ware" to 1499,
            "Randall Terry / Stephen E Broden" to 147,
            "Cornel West / Melina Abdullah" to 457,
            "Robert F. Kennedy Jr. / Nicole Shanahan" to 1754,
            "Chris Garrity / Cody Ballard" to 4,
            "Claudia De la Cruz / Karina García" to 82,
            "Shiva Ayyadurai / Crystal Ellis" to 2,
            "Peter Sonski / Lauren Onak" to 65,
            "Bill Frankel / Steve Jenkins" to 1,
            "Brian Anthony Perry / Mark Sbani" to 0,
        ) */

        val contestPrez = contests.find { it.name.startsWith("President")}!!
        val candidatesById = contestPrez.info.candidateNames.map { (name, id) -> id to name }.toMap()
        val votesByCandidateName = contestPrez.votes.toSortedMap().map { (id, nvotes) ->
            Pair(candidatesById[id]!!, nvotes)
        }.toMap()
        // assertEquals(expected, votesByCandidateName)
    }

    @Test
    fun testMakeRedactedCvrs() {
        val stopwatch = Stopwatch()
        // redaction lines are present
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrFilename, "Boulder")

        val electionSimCvrs = CreateBoulderElection(export, sovo, isClca = true,  poolsHaveOneCardStyle=true)
        val infos = electionSimCvrs.makeContestInfo()
        println("ncontests with info = ${infos.size}")

        val redactedCvrs = electionSimCvrs.makeRedactedCvrs()
        println("nredacted cvrs = ${redactedCvrs.size}")
        println("took = $stopwatch")

        // TODO check that vote tallies agree...
        val redactedCvrVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(redactedCvrs.iterator())

        val redactedDirect = mutableMapOf<Int, MutableMap<Int, Int>>()
        export.redacted.forEach { redacted ->
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
