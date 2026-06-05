package org.cryptobiotic.rlauxe.belgium

import org.cryptobiotic.rlauxe.persist.readCanonicalPartyTxtFile
import org.cryptobiotic.rlauxe.persist.readCoalitionTxtFile
import org.cryptobiotic.rlauxe.persist.readLimitsTxtFile
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.test.Test
import kotlin.test.assertEquals

class TestReadBelgiumElection {
    val partyFilename = "src/test/data/belgium2024/parties.txt"
    val limitsFilename = "src/test/data/belgium2024/sampleLimits.txt"
    val canonicalPartiesFilename = "src/test/data/belgium2024/canonicalParties.txt"
    val royaumeFilename =  "src/test/data/belgium2024/2024_chambre-des-représentants_Royaume.csv"

    @Test
    fun testReadPartyTxtFile() {
        val filename = partyFilename
        val result = readPartyTxtFile(filename)
        result.forEach { (key, value) -> println("'$key': $value") }
        assertEquals(30, result.size)
    }

    @Test
    fun testReadRoyaumeTxtFile() {
        val filename = royaumeFilename
        val result = readRoyaumeTxtFile(filename).sortedByDescending { it.seats }
        result.forEach { println(it) }
        assertEquals(25, result.size)
    }

    @Test
    fun comparePartiesAndRoyaume() {
        val parties = readPartyTxtFile(partyFilename)
        val roys = readRoyaumeTxtFile(royaumeFilename).sortedBy { it.name }

        println("RoyaumeTxtFile")
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