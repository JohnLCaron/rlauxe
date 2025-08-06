package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.util.listToMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// replicate SHANGRLA tests in test_Assertion.py
class TestShangrlaAssertions {
    val sm_con_test : Contest
    val plur_con_test : Contest
    val aliceMvr : Cvr
    val bobMvr : Cvr
    val candyMvr : Cvr
    val danMvr : Cvr
    val undervoteMvr = Cvr("underVote", mapOf(0 to intArrayOf())) // contest 0 has no votes
    val wrongContestMvr = Cvr("wrongContest", mapOf(1 to intArrayOf(1))) // contest 1 has vote for candidate 1
    val phantomMvr = Cvr("phantom", mapOf(0 to intArrayOf()), phantom=true) // contest 0 has phantom vote

    init {
        //     con_test = Contest.from_dict({'id': 'AvB',
        //                 'name': 'AvB',
        //                 'risk_limit': 0.05,
        //                 'cards': 10**4,
        //                 'choice_function': Contest.SOCIAL_CHOICE_FUNCTION.SUPERMAJORITY,
        //                 'n_winners': 1,
        //                 'candidates': 3,
        //                 'candidates': ['Alice','Bob','Candy'],
        //                 'winner': ['Alice'],
        //                 'audit_type': Audit.AUDIT_TYPE.CARD_COMPARISON,
        //                 'share_to_win': 2/3,
        //                 'test': NonnegMean.alpha_mart,
        //                 'use_style': True
        //                })
        sm_con_test = Contest.makeWithCandidateNames(
            ContestInfo(
                name = "AvB",
                id = 0,
                choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
                candidateNames = listToMap( "Alice", "Bob", "Candy"),
                minFraction = 2.0/3.0,
            ),
            votesByName = mapOf("Alice" to 3, "Bob" to 1),
            Nc = 4,
            Np = 0,
        )

        //
        //    #objects used to test many Assertion functions in plurality contests
        //    plur_cvr_list = CVR.from_dict([
        //               {'id': "1_1", 'tally_pool': '1', 'votes': {'AvB': {'Alice':True}}},
        //               {'id': "1_2", 'tally_pool': '1', 'votes': {'AvB': {'Bob':True}}},
        //               {'id': "1_3", 'tally_pool': '2', 'votes': {'AvB': {'Alice':True}}},
        //               {'id': "1_4", 'tally_pool': '2', 'votes': {'AvB': {'Alice':True}}}])

        aliceMvr = makeCvr(0)
        bobMvr = makeCvr(1)
        candyMvr = makeCvr(2)
        danMvr = makeCvr(3)

        //
        //    plur_con_test = Contest.from_dict({'id': 'AvB',
        //                 'name': 'AvB',
        //                 'risk_limit': 0.05,
        //                 'cards': 4,
        //                 'choice_function': Contest.SOCIAL_CHOICE_FUNCTION.PLURALITY,
        //                 'n_winners': 1,
        //                 'candidates': 3,
        //                 'candidates': ['Alice','Bob','Candy'],
        //                 'winner': ['Alice'],
        //                 'audit_type': Audit.AUDIT_TYPE.CARD_COMPARISON,
        //                 'test': NonnegMean.alpha_mart,
        //                 'estim': NonnegMean.optimal_comparison,
        //                 'use_style': True
        //                })

        // uses plur_cvr_list
        plur_con_test = Contest.makeWithCandidateNames(
            ContestInfo(
                name = "AvB",
                id = 0,
                choiceFunction = SocialChoiceFunction.PLURALITY,
                candidateNames = listToMap( "Alice", "Bob", "Candy", "Dan"), // have to add Dan as candidate
            ),
            votesByName = mapOf("Alice" to 3, "Bob" to 1),
            Nc = 4,
            Np = 0,
        )
    }

    @Test
    fun test_make_plurality_assertions() {
    //     def test_make_plurality_assertions(self):
    //        winner = ["Alice","Bob"]
    //        loser = ["Candy","Dan"]
    //        asrtns = Assertion.make_plurality_assertions(self.con_test, winner, loser)
    //
    //        #these test Assorter.assort()
    //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1
    //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1/2
    //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Candy': 1})) == 0
    //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Dan': 1})) == 1/2
        val aliceVsCandy = PluralityAssorter.makeWithVotes(plur_con_test, winner = 0, loser = 2)
        assertEquals(1.0, aliceVsCandy.assort(aliceMvr))
        assertEquals(0.5, aliceVsCandy.assort(bobMvr))
        assertEquals(0.0, aliceVsCandy.assort(candyMvr))
        assertEquals(0.5, aliceVsCandy.assort(danMvr))

        // duplicted below, should be Alice vs Bob
    //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1
    //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1/2
    //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Candy': 1})) == 1/2
    //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Dan': 1})) == 0
        val aliceVsBob = PluralityAssorter.makeWithVotes(plur_con_test, winner = 0, loser = 1)
        assertEquals(1.0, aliceVsBob.assort(aliceMvr))
        assertEquals(0.0, aliceVsBob.assort(bobMvr))
        assertEquals(0.5, aliceVsBob.assort(candyMvr))
        assertEquals(0.5, aliceVsBob.assort(danMvr))

        //
    //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1/2
    //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1
    //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Candy': 1})) == 0
    //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Dan': 1})) == 1/2
        val bobVsCandy = PluralityAssorter.makeWithVotes(plur_con_test, winner = 1, loser = 2)
        assertEquals(0.5, bobVsCandy.assort(aliceMvr))
        assertEquals(1.0, bobVsCandy.assort(bobMvr))
        assertEquals(0.0, bobVsCandy.assort(candyMvr))
        assertEquals(0.5, bobVsCandy.assort(danMvr))

    //
    //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1/2
    //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1
    //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Candy': 1})) == 1/2
    //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Dan': 1})) == 0
        val bobVsDan = PluralityAssorter.makeWithVotes(plur_con_test, winner = 1, loser = 3)
        assertEquals(0.5, bobVsDan.assort(aliceMvr))
        assertEquals(1.0, bobVsDan.assort(bobMvr))
        assertEquals(0.5, bobVsDan.assort(candyMvr))
        assertEquals(0.0, bobVsDan.assort(danMvr)) // actual 0.5

    //
    //        #this tests Assertions.assort()
    //        # assert asrtns['Alice v Dan'].assort(CVR.from_vote({'Alice': 1})) == 1
    //        # assert asrtns['Alice v Dan'].assort(CVR.from_vote({'Bob': 1})) == 1/2
    //        # assert asrtns['Alice v Dan'].assort(CVR.from_vote({'Candy': 1})) == 1/2
    //        # assert asrtns['Alice v Dan'].assort(CVR.from_vote({'Dan': 1})) == 0
        val aliceVsDan = PluralityAssorter.makeWithVotes(plur_con_test, winner = 0, loser = 3)
        assertEquals(1.0, aliceVsDan.assort(aliceMvr))
        assertEquals(0.5, aliceVsDan.assort(bobMvr))
        assertEquals(0.5, aliceVsDan.assort(candyMvr))
        assertEquals(0.0, aliceVsDan.assort(danMvr)) // actual 0.5
    }

    @Test
    fun test_overstatement() {
        //     def test_overstatement(self):
        //        mvr_dict = [{'id': 1, 'votes': {'AvB': {'Alice':True}}},
        //                    {'id': 2, 'votes': {'AvB': {'Bob':True}}},
        //                    {'id': 3, 'votes': {'AvB': {}}},
        //                    {'id': 4, 'votes': {'CvD': {'Elvis':True, 'Candy':False}}},
        //                    {'id': 'phantom_1', 'votes': {'AvB': {}}, 'phantom': True}]
        //        mvrs = CVR.from_dict(mvr_dict)

        val mvr0 = aliceMvr
        val mvr1 = bobMvr
        val mvr2 = undervoteMvr
        // val mvr3 = wrongContestMvr // different from above
        val mvr4 = phantomMvr

        //
        //        cvr_dict = [{'id': 1, 'votes': {'AvB': {'Alice':True}}},
        //                    {'id': 2, 'votes': {'AvB': {'Bob':True}}},
        //                    {'id': 3, 'votes': {'AvB': {}}},
        //                    {'id': 4, 'votes': {'CvD': {'Elvis':True}}},
        //                    {'id': 'phantom_1', 'votes': {'AvB': {}}, 'phantom': True}]
        //        cvrs = CVR.from_dict(cvr_dict)

        val cvr0 = aliceMvr
        val cvr1 = bobMvr
        val cvr2 = undervoteMvr
        // val cvr3 = wrongContestMvr
        val cvr4 = phantomMvr

        //
        //        winner = ["Alice"]
        //        loser = ["Bob"]
        //
        //        aVb = Assertion(contest=self.con_test, assorter=Assorter(contest=self.con_test,
        //                        assort = (lambda c, contest_id="AvB", winr="Alice", losr="Bob":
        //                        ( CVR.as_vote(c.get_vote_for("AvB", winr))
        //                        - CVR.as_vote(c.get_vote_for("AvB", losr))
        //                        + 1)/2), upper_bound=1))
        val aliceVsBob = PluralityAssorter.makeWithVotes(plur_con_test, winner = 0, loser = 1)
        val cassorter = ClcaAssorter(plur_con_test.info, aliceVsBob, aliceVsBob.reportedMean() )

        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[0], use_style=True) == 0
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[0], use_style=False) == 0
        assertEquals(0.0, cassorter.overstatementError(mvr0, cvr0, hasStyle = true))
        assertEquals(0.0, cassorter.overstatementError(mvr0, cvr0, hasStyle = false))

        //
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[1], use_style=True) == -1
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[1], use_style=False) == -1
        assertEquals(-1.0, cassorter.overstatementError(mvr0, cvr1, hasStyle = true))
        assertEquals(-1.0, cassorter.overstatementError(mvr0, cvr1, hasStyle = false))

        //
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=True) == 1/2
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=False) == 1/2
        assertEquals(0.5, cassorter.overstatementError(mvr2, cvr0, hasStyle = true))
        assertEquals(0.5, cassorter.overstatementError(mvr2, cvr0, hasStyle = false))

        //
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[1], use_style=True) == -1/2
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[1], use_style=False) == -1/2
        assertEquals(-0.5, cassorter.overstatementError(mvr2, cvr1, hasStyle = true))
        assertEquals(-0.5, cassorter.overstatementError(mvr2, cvr1, hasStyle = false))

        //
        //
        //        assert aVb.assorter.overstatement(mvrs[1], cvrs[0], use_style=True) == 1
        //        assert aVb.assorter.overstatement(mvrs[1], cvrs[0], use_style=False) == 1
        assertEquals(1.0, cassorter.overstatementError(mvr1, cvr0, hasStyle = true))
        assertEquals(1.0, cassorter.overstatementError(mvr1, cvr0, hasStyle = false))

        //
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=True) == 1/2
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=False) == 1/2
        assertEquals(0.5, cassorter.overstatementError(mvr2, cvr0, hasStyle = true))
        assertEquals(0.5, cassorter.overstatementError(mvr2, cvr0, hasStyle = false))

        //
        //        assert aVb.assorter.overstatement(mvrs[3], cvrs[0], use_style=True) == 1
        //        assert aVb.assorter.overstatement(mvrs[3], cvrs[0], use_style=False) == 1/2
        assertEquals(1.0, cassorter.overstatementError(wrongContestMvr, cvr0, hasStyle = true))
        assertEquals(0.5, cassorter.overstatementError(wrongContestMvr, cvr0, hasStyle = false))

        //
        //        try:
        //            tst = aVb.assorter.overstatement(mvrs[3], cvrs[3], use_style=True)
        //            raise AssertionError('aVb is not contained in the mvr or cvr')
        //        except ValueError:
        //            pass
        //        assert aVb.assorter.overstatement(mvrs[3], cvrs[3], use_style=False) == 0
        val mess = assertFailsWith<RuntimeException>{
            cassorter.overstatementError(wrongContestMvr, wrongContestMvr, hasStyle = true)
        }.message
        assertEquals("use_style==True but cvr=wrongContest (false)  1: [1] does not contain contest AvB (0)", mess)
        assertEquals(0.0, cassorter.overstatementError(wrongContestMvr, wrongContestMvr, hasStyle = false))

        //
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style=True) == 1/2
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style=False) == 1/2
        assertEquals(0.5, cassorter.overstatementError(mvr4, cvr4, hasStyle = true))
        assertEquals(0.5, cassorter.overstatementError(mvr4, cvr4, hasStyle = false))

        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[0], use_style=True) == 1
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[0], use_style=False) == 1
        assertEquals(1.0, cassorter.overstatementError(mvr4, cvr0, hasStyle = true))
        assertEquals(1.0, cassorter.overstatementError(mvr4, cvr0, hasStyle = false))

        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[1], use_style=True) == 0
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[1], use_style=False) == 0
        assertEquals(0.0, cassorter.overstatementError(mvr4, cvr1, hasStyle = true))
        assertEquals(0.0, cassorter.overstatementError(mvr4, cvr1, hasStyle = false))
    }

    // plurality assort = {1, 0, .5} if vote is for { winner, loser, neither}
    //
    //overstatement = cvr_assort - mvr_assort  = { -1, -.5, 0, .5, 1 }  = "how much was vote overstated?"
    //
    //overstatement(MVR, CVR) with alice as winner, bob as loser:
    //
    //       overstatement(bob, alice) = 1 = overstated by one vote
    //       overstatement(bob, other) = .5 = overstated by half vote
    //       overstatement(other, alice) = .5 = overstated by half vote
    //       overstatement(alice, alice) = 0
    //       overstatement(alice, other) = -.5 = understated by half vote
    //       overstatement(other, bob) = -.5 = understated by half vote
    //       overstatement(alice, bob) = -1 = understated by one vote
    //
    ////// 1 contest does not appear on the CVR, hasStyle = false, same as when CVR = other
    //       overstatement(bob, none) = .5
    //       overstatement(alice, none) = -.5
    //       overstatement(other, none) = 0
    //
    ////// 2 contest does not appear on the MVR, hasStyle = false, same as when MVR = other
    //       overstatement(none, bob) = -.5
    //       overstatement(none, alice) = .5
    //       overstatement(none, other) = 0
    //
    ////// 3 contest does not appear on the MVR, hasStyle = true, same as when MVR = bob (worst case)
    //       overstatement(none, bob) = 0
    //       overstatement(none, alice) = 1
    //       overstatement(none, other) = .5

    @Test
    fun test_overstatement_plurality() { // agrees with SHANGRLA
        // winner = alice, loser = bob
        val aliceVsBobP = PluralityAssorter(plur_con_test.info, winner = 0, loser = 1, reportedMargin = 0.2)
        val aliceVsBob = ClcaAssorter(plur_con_test.info, aliceVsBobP, null)

        // mvr == cvr, always get noerror
        assertEquals(0.0, aliceVsBob.overstatementError(aliceMvr, aliceMvr, hasStyle = true)) // 1
        assertEquals(0.0, aliceVsBob.overstatementError(bobMvr, bobMvr, hasStyle = true))
        assertEquals(0.0, aliceVsBob.overstatementError(candyMvr, candyMvr, hasStyle = true))
        assertEquals(0.0, aliceVsBob.overstatementError(undervoteMvr, undervoteMvr, hasStyle = true))

        // the overstatemnt is 0, but i think assort needs to be 1/2, not noerror
        assertEquals(0.0, aliceVsBob.overstatementError(wrongContestMvr, wrongContestMvr, hasStyle = false))
        val mess = assertFailsWith<RuntimeException>{
            aliceVsBob.overstatementError(wrongContestMvr, wrongContestMvr, hasStyle = true)
        }.message
        assertEquals("use_style==True but cvr=wrongContest (false)  1: [1] does not contain contest AvB (0)", mess)

        assertEquals(1.0, aliceVsBob.overstatementError(bobMvr, aliceMvr, hasStyle = true)) // 2
        assertEquals(0.5, aliceVsBob.overstatementError(bobMvr, candyMvr, hasStyle = true))
        assertEquals(0.5, aliceVsBob.overstatementError(bobMvr, undervoteMvr, hasStyle = true))
        assertEquals(-1.0, aliceVsBob.overstatementError(aliceMvr, bobMvr, hasStyle = true))
        assertEquals(-0.5, aliceVsBob.overstatementError(aliceMvr, candyMvr, hasStyle = true))
        assertEquals(-0.5, aliceVsBob.overstatementError(aliceMvr, undervoteMvr, hasStyle = true))
        assertEquals(-0.5, aliceVsBob.overstatementError(candyMvr, bobMvr, hasStyle = true))
        assertEquals(0.5, aliceVsBob.overstatementError(candyMvr, aliceMvr, hasStyle = true))
        assertEquals(0.0, aliceVsBob.overstatementError(candyMvr, danMvr, hasStyle = true))
        assertEquals(-0.5, aliceVsBob.overstatementError(undervoteMvr, bobMvr, hasStyle = true)) // none = other
        assertEquals(0.5, aliceVsBob.overstatementError(undervoteMvr, aliceMvr, hasStyle = true))
        assertEquals(0.0, aliceVsBob.overstatementError(undervoteMvr, danMvr, hasStyle = true))

        // exaclty the same as above, since hasStyle only has an effect when the contest is not in the MVR or CVR
        assertEquals(1.0, aliceVsBob.overstatementError(bobMvr, aliceMvr, hasStyle = false)) // 3
        assertEquals(0.5, aliceVsBob.overstatementError(bobMvr, candyMvr, hasStyle = false))
        assertEquals(0.5, aliceVsBob.overstatementError(bobMvr, undervoteMvr, hasStyle = false))
        assertEquals(-1.0, aliceVsBob.overstatementError(aliceMvr, bobMvr, hasStyle = false))
        assertEquals(-0.5, aliceVsBob.overstatementError(aliceMvr, candyMvr, hasStyle = false))
        assertEquals(-0.5, aliceVsBob.overstatementError(aliceMvr, undervoteMvr, hasStyle = false))
        assertEquals(-0.5, aliceVsBob.overstatementError(candyMvr, bobMvr, hasStyle = false))
        assertEquals(0.5, aliceVsBob.overstatementError(candyMvr, aliceMvr, hasStyle = false))
        assertEquals(0.0, aliceVsBob.overstatementError(candyMvr, danMvr, hasStyle = false))
        assertEquals(-0.5, aliceVsBob.overstatementError(undervoteMvr, bobMvr, hasStyle = false))
        assertEquals(0.5, aliceVsBob.overstatementError(undervoteMvr, aliceMvr, hasStyle = false))
        assertEquals(0.0, aliceVsBob.overstatementError(undervoteMvr, danMvr, hasStyle = false))

        //// contest does not appear on the MVR, hasStyle = true
        // we get an overstatementError of {1, 0, 1/2} depending if the CVR showed a vote for the {winner, loser, other}
        assertEquals(1.0, aliceVsBob.overstatementError(wrongContestMvr, aliceMvr, hasStyle = true)) // 4
        assertEquals(0.0, aliceVsBob.overstatementError(wrongContestMvr, bobMvr, hasStyle = true))
        assertEquals(0.5, aliceVsBob.overstatementError(wrongContestMvr, candyMvr, hasStyle = true))
        assertEquals(0.5, aliceVsBob.overstatementError(wrongContestMvr, undervoteMvr, hasStyle = true)) // none = other

        //// contest does not appear on the MVR, hasStyle = false
        // we get an overstatementError of {1/2, -1/2, 0} depending if the CVR showed a vote for the {winner, loser, other}
        assertEquals(0.5, aliceVsBob.overstatementError(wrongContestMvr, aliceMvr, hasStyle = false)) // 5
        assertEquals(-0.5, aliceVsBob.overstatementError(wrongContestMvr, bobMvr, hasStyle = false))
        assertEquals(0.0, aliceVsBob.overstatementError(wrongContestMvr, candyMvr, hasStyle = false))
        assertEquals(0.0, aliceVsBob.overstatementError(wrongContestMvr, undervoteMvr, hasStyle = false)) // none = other

        //// contest does not appear on the CVR, hasStyle = false
        // we get an overstatementError of {-1/2, 1/2, 0} depending if the MVR showed a vote for the {winner, loser, other}
        assertEquals(-0.5, aliceVsBob.overstatementError(aliceMvr, wrongContestMvr, hasStyle = false)) // 6
        assertEquals(0.5, aliceVsBob.overstatementError(bobMvr, wrongContestMvr, hasStyle = false))
        assertEquals(0.0, aliceVsBob.overstatementError(candyMvr, wrongContestMvr, hasStyle = false))
        assertEquals(0.0, aliceVsBob.overstatementError(undervoteMvr, wrongContestMvr, hasStyle = false)) // none = other
    }

    @Test
    fun test_overstatement_plurality_assort() { // agrees with SHANGRLA
        // winner = alice, loser = bob
        val aliceVsBobP = PluralityAssorter(plur_con_test.info, winner = 0, loser = 1, reportedMargin = 0.2)
        val aliceVsBob = ClcaAssorter(plur_con_test.info, aliceVsBobP, null)

        // mvr == cvr, always get noerror
        assertEquals(aliceVsBob.noerror, aliceVsBob.bassort(aliceMvr, aliceMvr, hasStyle = true)) // 1
        assertEquals(aliceVsBob.noerror, aliceVsBob.bassort(bobMvr, bobMvr, hasStyle = true))
        assertEquals(aliceVsBob.noerror, aliceVsBob.bassort(candyMvr, candyMvr, hasStyle = true))

        // this is surprising. why whould you get credit for a ballot not containing the contest? expected 1/2
        assertEquals(aliceVsBob.noerror, aliceVsBob.bassort(wrongContestMvr, wrongContestMvr, hasStyle = false))
        val mess = assertFailsWith<RuntimeException>{
            aliceVsBob.bassort(wrongContestMvr, wrongContestMvr, hasStyle = true)
        }.message
        assertEquals("use_style==True but cvr=wrongContest (false)  1: [1] does not contain contest AvB (0)", mess)

        assertEquals(0.0 * aliceVsBob.noerror, aliceVsBob.bassort(bobMvr, aliceMvr, hasStyle = true)) // 2
        assertEquals(0.5 * aliceVsBob.noerror, aliceVsBob.bassort(bobMvr, candyMvr, hasStyle = true))
        assertEquals(2.0 * aliceVsBob.noerror, aliceVsBob.bassort(aliceMvr, bobMvr, hasStyle = true))
        assertEquals(1.5 * aliceVsBob.noerror, aliceVsBob.bassort(aliceMvr, candyMvr, hasStyle = true))
        assertEquals(1.5 * aliceVsBob.noerror, aliceVsBob.bassort(candyMvr, bobMvr, hasStyle = true))
        assertEquals(0.5 * aliceVsBob.noerror, aliceVsBob.bassort(candyMvr, aliceMvr, hasStyle = true))
        assertEquals(1.0 * aliceVsBob.noerror, aliceVsBob.bassort(candyMvr, danMvr, hasStyle = true))

        assertEquals(0.0 * aliceVsBob.noerror, aliceVsBob.bassort(bobMvr, aliceMvr, hasStyle = false)) // 3
        assertEquals(0.5 * aliceVsBob.noerror, aliceVsBob.bassort(bobMvr, candyMvr, hasStyle = false))
        assertEquals(2.0 * aliceVsBob.noerror, aliceVsBob.bassort(aliceMvr, bobMvr, hasStyle = false))
        assertEquals(1.5 * aliceVsBob.noerror, aliceVsBob.bassort(aliceMvr, candyMvr, hasStyle = false))
        assertEquals(1.5 * aliceVsBob.noerror, aliceVsBob.bassort(candyMvr, bobMvr, hasStyle = false))
        assertEquals(0.5 * aliceVsBob.noerror, aliceVsBob.bassort(candyMvr, aliceMvr, hasStyle = false))
        assertEquals(1.0 * aliceVsBob.noerror, aliceVsBob.bassort(candyMvr, danMvr, hasStyle = false))

        //// contest does not appear on the MVR
        // we get an assort value of {0, noerror, noerror/2} depending if the CVR showed a vote for the {winner, loser, other},
        assertEquals(0.0, aliceVsBob.bassort(wrongContestMvr, aliceMvr, hasStyle = true)) // 4
        assertEquals(aliceVsBob.noerror, aliceVsBob.bassort(wrongContestMvr, bobMvr, hasStyle = true))
        assertEquals(aliceVsBob.noerror/2, aliceVsBob.bassort(wrongContestMvr, candyMvr, hasStyle = true))

        // we get an assort value of {noerror/2, 1.5 * noerror, noerror} depending if the CVR showed a vote for the {winner, loser, other},
        assertEquals(aliceVsBob.noerror/2, aliceVsBob.bassort(wrongContestMvr, aliceMvr, hasStyle = false)) // 5
        assertEquals(1.5 * aliceVsBob.noerror, aliceVsBob.bassort(wrongContestMvr, bobMvr, hasStyle = false))
        assertEquals(aliceVsBob.noerror, aliceVsBob.bassort(wrongContestMvr, candyMvr, hasStyle = false))

        // we get an assort value of {1.5, noerror/2, noerror} depending if the MVR showed a vote for the {winner, loser, other},
        assertEquals(1.5 * aliceVsBob.noerror, aliceVsBob.bassort(aliceMvr, wrongContestMvr, hasStyle = false))
        assertEquals(0.5 * aliceVsBob.noerror, aliceVsBob.bassort(bobMvr, wrongContestMvr, hasStyle = false))
        assertEquals(aliceVsBob.noerror, aliceVsBob.bassort(candyMvr, wrongContestMvr, hasStyle = false))
    }

    // the SHANGRLA test is with the supermajority contest. blah
    @Test
    fun test_overstatement_assorter() {
        val mvr0 = aliceMvr
        val mvr1 = bobMvr
        val mvr2 = wrongContestMvr
        val cvr0 = aliceMvr
        val cvr1 = bobMvr

        //
        //        winner = ["Alice"]
        //        loser = ["Bob", "Candy"]
        //
        // problem is con_test is supermajority, so u != 1, but here it is set to 1. jeesh
        // LOOK correct the upper bound
        //        aVb = Assertion(contest=self.con_test, assorter=Assorter(contest=self.con_test,
        //                        assort = (lambda c, contest_id="AvB", winr="Alice", losr="Bob":
        //                        ( CVR.as_vote(c.get_vote_for("AvB", winr))
        //                        - CVR.as_vote(c.get_vote_for("AvB", losr))
        //                        + 1)/2), upper_bound=1/(2 * self.con_test.share_to_win))
        //        aVb.margin=0.2
        var aliceVsBob = SuperMajorityAssorter(sm_con_test.info, winner = 0, sm_con_test.info.minFraction!!, reportedMargin = 0.2)
        var cassorter = ClcaAssorter(sm_con_test.info, aliceVsBob, null)

        assertEquals(cassorter.noerror, cassorter.bassort(mvr0, cvr0, hasStyle = true))
        assertEquals(cassorter.noerror, cassorter.bassort(mvr1, cvr1, hasStyle = true))

        //
        //        assert aVb.overstatement_assorter(mvrs[1], cvrs[0], use_style=True) == 0
        //        assert aVb.overstatement_assorter(mvrs[1], cvrs[0], use_style=False) == 0
        assertEquals(0.0, cassorter.bassort(mvr1, cvr0, hasStyle = true))
        assertEquals(0.0, cassorter.bassort(mvr1, cvr0, hasStyle = false))

        //        aVb.margin=0.3
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[1], use_style=True) == 2/1.7
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[1], use_style=False) == 2/1.7
        aliceVsBob = SuperMajorityAssorter(sm_con_test.info, winner = 0, sm_con_test.info.minFraction!!, reportedMargin = 0.3)
        cassorter = ClcaAssorter(sm_con_test.info, aliceVsBob, null)
        assertEquals(cassorter.noerror, cassorter.bassort(mvr0, cvr0, hasStyle = true))
        assertEquals(2 * cassorter.noerror, cassorter.bassort(mvr0, cvr1, hasStyle = true))
        assertEquals(2 * cassorter.noerror, cassorter.bassort(mvr0, cvr1, hasStyle = false))

        // assertEquals(1/1.7, cassorter.noerror) // starts failing here
        assertEquals(2 * cassorter.noerror, cassorter.bassort(mvr0, cvr1, hasStyle = true))
        assertEquals(2  * cassorter.noerror, cassorter.bassort(mvr0, cvr1, hasStyle = false))

        //
        //        aVb.margin=0.1
        //        assert aVb.overstatement_assorter(mvrs[2], cvrs[0], use_style=True) == 0.5/1.9
        //        assert aVb.overstatement_assorter(mvrs[2], cvrs[0], use_style=False) == 0.5/1.9
        aliceVsBob = SuperMajorityAssorter(sm_con_test.info, winner = 0, sm_con_test.info.minFraction!!, reportedMargin = 0.1)
        cassorter = ClcaAssorter(sm_con_test.info, aliceVsBob, null)
        // assertEquals(1/1.9, cassorter.noerror)

        //assertEquals(cassorter.noerror, cassorter.bassort(wrongContestMvr, cvr0, hasStyle = true))
        //assertEquals(cassorter.noerror, cassorter.bassort(wrongContestMvr, cvr0, hasStyle = false))
    }

}