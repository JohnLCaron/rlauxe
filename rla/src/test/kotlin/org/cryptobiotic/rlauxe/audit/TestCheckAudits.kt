package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.CvrExportAdapter
import org.cryptobiotic.rlauxe.persist.csv.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class TestCheckAudits {

    @Test
    fun testCheckContestsWithCvrs() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val auditDir = "$topDir/audit"
        val cvrExport = "$topDir/cvrExport.csv"

        val publisher = Publisher(auditDir)
        val contestsUA = readContestsJsonFile(publisher.contestsFile()).unwrap()
        val state = checkContestsWithCvrs(contestsUA, CvrExportAdapter(cvrExportCsvIterator(cvrExport)))
        println(state)
        Assertions.assertTrue(!state.contains("***"))
    }

    // TODO move to verifier
    @Test
    fun testCardReading() {
        val auditDir = "/home/stormy/rla/cases/sf2024oa/audit"
        val cardIter = readCardsCsvIterator("$auditDir/sortedCards.csv")

        val publisher = Publisher(auditDir)
        val contestsUA = readContestsJsonFile(publisher.contestsFile()).unwrap()
        val state = checkContestsWithCvrs(contestsUA, CvrIteratorAdapter(cardIter))
        println(state)
        Assertions.assertTrue(!state.contains("***"))

        val tabCvrs: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(CvrIteratorAdapter(cardIter)).toSortedMap()
        tabCvrs.forEach { (contest, cvrMap) -> println("contest $contest : ${cvrMap}")}
    }

}