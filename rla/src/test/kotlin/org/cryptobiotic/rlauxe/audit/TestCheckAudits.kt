package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.AuditableCardToCvrAdapter
import org.cryptobiotic.rlauxe.persist.csv.CvrExportToCvrAdapter
import org.cryptobiotic.rlauxe.persist.csv.cvrExportCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.readContestsJsonFile
import org.junit.jupiter.api.Assertions

// TODO move to verifier
class TestCheckAudits {

    /* @Test
    fun testCheckContestsWithCvrs() {
        val topDir = "/home/stormy/rla/cases/sf2024"
        val auditDir = "$topDir/audit"
        val cvrExport = "$topDir/cvrExport.csv"

        val publisher = Publisher(auditDir)
        val contestsUA = readContestsJsonFile(publisher.contestsFile()).unwrap()
        // fun checkContestsWithCvrs(contestsUA: List<ContestUnderAudit>, cvrs: CloseableIterator<Cvr>,
        //                          ballotPools: List<BallotPool>?, show: Boolean = false) = buildString {
        val state =
            checkContestsWithCvrs(contestsUA, CvrExportToCvrAdapter(cvrExportCsvIterator(cvrExport)), cardPools = null)
        println(state)
        Assertions.assertTrue(!state.contains("***"))
    }

    // @Test
    fun testCardReading() {
        val auditDir = "/home/stormy/rla/cases/sf2024oa/audit"
        val cardIter = readCardsCsvIterator("$auditDir/sortedCards.csv")

        val publisher = Publisher(auditDir)
        val contestsUA = readContestsJsonFile(publisher.contestsFile()).unwrap()
        val state = checkContestsWithCvrs(contestsUA, AuditableCardToCvrAdapter(cardIter), cardPools = null)
        println(state)
        Assertions.assertTrue(!state.contains("***"))

        val tabCvrs: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(AuditableCardToCvrAdapter(cardIter)).toSortedMap()
        tabCvrs.forEach { (contest, cvrMap) -> println("contest $contest : ${cvrMap}")}
    } */

}