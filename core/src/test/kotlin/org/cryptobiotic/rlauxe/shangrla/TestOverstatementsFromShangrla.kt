package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.CvrBuilders
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.makeCvr
import org.cryptobiotic.rlauxe.util.makeFakeContest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// from SHANGRLA test_Assertion.py
class TestOverstatementsFromShangrla {

    //     # is it weird that margin calling signature involves cvr_list,
    //    # but not so for overstatement_assorter_margin
    //    def test_overstatement_assorter_margin(self):
    //        AvB_asrtn = Assertion(
    //            contest = self.plur_con_test,
    //            winner = "Alice",
    //            loser = "Bob",
    //            assorter = Assorter(
    //                contest = self.plur_con_test,
    //                assort = lambda c:
    //                             (CVR.as_vote(c.get_vote_for("AvB", "Alice"))
    //                             - CVR.as_vote(c.get_vote_for("AvB", "Bob"))
    //                              + 1)/2,
    //                upper_bound = 1
    //            ),
    //            margin = 0.5
    //        )
    //        assert Assertion.overstatement_assorter_margin(AvB_asrtn) == 1 / 3
    //
    //     def overstatement_assorter_margin(
    //        self, error_rate_1: float = 0, error_rate_2: float = 0
    //    ) -> float:
    //        """
    //        find the overstatement assorter margin corresponding to an assumed rate of 1-vote and 2-vote overstatements
    //
    //        Parameters
    //        ----------
    //        error_rate_1: float
    //            the assumed rate of one-vote overstatement errors in the CVRs
    //        error_rate_2: float
    //            the assumed rate of two-vote overstatement errors in the CVRs
    //
    //        Returns
    //        -------
    //        the overstatement assorter margin implied by the reported margin and the assumed rates of overstatements
    //        """
    //        return (
    //            1
    //            - (error_rate_2 + error_rate_1 / 2)
    //            * self.assorter.upper_bound
    //            / self.margin
    //        ) / (2 * self.assorter.upper_bound / self.margin - 1)

    @Test
    fun test_overstatement_assorter_margin() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "Alice", "Bob", "Candy"),
        )
        val contest = makeFakeContest(info, 100)
        val assort = PluralityAssorter.makeWithVotes(contest, 0, 1)
        var margin = 0.5
        var aVb = ComparisonAssorter(contest, assort, (margin + 1) / 2)

        //        assert Assertion.overstatement_assorter_margin(AvB_asrtn) == 1 / 3
        assertEquals(1.0/3.0, overstatement_assorter_margin(aVb))
    }

    fun overstatement_assorter_margin(assort: ComparisonAssorter, error_rate_1: Double = 0.0, error_rate_2: Double = 0.0): Double {
        return (1 - (error_rate_2 + error_rate_1 / 2) * assort.assorter.upperBound() / assort.margin) / (2 * assort.assorter.upperBound() / assort.margin - 1)
    }

    fun overstatement_assorter_mean(assort: ComparisonAssorter, error_rate_1: Double = 0.0, error_rate_2: Double = 0.0): Double {
        return (1 - error_rate_1 / 2 - error_rate_2) / (2 - assort.margin / assort.assorter.upperBound())
    }

    //
    //    def test_overstatement_assorter_mean(self):
    //        AvB_asrtn = Assertion(
    //            contest = self.plur_con_test,
    //            winner = "Alice",
    //            loser = "Bob",
    //            assorter = Assorter(
    //                contest = self.plur_con_test,
    //                assort = lambda c:
    //                             (CVR.as_vote(c.get_vote_for("AvB", "Alice"))
    //                             - CVR.as_vote(c.get_vote_for("AvB", "Bob"))
    //                              + 1)/2,
    //                upper_bound = 1
    //            ),
    //            margin = 0.5
    //        )
    //        assert Assertion.overstatement_assorter_mean(AvB_asrtn) == 1/1.5
    //        assert Assertion.overstatement_assorter_mean(AvB_asrtn, error_rate_1 = 0.5) == 0.5
    //        assert Assertion.overstatement_assorter_mean(AvB_asrtn, error_rate_2 = 0.25) == 0.5
    //        assert Assertion.overstatement_assorter_mean(AvB_asrtn, error_rate_1 = 0.25, error_rate_2 = 0.25) == \
    //            (1 - 0.125 - 0.25)/(2-0.5)
    //
    //     def overstatement_assorter_mean(
    //        self, error_rate_1: float = 0, error_rate_2: float = 0
    //    ) -> float:
    //        """
    //        find the overstatement assorter mean corresponding to assumed rates of 1-vote and 2-vote overstatements
    //
    //        Parameters
    //        ----------
    //        error_rate_1: float
    //            the assumed rate of one-vote overstatement errors in the CVRs
    //        error_rate_2: float
    //            the assumed rate of two-vote overstatement errors in the CVRs
    //
    //
    //        Returns
    //        -------
    //        overstatement assorter mean implied by the assorter mean and the assumed error rates
    //        """
    //        return (1 - error_rate_1 / 2 - error_rate_2) / (
    //            2 - self.margin / self.assorter.upper_bound
    //        )

    @Test
    fun test_overstatement_assorter_mean() {
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "Alice", "Bob", "Candy"),
        )
        val contest = makeFakeContest(info, 100)

        val assort = PluralityAssorter.makeWithVotes(contest, 0, 1)
        var margin = 0.5
        var aVb = ComparisonAssorter(contest, assort, (margin + 1) / 2)

        //        assert Assertion.overstatement_assorter_mean(AvB_asrtn) == 1/1.5
        assertEquals(1.0/1.5, overstatement_assorter_mean(aVb))

        //        assert Assertion.overstatement_assorter_mean(AvB_asrtn, error_rate_1 = 0.5) == 0.5
        assertEquals(0.5, overstatement_assorter_mean(aVb, error_rate_1 = 0.5))
        //        assert Assertion.overstatement_assorter_mean(AvB_asrtn, error_rate_2 = 0.25) == 0.5
        assertEquals(0.5, overstatement_assorter_mean(aVb, error_rate_2 = 0.25))
        //        assert Assertion.overstatement_assorter_mean(AvB_asrtn, error_rate_1 = 0.25, error_rate_2 = 0.25) == \
        //            (1 - 0.125 - 0.25)/(2-0.5)
        val expected = (1 - 0.125 - 0.25)/(2-0.5)
        assertEquals(expected, overstatement_assorter_mean(aVb, error_rate_1 = 0.25, error_rate_2 = 0.25))
    }

    @Test
    fun test_overstatement() {

        // def test_overstatement(self):
        //        mvr_dict = [{'id': 1, 'votes': {'AvB': {'Alice':True}}},
        //                    {'id': 2, 'votes': {'AvB': {'Bob':True}}},
        //                    {'id': 3, 'votes': {'AvB': {}}},
        //                    {'id': 4, 'votes': {'CvD': {'Elvis':True, 'Candy':False}}},
        //                    {'id': 'phantom_1', 'votes': {'AvB': {}}, 'phantom': True}]
        //        mvrs = CVR.from_dict(mvr_dict)
        val mvrb = CvrBuilders()
            .addCvr().addContest("AvB", "Alice").ddone()
            .addCvr().addContest("AvB", "Bob").ddone()
            .addCvr().addContest("AvB").ddone()
            .addCvr().addContest("CvD", "Elvis").addCandidate("Candy", 0).ddone()
        println(mvrb.show())
        val mvrs = mvrb.build()  + Cvr.makePhantom("Phantastic", 0)

        //
        //        cvr_dict = [{'id': 1, 'votes': {'AvB': {'Alice':True}}},
        //                    {'id': 2, 'votes': {'AvB': {'Bob':True}}},
        //                    {'id': 3, 'votes': {'AvB': {}}},
        //                    {'id': 4, 'votes': {'CvD': {'Elvis':True}}},
        //                    {'id': 'phantom_1', 'votes': {'AvB': {}}, 'phantom': True}]
        //        cvrs = CVR.from_dict(cvr_dict)
        val cvrb = CvrBuilders()
            .addCvr().addContest("AvB", "Alice").ddone()
            .addCvr().addContest("AvB", "Bob").ddone()
            .addCvr().addContest("AvB").ddone()
            .addCvr().addContest("CvD", "Elvis").ddone()
        println(cvrb.show())
        val cvrs = cvrb.build() + Cvr.makePhantom("Phantastic", 0)

        //
        //        winner = ["Alice"]
        //        loser = ["Bob"]
        //
        //        aVb = Assertion(contest=self.con_test, assorter=Assorter(contest=self.con_test,
        //                        assort = (lambda c, contest_id="AvB", winr="Alice", losr="Bob":
        //                        ( CVR.as_vote(c.get_vote_for("AvB", winr))
        //                        - CVR.as_vote(c.get_vote_for("AvB", losr))
        //                        + 1)/2), upper_bound=1))
        //        aVb.margin=0.2

        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "Alice", "Bob"),
        )
        val contest = makeFakeContest(info, 100)
        val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()
        val asrtns = contestUA.pollingAssertions
        val assort = asrtns.first().assorter

        val margin = 0.2

        var aVb = ComparisonAssorter(contest, assort, (margin + 1.0)/2)

        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[0], use_style=True) == 0
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[0], use_style=False) == 0
        assertEquals(0.0, aVb.overstatementError(mvrs[0], cvrs[0], false))
        assertEquals(0.0, aVb.overstatementError(mvrs[0], cvrs[0], true))

        //
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[1], use_style=True) == -1
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[1], use_style=False) == -1
        assertEquals(-1.0, aVb.overstatementError(mvrs[0], cvrs[1], false))
        assertEquals(-1.0, aVb.overstatementError(mvrs[0], cvrs[1], true))
    //
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=True) == 1/2
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=False) == 1/2
        assertEquals(.5, aVb.overstatementError(mvrs[2], cvrs[0], false))
        assertEquals(.5, aVb.overstatementError(mvrs[2], cvrs[0], true))

        //
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[1], use_style=True) == -1/2
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[1], use_style=False) == -1/2
        assertEquals(-0.5, aVb.overstatementError(mvrs[2], cvrs[1], false))
        assertEquals(-0.5, aVb.overstatementError(mvrs[2], cvrs[1], true))

        //
        //        assert aVb.assorter.overstatement(mvrs[1], cvrs[0], use_style=True) == 1
        //        assert aVb.assorter.overstatement(mvrs[1], cvrs[0], use_style=False) == 1
        assertEquals(1.0, aVb.overstatementError(mvrs[1], cvrs[0], false))
        assertEquals(1.0, aVb.overstatementError(mvrs[1], cvrs[0], true))

        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=True) == 1/2
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=False) == 1/2
        assertEquals(0.5, aVb.overstatementError(mvrs[2], cvrs[0], false))
        assertEquals(0.5, aVb.overstatementError(mvrs[2], cvrs[0], true))

        //        assert aVb.assorter.overstatement(mvrs[3], cvrs[0], use_style=True) == 1
        //        assert aVb.assorter.overstatement(mvrs[3], cvrs[0], use_style=False) == 1/2
        assertEquals(0.5, aVb.overstatementError(mvrs[3], cvrs[0], false))
        assertEquals(1.0, aVb.overstatementError(mvrs[3], cvrs[0], true))


        //        try:
        //            tst = aVb.assorter.overstatement(mvrs[3], cvrs[3], use_style=True)
        //            raise AssertionError('aVb is not contained in the mvr or cvr')
        //        except ValueError:
        //            pass
        assertFailsWith<RuntimeException> {
            assertEquals(0.0, aVb.overstatementError(mvrs[3], cvrs[3], true))
        }

        //        assert aVb.assorter.overstatement(mvrs[3], cvrs[3], use_style=False) == 0
        assertEquals(0.0, aVb.overstatementError(mvrs[3], cvrs[3], false))
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style=True) == 1/2
        assertEquals(0.5, aVb.overstatementError(mvrs[4], cvrs[4], true))

        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style=False) == 1/2
        assertEquals(0.5, aVb.overstatementError(mvrs[4], cvrs[4], false))
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style=False) == 1/2
        assertEquals(0.5, aVb.overstatementError(mvrs[4], cvrs[4], false))
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[0], use_style=True) == 1
        assertEquals(1.0, aVb.overstatementError(mvrs[4], cvrs[0], true))
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[0], use_style=False) == 1
        assertEquals(1.0, aVb.overstatementError(mvrs[4], cvrs[0], false))
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[1], use_style=True) == 0
        assertEquals(0.0, aVb.overstatementError(mvrs[4], cvrs[1], true))
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[1], use_style=False) == 0
        assertEquals(0.0, aVb.overstatementError(mvrs[4], cvrs[1], true))
    }

    @Test
    fun test_overstatement_assorter() {
        // SHANGRLA TestAssertion
        //    def test_overstatement_assorter(self):
        //        '''
        //        (1-o/u)/(2-v/u)
        //        '''
        //        mvr_dict = [{'id': 1, 'votes': {'AvB': {'Alice':True}}},
        //                    {'id': 2, 'votes': {'AvB': {'Bob':True}}},
        //                    {'id': 3, 'votes': {'AvB': {'Candy':True}}}]
        //        mvrs = CVR.from_dict(mvr_dict)
        val mvrs = listOf(makeCvr(0), makeCvr(1), makeCvr(2))

        //
        //        cvr_dict = [{'id': 1, 'votes': {'AvB': {'Alice':True}}},
        //                    {'id': 2, 'votes': {'AvB': {'Bob':True}}}]
        //        cvrs = CVR.from_dict(cvr_dict)
        val cvrs = listOf(makeCvr(0), makeCvr(1))

        //
        //        winner = ["Alice"]
        //        loser = ["Bob", "Candy"]
        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "Alice", "Bob", "Candy"),
        )
        val contest = makeFakeContest(info, 100)
        val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()
        val asrtns = contestUA.pollingAssertions
        val assort = asrtns.first().assorter

        // python
        //
        //        aVb = Assertion(contest=self.con_test,
        //              assorter= Assorter(contest=self.con_test,
        //                        assort = (lambda c, contest_id="AvB", winr="Alice", losr="Bob":
        //                        ( CVR.as_vote(c.get_vote_for("AvB", winr))
        //                        - CVR.as_vote(c.get_vote_for("AvB", losr))
        //                        + 1)/2), upper_bound=1))
        //
        //        aVb.margin=0.2
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[0], use_style=True) == 1/1.8
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[0], use_style=False) == 1/1.8

        //         return ( 1 - self.assorter.overstatement(mvr, cvr, use_style) / self.assorter.upper_bound )
        //                / (2 - self.margin / self.assorter.upper_bound)
        //
        //        self.assorter.overstatement(mvr, cvr, use_style):
        //
        //        # sanity check
        //        if use_style and not cvr.has_contest(self.contest.id):
        //            raise ValueError(
        //                f"use_style==True but {cvr=} does not contain contest {self.contest.id}"
        //            )
        //        # assort the MVR
        //        mvr_assort = (
        //            0
        //            if mvr.phantom or (use_style and not mvr.has_contest(self.contest.id))
        //            else self.assort(mvr)
        //        )
        //        # assort the CVR
        //        cvr_assort = (
        //            self.tally_pool_means[cvr.tally_pool]
        //            if cvr.pool and self.tally_pool_means is not None
        //            else int(cvr.phantom) / 2 + (1 - int(cvr.phantom)) * self.assort(cvr)
        //        )
        //        return cvr_assort - mvr_assort

        //  so self.margin = v, but they just set v = 2.0
        //  margin = 2.0 * avgCvrAssortValue - 1.0, so avgCvrAssortValue = (margin + 1) / 2


        //        aVb.margin=0.2
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[0], use_style=True) == 1/1.8
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[0], use_style=False) == 1/1.8
        var margin = 0.2
        var aVb = ComparisonAssorter(contest, assort, (margin + 1) / 2)
        var have = aVb.bassort(mvrs[0], cvrs[0])
        assertEquals(1.0 / 1.8, have)


        //
        //        assert aVb.overstatement_assorter(mvrs[1], cvrs[0], use_style=True) == 0
        //        assert aVb.overstatement_assorter(mvrs[1], cvrs[0], use_style=False) == 0
        //
        //        aVb.margin=0.3
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[1], use_style=True) == 2/1.7
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[1], use_style=False) == 2/1.7
        margin = 0.3
        aVb = ComparisonAssorter(contest, assort, (margin + 1) / 2)
        have = aVb.bassort(mvrs[0], cvrs[1])
        assertEquals(2.0 / 1.7, have)

        //
        //        aVb.margin=0.1
        //        assert aVb.overstatement_assorter(mvrs[2], cvrs[0], use_style=True) == 0.5/1.9
        //        assert aVb.overstatement_assorter(mvrs[2], cvrs[0], use_style=False) == 0.5/1.9
        margin = 0.1
        aVb = ComparisonAssorter(contest, assort, (margin + 1) / 2)
        have = aVb.bassort(mvrs[2], cvrs[0])
        assertEquals(0.5 / 1.9, have)
    }

    @Test
    fun test_overstatement_with_phantoms() {

        // def test_overstatement(self):
        //        mvr_dict = [{'id': 1, 'votes': {'AvB': {'Alice':True}}},
        //                    {'id': 2, 'votes': {'AvB': {'Bob':True}}},
        //                    {'id': 3, 'votes': {'AvB': {}}},
        //                    {'id': 4, 'votes': {'CvD': {'Elvis':True, 'Candy':False}}},
        //                    {'id': 'phantom_1', 'votes': {'AvB': {}}, 'phantom': True}]
        //        mvrs = CVR.from_dict(mvr_dict)
        val mvrb = CvrBuilders()
            .addCvr().addContest("AvB", "Alice").ddone()
            .addCvr().addContest("AvB", "Bob").ddone()
            .addCvr().addContest("AvB").ddone()
            .addCvr().addContest("CvD", "Elvis").addCandidate("Candy", 0).ddone()
            .addPhantomCvr().addContest("AvB").ddone()
        println(mvrb.show())
        val mvrs = mvrb.build()

        //        cvr_dict = [{'id': 1, 'votes': {'AvB': {'Alice':True}}},
        //                    {'id': 2, 'votes': {'AvB': {'Bob':True}}},
        //                    {'id': 3, 'votes': {'AvB': {}}},
        //                    {'id': 4, 'votes': {'CvD': {'Elvis':True}}},
        //                    {'id': 'phantom_1', 'votes': {'AvB': {}}, 'phantom': True}]
        //        cvrs = CVR.from_dict(cvr_dict)
        val cvrb = CvrBuilders()
            .addCvr().addContest("AvB", "Alice").ddone()
            .addCvr().addContest("AvB", "Bob").ddone()
            .addCvr().addContest("AvB").ddone()
            .addCvr().addContest("CvD", "Elvis").ddone()
            .addPhantomCvr().addContest("AvB").ddone()
        println(cvrb.show())
        val cvrs = cvrb.build()

        //        winner = ["Alice"]
        //        loser = ["Bob"]
        //
        //        aVb = Assertion(contest=self.con_test, assorter=Assorter(contest=self.con_test,
        //                        assort = (lambda c, contest_id="AvB", winr="Alice", losr="Bob":
        //                        ( CVR.as_vote(c.get_vote_for("AvB", winr))
        //                        - CVR.as_vote(c.get_vote_for("AvB", losr))
        //                        + 1)/2), upper_bound=1))
        //        aVb.margin=0.2

        val info = ContestInfo(
            name = "AvB",
            id = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            candidateNames = listToMap( "Alice", "Bob"),
        )
        val contest = makeFakeContest(info, 100)
        val contestUA = ContestUnderAudit(contest, isComparison = false).makePollingAssertions()
        val asrtns = contestUA.pollingAssertions
        val assort = asrtns.first().assorter

        val margin = 0.2

        var aVb = ComparisonAssorter(contest, assort, (margin + 1.0)/2)

        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[0], use_style=True) == 0
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[0], use_style=False) == 0
        assertEquals(0.0, aVb.overstatementError(mvrs[0], cvrs[0], false))
        assertEquals(0.0, aVb.overstatementError(mvrs[0], cvrs[0], true))

        //
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[1], use_style=True) == -1
        //        assert aVb.assorter.overstatement(mvrs[0], cvrs[1], use_style=False) == -1
        assertEquals(-1.0, aVb.overstatementError(mvrs[0], cvrs[1], false))
        assertEquals(-1.0, aVb.overstatementError(mvrs[0], cvrs[1], true))
        //
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=True) == 1/2
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=False) == 1/2
        assertEquals(.5, aVb.overstatementError(mvrs[2], cvrs[0], false))
        assertEquals(.5, aVb.overstatementError(mvrs[2], cvrs[0], true))

        //
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[1], use_style=True) == -1/2
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[1], use_style=False) == -1/2
        assertEquals(-0.5, aVb.overstatementError(mvrs[2], cvrs[1], false))
        assertEquals(-0.5, aVb.overstatementError(mvrs[2], cvrs[1], true))

        //
        //        assert aVb.assorter.overstatement(mvrs[1], cvrs[0], use_style=True) == 1
        //        assert aVb.assorter.overstatement(mvrs[1], cvrs[0], use_style=False) == 1
        assertEquals(1.0, aVb.overstatementError(mvrs[1], cvrs[0], false))
        assertEquals(1.0, aVb.overstatementError(mvrs[1], cvrs[0], true))

        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=True) == 1/2
        //        assert aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style=False) == 1/2
        assertEquals(0.5, aVb.overstatementError(mvrs[2], cvrs[0], false))
        assertEquals(0.5, aVb.overstatementError(mvrs[2], cvrs[0], true))

        //        assert aVb.assorter.overstatement(mvrs[3], cvrs[0], use_style=True) == 1
        //        assert aVb.assorter.overstatement(mvrs[3], cvrs[0], use_style=False) == 1/2
        assertEquals(1.0, aVb.overstatementError(mvrs[3], cvrs[0], true))
        assertEquals(0.5, aVb.overstatementError(mvrs[3], cvrs[0], false))


        //        try:
        //            tst = aVb.assorter.overstatement(mvrs[3], cvrs[3], use_style=True)
        //            raise AssertionError('aVb is not contained in the mvr or cvr')
        //        except ValueError:
        //            pass
        assertFailsWith<RuntimeException> {
            assertEquals(0.0, aVb.overstatementError(mvrs[3], cvrs[3], true))
        }
        //        assert aVb.assorter.overstatement(mvrs[3], cvrs[3], use_style=False) == 0
        assertEquals(0.0, aVb.overstatementError(mvrs[3], cvrs[3], false))

        //// mvrs[4], cvrs[4] are the phantoms
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style=True) == 1/2
        assertEquals(0.5, aVb.overstatementError(mvrs[4], cvrs[4], true))

        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style=False) == 1/2
        assertEquals(0.5, aVb.overstatementError(mvrs[4], cvrs[4], false))
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style=False) == 1/2
        assertEquals(0.5, aVb.overstatementError(mvrs[4], cvrs[4], false))
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[0], use_style=True) == 1
        assertEquals(1.0, aVb.overstatementError(mvrs[4], cvrs[0], true))
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[0], use_style=False) == 1
        assertEquals(1.0, aVb.overstatementError(mvrs[4], cvrs[0], false))
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[1], use_style=True) == 0
        assertEquals(0.0, aVb.overstatementError(mvrs[4], cvrs[1], true))
        //        assert aVb.assorter.overstatement(mvrs[4], cvrs[1], use_style=False) == 0
        assertEquals(0.0, aVb.overstatementError(mvrs[4], cvrs[1], true))
    }


}