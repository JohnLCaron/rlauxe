package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.doublesAreClose
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.junit.jupiter.api.Assertions.assertNotNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestOneAuditKalamazoo {

    @Test
    fun testOneAuditKalamazoo() {
        val contest = makeContestKalamazoo()
        val strataCvr = contest.strata[0]
        val strataNocvr = contest.strata[1]
        val info = contest.info

        // votes_cvr=5218.0 votes_poll=22082.0 diff cvr: 76.0, diff poll: 290.0
        assertEquals(5218, strataCvr.votes.values.sum())
        assertEquals(22082, strataNocvr.votes.values.sum())
        assertEquals(76, strataCvr.Ng - strataCvr.votes.values.sum())
        assertEquals(290, strataNocvr.Ng - strataNocvr.votes.values.sum())

        // whitmer=20699, schuette=5569, assorter_mean_all=0.5468806477264513
        val whitmerTotal = contest.votes[info.candidateNames["Whitmer"]!!]!!
        val schuetteTotal = contest.votes[info.candidateNames["Schuette"]!!]!!
        assertEquals(20699, whitmerTotal)
        assertEquals(5569, schuetteTotal)
        val assorterMeanAll = (whitmerTotal - schuetteTotal).toDouble() / contest.Nc
        assertEquals(0.5468806477264513, assorterMeanAll, doublePrecision)

        // assorter_mean_poll=0.5682996602896477,
        val whitmerNoCvr = strataNocvr.votes[info.candidateNames["Whitmer"]!!]!!
        val schuetteNoCvr = strataNocvr.votes[info.candidateNames["Schuette"]!!]!!
        val assorterMeanNoCvr = (whitmerNoCvr - schuetteNoCvr).toDouble() / strataNocvr.Ng  // using strata Nc
        assertEquals(0.5682996602896477, assorterMeanNoCvr, doublePrecision)

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
        val testCvrs = contest.makeTestCvrs()
        val contestUA = contest.makeContestUnderAudit(testCvrs)
        val minAllAsserter = contestUA.minAssertion()
        assertNotNull(minAllAsserter)
        val minAllAssorter = minAllAsserter!!.assorter
        println(minAllAssorter)
        assertEquals(assorterMeanAll, minAllAssorter.reportedMargin(), doublePrecision)
        assertEquals(0.5468806477264513, minAllAssorter.reportedMargin(), doublePrecision)

        val minAssorterMargin = minAllAssorter.calcAssorterMargin(contest.id, testCvrs)
        println(" calcAssorterMargin for min = $minAssorterMargin")
        assertEquals(0.5468806477264513, minAssorterMargin, doublePrecision)
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
    val candidates = mapOf(     // candidateName -> [votes(cvr), votes(nocvr)]
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
    val stratumNames = listOf("hasCvr", "noCvr")
    val stratumSizes = listOf(5294, 22372) // CVR, noCvr

    //    val strataName: String,
    //    val info: ContestInfo,
    //    val votes: Map<Int, Int>,   // candidateId -> nvotes
    //    val Nc: Int,  // upper limit on number of ballots in this starata for this contest
    //    val Np: Int,  // number of phantom ballots in this starata for this contest
    val strata = mutableListOf<OneAuditStratum>()
    repeat(2) { idx ->
        strata.add(
            OneAuditStratum(
                stratumNames[idx],
                hasCvrs = (idx == 0),
                info,
                candidates.map { (key, value) -> Pair(info.candidateNames[key]!!, value[idx]) }.toMap(),
                Ng = stratumSizes[idx],
                Np = 0  // TODO investigate
            )
        )
    }
    return OneAuditContest(info, strata)
}

fun makeCvrs() { // TODO set proportion
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

    val pollingCvrs = mutableListOf<Cvr>()
    repeat(otherNoCvrVotes) { pollingCvrs.add(makeCvr(1)) }
    repeat(schuetteNoCvrVotes) { pollingCvrs.add(makeCvr(4)) }
    repeat(whitmerNoCvrVotes) { pollingCvrs.add(makeCvr(5)) }
}