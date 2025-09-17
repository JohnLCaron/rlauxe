package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.tabulateCvrs
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import kotlin.test.Test
import kotlin.text.get

class TestBoulderElectionOneAudit {

    @Test
    fun testSovoContests() {
        val sovo = readBoulderStatementOfVotes(
            "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv",
            "Boulder2024"
        )

        val filename = "src/test/data/Boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
        val export: DominionCvrExportCsv = readDominionCvrExportCsv(filename, "Boulder")

        val election = BoulderElectionOneAudit(export, sovo)
        val voteForN = election.oaContests.mapValues { it.value.info.voteForN }
        val allTab = tabulateCvrs(election.allCvrs.iterator(), voteForN).toSortedMap()

        election.oaContests.forEach { (contestId, oaContest) ->
            oaContest.checkCvrs(allTab[contestId]!!)
        }
        println()

        election.oaContests.forEach { (contestId, oaContest) ->
            oaContest.checkNcards(allTab[contestId]!!)
        }

        election.oaContests.forEach { (contestId, oaContest) ->
            oaContest.showSummary(allTab[contestId]!!)
        }
    }
}