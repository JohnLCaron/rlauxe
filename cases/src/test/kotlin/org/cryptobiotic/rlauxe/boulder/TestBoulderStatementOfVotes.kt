package org.cryptobiotic.rlauxe.boulder

import kotlin.test.Test

class TestBoulderStatementOfVotes {

    @Test
    fun testBoulder2024() {
        val filename = "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv"
        val sovo = readBoulderStatementOfVotes(filename, "Boulder2024")
        println(sovo.show())
    }

    @Test
    fun testBoulder2024recount() {
        val filename = "src/test/data/Boulder2024/2024G-Boulder-County-Amended-Statement-of-Votes.csv"
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