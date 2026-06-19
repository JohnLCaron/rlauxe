package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class TestReadAuditCenterFiles {
    val input: ColoradoInput = Colorado2020General()

    @Test
    fun problem() {
        // heres what CountyContestBuilder uses
        val mcontest = input.mergedContestMap["Presidential Electors"]!!
        // here we git it from input; probably the same
        // no transform of the choices cause they supposed to match hahaha
        val candidateNames: Map<String, Int> = mcontest.choices.mapIndexed { idx, choice -> Pair(choice, idx) }.toMap()
        candidateNames.forEach { println("${trunc(it.key, 40)} -> ${it.value}") }
        println()

        // here we read it from the file
        val contests: Map<String, ContestTabAllCounties> = readCountyTabulateCsv(input.tabulateCountyFile)

        val pres = contests["Presidential Electors"]!!
        val ctacChoices = pres.choices.map { Pair(it.key, it.value.totalVotes) }.sortedBy { it.first }.toMap()
        println(pres.contestName)

        val canonical: CanonicalContest = readGeneralCanonicalList(input.generalCanonicalFile).find { it.contestName == "Presidential Electors"}!!
        val canonVotes = canonical.choices.sorted()
        canonVotes.forEach {
            val match = ctacChoices[it] != null
            val match2 = candidateNames[it]
            println("${trunc(it, 40)} -> $match $match2")
        }
        println()

        ctacChoices.forEach {
            val match = canonical.matchCanonicalCandidate(it.key)
            val match2 = candidateNames[it.key]?.toString() ?: "HEYA STUPID"
            println("${trunc(it.key, 40)}: ${it.value} -> $match2 : $match")
        }
        println()
    }

    // data class CanonicalContest(
    //    val contestName: String,
    //    val choices: List<String>
    //) {
    //    val counties =  mutableSetOf<String>()
    //  }
    @Test
    fun readGeneralCanonicalList() {
        val canonical = readGeneralCanonicalList(input.generalCanonicalFile)
        println("read ${canonical.size} contests from ${input.generalCanonicalFile} (${canonical.sumOf { it.choices.size }} choices)")
        canonical.sortedBy { it.contestName }.forEach { println("  $it") }

        val sizedist = mutableMapOf<Int, Int>() // size, count
        canonical.forEach {
            val count = sizedist.getOrDefault(it.counties.size, 0)
            sizedist[it.counties.size] = count+1
        }
        println("There are ${canonical.size} contests")
        println("There are ${input.canonicalContests().size} canonicalContests")

        println("\nMulticounty contests")
        sizedist.toSortedMap().forEach { println("  ${nfn(it.value, 3)} contests are in ${it.key} counties") }
    }

    //data class CorlaContestRoundCsv(
    //    val contestName: String,
    //    val auditReason: AuditReason,
    //    val nwinners: Int,
    //    val ballotCardCount: Int,
    //    val contestBallotCardCount: Int,
    //    val winners: String,
    //    val minMargin: Int,
    //    val riskLimit: Double,
    //    val gamma: Double,
    //    val optimisticSamplesToAudit: Int,
    //    val estimatedSamplesToAudit: Int,
    //)
    @Test
    fun readColoradoContestRoundFile() {
        // no name cleanup
        val contests = readColoradoContestRoundCsv(input.contestRoundFile)
        // val contests = readColoradoContestRoundCsv(input.contestRoundFile) { contestNameCleanup(it) }
        println("read ${contests.size} contests from ${input.contestRoundFile}")

        println("\n${trunc("contest", -50)}   Npop,   Nc,   needSamples, auditReason")
        contests.values.forEach {
            print("${trunc("${it.contestName}", -50)} ")
            print("${nfn(it.ballotCardCount, 7)}, ${
                nfn(
                    it.contestBallotCardCount,
                    7
                )
            },  ${nfn(it.optimisticSamplesToAudit, 7)},")
            println(" ${it.auditReason}")
        }
    }

    @Test
    fun readCountyTabulateFile() {
        // no name cleanup
        val contests:Map<String, ContestTabAllCounties> = readCountyTabulateCsv(input.tabulateCountyFile)
        // val contests = readCountyTabulateCsv(input.tabulateCountyFile, { contestNameCleanup(it)}, { candidateNameCleanup(it) })

        println("read ${contests.size} contests from ${input.tabulateCountyFile} (${contests.values.sumOf { it.choices.size }} choices)")

        println("totalVotesAllCounties")
        contests.values.forEach { contest ->
            val counties = contest.counties()
            val sumCounties = counties.sumOf { contest.countyVotes(it) }
            Assertions.assertEquals(contest.totalVotesAllCounties, sumCounties)
            println(" ${nfn(contest.totalVotesAllCounties, 7)}, ${contest.contestName}")
        }

        println("\nconvertToCountyContestTabs")
        val cct = convertToCountyTabs(contests.values.toList())
        cct.forEach {
            println(it)
        }
    }

    @Test
    fun readContestComparison() {
        val (contestMvrs, countyMvrs, countyStyles) = readContestComparisonCsv(input.mvrComparisonFile)
        // val (contestMvrs, countyMvrs, countyStyles) = readContestComparisonCsv(input.mvrComparisonFile) { contestNameCleanup(it) }
        println("read ${countyStyles.size} counties from ${input.mvrComparisonFile}; totalStyles=${countyStyles.sumOf { it.styles.size }} totalCards=${ countyStyles.sumOf{it.cardCount} }")

        println("Styles by County")
        countyStyles.forEach {
            println(it.show())
        }

        var countMvrs = 0
        println("\nNmvrs by County")
        println("\ncounty,     county nmvrs")
        countyMvrs.sortedBy { it.countyName }.forEach {
            println("${trunc(it.countyName, -11)}, ${nfn(it.countMvr, 5)}")
            countMvrs += it.countMvr
        }
        println("total mvrs = $countMvrs")

        println("\nNmvrs by Contest")
        println("\n${trunc("contest", -51)}   county nmvrs, statewide nmvrs")
        contestMvrs.sortedBy { it.contestName }.forEach {
            println("${trunc(it.contestName, -60)} ${nfn(it.countMvr, 5)}, ${nfn(it.countStatewide, 5)}")
        }
    }

    // data class MergedContestInfo(
    //    // canonical
    //    val contestName: String,
    //    val choices: List<String>,
    //    val counties: Set<String>,
    //
    //    // contestRound
    //    val auditReason: AuditReason,
    //    val npop:Int,
    //    val nc:Int,
    //    val voteForN: Int,
    //    val nsamples: Int,
    //    val marginInVotes: Int,
    //
    //    // mvr file
    //    val countyMvrs: Int,
    //    val statewideMvrs: Int,
    //)
    //
    //data class MergedCountyInfo(
    //    val countyName: String,
    //    val countyMvrs: Int,
    //    val Npop: Int,
    //)
    //
    //data class MergedInfo(
    //    val mergedContestInfo: List<MergedContestInfo>,
    //    val mergedCountyInfo: List<MergedCountyInfo>,
    //    val statewideContests: List<CorlaContestRoundCsv>,
    //)
    @Test
    fun showMergeContestInfo() {
        val (mergedContestInfo, mergedCountyInfo, statewideContests) = mergeContestInfo(Colorado2024General())

        println("\nMerged Contest Info")
        println("\n${trunc("contest", -50)}    Npop,      Nc, voteMargin, countyMvrs, stateMvrs, Ncounties, auditReason")
        mergedContestInfo.forEach {
            print("${trunc("${it.contestName}", -50)} ")
            print("${nfn(it.npop, 7)}, ${nfn(it.nc, 7)}, ${nfn(it.marginInVotes, 7)},")
            print("   ${nfn(it.countyMvrs, 7)},  ${nfn(it.statewideMvrs, 7)}, ")
            println("         ${nfn(it.counties.size, 3)},   ${it.auditReason}")
        }
        println()

        println("\nStrata Info")
        println("\ncounty      nmvrs,  npop")
        mergedCountyInfo.sortedBy { it.strataName }.forEach {
            println("${sfn(it.strataName, -10)}  ${nfn(it.nmvrs, 5)}, ${nfn(it.ncards, 7)}")
        }
        println()

        println("\n${trunc("statewideContests", -50)}     Npop, Nc,   needSamples, auditReason")
        statewideContests.forEach {
            print("${trunc("${it.contestName}", -50)} ")
            print("${nfn(it.ballotCardCount, 7)}, ${
                nfn(
                    it.contestBallotCardCount,
                    7
                )
            },  ${nfn(it.optimisticSamplesToAudit, 7)},")
            println(" ${it.auditReason}")
        }
    }

}