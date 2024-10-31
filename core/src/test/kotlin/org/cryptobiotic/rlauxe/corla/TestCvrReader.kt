package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.csv.CvrExport
import org.cryptobiotic.rlauxe.csv.readDominionCvrExport
import kotlin.test.Test

class TestCvrReader {
    @Test
    fun parseThreeCandidatesTenVotesSucceeds() {
        val filename = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/corla/ThreeCandidatesTenVotes.csv"
        val result: CvrExport = readDominionCvrExport(filename, "Saguache")
        println(result.show())

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
    fun parseBoulder23Succeeds() {

        val filename = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/corla/Boulder-2023-Coordinated-CVR-Redactions-removed.csv"
        val result: CvrExport = readDominionCvrExport(filename, "Boulder")
        println(result.summary())


        /* There should be 38 contests. Check their metadata.
        val contests: List<Contest> = forCounties(Set.of(fromString("Boulder")))
        assertEquals(38, contests.size)

        val boulderMayoral: Contest = contests[0]
        assertEquals(boulderMayoral.name(), "City of Boulder Mayoral Candidates")
        assertEquals(boulderMayoral.votesAllowed() as Int, 4)
        assertEquals(boulderMayoral.winnersAllowed() as Int, 1)

         */

    }
}