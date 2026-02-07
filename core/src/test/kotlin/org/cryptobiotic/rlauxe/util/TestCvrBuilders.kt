package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsForClca
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsForPolling
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCvrBuilders {

    @Test
    fun testConvertCvrsRoundtrip() {
        val test = MultiContestTestData(20, 11, 20000)
        val contests: List<Contest> = test.contests
        val cvrs = test.makeCvrsFromContests()

        // turn the cvrs into mutable builders
        val cvrsbs = CvrBuilders()
        cvrsbs.addContests( contests.map { it.info } )
        cvrs.forEach { CvrBuilder.fromCvr(cvrsbs, it) }

        // convert back to Cvr
        val roundtrip: List<Cvr> = cvrsbs.build().map { it }
        // same order
        roundtrip.forEachIndexed { idx, it ->
            assertEquals(cvrs[idx], it)
        }

        // cvrsbs.show()

        // can we replace with CvrBuilder2? yes
        val cvrb2s = cvrs.map { CvrBuilder2.fromCvr(it)}
        cvrb2s.forEachIndexed { idx, it ->
            assertEquals(cvrs[idx], it.build())
        }
    }

    @Test
    fun testFuzzedCvrs() {
        val ncontests = 20
        val test = MultiContestTestData(ncontests, 11, 50000)
        val contests: List<Contest> = test.contests
        val cvrs = test.makeCvrsFromContests()
        val show = false
        val detail = false
        val ntrials = 1
        val fuzzPcts = listOf(0.001, .005, .01, .02, .05)
        fuzzPcts.forEach { fuzzPct ->
            val fcvrs = makeFuzzedCvrsForPolling(contests.map { it.info() }, cvrs, fuzzPct)
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
                    if (show) println("$it ${contest.name} changed = $count out of ${ccount} = ${df(fuzz)}")
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

            //////////////////////////////////////////////////////////////
            val infoList = contests.map { it.info() }
            val cvrsForClca = makeFuzzedCvrsForClca(infoList, cvrs, fuzzPct)
            val allErrorRates2 = mutableListOf<PluralityErrorRates>()
            contests.forEach { contest ->
                val contestUA = makeContestUAfromCvrs(contest.info, cvrsForClca)
                val minAssert = contestUA.minClcaAssertion()
                if (minAssert != null) repeat(ntrials) {
                    val minAssort = minAssert.cassorter
                    val samples = PluralityErrorTracker(minAssort.noerror())
                    var ccount = 0
                    var countChanged = 0
                    cvrsForClca.forEachIndexed { idx, fcvr ->
                        if (fcvr.hasContest(contest.id)) {
                            samples.addSample(minAssort.bassort(fcvr, cvrs[idx]))
                            ccount++
                            if (cvrs[idx] != fcvr) countChanged++
                        }
                    }
                    val fuzz = countChanged.toDouble() / ccount
                    if (show) println("$it ${contest.name} changed = $countChanged out of ${ccount} = ${df(fuzz)}")
                    if (detail) {
                        println("  errorCounts = ${samples.pluralityErrorCounts()}")
                        println("  errorRates =  ${samples.errorRates()}")
                    }
                    allErrorRates2.add(samples.pluralityErrorRates())
                }
            }
            val total2 = ntrials * ncontests
            val avgRates2 = PluralityErrorRates(
                allErrorRates.sumOf { it.p2o } / total2,
                allErrorRates.sumOf { it.p1o } / total2,
                allErrorRates.sumOf { it.p1u } / total2,
                allErrorRates.sumOf { it.p2u } / total2,
            )
            println("  avgRates2 = $avgRates2")
            assertEquals(avgRates, avgRates2)
        }
    }
}