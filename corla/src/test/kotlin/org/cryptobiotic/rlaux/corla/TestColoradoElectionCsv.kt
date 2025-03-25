package org.cryptobiotic.rlaux.corla

import kotlin.test.Test

class TestColoradoElectionCsv {

    @Test
    fun testColoradoElectionSummary() {
        val filename = "src/test/data/2024election/summary.csv"
        val contests = readColoradoElectionSummaryCsv(filename)
        contests.forEach { it.complete() }
        println("--------------------------------------------------------------")
        println("contest sort by reversed underVote percentage\n")
        contests.sortedBy { it.underPct }.reversed().forEach { println(it) }
        println("--------------------------------------------------------------")
        println("contest sort by dilutedMargin percentage\n")
        contests.filter{ it.dilutedMargin != 0.0 }.sortedBy { it.dilutedMargin }.forEach { print(it.show()) }
        println("--------------------------------------------------------------")
    }

    @Test
    fun testColoradoContestRoundCsv() {
        val filename = "src/test/data/2024audit/round1/contest.csv"
        val contests = readColoradoContestRoundCsv(filename)
        contests.forEach { it.showEstimation() }
    }

}