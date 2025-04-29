package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.doublesAreClose
import org.junit.jupiter.api.Assertions.assertNotNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// here we recreate Kalamazoo example in rlaux framework.
// also see plots/src/test/kotlin/org/cryptobiotic/rlauxe/oneaudit/KalamazooExample.kt, which ports the python directly
class TestOneAuditKalamazoo {

    @Test
    fun testOneAuditKalamazoo() {
        val contestOA = makeContestKalamazoo()
        val strataCvr = contestOA.cvrVotes
        val strataNocvr = contestOA.pools[1]!!
        val info = contestOA.info

        // votes_cvr=5218.0 votes_poll=22082.0 diff cvr: 76.0, diff poll: 290.0
        assertEquals(5218, strataCvr.values.sum())
        assertEquals(22082, strataNocvr.votes.values.sum())
        assertEquals(76, contestOA.cvrNc - strataCvr.values.sum())
        assertEquals(290, strataNocvr.ncards - strataNocvr.votes.values.sum())

        // whitmer=20699, schuette=5569, assorter_mean_all=0.5468806477264513
        val whitmerTotal = contestOA.votes[info.candidateNames["Whitmer"]!!]!!
        val schuetteTotal = contestOA.votes[info.candidateNames["Schuette"]!!]!!
        assertEquals(20699, whitmerTotal)
        assertEquals(5569, schuetteTotal)
        val assorterMeanAll = (whitmerTotal - schuetteTotal)/ contestOA.Nc.toDouble()
        assertEquals(0.5468806477264513, assorterMeanAll, doublePrecision) // margin not mean

        // assorter_mean_poll=0.5682996602896477,
        val whitmerNoCvr = strataNocvr.votes[info.candidateNames["Whitmer"]!!]!!
        val schuetteNoCvr = strataNocvr.votes[info.candidateNames["Schuette"]!!]!!
        val assorterMeanNoCvr = (whitmerNoCvr - schuetteNoCvr) / strataNocvr.ncards.toDouble()
        assertEquals(0.5682996602896477, assorterMeanNoCvr, doublePrecision) // margin not mean

        // eta=0.5245932724032007, v=0.09376129545290257, u_b=1.0491865448064015,
        val u = 1.0
        val v = 2 * assorterMeanAll - 1
        val uB = 2 * u / (2 * u - v) // upper bound on the overstatement assorter
        val noerror = uB / 2         // assorter value when cvr == mvr
        assertEquals(0.5245932724032007, noerror, doublePrecision)
        assertEquals(0.09376129545290257, v, doublePrecision)
        assertEquals(1.0491865448064015, uB, doublePrecision)

        // assort values for "assert that Whitmer is winner and Schuette is loser" from the sampled ballots
        val votesAudPoll = mapOf(
            "Butkovich" to 0,
            "Gelineau" to 1,
            "Kurland" to 0,
            "Schleiger" to 0,
            "Schuette" to 8,
            "Whitmer" to 23
        )
        val nCvr = 8
        val nPoll = 32
        val otherNoCvrVotes =
            votesAudPoll.filterKeys { it in listOf("Butkovich", "Gelineau", "Kurland", "Schleiger") }.values.sum()
        val schuetteNoCvrVotes = votesAudPoll["Schuette"]!!
        val whitmerNoCvrVotes = votesAudPoll["Whitmer"]!!

        // sam = np.array([u_b/2]*n_cvr
        //               + [1/2]*np.sum(np.fromiter((votes_aud_poll[can] for can in ['Butkovich','Gelineau','Kurland','Schleiger']),
        //                                         dtype=int))
        //               + [(1-assorter_mean_poll)/(2-v)]*votes_aud_poll['Schuette']
        //               + [(2-assorter_mean_poll)/(2-v)]*votes_aud_poll['Whitmer'])

        // val sam = DoubleArray(nCvr) { noerror } +  // assume no errors on the cvr, the assorter value = noerror
        //           DoubleArray(
        //                    votesAudPoll.filterKeys { it in listOf("Butkovich", "Gelineau", "Kurland", "Schleiger") }
        //                    .values.sum()) { 1.0 / 2 } +
        //           DoubleArray(votesAudPoll["Schuette"]!!) { (1 - assorterMeanPoll) / (2 - v) } +
        //           DoubleArray(votesAudPoll["Whitmer"]!!) { (2 - assorterMeanPoll) / (2 - v) }
        val sam: List<Double> = List(nCvr) { noerror } +  // assume no errors on the cvr, the assorter value = noerror
                List(otherNoCvrVotes) { 1.0 / 2 } +
                List(schuetteNoCvrVotes) { (1 - assorterMeanNoCvr) / (2 - v) } +
                List(whitmerNoCvrVotes) { (2 - assorterMeanNoCvr) / (2 - v) }

        // sam=array([0.52459327, 0.52459327, 0.52459327, 0.52459327, 0.52459327,
        //       0.52459327, 0.52459327, 0.52459327, 0.5       , 0.22646709,
        //       0.22646709, 0.22646709, 0.22646709, 0.22646709, 0.22646709,
        //       0.22646709, 0.22646709, 0.75106037, 0.75106037, 0.75106037,
        //       0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037,
        //       0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037,
        //       0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037,
        //       0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037]

        val expectedSam = listOf(
            0.52459327, 0.52459327, 0.52459327, 0.52459327, 0.52459327,
            0.52459327, 0.52459327, 0.52459327, 0.5, 0.22646709,
            0.22646709, 0.22646709, 0.22646709, 0.22646709, 0.22646709,
            0.22646709, 0.22646709, 0.75106037, 0.75106037, 0.75106037,
            0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037,
            0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037,
            0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037,
            0.75106037, 0.75106037, 0.75106037, 0.75106037, 0.75106037
        )
        assertTrue(doublesAreClose(sam, expectedSam, doublePrecision))

        // do the same thing using real assorters
        val testMvrs = contestOA.makeTestMvrs()
        val contestUA = contestOA.makeContestUnderAudit()
        val minAllAsserter = contestUA.minAssertion()
        assertNotNull(minAllAsserter)
        val minAllAssorter = minAllAsserter!!.assorter
        println(minAllAssorter)
        assertEquals(assorterMeanAll, minAllAssorter.reportedMargin(), doublePrecision)
        assertEquals(0.5468806477264513, minAllAssorter.reportedMargin(), doublePrecision)

        // TODO
        // val minAssorterMargin = minAllAssorter.calcAssorterMargin(contestOA.id, testCvrs)
        // println(" calcAssorterMargin for min = $minAssorterMargin")
        // assertEquals(0.5468806477264513, minAssorterMargin, doublePrecision)
    }
}

// from oa_polling.ipynb
fun makeContestKalamazoo(): OneAuditContest { // TODO set margin

    // the candidates
    val info = ContestInfo(
        "Kalamazoo", 0,
        mapOf(
            "Butkovich" to 0,
            "Gelineau" to 1,
            "Kurland" to 2,
            "Schleiger" to 3,
            "Schuette" to 4,
            "Whitmer" to 5,
        ),
        SocialChoiceFunction.PLURALITY,
        nwinners = 1,
    )

    // reported results for the two strata
    val candidateVotes = mapOf(     // candidateName -> [votes(cvr), votes(nocvr)]
        "Schuette" to listOf(1349, 4220),
        "Whitmer" to listOf(3765, 16934),
        "Gelineau" to listOf(56, 462),
        "Schleiger" to listOf(19, 116),
        "Kurland" to listOf(23, 284),
        "Butkovich" to listOf(6, 66)
    )
    // val votes0: Map<Int, Int> = candidates.map { (key: String, value: List<Int>) -> Pair(info.candidateNames[key]!!, value[0]) }.toMap()

    // The stratum with linked CVRs comprised 5,294 ballots with 5,218 reported votes in the contest
    // the “no-CVR” stratum comprised 22,372 ballots with 22,082 reported votes.
    val stratumSizes = listOf(5294, 22372) // hasCvr, noCvr
    val Nc = stratumSizes.sum()

    // reported results for the two strata
    val votesCvr = candidateVotes.map { (key, value) -> Pair(info.candidateNames[key]!!, value[0]) }.toMap()
    val votesNoCvr = candidateVotes.map { (key, value) -> Pair(info.candidateNames[key]!!, value[1]) }.toMap()

    val pools = mutableListOf<BallotPool>()
    pools.add(
        // data class BallotPool(val name: String, val id: Int, val contest:Int, val ncards: Int, val votes: Map<Int, Int>) {
        BallotPool(
            "noCvr",
            1, // poolId
            0, // contestId
            stratumSizes[1],
            votes = votesNoCvr,
        )
    )

    //    override val info: ContestInfo,
    //    cvrVotes: Map<Int, Int>,   // candidateId -> nvotes;  sum is nvotes or V_c
    //    cvrNc: Int,
    //    val pools: Map<Int, OneAuditPool>, // pool id -> pool
    return OneAuditContest.make(info, votesCvr, stratumSizes[0], pools.associateBy { it.id }, Np = 0)
}