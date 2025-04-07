package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.util.ZipReader
import kotlin.test.Test

class TestColoradoPrecinctLevelResults {

    @Test
    fun testRead() {
        val filename = "src/test/data/2024election/2024GeneralPrecinctLevelResults.zip"
        val reader = ZipReader(filename)
        val input = reader.inputStream("2024GeneralPrecinctLevelResults.csv")
        val precincts = readColoradoPrecinctLevelResults(input)
        println(precincts[0])
        println(precincts[42])
        println(precincts.last())

        println(precincts[0].summ())
        println(precincts[42].summ())
        println(precincts.last().summ())

        // the problem of turning into CVRS is we dont know the undervotes
        // could assume that all contests are on all ballots for each precinct

        val summaryFile = "src/test/data/2024election/summary.csv"
        val scontests = readColoradoElectionSummaryCsv(summaryFile)
        val scontestMap = scontests.associateBy {
            removeCruft(it.contestName)
        }

        /* make sure we match the contest
        var ok = true
        precincts.forEach {
            it.contestChoices.forEach { pcontest ->
                if (!scontestMap.contains(pcontest.key)) {
                    println("***missing contest ${pcontest.key}")
                    ok = false
                } else {
                    val scontest = scontestMap[pcontest.key]!!
                    // make sure precinct choices exist in scontest
                    pcontest.value.forEach { pcc ->
                        val foundit = scontest.candidates.find { it.choiceName == pcc.choice }
                        if (foundit == null) {
                            println("***missing choice '${pcc.choice}' for contest ${pcontest.key}")
                            ok = false
                            scontest.candidates.forEach { println("   '${it.choiceName}'") }
                            println()
                        }
                    }
                }

            }
        }

        println("done $ok")
         */
    }
}

fun removeCruft(name: String): String {
    if (!name.contains("(Vote For")) return name.trim()
    val tokens = name.split("(Vote For")
    require(tokens.size == 2) { "unexpected contest name $name" }
    val name = tokens[0].trim()
    return name
}