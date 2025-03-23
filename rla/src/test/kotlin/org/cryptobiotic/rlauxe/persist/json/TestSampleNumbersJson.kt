package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestSampleNumbersJson {
    val filename = "/home/stormy/temp/persist/test/TestSampleIndices.json"

    @Test
    fun testRoundtrip() {
        val target = List(1111) { Random.nextLong() }
        val json = target.publishJson()

        val roundtrip = json.import()
        assertNotNull(roundtrip)
        val dataZip = target.zip( roundtrip )
        dataZip.forEach { (a, b) ->
            assertEquals(a, b)
        }
        assertTrue(roundtrip.equals(target))
    }

    @Test
    fun testRoundtripIO() {
        val target = List(1111) { Random.nextLong() }

        writeSampleNumbersJsonFile(target, filename)
        val result = readSampleNumbersJsonFile(filename)
        assertTrue(result is Ok)
        val roundtripJson = result.unwrap()
        val roundtrip = roundtripJson
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }
}