package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TestBoulderElection {
    @Test
    fun testRunVerifyBoulder24oa() {
        val topdir = "$cases/boulder/boulder2024/oa"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = false)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testRunVerifyBoulder24clca() {
        val topdir = "$cases/boulder/boulder2024/clca"
        val results = RunVerifyContests.runVerifyContests(topdir, null, show = false)
        println()
        print(results)
        if (results.hasErrors) fail()
    }

    @Test
    fun testParseContestName() {
        assertEquals(Pair("Frankenfurter", 11), parseContestNameAndVoteFor("Frankenfurter (Vote For=11)"))
        assertEquals(Pair("Frankenfurter", 11), parseContestNameAndVoteFor("Frankenfurter(Vote For=11)"))
        assertEquals(Pair("Frankenfurter", 11), parseContestNameAndVoteFor("Frankenfurter(Vote For=11"))
        assertEquals(Pair("Frankenfurter", 11), parseContestNameAndVoteFor("Frankenfurter(Vote For=11) but wait theres more"))
        assertEquals(Pair("Heather (Bob) Morrisson", 11), parseContestNameAndVoteFor("Heather (Bob) Morrisson (Vote For=11)"))
        assertEquals(Pair("Stereoscopic", 1), parseContestNameAndVoteFor("Stereoscopic    "))
    }

    @Test
    fun testParseIrvContestName() {
        assertEquals(Pair("Frankenfurter (Vote For=11)", 1), parseIrvContestName("Frankenfurter (Vote For=11)"))
        assertEquals(
            Pair("Frankenfurter", 11),
            parseIrvContestName("Frankenfurter (Number of positions=11, Number of ranks=4)")
        )
        assertEquals(
            Pair("Frankenfurter", 11),
            parseIrvContestName("Frankenfurter(Number of positions=11, but wait theres more")
        )
        assertEquals(
            Pair("Heather (Bob) Morrisson", 11),
            parseIrvContestName("Heather (Bob) Morrisson (Number of positions=11,)")
        )
        assertEquals(Pair("Number of positions=", 1), parseIrvContestName("Number of positions=    "))
    }
}
