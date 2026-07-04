package org.cryptobiotic.rlauxe.ga

import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.auditcenter.munge
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.forEach
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestReadGaInputCsv {
    val topdir = "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted"

    @Test
    fun readGaCountyInputCsv() {
        readGaCountyInputCsv(topdir)
    }

    @Test
    fun testReadGaCountyInputCsvOrg() {
        val contests = mutableSetOf<String>()
        val candidates  = mutableSetOf<String>()

        var count = 0
        val manifests = "$topdir/manifests"
        Path(manifests).listDirectoryEntries().sorted().forEach { countyPath ->
            val countyName = countyPath.name
            // if (countyName !in listOf("BURKE", "CHATHAM", "FULTON")) {
                try {
                    val batches = readGaCountyInputCsvOrg(countyName)
                    batches.forEach {
                        it.candCount.keys.forEach { key ->
                            contests.add(key.contest)
                            candidates.add(key.candName)
                        }
                    }
                } catch (e: Exception) {
                    println("*** ${e.message}")
                }
            // }
            count++
        }
        println("  $count counties")
        println()
        println("Contests = $contests")
        println("Candidates")
        candidates.sorted().forEach { println("   $it") }
    }

    fun readGaCountyInputCsvOrg(countyName: String): List<CountyBatch> {
        println("Read County $countyName")
        val manifests = "$topdir/manifests/$countyName"
        val manifestData = Path(manifests).listDirectoryEntries().first()
        val batches = readCountyManifest(manifestData.toString())

        val candidate_totals = "$topdir/candidate_totals/$countyName"
        val candData = Path(candidate_totals).listDirectoryEntries().first()
        readCandidateTotals(candData.toString(), batches)

        //batches.forEach {
        //    println(it)
        //}
        return batches
    }

    @Test
    fun readBallotImageAudit() {
        val filename =
            "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/downloads/contest_results_comparison.csv"
        val (contests, counties, contestsia) = makeContestsFromImageAuditFile(filename, showLevel = 0)

        println("Contests = ${contests.size}")
        var singleCounty = 0
        var multiCounty = 0
        var oneCandidate = 0
        var statewide = 0
        contests.forEach { contest ->
            if (contest.info.candidateNames.size == 1) oneCandidate++
            val contested = (contest.info.candidateNames.size > 1)
            val counties = contest.info.metadata["Counties"] ?: ""
            val ncounties = counties.split(" ").size
            if (contested && ncounties == 1) singleCounty++
            if (contested && ncounties > 1 && ncounties <= 50) multiCounty++
            if (contested && ncounties > 50) statewide++
        }
        println("oneCandidate = $oneCandidate")
        println("contested = ${contests.size - oneCandidate}")
        println("  singleCounty = $singleCounty")
        println("  multiCounty = ${multiCounty}")
        println("  statewide = ${statewide}")
        println()

        var dups = 0
        val unique = mutableMapOf<String, Contest>()
        contests.forEach {
            if (unique.contains(it.name)) {
                val org = unique[it.name]!!
                println("first ${org.name} cands=${org.info.candidateNames.keys}")
                println("  dup ${it.name} cands=${org.info.candidateNames.keys}")
                println()
                dups++
            }
            else unique[it.name] = it
        }
        println("dups = $dups")

        val contestMap = contests.associateBy { it.name }

        counties.forEach { countyia ->
            countyia.contests.forEach { (adjname, contestia) ->
                val contest = contestMap[adjname]
                if (contest != null) {
                    val info = contest.info
                    // println("${info.name} has ${contestia.candCount} votes")
                    // we want the contest subtotals for this county
                    contestia.candCount.map { (cand, votes) ->
                        if (info.candidateNames[cand] == null) {
                            println("Cant find '$cand' in ${info.candidateNames}")
                        }
                    }
                } else {
                    println("Cant find '${adjname}'")
                }
            }
        }

    }
}