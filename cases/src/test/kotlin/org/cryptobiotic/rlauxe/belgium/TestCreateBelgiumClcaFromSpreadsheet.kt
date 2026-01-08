package org.cryptobiotic.rlauxe.belgium


import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.audit.writeSortedCardsExternalSort
import org.cryptobiotic.rlauxe.cli.RunVerifyContests
import org.cryptobiotic.rlauxe.dhondt.ProtoContest
import org.cryptobiotic.rlauxe.dhondt.DhondtCandidate
import org.cryptobiotic.rlauxe.dhondt.makeProtoContest
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import kotlin.test.Test
import kotlin.test.fail

// deprecated - use TestCreateBelgium

class TestCreateBelgiumClcaFromSpreadsheet {
    val Afile = "$testdataDir/cases/belgium/2024/CK_K_2024.xlsx"
    val Bfile = "$testdataDir/cases/belgium/2024/CK_CEListes_2024.xlsx"
    val topdir = "$testdataDir/cases/belgium/2024"

    @Test
    fun createBelgium2024() {
        createBelgium2024("92094")
    }

    // @Test
    fun testAllContests() {
        val contests = listOf("92094", "81001", "71022", "62063", "53053", "31005", "25072", "24062", "21004", "11002", )
        val errors = listOf("44021",  )
        contests.forEach { createBelgium2024(it) }
    }

    fun createBelgium2024(sheetName: String) {
        println("======================================================")
        println("Contest $sheetName")
        val contest = BelgiumElectionFromSpreadsheet(Afile, Bfile, sheetName)
        val infoA = contest.readA()
        println(infoA.show())
        val infoB = contest.readB()
        println(infoB.show())

        // use infoA parties, because they are complete
        val dhondtParties = infoA.parties.map { DhondtCandidate(it.name, it.num, it.total) }
        val dcontest: ProtoContest = makeProtoContest(infoB.electionName, 1, dhondtParties, infoB.winners.size, 0,.05)
        println("Calculated Winners")
        dcontest.winners.sortedBy { it.winningSeat }.forEach {
            println("  ${it}")
        }
        println()

        val auditdir = "$topdir/audit"
        createBelgiumClca(auditdir, dcontest.createContest())

        val publisher = Publisher(auditdir)
        val config = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
        writeSortedCardsExternalSort(topdir, publisher, config.seed)

        val results = RunVerifyContests.runVerifyContests(auditdir, null, show = true)
        println()
        print(results)
        if (results.hasErrors) fail()
    }
}