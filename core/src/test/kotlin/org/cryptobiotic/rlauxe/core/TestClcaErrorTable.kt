package org.cryptobiotic.rlauxe.core

import org.cryptobiotic.rlauxe.estimate.ContestSimulation
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsForPolling
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

// TODO REDO
class TestClcaErrorTable {

    /* @Test
    fun testClcaRateRoundtrip() {
        repeat(111) {
            val pct = Random.nextDouble(1.0)
            for (ncands in 1..11) {
                val errors = ClcaErrorTable.getErrorRates(ncands, pct)
                val roundtrip = ClcaErrorTable.calcFuzzPct(ncands, errors)
                roundtrip.forEach { assertEquals(pct, it, doublePrecision) }
            }
        }
    } TODO */

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
            val testMvrs = makeFuzzedCvrsForPolling(listOf(sim.contest), testCvrs, mvrsFuzzPct)

            val contestUA = ContestWithAssertions(sim.contest).addStandardAssertions()
            val assertion = contestUA.minClcaAssertion()!!
            val errors = ClcaErrorTable.calcErrorRates(0, assertion.cassorter, testMvrs.zip(testCvrs))
            val estPct = ClcaErrorTable.calcFuzzPct(2, errors)
            println("margin=$margin mvrsFuzzPct=$mvrsFuzzPct estPct=$estPct")
            estPct.forEach { print(" ${df(abs(it - mvrsFuzzPct))},") }
            println()
            estPct.forEach { assertEquals(mvrsFuzzPct, it, .05) }
        }
    }
}