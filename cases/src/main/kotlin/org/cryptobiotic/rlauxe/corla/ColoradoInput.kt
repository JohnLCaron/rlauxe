package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.util.nfn
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/*
   1. generalCanonicalFile can be used for the canonical contestName, choiceNames, and counties
      subclasses add overrides

   2. the following files are sufficient for calculating the uniform audit risks:
        val tabulateCountyFile = "2024/general/tabulateCounty.csv"
        val contestRoundFile =   "2024/general/round1/contest.csv"
        val mvrComparisonFile =  "2024/general/round3/contestComparison.csv"

   use TestColoradoInputNames to cross check names with generalCanonicalFile

   additionally, we may need to make adjustments for cvrExport files, which tend to be divergent.
   subclasses provide contestNameCleanup and candidateNameCleanup
 */

abstract class ColoradoInput(
    val generalCanonicalFile: String,
    val contestRoundFile: String,
    val tabulateCountyFile: String,
    val mvrComparisonFile: String
) {
    //      CountyName,ContestName,ContestChoices
    //      El Paso,City of Colorado Springs Ballot Question 300,"Yes/For,No/Against"
    //
    // data class CanonicalContest(
    //    val contestName: String,
    //    val choices: List<String>
    //    val counties =  mutableSetOf<String>()

    abstract fun canonicalContests(): Map<String, CanonicalContest>
    fun counties() = canonicalContests().values.map { it.counties }.flatten().toSet().toList().sorted()

    // data class CorlaContestRoundCsv(
    //    val contestName: String,
    //    val auditReason: AuditReason,
    //    val nwinners: Int,
    //    val ballotCardCount: Int,         // population size = county size when uniform audit
    //    val contestBallotCardCount: Int,  // Nc = number of cards with this contest on it
    //    val winners: String,
    //    val minMargin: Int,
    //    val riskLimit: Double,
    //    val gamma: Double,
    //    val optimisticSamplesToAudit: Int,
    //    val estimatedSamplesToAudit: Int,
    //)
    val roundContests: Map<String, CorlaContestRoundCsv> by lazy {
        readColoradoContestRoundCsv(contestRoundFile)
    } // 725

    //////////////////////////////////////////////////////////
    // corla/src/test/data/2024audit/tabulateCounty.csv
    // county_name,contest_name,choice,votes
    // Adams,Presidential Electors,Kamala D. Harris / Tim Walz,124050
    // Adams,Presidential Electors,Donald J. Trump / JD Vance,103011
    // Adams,Presidential Electors,Robert F. Kennedy Jr. / Nicole Shanahan,2909
    //
    // data class ContestTabByCounty(
    //    val contestName: String
    //    val choices = mutableMapOf<String, CountyTabulateChoice>()
    //
    // data class CountyContestTabs(
    //    val countyName: String
    //    val contests = mutableMapOf<String, CountyContestTab>()
    // data class CountyContestTab(
    //    val contestName: String) {
    //    val choices = mutableMapOf<String, Int>() // choice name -> nvotes in this county

    val contestTabsByCounty: Map<String, ContestTabByCounty> by lazy {
        readCountyTabulateCsv(tabulateCountyFile)
    }
    val countyContestTabs: List<CountyContestTabs> by lazy { convertToCountyContestTabs(contestTabsByCounty.values.toList()) }

    //////////
    // from the list of mvr, cvr comparisions, we derive the following:
    val cardComparison: CardComparisonResults by lazy {
        readContestComparisonCsv(mvrComparisonFile)
    }

    // for each contest, total mvrs over all counties
    // data class ContestMvrCount(val contestName: String) {
    //    var countMvr = 0
    //    var countStatewide = 0
    val contestMvrs: List<ContestMvrCount> by lazy { cardComparison.contestMvrs }

    // for each county, over all contests
    // data class CountyMvrCount(val countyName: String) {
    //    var countMvr = 0
    val countyMvrs: List<CountyMvrCount> by lazy { cardComparison.countyMvrs }

    // data class CountyStylesFromMvrs(
    //    val countyName: String
    //    val styles = Map<Set<String>, MvrStyle>
    // data class MvrStyle(val id: Int, val contests: Set<String>) {
    //    var cardCount = 0
    val mvrStyles: List<CountyStylesFromMvrs> by lazy { cardComparison.stylesByCounty }

    ///////////////////
    // merge info from all the above, derive the following
    val mergedInfo: MergedInfo by lazy { mergeContestInfo(this) } // mergedContestInfo, strataInfo, statewideContests

    // data class MergedContestInfo(
    //    // canonical
    //    val contestName: String,
    //    val choices: List<String>,
    //    val counties: Set<String>,
    //
    //    // contestRound
    //    val auditReason: AuditReason,
    //    val npop:Int,       // ballotCardCount
    //    val nc:Int,         // contestBallotCardCount
    //    val voteForN: Int,  // nwinners
    //    val nsamples: Int,  // optimisticSamplesToAudit
    //    val marginInVotes: Int, // minMargin
    //
    //    // mvr file
    //    val countyMvrs: Int,
    //    val statewideMvrs: Int,
    //)
    val mergedContestMap: Map<String, MergedContestInfo> by lazy { mergedInfo.mergedContestInfo.associateBy { it.contestName } }

    // data class StrataInfo(
    //    val strataName: String,
    //    val nmvrs: Int, // countyMvr.countMvr
    //    val ncards: Int,  // round.ballotCardCount
    //)
    val strataMap: Map<String, StrataInfo> by lazy { mergedInfo.strataInfo.associateBy { it.strataName } } // strata ~= county
    val statewideContests: List<CorlaContestRoundCsv> by lazy { mergedInfo.statewideContests }

    // use these on the export contest names, to match the canonical contest names contained in generalCanonicalFile
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
    val nmvrs: Int, // countyMvr.countMvr
    val ncards: Int,  // round.ballotCardCount
)

data class MergedInfo(
    val mergedContestInfo: List<MergedContestInfo>,
    val strataInfo: List<StrataInfo>,
    val statewideContests: List<CorlaContestRoundCsv>,
)

fun mergeContestInfo(input: ColoradoInput): MergedInfo {
    val canonical: Map<String, CanonicalContest> = input.canonicalContests()
    val contests: Map<String, CorlaContestRoundCsv> = input.roundContests
    val compareMap: Map<String, ContestMvrCount> = input.contestMvrs.associateBy { it.contestName }
    val countyMap: Map<String, CountyMvrCount>  = input.countyMvrs.associateBy { it.countyName }

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
        val round: CorlaContestRoundCsv? = contests[it.contestName]
        if (round != null && round.auditReason == AuditReason.county_wide_contest) {
            if (it.counties.size != 1)
                println("*** ${it.contestName} has multiple counties: ${it.counties}")
            val county: String = it.counties.first()
            val countyMvr: CountyMvrCount = countyMap[county]!!

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

    val statewideBallots = if (statewideContests.size > 0) statewideContests.first().ballotCardCount else 0
    val stateMvrCount = mergedContestInfo.filter { it.auditReason == AuditReason.state_wide_contest}.maxOf {
        it.statewideMvrs
    }
    strataInfo.add(StrataInfo("Statewide", nmvrs = stateMvrCount, ncards= statewideBallots, ))

    return MergedInfo(mergedContestInfo, strataInfo, statewideContests)
}

fun writeCountyData(topdir: String, strataInfo: List<StrataInfo>) {
    // misc data by county
    val outputFilename = "$topdir/${CountyAudit.countyDataFile}"
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("county,   nmvrs, npop\n")
    strataInfo.sortedBy { it.strataName }.forEach {
        writer.write("${it.strataName}, ${nfn(it.nmvrs, 5)}, ${nfn(it.ncards, 5)}\n")
    }
    writer.close()
    println("wrote ${strataInfo.size} countyData to $outputFilename")
}

// data class CountyContestTab(val countyName: String) {
//    val contests = mutableMapOf<String, ContestTab>()
// data class ContestTab(val contestName: String) {
//    val choices = mutableMapOf<String, Int>()

fun writeCountyContestData(topdir: String, contestMap: Map<String, ContestWithAssertions>, countyTabs: List<CountyContestTabs>) {
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