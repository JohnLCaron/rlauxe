package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.estimate.tabulateVotesFromCvrs
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.cvr.CorlaCvrs
import org.cryptobiotic.rlauxe.cvr.RedactionBoulder
import org.cryptobiotic.rlauxe.cvr.SchemaContestInfo
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrsFromFile
import org.cryptobiotic.rlauxe.cvr.readCorlaCvrsFromResource
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBoulderCvrs {

    @Test
    fun testBoulder24() {
        val cvrFilename = "/resources/data/cases/boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip"
        val sovoFilename = "/resources/data/cases/boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv"

        val corlaCvrs = readCorlaCvrsFromResource(cvrFilename, redaction = RedactionBoulder())
        val sovo: BoulderStatementOfVotes = readBoulderSOVfromResourcePath(sovoFilename, "Boulder2024")
        testCompareSovoAndCvrs(corlaCvrs, sovo)

        testParseBoulderCvrs(corlaCvrs, 396_012)
    }

    @Test
    fun testBoulder25() {
        val cvrFilename = "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv"
        val sovoFilename = "src/test/data/Boulder2025/2025C-Boulder-County-Official-Statement-of-Votes.csv"

        val corlaCvrs = readCorlaCvrsFromFile(cvrFilename, redaction = RedactionBoulder())
        // parseBoulderCvrs(corlaCvrs, 121538) // should be 121584 ?? from 2025-Post-Election-Data-Report.pdf:
        // Estimates for these items are based off our award-winning Ballot
        // Box Tracking System which provides estimates of the number of
        // ballots based on weight of returned ballots.

        val sovo: BoulderStatementOfVotes = readBoulderStatementOfVotes(sovoFilename, "Boulder2025")
        testCompareSovoAndCvrs(corlaCvrs, sovo)
    }

    @Test
    fun testBoulder26() {
        val cvrFilename = "/resources/data/cases/boulder26p/2026P-Redacted-CVR-Public.csv"
        val sovoFilename = "/resources/data/cases/boulder26p/2026P-Boulder-County-Official-Statement-of-Votes.csv"

        val corlaCvrs = readCorlaCvrsFromResource(cvrFilename)
        testParseBoulderCvrs(corlaCvrs, 100_423)

        val sovo: BoulderStatementOfVotes = readBoulderSOVfromResourcePath(sovoFilename, "Boulder2026")
        testCompareSovoAndCvrs(corlaCvrs, sovo)
    }

    fun testCompareSovoAndCvrs(corlaCvrs: CorlaCvrs, sovo: BoulderStatementOfVotes) {

        println("\nCVR schema contests ${corlaCvrs.schema.contests.size}")
        corlaCvrs.schema.contests.sortedBy {  it.contestName }.forEach { println("  ${nfn(it.contestIdx,2)}: ${it.contestName}") }
        println()
        val voteForNs = corlaCvrs.schema.contests.map { Pair(it.contestName, it.voteForN) }

        println("SOVO contests ${sovo.contests.size}")
        println("  ${SovoContestVotes.header}, calcNcast")
        var miss = 0
        sovo.contests.sortedBy {  it.contestTitle }.forEach {
            val missing = !hasCvrContest(it.contestTitle, corlaCvrs.schema.contests)
            if (missing) {
                miss++
                assertEquals(0, it.totalVotes) // "There are no candidates for this office"
            }
            val vnsPair = voteForNs.find { vns -> vns.first.contains(it.contestTitle) }
            val votesForN = vnsPair?.second ?: 1
            println( "  ${it}       ${it.calcNcast(votesForN)},    ${if (!missing) "" else "MISS"}" )
        }
        println("miss = $miss\n")

        val election = CreateBoulderElection("boulder2026", AuditType.ONEAUDIT, corlaCvrs, sovo, hasStyle = true)
        println("Election contests ${election.contestsUA.size}")
        println("  ${BoulderContestBuilder.header}")
        election.contestBuilders.values.sortedBy {  it.info.name }.forEach { println("  $it") }

        assertEquals(corlaCvrs.schema.contests.size,sovo.contests.size - miss)
        assertEquals(corlaCvrs.schema.contests.size,election.contestsUA.size)
    }

    fun hasCvrContest(sovoContestName: String, cvrContests: List<SchemaContestInfo>): Boolean {
        return cvrContests.find { it.contestName.contains(sovoContestName) } != null
    }

    fun testParseBoulderCvrs(corlaCvrs: CorlaCvrs, expectedCvrs: Int) {
        val exportCvrs: List<Cvr> = corlaCvrs.cvrs.map { it.convertToCvr() }
        println("Total cvrs=${exportCvrs.size}")

        val votes = tabulateVotesFromCvrs(exportCvrs.iterator()).toSortedMap()
        votes.forEach { (contestId, votes) ->
            println("  ${contestId}: ${votes.toSortedMap()}")
        }

        println("\nContests")
        corlaCvrs.schema.contests.sortedBy {  it.contestName }.forEach { println("  ${it.contestIdx}: ${it.contestName}") }

        println("\nBallot Types")
        corlaCvrs.cardStyles().forEach { println("  ${it}") }
        val countBallotTypeCards = corlaCvrs.cardStyles().sumOf { it.countCards }
        println("countBallotTypeCards=${countBallotTypeCards}")

        println("\nRedacted Groups")
        corlaCvrs.redactedGroups().forEach { println("  ${it}") }
        println("Total redacted votes=${corlaCvrs.redactedGroups().sumOf { it.totalVotes() }}")

        val redactedNcards = corlaCvrs.redactedGroups().sumOf { it.ncards() }
        println("Total redacted cards=${redactedNcards}")
        println("Total cvrs + redacted cards=${exportCvrs.size + redactedNcards}")

        assertEquals(expectedCvrs, exportCvrs.size + redactedNcards, )
        assertEquals(exportCvrs.size, countBallotTypeCards, )
    }

}
