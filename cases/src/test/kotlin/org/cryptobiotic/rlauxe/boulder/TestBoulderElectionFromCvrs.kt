package org.cryptobiotic.rlauxe.boulder

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestBoulderElectionFromCvrs {

    @Test
    fun testParseContestName() {
        assertEquals(Pair("Frankenfurter", 11), parseContestName("Frankenfurter (Vote For=11)"))
        assertEquals(Pair("Frankenfurter", 11), parseContestName("Frankenfurter(Vote For=11)"))
        assertEquals(Pair("Frankenfurter", 11), parseContestName("Frankenfurter(Vote For=11"))
        assertEquals(Pair("Frankenfurter", 11), parseContestName("Frankenfurter(Vote For=11) but wait theres more"))
        assertEquals(Pair("Heather (Bob) Morrisson", 11), parseContestName("Heather (Bob) Morrisson (Vote For=11)"))
        assertEquals(Pair("Stereoscopic", 1), parseContestName("Stereoscopic    "))
    }

    @Test
    fun testParseIrvContestName() {
        assertEquals(Pair("Frankenfurter (Vote For=11)", 1), parseIrvContestName("Frankenfurter (Vote For=11)"))
        assertEquals(Pair("Frankenfurter", 11), parseIrvContestName("Frankenfurter (Number of positions=11, Number of ranks=4)"))
        assertEquals(Pair("Frankenfurter", 11), parseIrvContestName("Frankenfurter(Number of positions=11, but wait theres more"))
        assertEquals(Pair("Heather (Bob) Morrisson", 11), parseIrvContestName("Heather (Bob) Morrisson (Number of positions=11,)"))
        assertEquals(Pair("Number of positions=", 1), parseIrvContestName("Number of positions=    "))
    }

    // looks like the 2024-Boulder-County-General-Redacted-Cast-Vote-Record.xlsx got saved with incorrect character encoding.
    // hand corrected "Claudia De la Cruz / Karina García"
    @Test
    fun createBoulder24() {
        createElectionFromDominionCvrs(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip",
            "/home/stormy/temp/cases/boulder24",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
        )
    }

    @Test
    fun createBoulder24recount() {
        createElectionFromDominionCvrs(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv",
            "/home/stormy/temp/cases/boulder24recount",
            "src/test/data/Boulder2024/2024G-Boulder-County-Amended-Statement-of-Votes.csv",
            minRecountMargin = 0.0,
            )
    }

    @Test
    fun createBoulder23() {
        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes.csv", "Boulder2023")
        val sovoRcv = readBoulderStatementOfVotes(
            "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes-RCV.csv", "Boulder2023Rcv")
        val combined = BoulderStatementOfVotes.combine(listOf(sovoRcv, sovo))

        createElectionFromDominionCvrs(
            "src/test/data/Boulder2023/Redacted-2023Coordinated-CVR.csv",
            "/home/stormy/temp/cases/boulder23",
            combined,
        )
    }

    @Test
    fun createBoulder23recount() {
        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2023/2023C-Boulder-County-Official-Statement-of-Votes-Recount.csv", "Boulder2023")
        createElectionFromDominionCvrs(
            "src/test/data/Boulder2023/Redacted-2023Coordinated-CVR.csv",
            "/home/stormy/temp/cases/boulder23recount",
            sovo,
            minRecountMargin = 0.0,
            )
    }
}