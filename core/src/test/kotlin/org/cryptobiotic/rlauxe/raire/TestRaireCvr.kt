package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.Cvr
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRaireCvr {

    @Test
    fun testGetVoteFor() {
        // if candidate not ranked, 0, else rank (1 based)
        val rcvr = Cvr(0, listOf(1, 2, 3))
        assertEquals(0, raire_get_vote_for(rcvr, contest=1, candidate=1))
        assertEquals(1, raire_get_vote_for(rcvr, contest=0, candidate=1))
        assertEquals(2, raire_get_vote_for(rcvr, contest=0, candidate=2))
        assertEquals(3, raire_get_vote_for(rcvr, contest=0, candidate=3))
        assertEquals(0, raire_get_vote_for(rcvr, contest=0, candidate=4))
    }

    @Test
    fun testGetVoteForUnordered() {
        val rcvr = Cvr(0, listOf(3,4,2))
        assertEquals(0, raire_get_vote_for(rcvr, contest=1, candidate=1))
        assertEquals(0, raire_get_vote_for(rcvr, contest=0, candidate=1))
        assertEquals(3, raire_get_vote_for(rcvr, contest=0, candidate=2))
        assertEquals(1, raire_get_vote_for(rcvr, contest=0, candidate=3))
        assertEquals(2, raire_get_vote_for(rcvr, contest=0, candidate=4))
        assertEquals(0, raire_get_vote_for(rcvr, contest=0, candidate=0))
    }

    //  Check whether vote is a vote for the loser with respect to a 'winner only' assertion.
    //  Its a vote for the loser if they appear and the winner does not, or they appear before the winner
    @Test
    fun testLoserFunctionUnordered() {
        val rcvr = Cvr(0, listOf(3,4,2))
        assertEquals(1, raire_rcv_lfunc_wo(rcvr, contest=0, winner=1, loser=2))
        assertEquals(1, raire_rcv_lfunc_wo(rcvr, contest=0, winner=2, loser=4))
        assertEquals(0, raire_rcv_lfunc_wo(rcvr, contest=1, winner=1, loser=2))
        assertEquals(0, raire_rcv_lfunc_wo(rcvr, contest=1, winner=2, loser=4))

        assertEquals(0, raire_rcv_lfunc_wo(rcvr, contest=0, winner=3, loser=4))
        assertEquals(0, raire_rcv_lfunc_wo(rcvr, contest=0, winner=1, loser=5))
        assertEquals(0, raire_rcv_lfunc_wo(rcvr, contest=0, winner=5, loser=1))
    }

    // From SHANGRLA TestCVR
    @Test
    fun test_rcv_lfunc_wo() {
        //     votes = CVR.from_vote({"Alice": 1, "Bob": 2, "Candy": 3, "Dan": ''})
        val rcvr = Cvr(0, listOf(1, 2, 3))
        //     assert votes.rcv_lfunc_wo("AvB", "Bob", "Alice") == 1
        assertEquals(1, raire_rcv_lfunc_wo(rcvr, contest=0, winner=2, loser=1))
        //     assert votes.rcv_lfunc_wo("AvB", "Alice", "Candy") == 0
        assertEquals(0, raire_rcv_lfunc_wo(rcvr, contest=0, winner=1, loser=3))
        //     votes.rcv_lfunc_wo("AvB", "Dan", "Candy") == 1
        assertEquals(1, raire_rcv_lfunc_wo(rcvr, contest=0, winner=4, loser=3))
    }

    // if you reduce the ballot down to only those candidates in 'remaining',
    // and 'cand' is the first preference, return 1; otherwise return 0.
    @Test
    fun testVoteforCand() {
        val rcvr = Cvr(0, listOf(4, 2, 3, 5, 6))
        val remaining = listOf(2, 4, 6, 3)
        assertEquals(1, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 4, remaining))
        assertEquals(0, raire_rcv_votefor_cand(rcvr, contest = 1, cand = 4, remaining))
        assertEquals(0, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 2, remaining))
        assertEquals(0, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 1, remaining))
        assertEquals(0, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 6, remaining))
    }

    // From SHANGRLA TestCVR
    @Test
    fun test_rcv_votefor_cand() {
        //        votes = CVR.from_vote({"Alice": 1, "Bob": 2, "Candy": 3, "Dan": '', "Ross": 4, "Aaron": 5})
        val rcvr = Cvr(0, listOf(1, 2, 3, 5, 6))
        //        remaining = ["Bob","Dan","Aaron","Candy"]
        val remaining = listOf(2, 4, 6, 3)
        //        assert votes.rcv_votefor_cand("AvB", "Candy", remaining) == 0
        assertEquals(0, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 3, remaining))
        //        assert votes.rcv_votefor_cand("AvB", "Alice", remaining) == 0
        assertEquals(0, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 1, remaining))
        //        assert votes.rcv_votefor_cand("AvB", "Bob", remaining) == 1
        assertEquals(1, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 2, remaining))
        //        assert votes.rcv_votefor_cand("AvB", "Aaron", remaining) == 0
        assertEquals(0, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 6, remaining))
        //
        //        remaining = ["Dan","Aaron","Candy"]
        val remaining2 = listOf(4, 6, 3)
        //        assert votes.rcv_votefor_cand("AvB", "Candy", remaining) == 1
        assertEquals(1, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 3, remaining2))
        //        assert votes.rcv_votefor_cand("AvB", "Alice", remaining) == 0
        assertEquals(0, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 1, remaining2))
        //        assert votes.rcv_votefor_cand("AvB", "Bob", remaining) == 0
        assertEquals(0, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 2, remaining2))
        //        assert votes.rcv_votefor_cand("AvB", "Aaron", remaining) == 0
        assertEquals(0, raire_rcv_votefor_cand(rcvr, contest = 0, cand = 6, remaining2))
    }

}

/////////////////////////////////////////////////////////////////////
// DO NOT USE, use readRaireBallots()
// this is (apparently) RAIRE CSV format for ranked choice CVRs from SFDA2019
// input to RAIRE, not sure where these are produced, somewhere in SFDA2019
// eg SFDA2019_PrelimReport12VBMJustDASheets.raire
// TODO Colorado may be very different, see corla
//
// This reads:
// 1
// Contest,339,4,15,16,17,18
// 339,99813_1_1,17
// 339,99813_1_3,16
// 339,99813_1_6,18,17,15,16
// ...

/*
fun readRaireSfdaCvrs(filename: String): RaireCvrs {
    val path: Path = Paths.get(filename)
    val reader: Reader = Files.newBufferedReader(path)
    val parser = CSVParser(reader, CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the number of contests
    val ncontests = records.next().get(0).toInt()
    if (ncontests != 1) throw RuntimeException("readRaireSfdaCvrs only allows one contest")

    val contests = mutableListOf<RaireCvrContest>()
    var cvrs = mutableListOf<RaireCvr>()
    var contestId = 0
    var nchoices = 0
    var choices = emptyList<Int>()

    while (records.hasNext()) {
        val line = records.next()
        val first = line.get(0)
        if (first.equals("Contest")) {
            if (cvrs.isNotEmpty()) {
                contests.add(RaireCvrContest(contestId, choices, cvrs.size))
            }
            // start a new contest
            contestId = line.get(1).toInt()
            nchoices = line.get(2).toInt()
            choices = readVariableListOfInt(line,3)
            require(nchoices == choices.size)
            cvrs = mutableListOf()
        } else {
            val cid = line.get(0).toInt()
            val location = line.get(1)
            val rankedChoices = readVariableListOfInt(line, 2)
            require(cid == contestId)
            require(choices.containsAll(rankedChoices))
            cvrs.add(cid, location, rankedChoices))
        }
    }
    if (cvrs.isNotEmpty()) {
        contests.add(RaireCvrContest(contestId, choices, cvrs.size)) // dont know the winner
    }

    return RaireCvrs(contests, cvrs, filename)
}

private fun readVariableListOfInt(line: CSVRecord, startPos: Int): List<Int> {
    val result = mutableListOf<Int>()
    while (startPos + result.size < line.size()) {
        val s = line.get(startPos + result.size)
        if (s.isEmpty()) break
        val sn = line.get(startPos + result.size)
        result.add(sn.toInt())
    }
    return result
}

 */

