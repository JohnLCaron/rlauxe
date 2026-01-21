package org.cryptobiotic.rlauxe.persist.csv

import kotlin.test.Test
import kotlin.test.assertEquals

import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.raire.HashableIntArray
import org.cryptobiotic.rlauxe.raire.RaireContestTestData
import org.cryptobiotic.rlauxe.raire.VoteConsolidator
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.createZipFile
import org.cryptobiotic.rlauxe.util.makeContestsWithUndervotesAndPhantoms
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.io.path.createTempFile
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestContestTabulationCsv {

    @Test
    fun testRegVotes() {
        val candVotes = mutableListOf<Map<Int, Int>>()
        candVotes.add(mapOf(0 to 200, 1 to 123, 2 to 17))
        candVotes.add(mapOf(0 to 71, 1 to 123, 2 to 0, 3 to 77, 4 to 99))
        candVotes.add(mapOf(0 to 102, 1 to 111))

        val undervotes = listOf(15, 123, 3)
        val phantoms = listOf(2, 7, 0)
        val voteForNs = listOf(1, 2, 1)
        val (contests, cvrs) = makeContestsWithUndervotesAndPhantoms(candVotes,
            undervotes=undervotes, phantoms=phantoms, voteForNs = voteForNs)

        val infos = contests.associate { Pair(it.id, it.info) }
        val tabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)
        val target = tabs.values.toList()

        val csvFile = "$testdataDir/tests/scratch/contestTabulationCsvFile.csv"
        val csv = writeContestTabulationCsvFile(target, csvFile)
        print(ContestTabulationHeader)
        println(csv)

        val roundtrip = readContestTabulationCsvFile(csvFile)

        assertEquals(target, roundtrip)
    }

    @Test
    fun testIrvVotes() {
        val N = 20000
        val minMargin = .05
        val undervotePct = 0.0
        val phantomPct = 0.0
        val testContest = RaireContestTestData(
            0,
            ncands = 4,
            ncards = N,
            minMargin = minMargin,
            undervotePct = undervotePct,
            phantomPct = phantomPct,
            excessVotes = 0,
        )
        val cvrs = testContest.makeCvrs()

        val infos = mapOf( 0 to testContest.info)
        val tabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)
        val target = tabs.values.toList()

        val csvFile = "$testdataDir/tests/scratch/contestTabulationCsvIrv.csv"
        val csv = writeContestTabulationCsvFile(target, csvFile)
        print(ContestTabulationHeader)
        println(csv)

        val roundtrip = readContestTabulationCsvFile(csvFile)
        val rountripVotes = roundtrip.first().irvVotes.votes
        target.first().irvVotes.votes.forEach { (key, value) ->
            val rountripVote = rountripVotes[key]
            println("$rountripVote ? $value")
            assertEquals(rountripVote, value)
        }
        assertEquals(target.first().irvVotes, roundtrip.first().irvVotes)
        assertEquals(target, roundtrip)
    }

}