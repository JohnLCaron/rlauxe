package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestElectionInitJson {
    val filename = "/home/stormy/temp/persist/test/TestElectionInit.json"

    @Test
    fun testRoundtrip() {
        val testData = MultiContestTestData(11, 4, 50000)
        val contests: List<Contest> = testData.contests
        val target = ElectionInit(
            "TestElectionInitJson",
            contests.map { it.info }
        )
        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }

    @Test
    fun testRoundtripIO() {
        val testData = MultiContestTestData(11, 4, 50000)
        val contests: List<Contest> = testData.contests
        val target = ElectionInit(
            "TestElectionInitJson",
            contests.map { it.info }
        )
        val electionInitJson = target.publishJson()

        writeElectionInitJsonFile(electionInitJson, filename)
        val result = readElectionInitJsonFile(filename)
        assertTrue(result is Ok)
        val roundtripJson = result.unwrap()
        val roundtrip = roundtripJson.import()
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }
}