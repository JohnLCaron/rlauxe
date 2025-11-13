package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCardBuilders {

    @Test
    fun testConvertCardsRoundtrip() {
        val test = MultiContestTestData(20, 11, 20000, hasStyle=true)
        val cards = test.makeCardsFromContests()
        val cardMap = cards.associateBy { it.location }

        val cardbs = mutableListOf<CardBuilder>()
        cards.forEach {
            val cb = CardBuilder(it.location, it.index, it.prn, it.phantom, it.possibleContests, null, it.poolId, it.cardStyle)
            if (it.votes != null) {
                it.votes.forEach{ (contestId, votes) -> cb.replaceContestVotes(contestId, votes) }
            }
            cardbs.add( cb)
        }

        // convert back to Cvr
        val roundtrip: List<AuditableCard> = cardbs.map { it.build() }
        // same order
        roundtrip.forEach{
            val orgCard = cardMap[it.location]!!
            if (orgCard != it) {
                orgCard.equals(it)
            }
            assertEquals(orgCard, it)
        }
    }

    @Test
    fun testFuzzedCards() {
        val ncontests = 20
        val test = MultiContestTestData(ncontests, 11, 50000, hasStyle=true)
        val contests: List<Contest> = test.contests
        val cards = test.makeCardsFromContests()
        val cvrs = cards.map { it.cvr() }
        val detail = false
        val ntrials = 1
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = makeFuzzedCvrsFrom(contests, cvrs, fuzzPct)
            println("fuzzPct = $fuzzPct")
            val allErrorRates = mutableListOf<ClcaErrorRates>()
            contests.forEach { contest ->
                val contestUA = makeContestUAfromCvrs(contest.info, cvrs)
                val minAssert = contestUA.minClcaAssertion().first
                if (minAssert != null) repeat(ntrials) {
                    val minAssort = minAssert.cassorter
                    val samples = PrevSamplesWithRates(minAssort.noerror())
                    var ccount = 0
                    var count = 0
                    fcvrs.forEachIndexed { idx, fcvr ->
                        if (fcvr.hasContest(contest.id)) {
                            samples.addSample(minAssort.bassort(fcvr, cvrs[idx]))
                            ccount++
                            if (cvrs[idx] != fcvr) count++
                        }
                    }
                    val fuzz = count.toDouble() / ccount
                    println("$it ${contest.name} changed = $count out of ${ccount} = ${df(fuzz)}")
                    if (detail) {
                        println("  errorCounts = ${samples.errorCounts()}")
                        println("  errorRates =  ${samples.errorRates()}")
                    }
                    allErrorRates.add(samples.errorRates())
                }
            }
            val total = ntrials * ncontests
            val avgRates = ClcaErrorRates(
                allErrorRates.sumOf { it.p2o } / total,
                allErrorRates.sumOf { it.p1o } / total,
                allErrorRates.sumOf { it.p1u } / total,
                allErrorRates.sumOf { it.p2u } / total,
            )
            println("  avgRates = $avgRates")
            // println("  error% = ${avgRates.map { it / (total * fuzzPct) }}")
        }
    }
}