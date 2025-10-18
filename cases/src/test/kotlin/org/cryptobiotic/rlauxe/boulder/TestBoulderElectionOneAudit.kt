package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.tabulateCvrs
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import kotlin.test.Test

class TestBoulderElectionOneAudit {

    @Test
    fun testSovoContests() {
        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            "Boulder2024"
        )

        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(filename, "Boulder")

        val election = BoulderElectionOA(export, sovo, isClca=true)
        val infos = election.oaContests.mapValues { it.value.info }
        val (cvrs, _) = election.allCvrs()
        val allTab = tabulateCvrs(cvrs.iterator(), infos).toSortedMap()

        election.oaContests.forEach { (contestId, oaContest) ->
            oaContest.checkCvrs(allTab[contestId]!!)
        }
        println()

        election.oaContests.forEach { (contestId, oaContest) ->
            oaContest.checkNcards(allTab[contestId]!!)
        }

        /* election.oaContests.forEach { (_, oaContest) ->
            oaContest.showSummary()
        } */
    }
}