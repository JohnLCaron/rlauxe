package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.util.CvrBuilders
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.makeCvr
import org.cryptobiotic.rlauxe.core.makePluralityAssertions
import org.cryptobiotic.rlauxe.core.makeSuperMajorityAssertions
import kotlin.test.Test
import kotlin.test.assertEquals

// from SHANGRLA test_Assertion.py
class TestAssertionsFromShangrla {

    //     def test_make_plurality_assertions(self):
    //        winner = ["Alice","Bob"]
    //        loser = ["Candy","Dan"]
    //        asrtns = Assertion.make_plurality_assertions(self.con_test, winner, loser)
    //
    //        #these test Assorter.assort()
    //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1, \
    //               f"{asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Alice': 1}))=}"
    //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1/2
    //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Candy': 1})) == 0
    //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Dan': 1})) == 1/2
    //
    //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1
    //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1/2
    //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Candy': 1})) == 1/2
    //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Dan': 1})) == 0
    //
    //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1/2
    //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1
    //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Candy': 1})) == 0
    //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Dan': 1})) == 1/2
    //
    //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1/2
    //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1
    //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Candy': 1})) == 1/2
    //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Dan': 1})) == 0

    @Test
    fun test_make_plurality_assertions() {
        val contest = AuditContest(
            id = "ABCs",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidates = listOf(0, 1, 2, 3), // Alice, Bob, Candy, Dan
            winners = listOf(0, 1), // Alice, Bob
        )

        val asrtns = makePluralityAssertions(contest = contest)

        val aliceVsCandy = asrtns.find { it.assorter.desc().contains("winner=0 loser=2") }!!

        //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1, \
        //               f"{asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Alice': 1}))=}"
        assertEquals(1.0, aliceVsCandy.assorter.assort(makeCvr(0)))
        //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1/2
        assertEquals(0.5, aliceVsCandy.assorter.assort(makeCvr(1)))
        //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Candy': 1})) == 0
        assertEquals(0.0, aliceVsCandy.assorter.assort(makeCvr(2)))
        //        assert asrtns['Alice v Candy'].assorter.assort(CVR.from_vote({'Dan': 1})) == 1/2
        assertEquals(0.5, aliceVsCandy.assorter.assort(makeCvr(3)))
        //

        val aliceVsDan = asrtns.find { it.assorter.desc().contains("winner=0 loser=3") }!!
        //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1
        assertEquals(1.0, aliceVsDan.assorter.assort(makeCvr(0)))
        //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1/2
        assertEquals(0.5, aliceVsDan.assorter.assort(makeCvr(1)))
        //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Candy': 1})) == 1/2
        assertEquals(0.5, aliceVsDan.assorter.assort(makeCvr(2)))
        //        assert asrtns['Alice v Dan'].assorter.assort(CVR.from_vote({'Dan': 1})) == 0
        assertEquals(0.0, aliceVsDan.assorter.assort(makeCvr(3)))
        //
        val bobVsCandy = asrtns.find { it.assorter.desc().contains("winner=1 loser=2") }!!
        //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1/2
        assertEquals(0.5, bobVsCandy.assorter.assort(makeCvr(0)))
        //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1
        assertEquals(1.0, bobVsCandy.assorter.assort(makeCvr(1)))
        //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Candy': 1})) == 0
        assertEquals(0.0, bobVsCandy.assorter.assort(makeCvr(2)))
        //        assert asrtns['Bob v Candy'].assorter.assort(CVR.from_vote({'Dan': 1})) == 1/2
        assertEquals(0.5, bobVsCandy.assorter.assort(makeCvr(3)))
        //
        val bobVsDan = asrtns.find { it.assorter.desc().contains("winner=1 loser=3") }!!
        //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Alice': 1})) == 1/2
        assertEquals(0.5, bobVsDan.assorter.assort(makeCvr(0)))
        //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Bob': 1})) == 1
        assertEquals(1.0, bobVsDan.assorter.assort(makeCvr(1)))
        //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Candy': 1})) == 1/2
        assertEquals(0.5, bobVsDan.assorter.assort(makeCvr(2)))
        //        assert asrtns['Bob v Dan'].assorter.assort(CVR.from_vote({'Dan': 1})) == 0
        assertEquals(0.0, bobVsDan.assorter.assort(makeCvr(3)))
    }

    //     def test_supermajority_assorter(self):
    //        loser = ['Bob','Candy']
    //        assn = Assertion.make_supermajority_assertion(contest=self.con_test, winner="Alice",
    //                                                      loser=loser)
    //
    //        label = 'Alice v ' + Contest.CANDIDATES.ALL_OTHERS
    //        votes = CVR.from_vote({"Alice": 1})
    //        assert assn[label].assorter.assort(votes) == 3/4, "wrong value for vote for winner"
    //
    //        votes = CVR.from_vote({"Bob": True})
    //        assert assn[label].assorter.assort(votes) == 0, "wrong value for vote for loser"
    //
    //        votes = CVR.from_vote({"Dan": True})
    //        assert assn[label].assorter.assort(votes) == 1/2, "wrong value for invalid vote--Dan"
    //
    //        votes = CVR.from_vote({"Alice": True, "Bob": True})
    //        assert assn[label].assorter.assort(votes) == 1/2, "wrong value for invalid vote--Alice & Bob"
    //
    //        votes = CVR.from_vote({"Alice": False, "Bob": True, "Candy": True})
    //        assert assn[label].assorter.assort(votes) == 1/2, "wrong value for invalid vote--Bob & Candy"

    @Test
    fun test_supermajority_assorter() {
        val contest = AuditContest(
            id = "ABCs",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidates = listOf(0, 1, 2),
            winners = listOf(0),
            minFraction = 2.0 / 3.0,
        )

        val target = makeSuperMajorityAssertions(contest = contest).first()

        var votes = makeCvr(0)
        assertEquals(0.75, target.assorter.assort(votes), "wrong value for vote for winner")

        votes = makeCvr(1)
        assertEquals(0.0, target.assorter.assort(votes), "wrong value for vote for loser")

        // A vote for someone not in the candidate list
        votes = makeCvr(3)
        assertEquals(0.5, target.assorter.assort(votes), "wrong value for vote for invalid vote--Dan")

        var cvr = CvrBuilders().addCrv().addContest("AvB", "Alice").addCandidate("Bob").done().build().first()
        assertEquals(0.5, target.assorter.assort(cvr), "wrong value for vote for invalid vote--Alice & Bob")

        cvr = CvrBuilders().addCrv().addContest("AvB", "Bob").addCandidate("Candy").done().build().first()
        assertEquals(0.5, target.assorter.assort(cvr), "wrong value for vote for invalid vote--Bob & Candy")
    }
}