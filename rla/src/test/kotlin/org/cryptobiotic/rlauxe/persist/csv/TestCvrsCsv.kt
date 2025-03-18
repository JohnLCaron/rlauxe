package org.cryptobiotic.rlauxe.persist.csv

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.persist.json.readCvrsJsonFile
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCvrsCsv {

    @Test
    fun testRoundtrip() {
        val filenameIn = "/home/stormy/temp/persist/testCvrs/TestCvrs.json"
        val filenameOut = "/home/stormy/temp/persist/testCvrs/TestCvrs.csv"

        val cvrsResult = readCvrsJsonFile(filenameIn)
        val cvrsFromJ = if (cvrsResult is Ok) cvrsResult.unwrap() else {
            println(cvrsResult)
            throw RuntimeException("readCvrsJsonFile failed")
        }

        writeCvrsCsvFile(cvrsFromJ, filenameOut)

        val roundtrip = readCvrsCsvFile(filenameOut)
        assertEquals(cvrsFromJ, roundtrip)
    }

    @Test
    fun testRoundtripIrv() {
        val filenameIn = "/home/stormy/temp/persist/testCvrs/runClcaRaire.json"
        val filenameOut = "/home/stormy/temp/persist/testCvrs/runClcaRaire.csv"

        val cvrsResult = readCvrsJsonFile(filenameIn)
        val cvrsFromJ = if (cvrsResult is Ok) cvrsResult.unwrap() else {
            println(cvrsResult)
            throw RuntimeException("readCvrsJsonFile failed")
        }

        writeCvrsCsvFile(cvrsFromJ, filenameOut)

        val roundtrip = readCvrsCsvFile(filenameOut)
        assertEquals(cvrsFromJ, roundtrip)
    }

    @Test
    fun testRoundtripBig() {
        val filenameIn = "/home/stormy/temp/persist/testCvrs/runBoulder23.json"
        val filenameOut = "/home/stormy/temp/persist/testCvrs/runBoulder23.csv"

        val cvrsResult = readCvrsJsonFile(filenameIn)
        val cvrsFromJ = if (cvrsResult is Ok) cvrsResult.unwrap() else {
            println(cvrsResult)
            throw RuntimeException("readCvrsJsonFile failed")
        }

        writeCvrsCsvFile(cvrsFromJ, filenameOut)

        val roundtrip = readCvrsCsvFile(filenameOut)
        assertEquals(cvrsFromJ, roundtrip)
    }

    @Test
    fun testRoundtripBigger() {
        val filenameIn = "/home/stormy/temp/persist/testCvrs/runBoulder24.json"
        val filenameOut = "/home/stormy/temp/persist/testCvrs/runBoulder24.csv"

        val cvrsResult = readCvrsJsonFile(filenameIn)
        val cvrsFromJ = if (cvrsResult is Ok) cvrsResult.unwrap() else {
            println(cvrsResult)
            throw RuntimeException("readCvrsJsonFile failed")
        }

        writeCvrsCsvFile(cvrsFromJ, filenameOut)

        val roundtrip = readCvrsCsvFile(filenameOut)
        assertEquals(cvrsFromJ, roundtrip)
    }
}