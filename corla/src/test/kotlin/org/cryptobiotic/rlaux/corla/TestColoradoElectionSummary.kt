package org.cryptobiotic.rlaux.corla

import kotlin.test.Test

class TestColoradoElectionSummary {

    @Test
    fun showMargins() {
        val contests = readColoradoElectionSummaryCsv("src/test/data/2024/summary.csv")
        contests.forEach { it.complete() }
        println("--------------------------------------------------------------")
        contests.sortedBy { it.underPct }.reversed().forEach { println(it) }
        println("--------------------------------------------------------------")
        contests.filter{ it.dilutedMargin != 0.0 }.sortedBy { it.dilutedMargin }.reversed().forEach { print(it.show()) }
        println("--------------------------------------------------------------")
    }

}