package org.cryptobiotic.rlauxe.corla

import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestCvrReader {
    @Test
    fun parseThreeCandidatesTenVotesSucceeds() {
        val filename = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/corla/ThreeCandidatesTenVotes.csv"
        val result: CvrExport = readDominionCvrExport(filename, "Saguache")
        println(result.show())

        // There should be one contest, the one we just read in.
        val schema = result.schema
        // There should be 38 contests. Check their metadata.
        assertEquals(1, schema.contests.size)
        val contest = schema.contests[0]

        // Check basic data
        assertEquals(contest.contestName, "TinyExample1")
        assertTrue(contest.isIRV)
        assertEquals(3, contest.nchoices)

        // Check that the 10 expected votes are there.
        assertEquals(10, result.cvrs.size)
    }

    @Test
    fun parseBoulder23Succeeds() {

        val filename = "../corla/src/test/data/corla/Boulder23/Boulder-2023-Coordinated-CVR-Redactions-removed.csv"
        val result: CvrExport = readDominionCvrExport(filename, "Boulder")
        println(result.summary())

        val schema = result.schema
        // There should be 38 contests. Check their metadata.
        assertEquals(38, schema.contests.size)

        val boulderMayoral: ContestInfo = schema.contests[0]
        assertEquals("City of Boulder Mayoral Candidates", boulderMayoral.contestName)
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
}