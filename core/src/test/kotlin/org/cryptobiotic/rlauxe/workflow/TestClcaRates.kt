package org.cryptobiotic.rlauxe.workflow

import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.doublePrecision
import org.cryptobiotic.rlauxe.sampling.ContestSimulation
import org.cryptobiotic.rlauxe.sampling.makeFuzzedCvrsFrom
import org.cryptobiotic.rlauxe.util.df
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TestClcaRates {

    @Test
    fun testClcaRateRoundtrip() {
        repeat(111) {
            val pct = Random.nextDouble(1.0)
            for (ncands in 1..11) {
                val errors = ClcaErrorRates.getErrorRates(ncands, pct)
                val roundtrip = ClcaErrorRates.calcFuzzPct(ncands, errors)
                roundtrip.forEach { assertEquals(pct, it, doublePrecision) }
            }
        }
    }

    @Test
    fun testClcaCalcErrorRates() {
        repeat(131) {
            val mvrsFuzzPct = Random.nextDouble(0.25)
            val margin = Random.nextDouble(0.10)
            val sim =
                ContestSimulation.make2wayTestContest(
                    Nc = 11111,
                    margin,
                    undervotePct = 0.0,
                    phantomPct = 0.0
                )

            val testCvrs = sim.makeCvrs() // includes undervotes and phantoms
            val testMvrs = makeFuzzedCvrsFrom(listOf(sim.contest), testCvrs, mvrsFuzzPct)

            val contestUA = ContestUnderAudit(sim.contest).makeClcaAssertions(testCvrs)
            val assertion = contestUA.minClcaAssertion()!!
            val errors = ClcaErrorRates.calcErrorRates(0, assertion.cassorter, testMvrs.zip(testCvrs))
            val estPct = ClcaErrorRates.calcFuzzPct(2, errors)
            println("margin=$margin mvrsFuzzPct=$mvrsFuzzPct estPct=$estPct")
            estPct.forEach { print(" ${df(abs(it - mvrsFuzzPct))},") }
            println()
            estPct.forEach { assertEquals(mvrsFuzzPct, it, .05) }
        }
    }
}