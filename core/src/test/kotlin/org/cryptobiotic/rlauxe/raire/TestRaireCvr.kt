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