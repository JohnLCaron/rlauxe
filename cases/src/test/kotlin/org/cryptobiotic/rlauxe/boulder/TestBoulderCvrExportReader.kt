package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.dominion.readCvrExportsFromFile
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestBoulderCvrExportCsv {
    @Test
    fun parseThreeCandidatesTenVotesSucceeds() {
        val filename = "src/test/data/corla/1misc/ThreeCandidatesTenVotes.csv"
        val export = readCvrExportsFromFile(filename)
        println(export.show())

        // There should be one contest, the one we just read in.
        val schema = export.schema
        // There should be 38 contests. Check their metadata.
        assertEquals(1, schema.contests.size)
        val contest = schema.contests[0]

        // Check basic data
        assertEquals("TinyExample1 (Number of positions=1, Number of ranks=3)", contest.contestName)
        assertTrue(contest.isIRV)
        assertEquals(3, contest.nchoices)

        // Check that the 10 expected votes are there.
        assertEquals(10, export.cvrs.size)
    }

    @Test
    fun test4CvrsWithIRV() {
        val filename = "src/test/data/Boulder2023/Test4CvrsWithIRV.csv"
        val export = readCvrExportsFromFile(filename)
        println(export.summary())

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
        val expected0 = makeCvr(cvr0.imprintedId, expectedVotes0)
        val actual0 = cvr0.convertToCvr()
        assertEquals(expected0, actual0)

        val cvr1 = export.cvrs[1]
        val expectedVotes1 = mapOf(
            21 to intArrayOf(1),
            22 to intArrayOf(1),
            23 to intArrayOf(0),
            24 to intArrayOf(1),
        )
        val expected1 = makeCvr(cvr1.imprintedId, expectedVotes1)
        val actual1 = cvr1.convertToCvr()
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
        val expected2 = makeCvr(cvr2.imprintedId, expectedVotes2)
        val actual2 = cvr2.convertToCvr()
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
        val expected3 = makeCvr(cvr3.imprintedId, expectedVotes3)
        val actual3 = cvr3.convertToCvr()
        assertEquals(expected3, actual3)
    }

    @Test
    fun testWithRedactions() {
        val filename = "src/test/data/Boulder2024/TestWithRedactions.csv"
        val export = readCvrExportsFromFile(filename)
        // println(export.summary())

        assertEquals(filename, export.filename)
        assertEquals("2024 Boulder County GE Recounts", export.electionName)
        assertEquals("5.17.17.1", export.versionName)
        assertEquals(65, export.schema.contests.size)
        assertEquals(8, export.redactedGroups.size)
        // export.redacted.forEach { println(it.contestVotes.toString()) }

        // Redacted and Aggregated,,,,,,7,265,104,0,0,2,1,1,5,2,0,0,0,0,0,0,228,74,6,2,5,0,0,233,12,0,89,209,2,5
        val group7 = export.redactedGroups.find { it.ballotType == "7"}!!
        var idx = 0
        assertEquals(listOf(265, 104, 0, 0, 2, 1, 1, 5, 2, 0, 0, 0, 0, 0, 0), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(228, 74, 6, 2, 5, 0, 0,), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(233, 12, 0), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(89, 209, 2, 5), group7.contestVotes[idx++]!!.toMap().values.toList())

        // ,,,227,38,,,,,212,83,,,,,228,,223,4,0,207,79,216,,,,,,,,,,,,,
        assertNull(group7.contestVotes[idx++])
        assertEquals(listOf(227, 38), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertNull(group7.contestVotes[idx++])
        assertNull(group7.contestVotes[idx++])
        assertEquals(listOf(212, 83), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertNull(group7.contestVotes[idx++])
        assertNull(group7.contestVotes[idx++])
        assertEquals(listOf(228), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertNull(group7.contestVotes[idx++])
        assertEquals(listOf(223, 4, 0), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(207, 79), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(216), group7.contestVotes[idx++]!!.toMap().values.toList())

        for (i in idx until 20) {
            assertNull(group7.contestVotes[i])
        }
        // 130,87,111,50,25,36,101,175,74,147,91,163,75,167,70,145,89,162,63,150,69,
        idx = 20
        assertEquals(listOf(130, 87, 111, 50, 25, 36, 101), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(175, 74), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(147, 91), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(163, 75), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(167, 70), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(145, 89), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(162, 63), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(150, 69), group7.contestVotes[idx++]!!.toMap().values.toList())
        // 152,67,147,55,148,55,150,54,141,58,149,60,223,73,
        assertEquals(listOf(152, 67), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(147, 55), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(148, 55), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(150, 54), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(141, 58), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(149, 60), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(223, 73), group7.contestVotes[idx++]!!.toMap().values.toList())
        // 212,58,195,93,261,53,133,135,255,68,170,137,263,40,204,104,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
        assertEquals(listOf(212, 58), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(195, 93), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(261, 53), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(133, 135), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(255, 68), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(170, 137), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(263, 40), group7.contestVotes[idx++]!!.toMap().values.toList())
        assertEquals(listOf(204, 104), group7.contestVotes[idx++]!!.toMap().values.toList())

        for (i in idx until group7.contestVotes.size) {
            assertNull(group7.contestVotes[i])
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
        val export = readCvrExportsFromFile(filename)
        println(export.summary())

        val schema = export.schema
        // There should be 38 contests. Check their metadata.
        assertEquals(38, schema.contests.size)

        val boulderMayoral = schema.contests[0]
        assertEquals(
            "City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)",
            boulderMayoral.contestName
        )
        //assertEquals(boulderMayoral.votesAllowed() as Int, 4)
        //assertEquals(boulderMayoral.winnersAllowed() as Int, 1)
        val boulderCouncil = schema.contests.get(1)
        // println(schema.choices(boulderCouncil.contestIdx).forEach{ print("\"${it}\", ")} )
        assertEquals(
            listOf(
                "Terri Brncic",
                "Jenny Robins",
                "Aaron Gabriel Neyer",
                "Jacques Decalo",
                "Silas Atkins",
                "Waylon Lewis",
                "Ryan Schuchard",
                "Tara Winer",
                "Tina Marquis",
                "Taishya Adams"
            ),
            schema.choices(boulderCouncil.contestIdx)
        )

        // Check that the first cvr was correctly parsed.
        assertEquals(export.cvrs.size, 118669)
        val cvr1 = export.cvrs.get(0)

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
        val export = readCvrExportsFromFile(filename)
        println(export.summary())

        assertEquals(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv",
            export.filename
        )
        assertEquals("2024 Boulder County GE Recounts", export.electionName)
        assertEquals("5.17.17.1", export.versionName)
        assertEquals(65, export.schema.contests.size)
        assertEquals(25430, export.cvrs.size)
    }
}