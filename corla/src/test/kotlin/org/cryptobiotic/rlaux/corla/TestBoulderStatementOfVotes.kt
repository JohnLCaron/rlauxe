package org.cryptobiotic.rlaux.corla

import kotlin.test.Test

// Not used, not sure its needed
// so far, only tested on "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/N19-manifest.csv"

class TestBoulderStatementOfVotes {

    @Test
    fun testBoulder2024() {
        val filename = "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv"
        val sovo = readBoulderStatementOfVotes(filename, "Boulder2024")
        println(sovo.show())
    }

    @Test
    fun testBoulder2023() {
        val filename = "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes.csv"
        val sovo = readBoulderStatementOfVotes(filename, "Boulder2023")
        println(sovo.show())
    }

    @Test
    fun testBoulder2023Rcv() {
        val filename = "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes-RCV.csv"
        val sovo = readBoulderStatementOfVotes(filename, "Boulder2023Rcv")
        println(sovo.show())
    }

}