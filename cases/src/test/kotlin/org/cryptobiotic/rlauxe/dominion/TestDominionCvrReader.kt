package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.audit.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.boulder.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestDominionCvrReader {
    @Test
    fun parseThreeCandidatesTenVotesSucceeds() {
        val filename = "src/test/data/corla/ThreeCandidatesTenVotes.csv"
        val result: DominionCvrExport = readDominionCvrExport(filename, "Saguache")
        println(result.show())

        // There should be one contest, the one we just read in.
        val schema = result.schema
        // There should be 38 contests. Check their metadata.
        assertEquals(1, schema.contests.size)
        val contest = schema.contests[0]

        // Check basic data
        assertEquals("TinyExample1 (Number of positions=1, Number of ranks=3)", contest.contestName)
        assertTrue(contest.isIRV)
        assertEquals(3, contest.nchoices)

        // Check that the 10 expected votes are there.
        assertEquals(10, result.cvrs.size)

        /*
        val reader = Files.newBufferedReader(path)

        val parser = DominionCVRExportParser(
            reader,
            fromString("Saguache"), blank, true
        )
        assertTrue(parser.parse().success)


        // There should be one contest, the one we just read in.
        val contests = forCounties(setOf(fromString("Saguache")))
        assertEquals(1, contests.size)
        val contest = contests[0]

        // Check basic data
        assertEquals(contest.name(), "TinyExample1")
        assertEquals(contest.description(), ContestType.IRV.toString())
        assertEquals(contest.choices().stream().map(Choice::name).collect(Collectors.toList()), ABC)

        // Votes allowed should be 3 (because there are 3 ranks), whereas winners=1 always for IRV.
        assertEquals(contest.votesAllowed().toInt(), 3)
        assertEquals(contest.winnersAllowed().toInt(), 1)

        // Check that the 10 expected votes are there.
        val cvrs = getMatching(
            fromString("Saguache").id(),
            CastVoteRecord.RecordType.UPLOADED
        ).map { cvr -> cvr.contestInfoForContest(contest) }.toList()
        assertEquals(10, cvrs.size)
        for (i in expectedChoices.indices) {
            assertEquals(cvrs[i].choices(), expectedChoices[i])
        }

         */
    }

    @Test
    fun test4CvrsWithIRV() {
        val filename = "src/test/data/Boulder2023/Test4CvrsWithIRV.csv"
        val export: DominionCvrExport = readDominionCvrExport(filename, "Boulder")
        println(export.summary())

        assertEquals("Boulder", export.countyId)
        assertEquals("src/test/data/Boulder2023/Test4CvrsWithIRV.csv", export.filename)
        assertEquals("2023 Coordinated Election", export.electionName)
        assertEquals("5.17.17.1", export.versionName)
        assertEquals(38, export.schema.contests.size)
        assertEquals(4, export.cvrs.size)

        val cvr0 = export.cvrs[0]
        val expectedVotes0 = mapOf(
            0 to intArrayOf(0, 1, 2, 3),
            1 to intArrayOf(4, 6, 7, 9),
            11 to intArrayOf(0),
            12 to intArrayOf(1),
            13 to intArrayOf(0),
            14 to intArrayOf(2),
            21 to intArrayOf(0),
            22 to intArrayOf(0),
            23 to intArrayOf(0),
            24 to intArrayOf(0),
            25 to intArrayOf(0),
            26 to intArrayOf(0),
            27 to intArrayOf(1),
        )
        val expected0 = makeCvr(cvr0.cvrNumber, expectedVotes0)
        val actual0 = cvr0.convert()
        assertEquals(expected0, actual0)

        val cvr1 = export.cvrs[1]
        val expectedVotes1 = mapOf(
            21 to intArrayOf(1),
            22 to intArrayOf(1),
            23 to intArrayOf(0),
            24 to intArrayOf(1),
        )
        val expected1 = makeCvr(cvr1.cvrNumber, expectedVotes1)
        val actual1 = cvr1.convert()
        assertEquals(expected1, actual1)

        val cvr2 = export.cvrs[2]
        val expectedVotes2 = mapOf(
            3 to intArrayOf(2),
            4 to intArrayOf(2),
            6 to intArrayOf(1),
            20 to intArrayOf(0),
            21 to intArrayOf(0),
            22 to intArrayOf(0),
            23 to intArrayOf(0),
            24 to intArrayOf(0),
            30 to intArrayOf(1),
            31 to intArrayOf(1),
            32 to intArrayOf(1),
        )
        val expected2 = makeCvr(cvr2.cvrNumber, expectedVotes2)
        val actual2 = cvr2.convert()
        assertEquals(expected2, actual2)

        val cvr3 = export.cvrs[3]
        val expectedVotes3 = mapOf(
            0 to intArrayOf(2, 3, 0, 1),
            1 to intArrayOf(0, 1, 7, 8),
            11 to intArrayOf(1),
            12 to intArrayOf(),
            13 to intArrayOf(),
            14 to intArrayOf(),
            21 to intArrayOf(1),
            22 to intArrayOf(0),
            23 to intArrayOf(1),
            24 to intArrayOf(1),
            25 to intArrayOf(1),
            26 to intArrayOf(1),
            27 to intArrayOf(1),
        )
        val expected3 = makeCvr(cvr3.cvrNumber, expectedVotes3)
        val actual3 = cvr3.convert()
        assertEquals(expected3, actual3)
    }

    @Test
    fun testWithRedactions() {
        val filename = "src/test/data/Boulder2024/TestWithRedactions.csv"
        val export: DominionCvrExport = readDominionCvrExport(filename, "Boulder")
        // println(export.summary())

        assertEquals("Boulder", export.countyId)
        assertEquals("src/test/data/Boulder2024/TestWithRedactions.csv", export.filename)
        assertEquals("2024 Boulder County GE Recounts", export.electionName)
        assertEquals("5.17.17.1", export.versionName)
        assertEquals(65, export.schema.contests.size)
        assertEquals(8, export.redacted.size)
        // export.redacted.forEach { println(it.contestVotes.toString()) }

        // Redacted and Aggregated,,,,,,7,265,104,0,0,2,1,1,5,2,0,0,0,0,0,0,228,74,6,2,5,0,0,233,12,0,89,209,2,5
        val cvr0 = export.redacted[0]
        assertEquals("7", cvr0.ballotType)
        var idx=0
        assertEquals(listOf(265,104,0,0,2,1,1,5,2,0,0,0,0,0,0), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(228,74,6,2,5,0,0,), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(233,12,0), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(89,209,2,5), cvr0.contestVotes[idx++]!!.toMap().values.toList())

        // ,,,227,38,,,,,212,83,,,,,228,,223,4,0,207,79,216,,,,,,,,,,,,,
        assertNull(cvr0.contestVotes[idx++])
        assertEquals(listOf(227,38), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertNull(cvr0.contestVotes[idx++])
        assertNull(cvr0.contestVotes[idx++])
        assertEquals(listOf(212,83), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertNull(cvr0.contestVotes[idx++])
        assertNull(cvr0.contestVotes[idx++])
        assertEquals(listOf(228), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertNull(cvr0.contestVotes[idx++])
        assertEquals(listOf(223,4,0), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(207,79), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(216), cvr0.contestVotes[idx++]!!.toMap().values.toList())

        for (i in idx until 20) {
            assertNull(cvr0.contestVotes[i])
        }
        // 130,87,111,50,25,36,101,175,74,147,91,163,75,167,70,145,89,162,63,150,69,
        idx = 20
        assertEquals(listOf(130,87,111,50,25,36,101), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(175,74), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(147,91), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(163,75), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(167,70), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(145,89), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(162,63), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(150,69), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        // 152,67,147,55,148,55,150,54,141,58,149,60,223,73,
        assertEquals(listOf(152,67), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(147,55), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(148,55), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(150,54), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(141,58), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(149,60), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(223,73), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        // 212,58,195,93,261,53,133,135,255,68,170,137,263,40,204,104,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
        assertEquals(listOf(212,58), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(195,93), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(261,53), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(133,135), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(255,68), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(170,137), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(263,40), cvr0.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(204,104), cvr0.contestVotes[idx++]!!.toMap().values.toList())

        for (i in idx until cvr0.contestVotes.size) {
            assertNull(cvr0.contestVotes[i])
        }

        /*
        export.redacted.forEach { redactedVotes ->
            println("ballotType=${redactedVotes.ballotType}")
            redactedVotes.contestVotes.forEach { (key, votes) ->
                println("  contest $key = ${votes.values.sum()}")
            }
            println()
        }

         */
    }


    @Test
    fun parseBoulder23Succeeds() {
        val filename = "src/test/data/Boulder2023/Boulder-2023-Coordinated-CVR-Redactions-removed.csv"
        val result = readDominionCvrExport(filename, "Boulder")
        println(result.summary())

        val schema = result.schema
        // There should be 38 contests. Check their metadata.
        assertEquals(38, schema.contests.size)

        val boulderMayoral = schema.contests[0]
        assertEquals("City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)", boulderMayoral.contestName)
        //assertEquals(boulderMayoral.votesAllowed() as Int, 4)
        //assertEquals(boulderMayoral.winnersAllowed() as Int, 1)
        val boulderCouncil = schema.contests.get(1)
        // println(schema.choices(boulderCouncil.contestIdx).forEach{ print("\"${it}\", ")} )
        assertEquals(listOf("Terri Brncic", "Jenny Robins", "Aaron Gabriel Neyer", "Jacques Decalo", "Silas Atkins", "Waylon Lewis", "Ryan Schuchard", "Tara Winer", "Tina Marquis", "Taishya Adams"),
            schema.choices(boulderCouncil.contestIdx))

        // Check that the first cvr was correctly parsed.
        assertEquals(result.cvrs.size, 118669)
        val cvr1: CastVoteRecord = result.cvrs.get(0)

        // IRV
        assertEquals(
            listOf("Aaron Brockett", "Nicole Speer", "Bob Yates", "Paul Tweedlie"),
            schema.voteFor(boulderMayoral.contestIdx, cvr1),
        )

        assertNotNull(cvr1.voteFor(boulderCouncil.contestIdx))
        assertEquals(
            listOf("Silas Atkins", "Ryan Schuchard", "Tara Winer", "Taishya Adams"),
            schema.voteFor(boulderCouncil.contestIdx, cvr1),
        )
        var cidx = 11
        assertEquals(
            listOf("Jason Unger"),
            schema.voteFor(cidx, cvr1),
        )
        cidx++
        assertEquals(
            listOf("Alex Medler"),
            schema.voteFor(cidx, cvr1),
        )
        cidx++
        assertEquals(
            listOf("Andrew Brandt"),
            schema.voteFor(cidx, cvr1),
        )
        cidx++
        assertEquals(
            listOf("Jorge ChÃ¡vez"),
            schema.voteFor(cidx, cvr1),
        )
        cidx = 21
        assertEquals(
            listOf("Yes/For"),
            schema.voteFor(cidx, cvr1),
        )
        cidx++
        assertEquals(
            listOf("Yes/For"),
            schema.voteFor(cidx, cvr1),
        )
        cidx++
        assertEquals(
            listOf("Yes/For"),
            schema.voteFor(cidx, cvr1),
        )
        cidx++
        assertEquals(
            listOf("Yes/For"),
            schema.voteFor(cidx, cvr1),
        )
        cidx++
        assertEquals(
            listOf("Yes/For"),
            schema.voteFor(cidx, cvr1),
        )
        cidx++
        assertEquals(
            listOf("Yes/For"),
            schema.voteFor(cidx, cvr1),
        )
        cidx++
        assertEquals(
            listOf("No/Against"),
            schema.voteFor(cidx, cvr1),
        )

        /* TODO
            // Test for the proper reporting of some known invalid votes.
    final String boulderMayoralName = "City of Boulder Mayoral Candidates";
    final String cvr = os.toString();
    assertTrue(StringUtils.contains(cvr,
        "county,contest,record_type,cvr_number,imprinted_id,raw_vote,valid_interpretation\n"));
    assertTrue(StringUtils.contains(cvr,
        "Boulder,"+boulderMayoralName+",UPLOADED,140,108-1-32,\"\"\"Bob Yates(1)\"\"," +
            "\"\"Bob Yates(2)\"\",\"\"Bob Yates(3)\"\",\"\"Bob Yates(4)\"\"\",\"\"\"Bob Yates\"\""));
    assertTrue(StringUtils.contains(cvr,
        "Boulder,"+boulderMayoralName+",UPLOADED,112680,108-100-48,\"\"\"Bob Yates(1)\"\"," +
            "\"\"Nicole Speer(2)\"\",\"\"Aaron Brockett(3)\"\",\"\"Bob Yates(3)\"\"," +
            "\"\"Paul Tweedlie(4)\"\"\",\"\"\"Bob Yates\"\",\"\"Nicole Speer\"\"," +
            "\"\"Aaron Brockett\"\",\"\"Paul Tweedlie\"\""));
    assertTrue(StringUtils.contains(cvr,
        "Boulder,"+boulderMayoralName+",UPLOADED,107599,101-178-114,\"\"\"Bob Yates(1)\"\"," +
            "\"\"Paul Tweedlie(1)\"\",\"\"Aaron Brockett(2)\"\",\"\"Paul Tweedlie(2)\"\"," +
            "\"\"Paul Tweedlie(3)\"\",\"\"Paul Tweedlie(4)\"\"\","));
    assertTrue(StringUtils.contains(cvr,
        "Boulder,"+boulderMayoralName+",UPLOADED,118738,101-190-124," +
            "\"\"\"Aaron Brockett(1)\"\",\"\"Bob Yates(1)\"\"\","));
  }
         */
    }

    @Test
    fun parseBoulder24Recount() {
        // redaction lines are present
        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv"
        val export: DominionCvrExport = readDominionCvrExport(filename, "Boulder County")
        println(export.summary())

        assertEquals("Boulder County", export.countyId)
        assertEquals(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv",
            export.filename
        )
        assertEquals("2024 Boulder County GE Recounts", export.electionName)
        assertEquals("5.17.17.1", export.versionName)
        assertEquals(65, export.schema.contests.size)
        assertEquals(25430, export.cvrs.size)
    }

    @Test
    fun parseBoulder24() {
        val stopwatch = Stopwatch()
        // redaction lines are present
        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
        val export: DominionCvrExport = readDominionCvrExport(filename, "Boulder")
        // println(export.summary())
        println("took = $stopwatch")

        assertEquals("Boulder", export.countyId)
        assertEquals(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            export.filename
        )
        assertEquals("2024 Boulder County General Election", export.electionName)
        assertEquals("5.17.17.1", export.versionName)
        assertEquals(65, export.schema.contests.size)
        assertEquals(384384, export.cvrs.size)

        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            "Boulder2024")

        val maker = BoulderElectionFromCvrs(export, sovo)
        val infos = maker.makeContestInfo()
        println("ncontests with info = ${infos.size}")

        /* infos.forEach { info ->
            println(info.show())
        } */
        val countVotes = maker.countVotes()
        val contests = infos.map { info ->
            val contestTab = countVotes[info.id]!!
            contestTab.votes.forEach {
                if (!info.candidateIds.contains(it.key)) {
                    "contestCount ${info.id } has candidate '${it.key}' not found in contestInfo candidateIds ${info.candidateIds}"
                }
            }
            val inputVotes = contestTab.votes.filter{ info.candidateIds.contains(it.key) }
            Contest(info, inputVotes, contestTab.ncards, 0)
        }
        /* println("ncontests with votes = ${contests.size}")
        contests.forEach { contest ->
            println(contest.show2())
        } */

        // from https://assets.bouldercounty.gov/wp-content/uploads/2024/11/2024G-Boulder-County-Official-Summary-of-Votes.pdf
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
        )

        val contestPrez = contests.find { it.name.startsWith("President")}!!
        val candidatesById = contestPrez.info.candidateNames.map { (name, id) -> id to name }.toMap()
        val votesByCandidateName = contestPrez.votes.toSortedMap().map { (id, nvotes) ->
            Pair(candidatesById[id]!!, nvotes)
        }.toMap()
        /*
        votesByCandidateName.forEach { (name, nvotes) ->
            println("  \"$name\" to $nvotes,")
        }
*/
        assertEquals(expected, votesByCandidateName)
    }

    @Test
    fun testMakeRedactedCvrs() {
        val stopwatch = Stopwatch()
        // redaction lines are present
        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
        val export: DominionCvrExport = readDominionCvrExport(filename, "Boulder")

        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            "Boulder2024")

        val maker = BoulderElectionFromCvrs(export, sovo)
        val infos = maker.makeContestInfo()
        println("ncontests with info = ${infos.size}")

        val redactedCvrs = maker.makeRedactedCvrs()
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

    @Test
    fun testIrvRedactedCvrs() {
        val stopwatch = Stopwatch()
        // redaction lines are present
        val filename = "src/test/data/Boulder2023/Redacted-2023Coordinated-CVR.csv"
        val export: DominionCvrExport = readDominionCvrExport(filename, "Boulder")

        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes.csv", "Boulder2023")
        // println("sovo = ${sovo.show()}")

        val sovoRcv = readBoulderStatementOfVotes(
            "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes-RCV.csv", "Boulder2023Rcv")
        // println("sovoRcv = ${sovoRcv.show()}")
        val irvContest: BoulderContestVotes = sovoRcv.contests.first()
        // println("irvContest = ${irvContest}")

        val combined = BoulderStatementOfVotes.combine(listOf(sovoRcv, sovo))

        val electionFromCvrs = BoulderElectionFromCvrs(export, combined)
        val (contests, raireContests) = electionFromCvrs.makeContests()
        val irvId = raireContests.first().id

        val countIrvCvrs = electionFromCvrs.cvrs.filter{ it.hasContest(irvId) }.count()
        println("countIrvCvrs = $countIrvCvrs")
        assertEquals(irvContest.totalBallots, countIrvCvrs)
    }

}

fun compareRedactions(votes1: Map<Int, Map<Int, Int>>, votes2: Map<Int, Map<Int, Int>>) = buildString {
    val svotes1 = votes1.toSortedMap()
    svotes1.forEach { (contestId, conVotes1) ->
        val conVotes2 = votes2[contestId]!!.toSortedMap()
        val sortedConVotes1 = conVotes1.toSortedMap()
        appendLine("  contest $contestId: cvrVotes = $sortedConVotes1")
        appendLine("  contest $contestId: redacted = $conVotes2")
    }
}

fun makeCvr(id: Int, votes: Map<Int, IntArray>): Cvr {
    val cvrb = CvrBuilder2(id.toString(),  false)
    votes.forEach {
        cvrb.addContest(it.key, it.value)
    }
    return cvrb.build()
}