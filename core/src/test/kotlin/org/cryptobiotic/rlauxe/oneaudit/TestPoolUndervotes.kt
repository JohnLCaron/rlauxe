package org.cryptobiotic.rlauxe.oneaudit

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.createSortedCards
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.partition
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.secureRandom
import org.junit.jupiter.api.Test
import kotlin.math.max


class TestPoolUndervotes {
    /*
    // TODO fuzz tests
    @Test
    fun testPoolUndervotesOneContest() {
        val contest = makeSmallContest("onlyContest", id = 1, Nc = 25)
        println(contest)
        val pool = contest.pools.values.first()

        val mvrs = makeTestMvrs(contest)
        val cards = createSortedCards(mvrs, secureRandom.nextLong())

        val contestUA = contest.makeContestUnderAudit()
        val bassorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        val pAssorter = bassorter.assorter
        val oaAssorter = OaPluralityAssorter.makeFromClcaAssorter(bassorter as OneAuditClcaAssorter)
        println("pool calcReportedMargin = ${pool.calcReportedMargin(pAssorter.winner(), pAssorter.loser())}")
        checkAssorterAvgFromCards(contest, cards)

        println()
        CountAssorterValues("pAssorter", pAssorter, contestUA.id, cards, show = false).show()
        println()
        CountAssorterValues("oaAssorter", oaAssorter, contestUA.id, cards, show = false).show()
        println()
    }

    // mix two contests on the ballots, but only examine the first one
    @Test
    fun testPoolUndervotesTwoContests() {
        val contest1 = makeSmallContest("contest1", id = 1, Nc = 21)
        print(contest1)

        val contest2 = makeSmallContest("contest2", id = 2, Nc = 23)
// println(contest2)

        val mvrns = makeTestNonPooledMvrs(listOf(contest1, contest2))
        val mvrps = makeTestPooledMvrs(listOf(contest1, contest2), poolId = 1)
        val cards = createSortedCards(mvrns + mvrps, secureRandom.nextLong())

        val contestUA = contest1.makeContestUnderAudit()
        val bassorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        val pAssorter = bassorter.assorter
        val oaAssorter = OaPluralityAssorter.makeFromClcaAssorter(bassorter)

        val pool = contest1.pools.values.first()
        println("pool calcReportedMargin = ${pool.calcReportedMargin(pAssorter.winner(), pAssorter.loser())}")
        checkAssorterAvgFromCards(contest1, cards)

// contest 1 only
        println()
        CountAssorterValues("pAssorter", pAssorter, contestUA.id, cards, show = false).show()
        println()
        CountAssorterValues("oaAssorter", oaAssorter, contestUA.id, cards, show = false).show()
        println()
    }

    @Test
    fun testClcaAssertions() {
        val contest1 = makeSmallContest("contest1", id = 1, Nc = 25)
        print(contest1)

        val contest2 = makeSmallContest("contest2", id = 2, Nc = 100)
// println(contest2)

        val mvrns = makeTestNonPooledMvrs(listOf(contest1, contest2))
        val mvrps = makeTestPooledMvrs(listOf(contest1, contest2), poolId = 1)
        val poolContest1 = mvrps.filter { it.hasContest(contest1.id) }.size
        val poolContest2 = mvrps.filter { it.hasContest(contest2.id) }.size
        println("pool ncards = ${mvrps.size} countContest1=$poolContest1  countContest2=$poolContest2")
        println()

        val mvrs = mvrns + mvrps
        val cards = createSortedCards(mvrs, secureRandom.nextLong())

        val contestUA = contest1.makeContestUnderAudit()
        val bassorter = contestUA.minClcaAssertion()!!.cassorter as OneAuditClcaAssorter
        val pAssorter = bassorter.assorter
        val oaAssorter = OaPluralityAssorter.makeFromClcaAssorter(bassorter)

        val pool = contest1.pools.values.first()
        println("pool calcReportedMargin = ${pool.calcReportedMargin(pAssorter.winner(), pAssorter.loser())}")
        checkAssorterAvgFromCards(contest1, cards)

// contest 1 only
        println()
        CountAssorterValues("pAssorter", pAssorter, contestUA.id, cards, show = false).show()
        println()
        CountAssorterValues("oaAssorter", oaAssorter, contestUA.id, cards, show = false).show()
        println()

        val countContest1 = cards.filter { it.hasContest(contest1.id) }.size
        val countContest2 = cards.filter { it.hasContest(contest2.id) }.size
        println("cards.size = ${cards.size} countContest1=$countContest1  countContest2=$countContest2")
        val prng = Prng(secureRandom.nextLong())
        val cvrs = mvrs.mapIndexed { idx, it -> AuditableCard.fromCvr(it, idx, prng.next()).cvr() }
        val pairs = mvrs.zip(cvrs)
        CountClcaAssorterValues("clcaAssorter", bassorter, contestUA.id, pairs, show = false).show()
        println()
    }
}

class CountClcaAssorterValues(val name: String, cassorter: ClcaAssorter, contestId: Int, pairs: List<Pair<Cvr, Cvr>>, usePhantoms: Boolean = false, show: Boolean= false) {
    val assortValueCount = AssortValueCount()
    val cvrValueCount = AssortValueCount()
    val passortValueCount = AssortValueCount()

    init {
        pairs.forEach { (mvr, cvr) ->
            // it.hasContest() is at the center of the issue
            if (mvr.hasContest(contestId)) {
                //     open fun bassort(mvr: Cvr, cvr:Cvr): Double {
                val av = cassorter.bassort(mvr, cvr)
                assortValueCount.addValue(av)
                if (cvr.poolId == null) {
                    cvrValueCount.addValue(av)
                } else {
                    passortValueCount.addValue(av)
                }
            }
        }
    }

    fun show() {
        println(name)
        println("  cvrValues=${cvrValueCount.show()}")
        println(" poolValues=${passortValueCount.show()}")
        println("  allValues=${assortValueCount.show()}")
    }
}

class CountAssorterValues(val name: String, passort: AssorterIF, contestId: Int, cards: Iterable<AuditableCard>, usePhantoms: Boolean = false, show: Boolean= false) {
    val assortValueCount = AssortValueCount()
    val cvrValueCount = AssortValueCount()
    val passortValueCount = AssortValueCount()
    val mean: Double

    init {
        mean = cards.filter { it.hasContest(contestId) }.map { card ->
            val cvr = card.cvr()
            val av = passort.assort(cvr, usePhantoms = usePhantoms)
            assortValueCount.addValue(av)
            if (cvr.poolId == null) {
                cvrValueCount.addValue(av)
            } else {
                passortValueCount.addValue(av)
            }
            if (show) println("    cvr ${cvr} assortValue=$av")
            av
        }.average()
    }

    fun show() {
        println(name)
        println("  cvrValues=${cvrValueCount.show()}")
        println(" poolValues=${passortValueCount.show()}")
        println("  allValues=${assortValueCount.show()}")
    }
}

class AssortValueCount() {
    val assortValueCount = mutableMapOf<Double, Int>()
    fun addValue(av: Double) {
        val accum = assortValueCount.getOrPut(av) { 0 }
        assortValueCount[av] = accum + 1
    }

    fun show() = buildString {
        val count = assortValueCount.map { it.value }.sum()
        val avg = assortValueCount.map { it.key * it.value }.sum() / count
        append("$assortValueCount count= $count avg = $avg")
    }
}

// assumes Np = 0
fun makeSmallContest(name: String, id:Int, Nc: Int, nwinners:Int = 1): OneAuditContest1 { // TODO set margin
    // the candidates
    val info = ContestInfo(
        name = name,
        id = id,
        mapOf(
            "Butkovich" to 0,
            "Gelineau" to 1,
        ),
        SocialChoiceFunction.PLURALITY,
    )

// partition Nc into 6 partitions randomly
    val part = partition(Nc, 6).map { it.second }
//  cvr: cand1, cand2, undervotes: 0, 1, 2
// pool: cand1, cand2, undervotes: 3, 4, 5

// reported results for the two strata
    val votesCvr = mapOf(
        0 to part[0],
        1 to part[1]
    )                                                       // pool: c1, c2, undervotes
    val votesNoCvr = mapOf(
        0 to part[3],
        1 to part[4]
    )                                                       // pool: c1, c2, undervotes

//  undervotes >= winnerVotes * voteForN - nvotes   // eq C3 (in Contest.kt)
    val winnerCvr = votesCvr.map { it.value }.max()
    val undervotesCvr = max(winnerCvr * info.voteForN - votesCvr.values.sum(), part[2])

    val winnerNoCvr = votesNoCvr.map { it.value }.max()
    val undervotesNoCvr = max(winnerNoCvr * info.voteForN - votesNoCvr.values.sum(), part[5])

// Nc = Np + (undervotes + nvotes) / voteForN  // C2
    val ncCvr = (undervotesCvr + votesCvr.values.sum()) / info.voteForN
    val ncNoCvr = (undervotesNoCvr + votesNoCvr.values.sum()) / info.voteForN

    val pools = mutableListOf<BallotPool>()
    pools.add(
// data class BallotPool(val name: String, val id: Int, val contest:Int, val ncards: Int, val votes: Map<Int, Int>) {
        BallotPool(
            "noCvr",
            poolId = 1,
            contest = id,
            ncards = ncNoCvr,
            votes = votesNoCvr,
        )
    )

    return OneAuditContest1.make(info, votesCvr, ncCvr, pools, 0, 0) // TODO
} */
}

