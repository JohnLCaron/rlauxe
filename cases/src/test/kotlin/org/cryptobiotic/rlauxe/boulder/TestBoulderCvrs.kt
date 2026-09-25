package org.cryptobiotic.rlauxe.boulder

import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corlacvr.CorlaRawCvrs
import org.cryptobiotic.rlauxe.corlacvr.RedactionBoulder
import org.cryptobiotic.rlauxe.corlacvr.SchemaContestInfo
import org.cryptobiotic.rlauxe.corlacvr.readCorlaCvrs
import org.cryptobiotic.rlauxe.estimate.tabulateCvrsWithVoteForNs
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBoulderCvrs {
    val contestNameWidth = 60

    @Test
    fun testBoulder23() {
        test(Boulder23Input(), 119_643)
    }

    @Test
    fun testBoulder24() {
        test(Boulder24Input(), 396_012)
    }

    @Test
    fun testBoulder25() {
        // Estimates for these items are based off our award-winning Ballot
        // Box Tracking System which provides estimates of the number of
        // ballots based on weight of returned ballots. (!)

        test(Boulder25Input(), 121_584)
    }

    @Test
    fun testBoulder26() {
        test(Boulder26PInput(), 100_423)
    }

    fun test(input: BoulderInput, sumManifest: Int) {
        val corlaRawCvrs: CorlaRawCvrs = readCorlaCvrs(input.cvrsSource, redaction = RedactionBoulder())
        println("\n${input.cvrsSource}\nCVR schema contests ${corlaRawCvrs.schema.contests.size}")

        val sovo = input.sovo()
        println("\n${input.sovoSource}\nSOVO contests ${sovo.contests.size}")

        val election = CreateBoulderElection(input.electionName, AuditType.ONEAUDIT, corlaRawCvrs, sovo, hasStyle = true,
            variantEnum = ElectionVariantEnum.Styles
        )
        val contestIds = election.contests.map { Pair(it.name, it.id) }
        println("\nCreateBoulderElection contests ${contestIds.size}")

        sovo.setIds(contestIds)
        compareSovoAndCvrs(corlaRawCvrs, sovo, election)
        println("--------------------------------------------------------------------------")
        testParseBoulderCvrs(corlaRawCvrs, contestIds, sumManifest)
    }

    fun compareSovoAndCvrs(corlaRawCvrs: CorlaRawCvrs, sovo: BoulderStatementOfVotes, election: CreateBoulderElection) {

        val voteForNs = corlaRawCvrs.schema.contests.map { Pair(it.contestName, it.voteForN) }

        println("Sovo contests")
        println("  ${SovoContestVotes.header}, calcNc")
        var miss = 0
        sovo.contests.sortedBy {  it.id }.forEach { sovoContest ->
            val missing = !hasCvrContest(sovoContest.contestTitle, corlaRawCvrs.schema.contests)
            if (missing) {
                miss++
                assertEquals(0, sovoContest.totalVotes) // "There are no candidates for this office"
            }
            val vnsPair = voteForNs.find { vns -> vns.first.contains(sovoContest.contestTitle) }
            val votesForN = vnsPair?.second ?: 1
            print("  ${sovoContest}       ${sovoContest.calcNcast(votesForN)}, " )
            println("${if (!missing) "" else "MISS"} ${if (sovoContest.checkTotalVotes(votesForN)) "" else "checkTotalVotes"}" )
        }
        println("contests in sov missing in cvr schema = $miss\n")

        var maxPhantoms = 0
        println("Election contests ${election.contestsUA.size}")
        println(" id, ${trunc("name", contestNameWidth)},       Nc,   ncvrs,    diff")
        election.contestsUA.forEach {
            print("${nfn(it.id,3)}, ${trunc(it.name, contestNameWidth)}, ")
            println(" ${nfn(it.Nc, 8)}, ${nfn(it.contest.Ncast(), 7)}, ${nfn(it.Nphantoms, 7)}")
            maxPhantoms = max(maxPhantoms, it.Nphantoms)
        }
        println("maxPhantoms = $maxPhantoms")

        assertEquals(corlaRawCvrs.schema.contests.size,sovo.contests.size - miss)
        assertEquals(corlaRawCvrs.schema.contests.size,election.contestsUA.size)
    }

    fun hasCvrContest(sovoContestName: String, cvrContests: List<SchemaContestInfo>): Boolean {
        return cvrContests.find { it.contestName.contains(sovoContestName) } != null
    }

    fun testParseBoulderCvrs(corlaRawCvrs: CorlaRawCvrs, contestIds:List<Pair<String, Int>>, sumManifest: Int) {
        val exportCvrs: List<Cvr> = corlaRawCvrs.cvrs.map { it.convertToCard().toCvr() }

        val votes = tabulateCvrsWithVoteForNs(exportCvrs.iterator(), corlaRawCvrs.schema.voteForNs).toSortedMap()
        votes.forEach { (contestId, tab) ->
            println("  ${contestId}: ${tab}")
        }

        println("\nCvr Contests")
        corlaRawCvrs.schema.contests.sortedBy {  it.contestName }.forEach { cvrContest ->
            val contestId = contestIds.find { cvrContest.contestName.contains(it.first) }
            if (contestId == null) println("cant find ${cvrContest.contestName}") else {
                val contestVotes = votes[contestId.second]
                println("  ${nfn(contestId.second, 2)}, ${trunc(contestId.first, contestNameWidth)}, ${contestVotes}")
            }
        }

        println("\nCvr Card Styles")
        corlaRawCvrs.cardStyleMap().values.forEach { println("  ${it}") }
        val countCardStyleCards = corlaRawCvrs.cardStyleMap().values.sumOf { it.countCards }
        println("countCardStyleCards=${countCardStyleCards}")
        println("Total cvrs=${exportCvrs.size}")

        println("\nRedacted Groups")
        corlaRawCvrs.redaction.groups().forEach { println("  ${it}") }
        val redactedNcards = corlaRawCvrs.redaction.groups().sumOf { it.ncards() }
        println("Total redacted cards=${redactedNcards}")
        println()
        println("Total cvrs + redacted cards=${exportCvrs.size + redactedNcards}")
        println("Expected Ncards (sumManifest) =${sumManifest} diff = ${sumManifest - exportCvrs.size - redactedNcards}")

        //assertEquals(expectedCvrs, exportCvrs.size + redactedNcards, )
        //assertEquals(exportCvrs.size, countBallotTypeCards, )
    }

}
