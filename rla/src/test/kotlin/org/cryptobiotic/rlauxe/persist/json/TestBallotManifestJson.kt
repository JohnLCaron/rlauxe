package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.sampling.MultiContestTestData
import org.cryptobiotic.rlauxe.workflow.BallotManifestUnderAudit
import org.cryptobiotic.rlauxe.workflow.BallotUnderAudit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestBallotManifestJson {
    val filename = "/home/stormy/temp/persist/test/TestBallotManifest.json"

    @Test
    fun testRoundtripWithStyle() {
        val testData = MultiContestTestData(11, 4, 1000)
        val (_, ballotManifest) = testData.makeCvrsAndBallotManifest(true)
        val ballotsUA = ballotManifest.ballots.map { BallotUnderAudit(it, Random.nextLong()) }
        val target = BallotManifestUnderAudit(ballotsUA, ballotManifest.ballotStyles)

        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testRoundtripWithNoStyle() {
        val testData = MultiContestTestData(11, 4, 1000)
        val (_, ballotManifest) = testData.makeCvrsAndBallotManifest(false)
        val ballotsUA = ballotManifest.ballots.map { BallotUnderAudit(it, Random.nextLong()) }
        val target = BallotManifestUnderAudit(ballotsUA, ballotManifest.ballotStyles)

        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testRoundtripIO() {
        val testData = MultiContestTestData(11, 4, 1000)
        val (_, ballotManifest) = testData.makeCvrsAndBallotManifest(true)
        val ballotsUA = ballotManifest.ballots.map { BallotUnderAudit(it, Random.nextLong()) }
        val target = BallotManifestUnderAudit(ballotsUA, ballotManifest.ballotStyles)

        writeBallotManifestJsonFile(target, filename)
        val result = readBallotManifestJsonFile(filename)
        assertTrue(result is Ok)
        val roundtripJson = result.unwrap()
        val roundtrip = roundtripJson
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }
}