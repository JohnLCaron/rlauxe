package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.CvrBuilders
import kotlin.test.Test
import kotlin.test.assertEquals

// from shangrla test_CVR
class TestCvr {
    // TODO assign idx
    val contests: List<AuditContest> = listOf(
        AuditContest("city_council", 0, candidateNames= listOf("Doug", "Emily", "Frank", "Gail", "Harry"),
            winnerNames= listOf("Doug", "Emily", "Frank")),
        AuditContest("measure_1", 1, candidateNames= listOf("yes", "no"),
            winnerNames= listOf("yes"), SocialChoiceFunction.SUPERMAJORITY, minFraction = .6666),
    )

    val cvrs = CvrBuilders()
        .addCrv().addContest("city_council", "Doug").done()
            .addContest("measure_1", "yes").done().done()
        .addCrv().addContest("city_council", "Emily").done()
            .addContest("measure_1", "yes").done().done()
        .addCrv().addContest("city_council", "Emily").done()
            .addContest("measure_1", "no").done().done()
        .addCrv().addContest("city_council", "Frank").done().done()
        .addCrv().addContest("city_council", "Gail").done().done()
        .addCrv().addContest("measure_1", "no").done().done()
        .build()


    //         val cvrs = listOf(CVR(id="1", votes=mapOf("city_council" to mapOf("Alice" to 1), "measure_1" to mapOf("yes" to 1)), phantom=false),
    //            CVR(id="2", votes=mapOf("city_council" to mapOf("Bob" to 1), "measure_1" to mapOf("yes" to 1)), phantom=false),
    //            CVR(id="3", votes=mapOf("city_council" to mapOf("Bob" to 1), "measure_1" to mapOf("no" to 1)), phantom=false),
    //            CVR(id="4", votes=mapOf("city_council" to mapOf("Charlie" to 1)), phantom=false),
    //            CVR(id="5", votes=mapOf("city_council" to mapOf("Doug" to 1)), phantom=false),
    //            CVR(id="6", votes=mapOf("measure_1" to mapOf("no" to 1)), phantom=false)
    //        )

    @Test
    fun test_assign_sample_nums() {
        val cvrbs: CvrBuilders = CvrBuilders()
            // .add(id = "1",  ContestVotes("city_council", Vote("Alice", 1)), ContestVotes("measure_1", Vote("yes", 1)))
            .addCrv().addContest("city_council", "Alice").done()
                .addContest("measure_1", "yes").done().done()
            // .add(id = "2",  ContestVotes("city_council", Vote("Bob", 1)), ContestVotes("measure_1", Vote("yes", 1)))
            .addCrv().addContest("city_council", "Bob").done()
                .addContest("measure_1", "yes").done().done()
            // .add(id = "3",  ContestVotes("city_council", Vote("Bob", 1)), ContestVotes("measure_1", Vote("no", 1)))
            .addCrv().addContest("city_council", "Bob").done()
                .addContest("measure_1", "no").done().done()
                // .add(id = "4",  ContestVotes("city_council", Vote("Charlie", 1)))
            .addCrv().addContest("city_council", "Charlie").done().done()
            // .add(id = "5",  ContestVotes("city_council", Vote("Doug", 1)))
            .addCrv().addContest("city_council", "Doug").done().done()
            // .add(id = "6",  ContestVotes("measure_1", Vote("no", 1)))
            .addCrv().addContest("measure_1", "no").done().done()

        //val cvrs =
        //    [Cvr(id = "1", votes = { "city_council": { "Alice": 1 }, "measure_1": { "yes": 1 } }, phantom = false),
        //        Cvr(id = "2", votes = { "city_council": { "Bob": 1 }, "measure_1": { "yes": 1 } }, phantom = false),
        //        Cvr(id = "3", votes = { "city_council": { "Bob": 1 }, "measure_1": { "no": 1 } }, phantom = false),
        //        Cvr(id = "4", votes = { "city_council": { "Charlie": 1 } }, phantom = false),
        //        Cvr(id = "5", votes = { "city_council": { "Doug": 1 } }, phantom = false),
        //        Cvr(id = "6", votes = { "measure_1": { "no": 1 } }, phantom = false) ]

        //         prng = SHA256(1234567890)
        //        CVR.assign_sample_nums(cvrs, prng)
        //        assert cvrs[0].sample_num == 100208482908198438057700745423243738999845662853049614266130533283921761365671
        //        assert cvrs[5].sample_num == 93838330019164869717966768063938259297046489853954854934402443181124696542865

        // TODO this is stupid, these are just random numbers, so what are we testing ??
        //val prng = SHA256(1234567890)
        //val cvrs = cvrbs.build()
        //assignSampleNums(cvrs, prng)
        //assertEquals(cvrs[0].sample_num, 100208482908198438057700745423243738999845662853049614266130533283921761365671)
        //assertEquals(cvrs[5].sample_num, 93838330019164869717966768063938259297046489853954854934402443181124696542865)
    }
    
    @Test
    fun test_make_phantoms() {
        val cvras = cvrs.map { CvrUnderAudit(it) }
        val contestas: Map<String, ContestUnderAudit> = contests.map { it.id to ContestUnderAudit(it) }.toMap()
        contestas["measure_1"]?.ncards = 5

        val prefix = "phantom-"

        //// use_style = true
        val (result: List<CvrUnderAudit>, nphantoms: Int) = makePhantoms(
            cvras = cvras,
            contestas = contestas.values.toList(),
            maxCards=8, // used when useStyles = false, or ncards hasnt been assigned
            prefix = prefix
        )
        assertEquals(9, result.size)
        assertEquals(3, nphantoms)

        assertEquals(5, contestas["city_council"]!!.ncvrs)
        assertEquals(4, contestas["measure_1"]!!.ncvrs)
        assertEquals(8, contestas["city_council"]!!.ncards)
        assertEquals(5, contestas["measure_1"]!!.ncards)

        assertEquals(8, result.filter{ it.hasContest(0) }.size)
        assertEquals(5, result.filter{ it.hasContest(1) }.size)
        assertEquals(5, result.filter{ it.hasContest(0) && !it.phantom }.size)
        assertEquals(4, result.filter{ it.hasContest(1) && !it.phantom }.size)

        //// use_style = false
        val cvrasf = cvrs.map { CvrUnderAudit(it) }
        val contestasf: Map<String, ContestUnderAudit> = contests.map { it.id to ContestUnderAudit(it) }.toMap()
        // contestasf["measure_1"]?.ncards = 5 // LOOK
        val (resultf: List<CvrUnderAudit>, nphantomsf: Int) = makePhantoms(
            cvras = cvrasf,
            contestas = contestasf.values.toList(),
            useStyles = false,
            maxCards=8, // used when useStyles = false, or ncards hasnt been assigned
            prefix = prefix
        )
        assertEquals(8, resultf.size)
        assertEquals(2, nphantomsf)

        assertEquals(5, contestasf["city_council"]!!.ncvrs)
        assertEquals(4, contestasf["measure_1"]!!.ncvrs)
        assertEquals(8, contestasf["city_council"]!!.ncards)
        assertEquals(8, contestasf["measure_1"]!!.ncards)

        assertEquals(5, resultf.filter{ it.hasContest(0) }.size)
        assertEquals(4, resultf.filter{ it.hasContest(1) }.size)
        assertEquals(5, resultf.filter{ it.hasContest(0) && !it.phantom }.size)
        assertEquals(4, resultf.filter{ it.hasContest(1) && !it.phantom }.size)

        // TODO Im surprised that the phantoms dont list any of the contests.
    }

    /*
def test_consistent_sampling(self):
    cvrs = [CVR(id="1", votes={"city_council": {"Alice": 1}, "measure_1": {"yes": 1}}, phantom=False),
            CVR(id="2", votes={"city_council": {"Bob": 1},   "measure_1": {"yes": 1}}, phantom=False),
            CVR(id="3", votes={"city_council": {"Bob": 1},   "measure_1": {"no": 1}}, phantom=False),
            CVR(id="4", votes={"city_council": {"Charlie": 1}}, phantom=False),
            CVR(id="5", votes={"city_council": {"Doug": 1}}, phantom=False),
            CVR(id="6", votes={"measure_1": {"no": 1}}, phantom=False)
            ]
    prng = SHA256(1234567890)
    for c, cvr in enumerate(cvrs):
        cvr.sample_num = c
    contests = {'city_council': {'risk_limit':0.05,
                                 'id': 'city_council',
                                 'cards': None,
                                 'choice_function':'plurality',
                                 'n_winners':3,
                                 'candidates':['Doug','Emily','Frank','Gail','Harry'],
                                 'winner': ['Doug', 'Emily', 'Frank'],
                                 'sample_size': 3
                                 },
                'measure_1':   {'risk_limit':0.05,
                                'id': 'measure_1',
                                'cards': 5,
                                'choice_function':'supermajority',
                                'share_to_win':2/3,
                                'n_winners':1,
                                'candidates':['yes','no'],
                                'winner': ['yes'],
                                'sample_size': 4
                                }
                }
    con_tests = Contest.from_dict_of_dicts(contests)
    sample_cvr_indices = CVR.consistent_sampling(cvrs, con_tests)
    assert sample_cvr_indices == [0, 1, 2, 5]
    np.testing.assert_approx_equal(con_tests['city_council'].sample_threshold, 2)
    np.testing.assert_approx_equal(con_tests['measure_1'].sample_threshold, 5)
     */
    data class CVR(val id: String, val votes: Map<String, Map<String, Int>>, var phantom: Boolean, var sampleNum: Int? = null)

    data class Contest(val riskLimit: Double, val id: String, val cards: Int?, val choiceFunction: String,
                       val nWinners: Int, val candidates: List<String>, val winner: List<String>, val sampleSize: Int, var sampleThreshold: Int? = null)

    @Test
    fun testConsistentSampling() {
        // consistentSampling assumes that phantoms have already been generated and sample_num has been assigned to every CVR, including phantoms
        // TODO following SHAGRLA test, there are no phantoms here
        val cvras = cvrs.map { CvrUnderAudit(it) }
        for ((index, cvra) in cvras.withIndex()) {
            cvra.sampleNum = index
        }
        val contestas: Map<String, ContestUnderAudit> = contests.map { it.id to ContestUnderAudit(it) }.toMap()
        contestas["city_council"]?.sampleSize = 3
        contestas["measure_1"]?.sampleSize = 4

        val starting = mutableListOf<Int>()
        val sampleCvrIndices = consistentSampling(cvras, contestas, starting)
        assertEquals(listOf(0, 1, 2, 5), sampleCvrIndices)
        contestas["city_council"]?.sampleThreshold?.let { assertEquals(2, it) }
        contestas["measure_1"]?.sampleThreshold?.let { assertEquals(5, it) }
    }

}