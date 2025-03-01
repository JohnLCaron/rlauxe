package org.cryptobiotic.rlauxe.shangrla

// SHANGRLA test_Assertion.py
// TODO move useful tests over to TestAssertionsFromShangrla

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
            minFraction = 2.0 / 3,
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
            strata = mapOf(
                "stratum_1" to Stratum(
                    max_cards = 4,
                    use_style = true,
                    replacement = false,
                    audit_type = AuditType.CARD_COMPARISON,
                    // estimator =   NonnegMean.optimal_comparison,
                )
            )
        )
    }

    //     def test_set_margin_from_cvrs(self):
    //        self.raw_AvB_asrtn.set_margin_from_cvrs(self.comparison_audit, self.plur_cvr_list)
    //        assert self.raw_AvB_asrtn.margin == 0.5
    @Test
    fun test_set_margin_from_cvrs() {
        val target = raw_AvB_asrtn.set_margin_from_cvrs(comparison_audit, plur_cvr_list)
        assertEquals(0.5, target.margin)
    }

     */
}

////
//     def set_margin_from_cvrs(self, audit: object = None, cvr_list: Collection = None):
//        """
//        find assorter margin from cvrs and store it
//
//        Parameters
//        ----------
//        cvr_list: Collection
//            cvrs from which the sample will be drawn
//        use_style: bool
//            is the sample drawn only from ballots that should contain the contest?
//
//        Returns
//        -------
//        nothing
//
//        Side effects
//        ------------
//        sets assorter.margin
//        """
//        if len(audit.strata) > 1:
//            raise NotImplementedError("stratified audits not yet supported")
//        stratum = next(iter(audit.strata.values()))
//        use_style = stratum.use_style
//        amean = self.assorter.mean(cvr_list, use_style=use_style)
//        if amean < 1 / 2:
//            warnings.warn(
//                f"assertion {self} not satisfied by CVRs: mean value is {amean}"
//            )
//        self.margin = 2 * amean - 1
//        if self.contest.audit_type == Audit.AUDIT_TYPE.POLLING:
//            self.test.u = self.assorter.upper_bound
//        elif self.contest.audit_type in [
//            Audit.AUDIT_TYPE.CARD_COMPARISON,
//            Audit.AUDIT_TYPE.ONEAUDIT,
//        ]:
//            self.test.u = 2 / (2 - self.margin / self.assorter.upper_bound)
//        else:
//            raise NotImplementedError(
//                f"audit type {self.contest.audit_type} not supported"
//            )
/*
fun setMarginFromCvrs(auditType: AuditType, useStyle: Boolean, assorter: AssorterFunction, cvrList: List<Cvr>): Double {

find assorter margin from cvrs and store it

Parameters
----------
cvr_list: Collection
    cvrs from which the sample will be drawn
use_style: bool
    is the sample drawn only from ballots that should contain the contest?

Returns
-------
nothing

Side effects
------------
sets assorter.margin


val aMean = assorter.mean(cvrList, useStyle = useStyle)
if (aMean < 1.0 / 2) {
    println("assertion $assorter not satisfied by CVRs: mean value is $aMean")
}
val margin = 2 * aMean - 1
when (auditType) {
    AuditType.POLLING -> {
        test.u = assorter.upperBound()
    }

    AuditType.CARD_COMPARISON,
    AuditType.ONEAUDIT,
        -> {
        test.u = 2 / (2 - margin / assorter.upperBound())
    }

    else -> {
        throw NotImplementedError("audit type ${auditType} not supported")
    }
}
return margin
}

//     def mean(self, cvr_list: "Collection[CVR]" = None, use_style: bool = True):
//        """
//        find the mean of the assorter applied to a list of CVRs
//
//        Parameters
//        ----------
//        cvr_list: Collection
//            a collection of cast-vote records
//        use_style: Boolean
//            does the audit use card style information? If so, apply the assorter only to CVRs
//            that contain the contest in question.
//
//        Returns
//        -------
//        mean: float
//            the mean value of the assorter over the collection of cvrs. If use_style, ignores CVRs that
//            do not contain the contest.
//        """
//        if use_style:
//            filtr = lambda c: c.has_contest(self.contest.id)
//        else:
//            filtr = lambda c: True
//        return np.mean([self.assort(c) for c in cvr_list if filtr(c)])

fun AssorterFunction.mean(cvrs: List<Cvr>, useStyle: Boolean): Double {

}

/*

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
*/