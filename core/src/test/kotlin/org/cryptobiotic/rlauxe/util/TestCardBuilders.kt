package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsForClca
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCardBuilders {

    @Test
    fun testConvertCardsRoundtrip() {
        val test = MultiContestTestData(20, 11, 20000)
        val cards = test.makeCardsFromContests()
        val cardMap = cards.associateBy { it.location }

        val cardbs = mutableListOf<CardBuilder>()
        cards.forEach {
            val cb = CardBuilder.fromCard(it)
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
            assertEquals(orgCard, it)
        }
    }

    @Test
    fun testFuzzedCards() {
        val ncontests = 20
        val test = MultiContestTestData(ncontests, 11, 50000)
        val contests: List<Contest> = test.contests
        val cards = test.makeCardsFromContests()
        val cvrs = cards.map { it.cvr() }
        val detail = false
        val ntrials = 1
        val fuzzPcts = listOf(0.0, 0.001, .005, .01, .02, .05)
        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = makeFuzzedCvrsForClca(contests.map { it.info} , cvrs, fuzzPct)
            println("fuzzPct = $fuzzPct")
            val allErrorRates = mutableListOf<PluralityErrorRates>()
            contests.forEach { contest ->
                val contestUA = makeContestUAfromCvrs(contest.info, cvrs)
                val minAssert = contestUA.minClcaAssertion()
                if (minAssert != null) repeat(ntrials) {
                    val minAssort = minAssert.cassorter
                    val samples = PluralityErrorTracker(minAssort.noerror())
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
                        println("  errorCounts = ${samples.pluralityErrorCounts()}")
                        println("  errorRates =  ${samples.errorRates()}")
                    }
                    allErrorRates.add(samples.pluralityErrorRates())
                }
            }
            val total = ntrials * ncontests
            val avgRates = PluralityErrorRates(
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