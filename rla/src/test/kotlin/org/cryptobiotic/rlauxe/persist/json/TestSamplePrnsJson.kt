package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestSamplePrnsJson {
    val filename = "/home/stormy/temp/tests/scratch/TestSampleIndices.json"

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

        writeSamplePrnsJsonFile(target, filename)
        val result = readSamplePrnsJsonFile(filename)
        assertTrue(result is Ok)
        val roundtripJson = result.unwrap()
        val roundtrip = roundtripJson
        assertTrue(roundtrip.equals(target))
        assertEquals(roundtrip, target)
    }
}