package org.cryptobiotic.rlauxe.unitTests

import org.cryptobiotic.rlauxe.core.AuditContest
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import java.lang.Math.log
import kotlin.math.ceil
import kotlin.test.*

// TODO test_rcv_assorter

class TestAssertion {
    /*

    val superContest: AuditContest
    val plur_cvr_list: List<org.cryptobiotic.rlauxe.core.Cvr>
    val plur_contest: AuditContest
    val raw_AvB_asrtn: org.cryptobiotic.rlauxe.core.Assertion
    val comparison_audit: AuditContest
    init {
        // val id: String,
        //    val idx: Int,
        //    var candidates: List<Int>,
        //    val winners: List<Int>,
        //    val choiceFunction: SocialChoiceFunction = SocialChoiceFunction.PLURALITY,
        //    val minFraction:
        superContest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.SUPERMAJORITY,
            candidates = listOf(0, 1, 2),
            winners = listOf(0),
            minFraction = 2.0/3,
        )

        // objects used to test many Assertion functions in plurality contests
        plur_cvr_list = CvrBuilders()
            .add(id = "1_1", tally_pool = "1", ContestVotes("AvB", "Alice", 1))
            .add(id = "1_2", tally_pool = "1", ContestVotes("AvB", "Bob", true))
            .add(id = "1_3", tally_pool = "2", ContestVotes("AvB", "Alice", true))
            .add(id = "1_4", tally_pool = "2", ContestVotes("AvB", "Alice", true))
            .build()

        plur_contest = AuditContest(
            id = "AvB",
            idx = 0,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            // candidates = 3,
            candidates = listOf("Alice", "Bob", "Candy"),
            reported_winners = listOf("Alice"),
            audit_type = AuditType.CARD_COMPARISON,
            estimFn = { x -> NonnegMean().optimal_comparison(0.2) },
        )

        // assertion without a margin
        raw_AvB_asrtn = Assertion(
            contest = plur_contest,
            winner = "Alice",
            loser = "Bob",
            test = NonnegMean(
                estimFnOverride = plur_contest.estimFn,
                u = 1.0, N = plur_contest.ncards, t = .5
            ),
            assorter = Assorter(
                contest = plur_contest,
                assort = { cvr ->
                    (Cvr.as_vote(cvr.get_vote_for("AvB", "Alice")) -
                            Cvr.as_vote(cvr.get_vote_for("AvB", "Bob")) + 1) * 0.5
                },
                upper_bound = 1.0,
            )
        )

        // comparison and polling audits referencing plur_cvr_list
        comparison_audit = Audit(
            quantile = 0.8,
            error_rate_1 = 0.0,
            error_rate_2 = 0.0,
            reps = 100,
            sim_seed = 1234567890,
            strata = mapOf( "stratum_1" to Stratum(
                max_cards =   4,
                use_style =   true,
                replacement = false,
                audit_type =  AuditType.CARD_COMPARISON,
                // estimator =   NonnegMean.optimal_comparison,
                )
            )
        )
    }


    /*
    @Test
    fun test_margin() {
        assertEquals(0.5, raw_AvB_asrtn.margin( plur_cvr_list, true) )
    }

    //         AvB_asrtn = Assertion(
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
    @Test
    fun test_overstatement_assorter_margin() {
        val AvB_asrtn = Assertion(
            contest = plur_contest,
            winner = "Alice",
            loser = "Bob",
            assorter = Assorter(
                contest = plur_contest,
                assort = { c ->
                    (Cvr.as_vote(c.get_vote_for("AvB", "Alice"))
                     - Cvr.as_vote(c.get_vote_for("AvB", "Bob")) + 1) / 2.0 } ,
                upper_bound = 1.0
            ),
            margin = 0.5,
            test = phonyTest,
        )
        assertEquals(1.0/3, AvB_asrtn.overstatement_assorter_margin() )
    }

    //     def test_overstatement_assorter_mean(self):
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
    @Test
    fun test_overstatement_assorter_mean() {
        val AvB_asrtn = Assertion(
            contest = plur_contest,
            winner = "Alice",
            loser = "Bob",
            assorter = Assorter(
                contest = plur_contest,
                assort = { c ->
                    (Cvr.as_vote(c.get_vote_for("AvB", "Alice"))
                            - Cvr.as_vote(c.get_vote_for("AvB", "Bob")) + 1) / 2.0 } ,
                upper_bound = 1.0
            ),
            margin = 0.5,
            test = phonyTest,
        )

        assertEquals(1.0/1.5, AvB_asrtn.overstatement_assorter_mean() )
        assertEquals(0.5, AvB_asrtn.overstatement_assorter_mean(error_rate_1 = 0.5) )
        assertEquals(0.5, AvB_asrtn.overstatement_assorter_mean(error_rate_2 = 0.25) )
        assertEquals((1.0 - 0.125 - 0.25) / (2 - 0.5), AvB_asrtn.overstatement_assorter_mean(error_rate_1 = 0.25, error_rate_2 = 0.25) )
    }

    //     def test_set_margin_from_cvrs(self):
    //        self.raw_AvB_asrtn.set_margin_from_cvrs(self.comparison_audit, self.plur_cvr_list)
    //        assert self.raw_AvB_asrtn.margin == 0.5
    @Test
    fun test_set_margin_from_cvrs() {
        val target = raw_AvB_asrtn.copy().set_margin_from_cvrs(comparison_audit, plur_cvr_list)
        assertEquals(0.5, target.margin )
    }

    @Test
    fun test_make_plurality_assertions() {
        val winner = listOf("Alice", "Bob")
        val loser = listOf("Candy", "Dan")
        val asrtns = Assertion.make_plurality_assertions(plur_contest, winner, loser)

        // these test Assorter.assort()
        var target = asrtns["Alice v Candy"]!!
        assertEquals(1.0, target.assorter.assort(cvrFromVote("Alice")))
        assertEquals(0.5, target.assorter.assort(cvrFromVote("Bob")))
        assertEquals(0.0, target.assorter.assort(cvrFromVote("Candy")))
        assertEquals(0.5, target.assorter.assort(cvrFromVote("Dan")))

        target = asrtns["Alice v Dan"]!!
        assertEquals(1.0, target.assorter.assort(cvrFromVote("Alice")))
        assertEquals(0.5, target.assorter.assort(cvrFromVote("Bob")))
        assertEquals(0.5, target.assorter.assort(cvrFromVote("Candy")))
        assertEquals(0.0, target.assorter.assort(cvrFromVote("Dan")))

        target = asrtns["Bob v Candy"]!!
        assertEquals(0.5, target.assorter.assort(cvrFromVote("Alice")))
        assertEquals(1.0, target.assorter.assort(cvrFromVote("Bob")))
        assertEquals(0.0, target.assorter.assort(cvrFromVote("Candy")))
        assertEquals(0.5, target.assorter.assort(cvrFromVote("Dan")))

        target = asrtns["Bob v Dan"]!!
        assertEquals(0.5, target.assorter.assort(cvrFromVote("Alice")))
        assertEquals(1.0, target.assorter.assort(cvrFromVote("Bob")))
        assertEquals(0.5, target.assorter.assort(cvrFromVote("Candy")))
        assertEquals(0.0, target.assorter.assort(cvrFromVote("Dan")))

        /*
        assert asrtns["Alice v Candy"].assorter.assort(CVR.from_vote({"Alice": 1})) == 1, \
               f"{asrtns["Alice v Candy"].assorter.assort(CVR.from_vote({"Alice": 1}))=}"
        assert asrtns["Alice v Candy"].assorter.assort(CVR.from_vote({"Bob": 1})) == 1/2
        assert asrtns["Alice v Candy"].assorter.assort(CVR.from_vote({"Candy": 1})) == 0
        assert asrtns["Alice v Candy"].assorter.assort(CVR.from_vote({"Dan": 1})) == 1/2

        assert asrtns ["Alice v Dan"].assorter.assort(CVR.from_vote({ "Alice": 1 })) == 1
        assert asrtns ["Alice v Dan"].assorter.assort(CVR.from_vote({ "Bob": 1 })) == 1 / 2
        assert asrtns ["Alice v Dan"].assorter.assort(CVR.from_vote({ "Candy": 1 })) == 1 / 2
        assert asrtns ["Alice v Dan"].assorter.assort(CVR.from_vote({ "Dan": 1 })) == 0

        assert asrtns ["Bob v Candy"].assorter.assort(CVR.from_vote({ "Alice": 1 })) == 1 / 2
        assert asrtns ["Bob v Candy"].assorter.assort(CVR.from_vote({ "Bob": 1 })) == 1
        assert asrtns ["Bob v Candy"].assorter.assort(CVR.from_vote({ "Candy": 1 })) == 0
        assert asrtns ["Bob v Candy"].assorter.assort(CVR.from_vote({ "Dan": 1 })) == 1 / 2

        assert asrtns ["Bob v Dan"].assorter.assort(CVR.from_vote({ "Alice": 1 })) == 1 / 2
        assert asrtns ["Bob v Dan"].assorter.assort(CVR.from_vote({ "Bob": 1 })) == 1
        assert asrtns ["Bob v Dan"].assorter.assort(CVR.from_vote({ "Candy": 1 })) == 1 / 2
        assert asrtns ["Bob v Dan"].assorter.assort(CVR.from_vote({ "Dan": 1 })) == 0

         */
    }

    @Test
    fun test_supermajority_assorter() {
        val loser = listOf("Bob", "Candy")
        val assn = Assertion.make_supermajority_assertion(
            contest = this.superContest,
            winner = "Alice",
            loser = loser
        )

        val label = "Alice v " + Candidates.ALL_OTHERS.name
        val target = assn[label]!!
        var votes = cvrFromVote("Alice")
        assertEquals(0.75, target.assorter.assort(votes), "wrong value for vote for winner")

        votes = cvrFromVote( "Bob")
        assertEquals(0.0, target.assorter.assort(votes), "wrong value for vote for loser")

        votes = cvrFromVote( "Dan")
        assertEquals(0.5, target.assorter.assort(votes), "wrong value for vote for invalid vote--Dan")

        votes = CvrBuilder("1").addVote( "AvB","Alice").addVote("AvB","Bob").build()
        assertEquals(0.5, target.assorter.assort(votes), "wrong value for vote for invalid vote--Alice & Bob")

        votes = CvrBuilder("1").addVote("AvB","Bob").addVote("AvB","Candy").build()
        // votes = cvrFromVote( "Alice": False, "Bob": true, "Candy": true })
        assertEquals(0.5, target.assorter.assort(votes), "wrong value for vote for invalid vote--Bob & Candy")

        //     def test_supermajority_assorter(self):
        //        loser = ["Bob","Candy"]
        //        assn = Assertion.make_supermajority_assertion(contest=self.con_test, winner="Alice",
        //                                                      loser=loser)
        //
        //        label = "Alice v " + Contest.CANDIDATES.ALL_OTHERS
        //        votes = CVR.from_vote({"Alice": 1})
        //        assert assn[label].assorter.assort(votes) == 3/4, "wrong value for vote for winner"
        //
        //        votes = CVR.from_vote({"Bob": true})
        //        assert assn[label].assorter.assort(votes) == 0, "wrong value for vote for loser"
        //
        //        votes = CVR.from_vote({"Dan": true})
        //        assert assn[label].assorter.assort(votes) == 1/2, "wrong value for invalid vote--Dan"
        //
        //        votes = CVR.from_vote({"Alice": true, "Bob": true})
        //        assert assn[label].assorter.assort(votes) == 1/2, "wrong value for invalid vote--Alice & Bob"
        //
        //        votes = CVR.from_vote({"Alice": False, "Bob": true, "Candy": true})
        //        assert assn[label].assorter.assort(votes) == 1/2, "wrong value for invalid vote--Bob & Candy"
    }

    @Test
    fun test_set_tally_pool_means() {

        val cvrBuilders = CvrBuilders()
            .add(
                id = "1", tally_pool = "1",
                ContestVotes("AvB", "Alice", 1),
                ContestVotes("CvD", "Candy", true)
            )
            .add(
                "2", "1",
                ContestVotes.add("CvD", Vote("Elvis", true), Vote("Candy", false)),
                ContestVotes("EvF")
            )
            .add(id = "3", tally_pool = "1", ContestVotes("GvH"))
            .add(
                "4", "2",
                ContestVotes("AvB", "Bob", 1),
                ContestVotes("CvD", "Candy", true)
            )
            .add(
                "5", "2",
                ContestVotes.add("CvD", Vote("Elvis", true), Vote("Candy", false)),
                ContestVotes("EvF")
            )

        val tally_pool = mutableMapOf<String, Set<String>>()
        for (p in cvrBuilders.poolSet()) {
            // tally_pool[p] = CVR.pool_contests(list([c for c in cvr_list if c.tally_pool == p]))
            tally_pool[p] = cvrBuilders.poolContests(p)
        }

        cvrBuilders.add_pool_contests(tally_pool)
        val cvr_list = cvrBuilders.build()

        // without use_style
        // this.raw_AvB_asrtn.assorter.set_tally_pool_means(cvr_list=cvr_list, tally_pool=tally_pool, use_style=false)
        this.raw_AvB_asrtn.assorter.set_tally_pool_means(
            cvr_list = cvr_list,
            tally_pool = cvrBuilders.poolSet(),
            use_style = false
        )
        assertEquals((1.0 + (1.0 / 2) + (1.0 / 2)) / 3.0, this.raw_AvB_asrtn.assorter.tally_pool_means!!["1"]!!)
        assertEquals((0.0 + (1.0 / 2)) / 2.0, this.raw_AvB_asrtn.assorter.tally_pool_means!!["2"]!!)

        // with use_style, but contests have already been added to every CVR in each pool
        this.raw_AvB_asrtn.assorter.set_tally_pool_means(
            cvr_list = cvr_list,
            tally_pool = cvrBuilders.poolSet(),
            use_style = true
        )
        assertEquals((1.0 + 1.0 / 2 + 1.0 / 2) / 3, this.raw_AvB_asrtn.assorter.tally_pool_means!!["1"]!!)
        assertEquals((0.0 + 1.0 / 2) / 2, this.raw_AvB_asrtn.assorter.tally_pool_means!!["2"]!!)
        println("ok")

        //     def test_set_tally_pool_means(this) in test_Assertions.py
        //        cvr_dicts = [{"id": 1, "tally_pool": "1", "votes": {"AvB": {"Alice": 1}, "CvD": {"Candy":true}}},
        //                     {"id": 2, "tally_pool": "1", "votes": {"CvD": {"Elvis":true, "Candy":false}, "EvF": {}}},
        //                     {"id": 3, "tally_pool": "1", "votes": {"GvH": {}}},
        //                     {"id": 4, "tally_pool": "2", "votes": {"AvB": {"Bob": 1}, "CvD": {"Candy":true}}},
        //                     {"id": 5, "tally_pool": "2", "votes": {"CvD": {"Elvis":true, "Candy":false}, "EvF": {}}}
        //                   ]
        //        cvr_list = CVR.from_dict(cvr_dicts)
        //        pool_set = set(c.tally_pool for c in cvr_list)
        //        tally_pool = {}
        //        for p in pool_set:
        //            tally_pool[p] = CVR.pool_contests(list([c for c in cvr_list if c.tally_pool == p]))
        //        assert CVR.add_pool_contests(cvr_list, tally_pool)
        //        //
        //        // without use_style
        //        this.raw_AvB_asrtn.assorter.set_tally_pool_means(cvr_list=cvr_list, tally_pool=tally_pool, use_style=false)
        //        np.testing.assert_almost_equal(this.raw_AvB_asrtn.assorter.tally_pool_means["1"], (1+1/2+1/2)/3)
        //        np.testing.assert_almost_equal(this.raw_AvB_asrtn.assorter.tally_pool_means["2"], (0+1/2)/2)
        //        //
        //        // with use_style, but contests have already been added to every CVR in each pool
        //        this.raw_AvB_asrtn.assorter.set_tally_pool_means(cvr_list=cvr_list, tally_pool=tally_pool, use_style=true)
        //        np.testing.assert_almost_equal(this.raw_AvB_asrtn.assorter.tally_pool_means["1"], (1+1/2+1/2)/3)
        //        np.testing.assert_almost_equal(this.raw_AvB_asrtn.assorter.tally_pool_means["2"], (0+1/2)/2)
        //        //
        //        // with use_style, without adding contests to every CVR in each pool
        //        cvr_dicts = [{"id": 1, "tally_pool": "1", "votes": {"AvB": {"Alice": 1}, "CvD": {"Candy":true}}},
        //                     {"id": 2, "tally_pool": "1", "votes": {"CvD": {"Elvis":true, "Candy":false}, "EvF": {}}},
        //                     {"id": 3, "tally_pool": "1", "votes": {"GvH": {}}},
        //                     {"id": 4, "tally_pool": "2", "votes": {"AvB": {"Bob": 1}, "CvD": {"Candy":true}}},
        //                     {"id": 5, "tally_pool": "2", "votes": {"CvD": {"Elvis":true, "Candy":false}, "EvF": {}}}
        //                   ]
        //        cvr_list = CVR.from_dict(cvr_dicts)
        //        print(f"{list([str(c) for c in cvr_list])}")
        //        this.raw_AvB_asrtn.assorter.set_tally_pool_means(cvr_list=cvr_list, tally_pool=tally_pool, use_style=true)
        //        np.testing.assert_almost_equal(this.raw_AvB_asrtn.assorter.tally_pool_means["1"], 1)
        //        np.testing.assert_almost_equal(this.raw_AvB_asrtn.assorter.tally_pool_means["2"], 0)
    }

    @Test
    fun test_set_tally_pool_means2() {
        val cvrBuilders = CvrBuilders()
            .add(
                id = "1", tally_pool = "1",
                ContestVotes("AvB", "Alice", 1),
                ContestVotes("CvD", "Candy", true)
            )
            .add(
                "2", "1",
                ContestVotes.add("CvD", Vote("Elvis", true), Vote("Candy", false)),
                ContestVotes("EvF")
            )
            .add(id = "3", tally_pool = "1", ContestVotes("GvH"))
            .add(
                "4", "2",
                ContestVotes("AvB", "Bob", 1),
                ContestVotes("CvD", "Candy", true)
            )
            .add(
                "5", "2",
                ContestVotes.add("CvD", Vote("Elvis", true), Vote("Candy", false)),
                ContestVotes("EvF")
            )

        val cvr_list = cvrBuilders.build()

        // with use_style, without adding contests to every CVR in each pool
        this.raw_AvB_asrtn.assorter.set_tally_pool_means(
            cvr_list = cvr_list,
            tally_pool = cvrBuilders.poolSet(),
            use_style = true
        )

        assertEquals(1.0, this.raw_AvB_asrtn.assorter.tally_pool_means!!["1"]!!)
        assertEquals(0.0, this.raw_AvB_asrtn.assorter.tally_pool_means!!["2"]!!)
        println("ok")
    }

    @Test
    fun test_overstatement() {
        val mvrs = CvrBuilders()
            .add(id = "1", ContestVotes("AvB", "Alice", 1))
            .add(id = "2", ContestVotes("AvB", "Bob", true))
            .add(id = "3", ContestVotes("AvB"))
            .add(id = "4", ContestVotes("CvD", "Elvis")) // LOOK "Candy":False
            .addBuilder(CvrBuilder(id = "phantom_1", phantom = true).addContest("AvB"))
            .build()

        //        mvr_dict = [{"id": 1, "votes": {"AvB": {"Alice":true}}},
        //                    {"id": 2, "votes": {"AvB": {"Bob":true}}},
        //                    {"id": 3, "votes": {"AvB": {}}},
        //                    {"id": 4, "votes": {"CvD": {"Elvis":true, "Candy":False}}},
        //                    {"id": "phantom_1", "votes": {"AvB": {}}, "phantom": true}]
        //        mvrs = CVR.from_dict(mvr_dict)

        val cvrs = CvrBuilders()
            .add(id = "1", ContestVotes("AvB", "Alice", 1))
            .add(id = "2", ContestVotes("AvB", "Bob", true))
            .add(id = "3", ContestVotes("AvB"))
            .add(id = "4", ContestVotes("CvD", "Elvis"))
            .addBuilder(CvrBuilder(id = "phantom_1", phantom = true).addContest("AvB"))
            .build()

        //        cvr_dict = [{"id": 1, "votes": {"AvB": {"Alice":true}}},
        //                    {"id": 2, "votes": {"AvB": {"Bob":true}}},
        //                    {"id": 3, "votes": {"AvB": {}}},
        //                    {"id": 4, "votes": {"CvD": {"Elvis":true}}},
        //                    {"id": "phantom_1", "votes": {"AvB": {}}, "phantom": true}]
        //        cvrs = CVR.from_dict(cvr_dict)

        val assortFn =  fun (cvr:Cvr, id:String, winr:String, losr: String): Double {
                val w = Cvr.as_vote(cvr.get_vote_for(id, winr))
                val l = Cvr.as_vote(cvr.get_vote_for(id, losr))
                val calc = (w - l + 1) * 0.5
                return calc
        }

        val assorter = Assorter(
                contest = this.superContest,
                assort = { cvr -> assortFn(cvr, "AvB", "Alice", "Bob") },
                upper_bound = 1.0)

        val aVb = Assertion(contest=this.superContest, assorter=assorter, "Alice", "Bob", NonnegMean())

    //        aVb = Assertion(contest=self.con_test, assorter=Assorter(contest=self.con_test,
    //                        assort = (lambda c, contest_id="AvB", winr="Alice", losr="Bob":
    //                        ( CVR.as_vote(c.get_vote_for("AvB", winr))
    //                        - CVR.as_vote(c.get_vote_for("AvB", losr))
    //                        + 1)/2), upper_bound=1))

        assertEquals(0.0, aVb.assorter.overstatement(mvrs[0], cvrs[0], use_style = true))
        assertEquals(0.0, aVb.assorter.overstatement(mvrs[0], cvrs[0], use_style = false))

        assertEquals(-1.0, aVb.assorter.overstatement(mvrs[0], cvrs[1], use_style = true))
        assertEquals(-1.0, aVb.assorter.overstatement(mvrs[0], cvrs[1], use_style = false))

        assertEquals(0.5, aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style = true))
        assertEquals(0.5, aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style = false))

        assertEquals(-0.5, aVb.assorter.overstatement(mvrs[2], cvrs[1], use_style = true))
        assertEquals(-0.5, aVb.assorter.overstatement(mvrs[2], cvrs[1], use_style = false))

        assertEquals(1.0, aVb.assorter.overstatement(mvrs[1], cvrs[0], use_style = true))
        assertEquals(1.0, aVb.assorter.overstatement(mvrs[1], cvrs[0], use_style = false))

        assertEquals(0.5, aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style = true))
        assertEquals(0.5, aVb.assorter.overstatement(mvrs[2], cvrs[0], use_style = false))

        assertEquals(1.0, aVb.assorter.overstatement(mvrs[3], cvrs[0], use_style = true))
        assertEquals(0.5, aVb.assorter.overstatement(mvrs[3], cvrs[0], use_style = false))


        val message = assertFailsWith<Exception> {
            aVb.assorter.overstatement(mvrs[3], cvrs[3], use_style = true)
            // throw AssertionError ("aVb is not contained in the mvr or cvr")
        }.message
        assertContains(message!!, "does not contain contest 'AvB'")

        assertEquals(0.0, aVb.assorter.overstatement(mvrs[3], cvrs[3], use_style = false))

        assertEquals(0.5, aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style = true))
        assertEquals(0.5, aVb.assorter.overstatement(mvrs[4], cvrs[4], use_style = false))

        assertEquals(1.0, aVb.assorter.overstatement(mvrs[4], cvrs[0], use_style = true))
        assertEquals(1.0, aVb.assorter.overstatement(mvrs[4], cvrs[0], use_style = false))

        assertEquals(0.0, aVb.assorter.overstatement(mvrs[4], cvrs[1], use_style = true))
        assertEquals(0.0, aVb.assorter.overstatement(mvrs[4], cvrs[1], use_style = false))

        /*
            assert aVb . assorter . overstatement (mvrs[3], cvrs[3], use_style = False) == 0

            assert aVb . assorter . overstatement (mvrs[4], cvrs[4], use_style = true) == 1/2
            assert aVb . assorter . overstatement (mvrs[4], cvrs[4], use_style = False) == 1/2
            assert aVb . assorter . overstatement (mvrs[4], cvrs[0], use_style = true) == 1
            assert aVb . assorter . overstatement (mvrs[4], cvrs[0], use_style = False) == 1
            assert aVb . assorter . overstatement (mvrs[4], cvrs[1], use_style = true) == 0
            assert aVb . assorter . overstatement (mvrs[4], cvrs[1], use_style = False) == 0
         */
        }

    @Test
    fun test_overstatement_assorter() {
        // (1 - o / u) / (2 - v / u)

        val mvrs = CvrBuilders()
            .add(id = "1", ContestVotes("AvB", "Alice", 1))
            .add(id = "2", ContestVotes("AvB", "Bob", true))
            .add(id = "3", ContestVotes("AvB", "Candy", true))
            .build()

        //        mvr_dict = [{"id": 1, "votes": {"AvB": {"Alice":true}}},
        //                    {"id": 2, "votes": {"AvB": {"Bob":true}}},
        //                    {"id": 3, "votes": {"AvB": {"Candy":true}}}]
        //        mvrs = CVR.from_dict(mvr_dict)

        val cvrs = CvrBuilders()
            .add(id = "1", ContestVotes("AvB", "Alice", 1))
            .add(id = "2", ContestVotes("AvB", "Bob", true))
            .build()

        //        cvr_dict = [{"id": 1, "votes": {"AvB": {"Alice":true}}},
        //                    {"id": 2, "votes": {"AvB": {"Bob":true}}}]
        //        cvrs = CVR.from_dict(cvr_dict)

        val assortFn =  fun (cvr:Cvr, id:String, winr:String, losr: String): Double {
            val w = Cvr.as_vote(cvr.get_vote_for(id, winr))
            val l = Cvr.as_vote(cvr.get_vote_for(id, losr))
            val calc = (w - l + 1) * 0.5
            return calc
        }

        val assorter = Assorter(
            contest = this.superContest,
            assort = { cvr -> assortFn(cvr, "AvB", "Alice", "Bob") },
            upper_bound = 1.0)

        val aVb = Assertion(contest=this.superContest, assorter=assorter, "Alice", "Bob", NonnegMean())

        aVb.margin = 0.2
        assertEquals(1.0/1.8, aVb.overstatement_assorter(mvrs[0], cvrs[0], use_style = true))
        assertEquals(1.0/1.8, aVb.overstatement_assorter(mvrs[0], cvrs[0], use_style = false))
        // assert aVb.overstatement_assorter(mvrs[0], cvrs[0], use_style=true) == 1/1.8

        assertEquals(0.0, aVb.overstatement_assorter(mvrs[1], cvrs[0], use_style = true))
        assertEquals(0.0, aVb.overstatement_assorter(mvrs[1], cvrs[0], use_style = false))

        aVb.margin = 0.3
        assertEquals(2/1.7, aVb.overstatement_assorter(mvrs[0], cvrs[1], use_style = true))
        assertEquals(2/1.7, aVb.overstatement_assorter(mvrs[0], cvrs[1], use_style = false))

        aVb.margin = 0.1
        assertEquals(0.5/1.9, aVb.overstatement_assorter(mvrs[2], cvrs[0], use_style = true))
        assertEquals(0.5/1.9, aVb.overstatement_assorter(mvrs[2], cvrs[0], use_style = false))

        // def test_overstatement_assorter(self):
        //        """
        //        (1-o/u)/(2-v/u)
        //        """
        //        mvr_dict = [{"id": 1, "votes": {"AvB": {"Alice":true}}},
        //                    {"id": 2, "votes": {"AvB": {"Bob":true}}},
        //                    {"id": 3, "votes": {"AvB": {"Candy":true}}}]
        //        mvrs = CVR.from_dict(mvr_dict)
        //
        //        cvr_dict = [{"id": 1, "votes": {"AvB": {"Alice":true}}},
        //                    {"id": 2, "votes": {"AvB": {"Bob":true}}}]
        //        cvrs = CVR.from_dict(cvr_dict)
        //
        //        winner = ["Alice"]
        //        loser = ["Bob", "Candy"]
        //
        //
        //        aVb = Assertion(contest=self.con_test, assorter=Assorter(contest=self.con_test,
        //                        assort = (lambda c, contest_id="AvB", winr="Alice", losr="Bob":
        //                        ( CVR.as_vote(c.get_vote_for("AvB", winr))
        //                        - CVR.as_vote(c.get_vote_for("AvB", losr))
        //                        + 1)/2), upper_bound=1))
        //        aVb.margin=0.2
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[0], use_style=true) == 1/1.8
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[0], use_style=False) == 1/1.8
        //
        //        assert aVb.overstatement_assorter(mvrs[1], cvrs[0], use_style=true) == 0
        //        assert aVb.overstatement_assorter(mvrs[1], cvrs[0], use_style=False) == 0
        //
        //        aVb.margin=0.3
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[1], use_style=true) == 2/1.7
        //        assert aVb.overstatement_assorter(mvrs[0], cvrs[1], use_style=False) == 2/1.7
        //
        //        aVb.margin=0.1
        //        assert aVb.overstatement_assorter(mvrs[2], cvrs[0], use_style=true) == 0.5/1.9
        //        assert aVb.overstatement_assorter(mvrs[2], cvrs[0], use_style=False) == 0.5/1.9
    }

    @Test
    fun test_assorter_sample_size() {
        // Test Assorter.sample_size using the Kaplan-Wald risk function
        val N = 10000

        val AvB = Contest(id = "AvB",
            name = "AvB",
            risk_limit = 0.05,
            ncards = N,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            n_winners = 1,
            candidates = listOf("Alice","Bob","Carol"),
            reported_winners = listOf("Alice"),
            audit_type = AuditType.CARD_COMPARISON,
            testFn = { x -> NonnegMean().kaplan_markov(x, g=0.1) },
            tally = mutableMapOf("Alice" to 3000, "Bob" to 2000, "Carol" to 1000),
            g = 0.1,
            use_style = true,
        )

        //        AvB = Contest.from_dict({"id": "AvB",
        //                             "name": "AvB",
        //                             "risk_limit": 0.05,
        //                             "cards": N,
        //                             "choiceFunction": Contest.SOCIAL_CHOICE_FUNCTION.PLURALITY,
        //                             "n_winners": 1,
        //                             "candidates": ["Alice","Bob", "Carol"],
        //                             "winner": ["Alice"],
        //                             "audit_type": Audit.AUDIT_TYPE.CARD_COMPARISON,
        //                             "test": NonnegMean.kaplan_markov,
        //                             "tally": {"Alice": 3000, "Bob": 2000, "Carol": 1000},
        //                             "g": 0.1,
        //                             "use_style": true
        //                        })

        val loser: List<String> = (AvB.candidates.toSet() - AvB.reported_winners.toSet()).toList()
        AvB.assertions = Assertion.make_plurality_assertions(AvB, winner = AvB.reported_winners, loser = loser).toMutableMap()
        AvB.find_margins_from_tally()
        for ((_, a) in AvB.assertions) {
            println("\nAssertion winner = ${a.winner} loser = ${a.loser}")

            // first test
            val rate_1 = 0.01
            val rate_2 = 0.001
            val sam_size1 = a.find_sample_size(data = DoubleArray(10) { 1.0 }, prefix = true, rate1 = rate_1, quantile = 0.5, seed = 1234567890)
            // Kaplan - Markov martingale is \prod (t+g)/(x+g). For x = [1, 1, ...], sample size should be:
            val ss1 = ceil(log(AvB.risk_limit) / log((a.test.t + AvB.g) / (1 + AvB.g))).toInt() // LOOK was a.test.g
            assertEquals(ss1, sam_size1)

            // second test
            // For "clean", the term is (1/2+g)/(clean+g); for a one -vote overstatement, it is (1/2+g)/(one_over+g).
    //            sam_size2 = a.find_sample_size(data=None, prefix=true, rate_1=rate_1, reps=10**2, quantile=0.5,
    //                                           seed=1234567890)
    //            clean = 1/(2-a.margin/a.assorter.upper_bound)
    //            over = clean/2 // corresponds to an overstatement of upper_bound/2, i.e., 1 vote.
    //            c = (a.test.t+a.test.g)/(clean+a.test.g)
    //            o = (a.test.t+a.test.g)/(clean/2+a.test.g)
    //            // the following calculation assumes the audit will terminate before the second overstatement error
    //            ss2 = math.ceil(np.log(AvB.risk_limit/o)/np.log(c))+1
    //            assert sam_size2 == ss2
            val sam_size2 = a.find_sample_size(data = null, prefix = true, rate1 = rate_1, reps = 100, quantile = 0.5, seed = 1234567890)
            val clean = 1 / (2 - a.margin!! / a.assorter.upper_bound)
            val over = clean / 2 // corresponds to an overstatement of upper_bound/2, i.e., 1 vote.
            val c = (a.test.t + AvB.g) / (clean + AvB.g)
            val o = (a.test.t + AvB.g) / (clean / 2 + AvB.g)
            // the following calculation assumes the audit will terminate before the second overstatement error
            val ss2 = ceil(log(AvB.risk_limit / o) / log(c)).toInt() + 1
            println(" sam_size2 = $sam_size2")
            assertEquals(ss2, sam_size2)

            // third test
            val sam_size3 = a.find_sample_size(data = null, prefix = true, rate1 = rate_1, rate2 = rate_2, reps = 100, quantile = 0.99, seed = 1234567890)
            assertTrue(sam_size3 > sam_size2)
        }


        // def test_assorter_sample_size(self):
        //        // Test Assorter.sample_size using the Kaplan-Wald risk function
        //        N = int(10**4)
        //        AvB = Contest.from_dict({"id": "AvB",
        //                             "name": "AvB",
        //                             "risk_limit": 0.05,
        //                             "cards": N,
        //                             "choiceFunction": Contest.SOCIAL_CHOICE_FUNCTION.PLURALITY,
        //                             "n_winners": 1,
        //                             "candidates": ["Alice","Bob", "Carol"],
        //                             "winner": ["Alice"],
        //                             "audit_type": Audit.AUDIT_TYPE.CARD_COMPARISON,
        //                             "test": NonnegMean.kaplan_markov,
        //                             "tally": {"Alice": 3000, "Bob": 2000, "Carol": 1000},
        //                             "g": 0.1,
        //                             "use_style": true
        //                        })
        //        loser = list(set(AvB.candidates)-set(AvB.winner))
        //        AvB.assertions = Assertion.make_plurality_assertions(AvB, winner=AvB.winner, loser=loser)
        //        AvB.find_margins_from_tally()
        //        for a_id, a in AvB.assertions.items():
        //            // first test
        //            rate_1=0.01
        //            rate_2=0.001
        //            sam_size1 = a.find_sample_size(data=np.ones(10), prefix=true, rate_1=rate_1, reps=None, quantile=0.5, 
        //                                           seed=1234567890)
        //            // Kaplan-Markov martingale is \prod (t+g)/(x+g). For x = [1, 1, ...], sample size should be:
        //            ss1 = math.ceil(np.log(AvB.risk_limit)/np.log((a.test.t+a.test.g)/(1+a.test.g)))
        //            assert sam_size1 == ss1
        //            //
        //            // second test
        //            // For "clean", the term is (1/2+g)/(clean+g); for a one-vote overstatement, it is (1/2+g)/(one_over+g).
        //            sam_size2 = a.find_sample_size(data=None, prefix=true, rate_1=rate_1, reps=10**2, quantile=0.5, 
        //                                           seed=1234567890)
        //            clean = 1/(2-a.margin/a.assorter.upper_bound)
        //            over = clean/2 // corresponds to an overstatement of upper_bound/2, i.e., 1 vote.
        //            c = (a.test.t+a.test.g)/(clean+a.test.g)
        //            o = (a.test.t+a.test.g)/(clean/2+a.test.g)
        //            // the following calculation assumes the audit will terminate before the second overstatement error
        //            ss2 = math.ceil(np.log(AvB.risk_limit/o)/np.log(c))+1
        //            assert sam_size2 == ss2
        //            //
        //            // third test
        //            sam_size3 = a.find_sample_size(data=None, prefix=true, rate_1=rate_1, rate_2=rate_2,
        //                                           reps=10**2, quantile=0.99, seed=1234567890)
        //            assert sam_size3 > sam_size2
    }

    @Test
    fun test_find_margin_from_tally() {
        val AvB = Contest(id = "AvB",
            name = "AvB",
            risk_limit = 0.01,
            ncards = 10000,
            choiceFunction = SocialChoiceFunction.PLURALITY,
            n_winners = 1,
            candidates = listOf("Alice","Bob","Carol"),
            reported_winners = listOf("Alice"),
            audit_type = AuditType.CARD_COMPARISON,
            testFn = { x -> NonnegMean().kaplan_markov(x, g=0.1) },
            tally = mutableMapOf("Alice" to 3000, "Bob" to 2000, "Carol" to 1000),
            g = 0.1,
            use_style = true,
        )

        val loser: List<String> = (AvB.candidates.toSet() - AvB.reported_winners.toSet()).toList()
        AvB.assertions = Assertion.make_plurality_assertions(AvB, winner = AvB.reported_winners, loser = loser).toMutableMap()
        AvB.find_margins_from_tally()

        assertEquals(AvB.assertions["Alice v Bob"]!!.margin!!, (AvB.tally["Alice"]!! - AvB.tally["Bob"]!!) / AvB.ncards.toDouble())
        assertEquals(AvB.assertions["Alice v Carol"]!!.margin!!, (AvB.tally["Alice"]!! - AvB.tally["Carol"]!!) / AvB.ncards.toDouble())

        val tally = mapOf("Alice" to 4000, "Bob" to 2000, "Carol" to 1000)
        AvB.assertions["Alice v Carol"]!!.find_margin_from_tally(tally)
        assertEquals(AvB.assertions["Alice v Carol"]!!.margin!!, (tally["Alice"]!! - tally["Carol"]!!) / AvB.ncards.toDouble())

        //     AvB = Contest.from_dict({'id': 'AvB',
        //                     'name': 'AvB',
        //                     'risk_limit': 0.01,
        //                     'cards': 10**4,
        //                     'choiceFunction': Contest.SOCIAL_CHOICE_FUNCTION.PLURALITY,
        //                     'n_winners': 1,
        //                     'candidates': ['Alice','Bob','Carol'],
        //                     'winner': ['Alice'],
        //                     'audit_type': Audit.AUDIT_TYPE.CARD_COMPARISON,
        //                     'tally': {'Alice': 3000, 'Bob': 2000, 'Carol': 1000},
        //                     'test': NonnegMean.kaplan_markov,
        //                     'g': 0.1,
        //                     'use_style': True
        //                })
        //        AvB.assertions = Assertion.make_plurality_assertions(AvB, winner=['Alice'], loser=['Bob','Carol'])
        //        AvB.find_margins_from_tally()
        //        assert AvB.assertions['Alice v Bob'].margin == (AvB.tally['Alice'] - AvB.tally['Bob'])/AvB.cards
        //        assert AvB.assertions['Alice v Carol'].margin == (AvB.tally['Alice'] - AvB.tally['Carol'])/AvB.cards
        //        tally = {'Alice': 4000, 'Bob': 2000, 'Carol': 1000}
        //        AvB.assertions['Alice v Carol'].find_margin_from_tally(tally)
        //        assert AvB.assertions['Alice v Carol'].margin == (tally['Alice'] - tally['Carol'])/AvB.cards
    }

    @Test
    fun test_interleave_values() {
        var n_small = 5
        var n_med = 3
        var n_big = 6
        var x = Assertion.interleave_values(n_small, n_med, n_big)
        assertEquals(14, x.size)
        assertEquals(0.0, x[0])
        assertEquals(5, x.filter{ it == 0.0 }.count())
        assertEquals(3, x.filter{ it == 0.5 }.count())
        assertEquals(6, x.filter{ it == 1.0 }.count())

        n_small = 0
        n_med = 3
        n_big = 6

        val big = 2.0
        val med = 1.0
        val small = 0.1
        x = Assertion.interleave_values(n_small, n_med, n_big, small = small, med = med, big = big)
        assertEquals(9, x.size)
        assertEquals(1.0, x[0])
        assertEquals(0, x.filter{ it == 0.1 }.count())
        assertEquals(3, x.filter{ it == 1.0 }.count())
        assertEquals(6, x.filter{ it == 2.0}.count())
    }

     */
}

     */
}
