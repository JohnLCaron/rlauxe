package org.cryptobiotic.rlauxe.belgium

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.readCanonicalPartyTxtFile
import org.cryptobiotic.rlauxe.persist.readLimitsTxtFile
import org.cryptobiotic.rlauxe.util.ErrorMessages
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBelgiumInput {
    val year = 2019
    val inputs = belgiumInput(year)

    val topdir = "$cases/belgium/belgium2024"
    val limitsFilename = "$topdir/sampleLimits.txt"
    val canonicalPartiesFilename = "$topdir/canonicalParties.txt"

    @Test
    fun testReadPartyTxtFile() {
        val result = readPartyTxtResource(inputs.parties)
        result.forEach { (key, value) -> println("'$key': $value") }
        assertEquals(30, result.size)
    }

    @Test
    fun testReadRoyaumeResource() {
        val result = readRoyaumeCsvResource(inputs.Royaume).sortedByDescending { it.votes }
        result.forEach { println(it) }
        // assertEquals(25, result.size)
    }

    @Test
    fun comparePartiesAndRoyaume() {
        val parties = readPartyTxtResource(inputs.parties)
        val roys = readRoyaumeCsvResource(inputs.Royaume).sortedBy { it.name }

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
        println("electionName = ${result.first}")
        result.second.forEach { println(it) }
    }

    @Test
    fun constructPartyMapping() {
        val partyNames = mutableMapOf<String, Int>() // name -> canonical id
        val roys = readRoyaumeCsvResource(inputs.Royaume).sortedByDescending { it.votes }
        println("Royalle party names sorted by votes (${roys.size}) ")
        // these will be the canonical contest ids
        roys.forEachIndexed { idx, roy ->
            partyNames[roy.name] = idx+1
            println(" ${nfn(idx+1, 2)}: ${sfn(roy.name, 20)} ${nfn(roy.votes, 7)}")
        }

        inputs.contests.forEach { (key, input) ->
            // val partyName = partyNames.getOrPut(key) { mutableSetOf() }
           // println("======================================================")
            // println("Election input: $input")
            val result: Result<BelgiumElectionJson, ErrorMessages> = readBelgiumJsonFromResourcePath(input)
            val belgiumElection = if (result .isOk) result.unwrap()
            else throw RuntimeException("Cannot read belgiumElection from ${input} err = $result")
            // println(belgiumElection)

            belgiumElection.ElectionLists.forEach {
                val party = partyNames[it.PartyLabel]
                if (party == null) partyNames[it.PartyLabel] = 0
            }
        }
        println()
        println("All party names from contests (${partyNames.size}) ")
        partyNames.toSortedMap().forEach { (key, value) ->
            val hasMatch = if (roys.find{it.name == key} != null) "*" else " "
            println(" $hasMatch ${sfn(key, 20)} : ${nfn(value, 2)}")
        }

        if (year == 2024) {
            // assign canonical ids to the variants
            partyNames["BLANCO"] = 13
            partyNames["Parti.j BLANCO"] = 13
            partyNames["PTB"] = 4
            partyNames["PTB-PVDA"] = 4
            partyNames["Voor U / Pour Vous"] = 15
        }
        if (year == 2019) {
            partyNames["PTB"] = 5
            partyNames["PTB*PVDA"] = 5
        }

        println()
        println("Party names with canonical id assignments")
        partyNames.toSortedMap().forEach { (key, value) ->
            val hasMatch = if (roys.find{it.name == key} != null) "*" else " "
            println(" $hasMatch ${sfn(key, 20)} : ${nfn(value, 2)}")
        }

        println("\nIf above ok, copy these lines to parties.txt:")
        println("Belgique $year – Chambre des représentants")
        partyNames.forEach { (key, value) ->
            println("$key, $value")
        }
    }
}