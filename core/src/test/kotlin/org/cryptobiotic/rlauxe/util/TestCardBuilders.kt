package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.workflow.makeFuzzedCvrsForClca
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCardBuilders {

    @Test
    fun testOneBuilder() {
        val card = AuditableCard ("cvr$42", null, 42, 4422L, false, // intArrayOf(1,2,3),
            null, mapOf(1 to intArrayOf(1,2,3), 2 to intArrayOf(4,5,6), 3 to intArrayOf(0,1)), CardStyle.fromCvrBatch)
        val cb = AuditableCardBuilder.fromCard(card)
        val back = cb.build()
        assertEquals(card, back)
    }

    @Test
    fun testReplaceContestVotes() {
        val card = AuditableCard ("cvr$42", null, 42, 4422L, false, // intArrayOf(1,2,3),
            null, mapOf(1 to intArrayOf(1,2,3), 2 to intArrayOf(4,5,6), 3 to intArrayOf(0,1)), CardStyle.fromCvrBatch)
        val cb = AuditableCardBuilder.fromCard(card)
        card.votes!!.forEach { (contestId, votes) -> cb.replaceContestVotes(contestId, votes) }
        val back = cb.build()
        assertEquals(card, back)
    }

    @Test
    fun testConvertCardsRoundtrip() {
        val test = MultiContestTestData(20, 11, 20000)
        val (mvrs, cards, pools, styles) = test.makeMvrCardAndPops()
        val cardMap = cards.associateBy { it.id }

        val cardbs = mutableListOf<AuditableCardBuilder>()
        cards.forEach {
            val cb = AuditableCardBuilder.fromCard(it)
            if (it.votes != null) {
                it.votes.forEach{ (contestId, votes) -> cb.replaceContestVotes(contestId, votes) }
            }
            cardbs.add( cb)
        }

        // convert back to Cvr
        val roundtrip: List<AuditableCard> = cardbs.map { it.build() }
        // same order
        roundtrip.forEach{
            val orgCard = cardMap[it.id]!!
            assertEquals(orgCard, it)
        }
    }

    @Test
    fun testFuzzedCards() {
        val ncontests = 20
        val test = MultiContestTestData(ncontests, 11, 50000)
        val contests: List<Contest> = test.contests
        val cards = test.makeCardsFromContests()
        val cvrs = cards.map { it.toCvr() }
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
                            samples.addSample(minAssort.bassort(fcvr, cvrs[idx], true))
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