package org.cryptobiotic.rlaux.corla

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestElectionFromCvrs {

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

    @Test
    fun createBoulder24() {
        createElectionFromDominionCvrs(
            "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.csv",
            "/home/stormy/temp/persist/runBoulder24",
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
        )
    }

    @Test
    fun createBoulder24recount() {
        createElectionFromDominionCvrs(
            "/home/stormy/dev/github/rla/rlauxe/corla/src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv",
            "/home/stormy/temp/persist/runBoulder24recount",
            "src/test/data/Boulder2024/2024G-Boulder-County-Amended-Statement-of-Votes.csv",

            )
    }

    @Test
    fun createBoulder23() {
        createElectionFromDominionCvrs("src/test/data/Boulder2023/Boulder-2023-Coordinated-CVR-Redactions-removed.csv",
            "/home/stormy/temp/persist/runBoulder23")
    }
}