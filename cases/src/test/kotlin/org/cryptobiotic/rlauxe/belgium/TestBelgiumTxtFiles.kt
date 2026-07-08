package org.cryptobiotic.rlauxe.belgium

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.readCanonicalPartyTxtFile
import org.cryptobiotic.rlauxe.persist.readCoalitionTxtFile
import org.cryptobiotic.rlauxe.persist.readLimitsTxtFile
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.test.Test
import kotlin.test.assertEquals

class TestReadBelgiumElection {
    val partyFilename = "$belgiumData/parties.txt"
    val royaumeFilename =  "$belgiumData/2024_chambre-des-représentants_Royaume.csv"

    val topdir = "$cases/belgium/belgium2024"
    val limitsFilename = "$topdir/sampleLimits.txt"
    val canonicalPartiesFilename = "$topdir/canonicalParties.txt"

    @Test
    fun testReadPartyTxtFile() {
        val result = readPartyTxtResource(partyFilename)
        result.forEach { (key, value) -> println("'$key': $value") }
        assertEquals(30, result.size)
    }

    @Test
    fun testReadRoyaumeTxtFile() {
        val result = readRoyaumeTxtResource(royaumeFilename).sortedByDescending { it.seats }
        result.forEach { println(it) }
        assertEquals(25, result.size)
    }

    @Test
    fun comparePartiesAndRoyaume() {
        val parties = readPartyTxtResource(partyFilename)
        val roys = readRoyaumeTxtResource(royaumeFilename).sortedBy { it.name }

        println("RoyaumeTxtFile") // can be used to test our reported results
        roys.forEachIndexed { idx, roy ->
            val partyId = parties[roy.name]
            println("${partyId}, ${sfn(roy.name, 20)}")
        }
        println()

        println("partyTxtFile")
        val royMap = roys.associateBy { it.name }
        parties.toList().forEachIndexed { idx, pp ->
            val roy = royMap[pp.first]
            println("${nfn(idx+1, 2)}  ${sfn(pp.first, 20)} == $roy  ==  ${pp.second}" )
        }
    }

    @Test
    fun testReadLimitsTxtFile() {
        val result = readLimitsTxtFile(limitsFilename)
        result.forEach { println(it) }
    }

    @Test
    fun readCanonicalPartyTxtFile() {
        val result = readCanonicalPartyTxtFile(canonicalPartiesFilename)
        result.forEach { println(it) }
    }
}