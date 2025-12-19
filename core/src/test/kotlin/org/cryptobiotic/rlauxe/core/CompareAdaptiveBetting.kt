package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// CANDIDATE for removal
class CompareAdaptiveBetting {

   //  @Test
    fun compareAdaptiveBetting() {
        val N = 1000
        val margins = listOf(.025) // , .05, .1)
        // val p2s = listOf(.0001, .001, .01)

        for (margin in margins) {
            val noerror = 1 / (2 - margin) // aka noerror
            println("margin=$margin")

            val errorRates = PluralityErrorRates(0.0, 0.0, 0.0, 0.0)
            val bet1 = AdaptiveBetting(N, a=noerror, d=100, errorRates=errorRates)
            val tracker1 = PluralityErrorTracker(noerror)

            val errorCounts = ClcaErrorCounts.fromPluralityErrorRates(errorRates, totalSamples = N, noerror = noerror, upper = 1.0)
            val bet2 = GeneralAdaptiveBetting(N, oaErrorRates=null, d=100, maxRisk = 1.0)
            val tracker2 = ClcaErrorTracker(noerror, 1.0)

            var count = 0
                var lam1 = 0.0
                var lam2 = 0.0
                repeat(100) {
                    count++
                    lam1 = bet1.bet(tracker1)
                    lam2 = bet2.bet(tracker2)
                    // println("lam1= $lam1, lam2=$lam2")
                    if (count % 50 == 0) {
                        tracker1.addSample(noerror/2)
                        tracker2.addSample(noerror/2)
                    } else {
                        tracker1.addSample(noerror)
                        tracker2.addSample(noerror)
                    }
                }
                println("FINAL lambda: AdaptiveBetting= $lam1, GeneralAdaptiveBetting=$lam2")
                assertEquals(lam1, lam2, .001)
            // }
        }
    }

    // @Test
    fun compareSimulatedCvrs() {
        val showSteps = false
        val Nc = 50000
        val margins = listOf(.025) // , .05, .1)
        // val p2s = listOf(.0001, .001, .01)

        for (margin in margins) {
            val noerror = 1 / (2 - margin) // aka noerror
            println("margin=$margin")

            val sim = ContestSimulation.make2wayTestContest(Nc=Nc, margin, undervotePct=0.0, phantomPct=0.0)
            val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
            val testMvrs =  makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, .01)

            val contestUA = ContestUnderAudit(sim.contest, NpopIn=Nc).addStandardAssertions()
            val cassorter = contestUA.minClcaAssertion()!!.cassorter

            val errorRates=PluralityErrorRates(0.0, 0.0, 0.0, 0.0)
            val bet1 = AdaptiveBetting(Nc, a=noerror, d=100, errorRates=errorRates)
            val tracker1 = PluralityErrorTracker(noerror)
            val risk1 = Risk(Nc, tracker1, bet1, cassorter.upperBound())

            val errorCounts = ClcaErrorCounts.fromPluralityErrorRates(errorRates, totalSamples = 0, noerror = noerror, upper = 1.0)
            val bet2 = GeneralAdaptiveBetting(Nc, oaErrorRates=null, d=100, maxRisk = 1.0)
            val tracker2 = ClcaErrorTracker(noerror, cassorter.assorter.upperBound())
            val risk2 = Risk(Nc, tracker2, bet2, cassorter.upperBound())

            var count = 0
            repeat(1000) {
                val bassortValue = cassorter.bassort(testMvrs[count], testCvrs[count])

                val lam1 = risk1.addSample(bassortValue)
                val lam2 = risk2.addSample(bassortValue)
                if (showSteps) {
                    println("bassortValue ${dfn(bassortValue, 6)}")
                    println("lam ${dfn(lam1, 6)} ${dfn(lam2, 6)}")
                    println("pvalue ${dfn(risk1.pvalue(), 6)} ${dfn(risk2.pvalue(), 6)}")
                }
                if (lam1/lam2 > 2.0 || lam2/lam1 > 2.0)
                    print(" hay ")
                if (risk1.pvalue()/risk2.pvalue() > 2.0 || risk2.pvalue()/risk1.pvalue() > 2.0)
                    print(" haya ")
                if (!doubleIsClose(lam1,lam2, .001))
                    println("wtf")

                count++
            }
            println("FINAL pvalue: AdaptiveBetting=${risk1.pvalue()} GeneralAdaptiveBetting=${risk2.pvalue()}")
            if(!doubleIsClose(risk1.pvalue(), risk2.pvalue(), .001))
                print("why")
            assertTrue(doubleIsClose(risk1.pvalue(), risk2.pvalue(), .001))
        }
    }
}

class Risk(val N : Int, val tracker: SampleTracker, val bet : BettingFn, val sampleUpperBound: Double) {
    var testStatistic = 1.0
    var mj = 0.0
    var tj = 0.0
    var lastBassort = 0.0

    fun addSample(bassort: Double): Double {
        val lamj = bet.bet(tracker)
        mj = populationMeanIfH0(N, true, tracker)  // approx .5

        tj = if (doubleIsClose(0.0, mj) || doubleIsClose(sampleUpperBound, mj)) { // 2, 3
            1.0
        } else {
            // terms[i] = (1 + λi (Xi − µi )) ALPHA eq 10 // approx (1 + lam * (xj - .5))
            val ttj = 1.0 + lamj * (bassort - mj) // (1 + λi (Xi − µi )) ALPHA eq 10, SmithRamdas eq 34 (WoR)
            if (doubleIsClose(ttj, 0.0)) 1.0 else ttj // 4
        }
        lastBassort = bassort
        testStatistic *= tj
        tracker.addSample(bassort)
        return lamj
    }
    fun pvalue() = 1.0 / testStatistic
}