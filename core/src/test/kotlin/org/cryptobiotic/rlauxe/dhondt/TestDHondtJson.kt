package org.cryptobiotic.rlauxe.dhondt

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.json.import
import org.cryptobiotic.rlauxe.persist.json.publishJson
import kotlin.test.Test
import kotlin.test.assertTrue

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestDHondtJson {

    @Test
    fun testContestRoundtrip() {
        val parties = listOf(DhondtCandidate(1, 10000), DhondtCandidate(2, 6000), DhondtCandidate(3, 1500))
        val Nc = parties.sumOf { it.votes }
        val dcontest = makeDhondtContest("contest1", 1, parties, 8, Nc, 0, 0.01)
        val info = dcontest.info

        val json = dcontest.publishJson()
        val roundtrip = json.import(info)
        assertNotNull(roundtrip)
        assertEquals(dcontest, roundtrip)
        assertTrue(roundtrip.equals(dcontest))
    }

    @Test
    fun testContestUARoundtrip() {
        val parties = listOf(DhondtCandidate(1, 10000), DhondtCandidate(2, 6000), DhondtCandidate(3, 1500))
        val Nc = parties.sumOf { it.votes }

        val contest = makeDhondtContest("contest1", 1, parties, 8, Nc, 0, 0.01)
        val contestUA = ContestWithAssertions(contest, isClca=true).addAssertionsFromAssorters(contest.assorters)

        val json = contestUA.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertEquals(contestUA, roundtrip)
        assertTrue(roundtrip.equals(contestUA))
    }

    @Test
    fun testAssortorRoundtrip() {
        val parties = listOf(DhondtCandidate(1, 10000), DhondtCandidate(2, 6000), DhondtCandidate(3, 1500))
        val Nc = parties.sumOf { it.votes }

        val contest = makeDhondtContest("contest1", 1, parties, 8, Nc, 0, 0.01)
        val contestUA = ContestWithAssertions(contest, isClca=true).addAssertionsFromAssorters(contest.assorters)

        val target = contestUA.minPollingAssertion()!!.assorter

        val json = target.publishJson()
        val roundtrip = json.import(contest.info())
        assertNotNull(roundtrip)
        assertEquals(target, roundtrip)
        assertTrue(roundtrip.equals(target))
    }
}