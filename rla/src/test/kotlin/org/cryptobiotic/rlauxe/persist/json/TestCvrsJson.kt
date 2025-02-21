package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.estimate.MultiContestTestData
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestCvrsJson {
    val filename = "/home/stormy/temp/persist/test/TestCvrs.json"

    @Test
    fun testRoundtrip() {
        val testData = MultiContestTestData(11, 4, 1000)
        val cvrs = testData.makeCvrsFromContests()
        val target = cvrs.map { CvrUnderAudit(it, Random.nextLong())}

        val json = target.publishJson()
        val roundtrip = json.import()
        assertNotNull(roundtrip)
        val cvrZip = target.zip( roundtrip )
        cvrZip.forEach { (a, b) ->
            assertEquals(a, b)
        }
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testRoundtripIO() {
        val testData = MultiContestTestData(11, 4, 1000)
        val cvrs = testData.makeCvrsFromContests()
        val target = cvrs.map { CvrUnderAudit(it, Random.nextLong())}

        writeCvrsJsonFile(target, filename)
        val result = readCvrsJsonFile(filename)
        assertTrue(result is Ok)
        val roundtripJson = result.unwrap()
        val roundtrip = roundtripJson
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }
}