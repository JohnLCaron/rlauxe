package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.RegVotes
import org.cryptobiotic.rlauxe.audit.RegVotesImpl
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.VotesAndUndervotes
import org.cryptobiotic.rlauxe.verifier.verifyBAssortAvg
import org.cryptobiotic.rlauxe.verifier.verifyCards
import kotlin.Int
import kotlin.test.Test


// here we recreate Kalamazoo example in rlaux framework.
// also see plots/src/test/kotlin/org/cryptobiotic/rlauxe/oneaudit/KalamazooExample.kt, which ports the python directly
class TestOneAuditKalamazoo {

    @Test
    fun testReportedMargins() {
        val (contestUA, ballotPools, cvrs) = makeContestKalamazoo()
        val info = contestUA.contest.info()
        println("oaContest = $contestUA  ncandidates = ${info.candidateIds.size}")

        // fun verifyCvrs(
        //    contests: List<ContestUnderAudit>,
        //    cardIter: Iterator<Cvr>,
        //    ballotPools: List<BallotPool>,
        //    infos: Map<Int, ContestInfo>,
        //    show: Boolean = false
        //): ContestSummary {
        val infos = mapOf( info.id to info )
        //val summ =  verifyCards(listOf(contestUA), cvrs.iterator(), ballotPools, infos, show = true)
        //println(summ.results)

        //val status = verifyBAssortAvg(listOf(contestUA), cvrs.iterator(), show = true)
       // println(status)

        /*
        val minAllAssertion = contestUA.minAssertion()
        assertNotNull(minAllAssertion)
        val minAllAssorter = minAllAssertion!!.assorter
        println(minAllAssorter)
        println()
        assertEquals(0.5468806477264513, minAllAssorter.reportedMargin(), doublePrecision)

        val assortAvg = margin2mean(minAllAssorter.calcAssorterMargin(info.id, cvrs))

        val mvrVotes = tabulateVotesWithUndervotes(cvrs.iterator(), info.id, contestUA.ncandidates)
        println("mvrVotes = ${mvrVotes} assortAvg = $assortAvg reportedAvg = ${minAllAssorter.reportedMean()}")

        val cvrsNotPooled = cvrs.filter{ it.poolId == null}
        val cvrAssortAvg = margin2mean(minAllAssorter.calcAssorterMargin(info.id, cvrsNotPooled))
        val cvrReportedAvg = margin2mean(minAllAssorter.calcReportedMargin(oaContest.cvrVotes, oaContest.cvrNcards))
        println("cvrVotes = ${oaContest.cvrVotesAndUndervotes()} cvrAssortAvg = $cvrAssortAvg reportedAvg = $cvrReportedAvg")
        assertEquals(cvrReportedAvg, cvrAssortAvg, doublePrecision)

        oaContest.pools.forEach { (id, pool) ->
            val poolCvrs = mvrs.filter{ it.poolId == id}
            val poolAssortAvg = margin2mean(minAllAssorter.calcAssorterMargin(oaContest.id, poolCvrs))
            val poolReportedAvg = margin2mean(minAllAssorter.calcReportedMargin(pool.votes, pool.ncards))
            println(
                "pool-${id} votes = ${pool.votesAndUndervotes(info.voteForN, contestUA.ncandidates)} " +
                    "poolAssortAvg = $poolAssortAvg reportedAvg = $poolReportedAvg"
            )
            assertEquals(poolReportedAvg, poolAssortAvg, doublePrecision)
        }

        assertEquals(minAllAssorter.reportedMean(), assortAvg, doublePrecision) */
    }
}

// from oa_polling.ipynb
fun makeContestKalamazoo(nwinners:Int = 1): Triple<OAContestUnderAudit, List<CardPoolIF>, List<Cvr>> {

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
        nwinners = nwinners,
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

    val allVotes = candidateVotes.map { (name, votes) ->
        Pair( info.candidateNames[name]!!, votes.sum())}.toMap()

    // The stratum with linked CVRs comprised 5,294 ballots with 5,218 reported votes in the contest
    // the “no-CVR” stratum comprised 22,372 ballots with 22,082 reported votes.
    val stratumSizes = listOf(5294, 22372) // hasCvr, noCvr
    val Nc = stratumSizes.sum()
    val contest = Contest(info, allVotes, Nc, Nc)

    val poolVotes = candidateVotes.map { (name, votes) ->
        Pair( info.candidateNames[name]!!, votes[1])}.toMap()
    val regVotes = RegVotesImpl(poolVotes, stratumSizes[1])
    val cardPool = CardPoolImpl("kali", 1, info.id, regVotes)

    val contestUA = OAContestUnderAudit(contest)
    addOAClcaAssortersFromMargin(listOf(contestUA), listOf(cardPool))

    // reported results for the two strata
    val cvrVotes = candidateVotes.map { (key, value) -> Pair(info.candidateNames[key]!!, value[0]) }.toMap()
    val cvrNcards = stratumSizes[0]
    val cvrUndervotes = cvrNcards - cvrVotes.values.sum()

    // fun makeTestMvrs(
    //    oaContestUA: OAContestUnderAudit,
    //    cvrNcards: Int,
    //    cvrVotes:Map<Int, Int>,
    //    cvrUndervotes: Int,
    //    pools: List<BallotPool>): List<Cvr> {
    val cvrs = makeTestMvrs(contestUA, cvrNcards = cvrNcards, cvrVotes, cvrUndervotes, listOf(cardPool))
    return Triple(contestUA, listOf(cardPool), cvrs)
}

// single contest, for testing
class CardPoolImpl(override val poolName: String, override val poolId: Int, val contestId: Int, val regVotes: RegVotes) : CardPoolIF {
    override val assortAvg = mutableMapOf<Int, MutableMap<AssorterIF, AssortAvg>>()  // contest -> assorter -> average
    override fun regVotes() = mapOf(contestId to regVotes)
    override fun contains(contestId: Int) = contestId == this.contestId
    override fun ncards() = regVotes.ncards()

    override fun contests() = intArrayOf(contestId)

    override fun toBallotPools(): List<BallotPool> {
        return listOf(BallotPool("poolName", poolId, contestId, regVotes.ncards(), regVotes.votes))
    }

    override fun votesAndUndervotes(contestId: Int): VotesAndUndervotes {
        val poolUndervotes = ncards() - regVotes.votes.values.sum()
        return VotesAndUndervotes(regVotes.votes, poolUndervotes, 1)
    }
}