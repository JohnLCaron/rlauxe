package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.util.nfn
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/*
   1. generalCanonicalFile can be used for the canonical contestName, choiceNames, and counties
      use canonicalContests to add overrides

   2. the following files are sufficient for calculating the uniform audit risks:
        val tabulateCountyFile = "2024/general/tabulateCounty.csv"
        val contestRoundFile =   "2024/general/round1/contest.csv"
        val mvrComparisonFile =  "2024/general/round3/contestComparison.csv"

        we need to make possible adjustments to the contest names to get them to match.
        use TestContestNames to cross check names

 */

abstract class ColoradoInput(
    val generalCanonicalFile: String,
    val contestRoundFile: String,
    val tabulateCountyFile: String,
    val mvrComparisonFile: String
) {

    abstract fun canonicalContests(): Map<String, CanonicalContest>
    fun counties() = canonicalContests().values.map { it.counties }.flatten().toSet().toList().sorted()

    val roundContests: Map<String, CorlaContestRoundCsv> by lazy {
        readColoradoContestRoundCsv(contestRoundFile)
    } // 725

    val contestTabsByCounty: Map<String, ContestTabByCounty> by lazy {
        readCountyTabulateCsv(tabulateCountyFile)
    }

    val cardComparison: CardComparisonResults by lazy {
        readContestComparisonCsv(mvrComparisonFile)
    }

    val contestMvrs: List<ContestMvrs> by lazy { cardComparison.contestMvrs }
    val countyMvrs: List<CountyMvrs> by lazy { cardComparison.countyMvrs }
    val countyStyles: List<CountyStyles> by lazy { cardComparison.stylesByCounty }

    val mergedInfo: MergedInfo by lazy { mergeContestInfo(this) } // mergedContestInfo, strataInfo, statewideContests
    val mergedContestMap: Map<String, MergedContestInfo> by lazy { mergedInfo.mergedContestInfo.associateBy { it.contestName } }
    val strataMap: Map<String, StrataInfo> by lazy { mergedInfo.strataInfo.associateBy { it.strataName } }
    val statewideContests: List<CorlaContestRoundCsv> by lazy { mergedInfo.statewideContests }
    val countyContestMap: List<CountyContestTab> by lazy { convertToCountyContestTabs(contestTabsByCounty.values.toList()) }

    // use these on the cvrExport contest names, to match the canonical contest names contained in generalCanonicalFile
    abstract fun contestNameCleanup(name: String): String
    abstract fun candidateNameCleanup(name: String): String
}

data class MergedContestInfo(
    // canonical
    val contestName: String,
    val choices: List<String>,
    val counties: Set<String>,

    // contestRound
    val auditReason: AuditReason,
    val npop:Int,       // ballotCardCount
    val nc:Int,         // contestBallotCardCount
    val voteForN: Int,  // nwinners
    val nsamples: Int,  // optimisticSamplesToAudit
    val marginInVotes: Int, // minMargin

    // mvr file
    val countyMvrs: Int,
    val statewideMvrs: Int,
)

data class StrataInfo(
    val strataName: String,
    val nmvrs: Int,
    val Npop: Int,
)

data class MergedInfo(
    val mergedContestInfo: List<MergedContestInfo>,
    val strataInfo: List<StrataInfo>,
    val statewideContests: List<CorlaContestRoundCsv>,
)

fun mergeContestInfo(input: ColoradoInput): MergedInfo {
    val canonical = input.canonicalContests()
    val contests = input.roundContests
    val compareMap = input.contestMvrs.associateBy { it.contestName }
    val countyMap = input.countyMvrs.associateBy { it.countyName }

    val mergedContestInfo = canonical.values.map {
        val round = contests[it.contestName]
        val compare = compareMap[it.contestName]

        MergedContestInfo(
            it.contestName,
            it.choices,
            it.counties,

            round?.auditReason ?: AuditReason.none,
            round?.ballotCardCount ?: 0,
            round?.contestBallotCardCount ?: 0,
            round?.nwinners ?: 1,
            round?.optimisticSamplesToAudit ?: 0,
            round?.minMargin ?: 0,

            compare ?. countMvr ?: 0,
            compare ?. countStatewide ?: 0,
        )
    }

    // pick out the contests that are the targeted ones; should have a single contest
    val strataInfo = mutableListOf<StrataInfo>()
    val statewideContests = mutableListOf<CorlaContestRoundCsv>()
    canonical.values.forEach {
        val round = contests[it.contestName]
        if (round != null && round.auditReason == AuditReason.county_wide_contest) {
            if (it.counties.size != 1)
                println("*** ${it.contestName} has multiple counties: ${it.counties}")
            val county = it.counties.first()
            val countyMvr = countyMap[county]!!

            val countyInfo = StrataInfo(
                county,
                countyMvr.countMvr,
                round.ballotCardCount
            )
            strataInfo.add(countyInfo)
        }
        if (round != null && round.auditReason == AuditReason.state_wide_contest) {
            statewideContests.add(round)
        }
    }

    val totalCards = strataInfo.sumOf { it.Npop }
    val stateMvrCount = mergedContestInfo.filter { it.auditReason == AuditReason.state_wide_contest}.maxOf { it.statewideMvrs }
    strataInfo.add(StrataInfo("Statewide", nmvrs = stateMvrCount, Npop= totalCards, ))

    return MergedInfo(mergedContestInfo, strataInfo, statewideContests)
}

fun writeCountyData(topdir: String, strataInfo: List<StrataInfo>) {
    // misc data by county
    val outputFilename = "$topdir/${CountyAudit.countyDataFile}"
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("county,   nmvrs, npop\n")
    strataInfo.sortedBy { it.strataName }.forEach {
        writer.write("${it.strataName}, ${nfn(it.nmvrs, 5)}, ${nfn(it.Npop, 5)}\n")
    }
    writer.close()
    println("wrote ${strataInfo.size} countyData to $outputFilename")
}

// data class CountyContestTab(val countyName: String) {
//    val contests = mutableMapOf<String, ContestTab>()
// data class ContestTab(val contestName: String) {
//    val choices = mutableMapOf<String, Int>()

fun writeCountyContestData(topdir: String, contestMap: Map<String, ContestWithAssertions>, countyTabs: List<CountyContestTab>) {
    // misc data by county
    val outputFilename = "$topdir/${CountyAudit.countyContestDataFile}"
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("county, contest, id, voteDiff, votes,\n")

    var count = 0
    countyTabs.forEach { countyTab ->
        countyTab.contests.values.forEach { contestTab ->
            val contestUA = contestMap[contestTab.contestName]!!
            val contest = contestUA.contest
            val info = contest.info()
            val candidateVotes = contestTab.choices.map { (choice, vote) ->
                Pair(info.candidateNames[choice] ?: 0, vote)
            }.toMap()

            if (info.id == 533)
                print("")

            // calculate the vote difference for the minimum assorter
            val minAssertion = contestUA.minAssertion()
            val voteDiff = if (minAssertion != null) minAssertion.assorter.calcMarginFromRegVotes(candidateVotes, 1).toInt()
                           else 0

            writer.write("${countyTab.countyName}, ${contestTab.contestName}, ${info.id}, $voteDiff, ")
            candidateVotes.forEach { (id, vote) ->
                writer.write("$id:$vote, ")
            }
            writer.write("\n")
            count++
        }
    }
    writer.close()
    println("wrote ${count}  countyContestData to $outputFilename")
}