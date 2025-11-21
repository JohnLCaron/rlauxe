package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.df
import kotlin.test.Test
import kotlin.test.assertEquals

class CompareAdaptiveBetting {

    @Test
    fun compareAdaptiveBetting() {
        val N = 1000
        val margins = listOf(.025) // , .05, .1)
        // val p2s = listOf(.0001, .001, .01)

        for (margin in margins) {
            val noerror = 1 / (2 - margin) // aka noerror
            // for (p2 in p2s) {
                println("margin=$margin")

                //     val N: Int, // max number of cards for this contest
                //    val withoutReplacement: Boolean = true,
                //    val a: Double, // compareAssorter.noerror
                //    val d: Int,  // weight
                //    errorRates: ClcaErrorRates,
                val bet1 = AdaptiveBetting(N, a=noerror, d=100, errorRates=PluralityErrorRates(0.0, 0.0, 0.0, 0.0))
                val tracker1 = PluralityErrorTracker(noerror)

                //     val N: Int, // max number of cards for this contest
                //    val noerror: Double, // a priori estimate of the error rates
                //    val withoutReplacement: Boolean = true,
                //    val d: Int,  // trunc weight
                //    val lowLimit: Double = .00001
                val bet2 = GeneralAdaptiveBetting(N, noerror=noerror, d=100)
                val tracker2 = ClcaErrorTracker(noerror)

            var count = 0
                var lam1 = 0.0
                var lam2 = 0.0
                repeat(1000) {
                    count++
                    lam1 = bet1.bet(tracker1)
                    lam2 = bet2.bet(tracker2)
                    // println("lam1= $lam1, lam2=$lam2")
                    if (count % 100 == 0) {
                        tracker1.addSample(noerror/2)
                        tracker2.addSample(noerror/2)
                    } else {
                        tracker1.addSample(noerror)
                        tracker2.addSample(noerror)
                    }
                }
                println("FINAL lam1= $lam1, lam2=$lam2")
                assertEquals(lam1, lam2)
            // }
        }
    }

    @Test
    fun compareSimulatedCvrs() {
        val Nc = 50000
        val margins = listOf(.025) // , .05, .1)
        // val p2s = listOf(.0001, .001, .01)

        for (margin in margins) {
            val noerror = 1 / (2 - margin) // aka noerror
            // for (p2 in p2s) {
            println("margin=$margin")

            val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=0.0, phantomPct=0.0)
            var testCvrs = sim.makeCvrs() // includes undervotes and phantoms
            val testMvrs =  makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, .01)

            val contestUA = ContestUnderAudit(sim.contest, Nbin=Nc).addStandardAssertions()
            val cassorter = contestUA.minClcaAssertion().first!!.cassorter

            //     val N: Int, // max number of cards for this contest
            //    val withoutReplacement: Boolean = true,
            //    val a: Double, // compareAssorter.noerror
            //    val d: Int,  // weight
            //    errorRates: ClcaErrorRates,
            val bet1 = AdaptiveBetting(Nc, a=noerror, d=100, errorRates=PluralityErrorRates(0.0, 0.0, 0.0, 0.0))
            val tracker1 = PluralityErrorTracker(noerror)
            val risk1 = Risk(Nc, tracker1, bet1)

            //     val N: Int, // max number of cards for this contest
            //    val noerror: Double, // a priori estimate of the error rates
            //    val withoutReplacement: Boolean = true,
            //    val d: Int,  // trunc weight
            //    val lowLimit: Double = .00001
            val bet2 = GeneralAdaptiveBetting(Nc, noerror=noerror, d=100)
            val tracker2 = ClcaErrorTracker(noerror)
            val risk2 = Risk(Nc, tracker2, bet2)

            var count = 0
            repeat(1000) {
                val bassortValue = cassorter.bassort(testMvrs[count], testCvrs[count])

                risk1.addSample(bassortValue)
                risk2.addSample(bassortValue)
                println("${df(risk1.pvalue())} ${df(risk2.pvalue())}")

                count++
            }
            println("FINAL pvalue1=${risk1.pvalue()} pvalue2=${risk2.pvalue()}")
            assertEquals(risk1.pvalue(), risk2.pvalue())
        }
    }
}

class Risk(val N : Int, val tracker: SampleTracker, val bet : BettingFn) {
    var testStatistic = 1.0
    fun addSample(bassort: Double) {
        val lamj = bet.bet(tracker)
        val mj = populationMeanIfH0(N, true, tracker)  // approx .5

        val ttj = 1.0 + lamj * (bassort - mj)
        testStatistic *= ttj
        tracker.addSample(bassort)
    }
    fun pvalue() = 1.0 / testStatistic
}