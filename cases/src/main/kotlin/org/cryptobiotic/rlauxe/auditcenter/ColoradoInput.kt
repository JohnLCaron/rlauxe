package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.nfn
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.collections.get

val auditcenter = "/home/stormy/datadrive/github/nealmcb/auditcenter"

private val logger = KotlinLogging.logger("ColoradoInput")

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

    // data class CountyTabAllContests(val countyName: String) {
    //    val contests = Map<String, CountyContestVotes>() // contestName (canonical I think) -> CountyContestVotes
    //    var ncards = 0
    // data class CountyContestVotes(val contestName: String) {
    //    val choices = Map<String, Int>() // choice name (not canonical) -> votes in this county and contest
    //    var ncards = 0
    val countyTabAllContests: Map<String, CountyTabAllContests> by lazy {
        readCountyTabulateCsv(tabulateCountyFile)
    }

    // data class ContestTabAllCounties(val contestName: String) {
    //    val choices = Map<String, Int>() // // canonical choice name -> votes
    //    val counties = Set<String>()
    //    var totalCardsInContest: Int
    open val contestTabAllCounties: Map<String, ContestTabAllCounties> by lazy {
        val tabs = mutableMapOf<String, ContestTabAllCounties>()
        countyTabAllContests.values.forEach { countyTabAllContests ->
            countyTabAllContests.contests.forEach { (contestName, countyContestVotes) ->
                val tab = tabs.getOrPut(contestName) { ContestTabAllCounties (contestName) }
                tab.add(countyTabAllContests.countyName, countyContestVotes)
            }
        }
        tabs.toMap()
    }

    //////////
    // from the list of mvr, cvr comparisions, we derive the following:
    val cardComparison: CardComparisonResults by lazy {
        readContestComparisonCsv(mvrComparisonFile)
    }

    // for each contest, total mvrs over all counties
    // data class ContestMvrCount(val contestName: String) {
    //    var countMvr = 0
    //    var countStatewide = 0
    val contestsFromMvrs: List<ContestMvrCount> by lazy { cardComparison.contestMvrs }

    // for each county, over all contests
    // data class CountyMvrCount(val countyName: String) {
    //    var countMvr = 0
    val countiesFromMvrs: List<CountyMvrCount> by lazy { cardComparison.countyMvrs }

    // data class CountyStylesFromMvrs(
    //    val countyName: String
    //    val styles = Map<Set<String>, MvrStyle>
    // data class MvrStyle(val id: Int, val contests: Set<String>) {
    //    var cardCount = 0
    val stylesFromMvrs: List<CountyStylesFromMvrs> by lazy { cardComparison.stylesByCounty }

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

    // dont use these directly, use matchCanonicalContest() and matchCanonicalCandidate()
    open fun contestNameCleanup(county: String, name: String) = name
    open fun candidateNameCleanup(county: String, name: String) = name

    //// needed to match the export contest/canidata names, all ColoradoInput classes should be consistent already

    fun matchCanonicalContest(county: String, exportContestName: String): CanonicalContest? {
        val transform = contestNameCleanup(county, exportContestName)
        val cleanup = munge(transform)
        return canonicalContestMungedNames[cleanup]
    }

    // return canonical candidate name
    fun matchCanonicalCandidate(county: String, contest: CanonicalContest, exportCandidateName: String): String? {
        val transform = candidateNameCleanup(county, exportCandidateName)
        var match = contest.choices.find { munge(it) == munge(transform) }
        if (match == null) match = contest.choices.find { it == yesno(exportCandidateName) }
        return match
    }

    private val canonicalContestMungedNames: Map<String, CanonicalContest> by lazy {
        canonicalContests().mapKeys { munge(it.key) }
    }
}

private val alphnumRE = "[^A-Za-z0-9]".toRegex()
fun munge(name: String): String {
    var munge = name.replace(alphnumRE, "").lowercase()
    // println("'$name' -> '$munge'")
    return munge
}

fun yesno(candName:String):String {
    return when (candName) {
        "Yes/For" -> "Yes"
        "No/Against" -> "No"
        "Yes" -> "Yes/For"
        "No" -> "No/Against"
        else -> candName
    }
}

data class MergedContestInfo(
    // canonical
    val canonicalContest: CanonicalContest,
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
    val ballotCardCount: Int,  // round.ballotCardCount
)

data class MergedInfo(
    val mergedContestInfo: List<MergedContestInfo>,
    val strataInfo: List<StrataInfo>,
    val statewideContests: List<CorlaContestRoundCsv>,
)

// just use munge to match names, no county name cleanup
fun CanonicalContest.matchCanonicalCandidate(candidateName: String): String? {
    var match = this.choices.find { munge(it) == munge(candidateName) }
    if (match == null) match = this.choices.find { it == yesno(candidateName) }
    return match
}

fun mergeContestInfo(input: ColoradoInput): MergedInfo {
    val canonical: Map<String, CanonicalContest> = input.canonicalContests() // has canonical name

    val roundContests: Map<String, CorlaContestRoundCsv> = input.roundContests // not canonical name
    val compareMap: Map<String, ContestMvrCount> = input.contestsFromMvrs.associateBy { it.contestName }
    val countyMap: Map<String, CountyMvrCount>  = input.countiesFromMvrs.associateBy { it.countyName }

    val mergedContestInfo = canonical.values.map {
        val round = roundContests[it.contestName]
        val compare = compareMap[it.contestName]

        MergedContestInfo(
            it,
            it.contestName,
            it.choices,
            it.counties,

            // TODO can we really tolerate missing the roundContest ??
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
        val round: CorlaContestRoundCsv? = roundContests[it.contestName]
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
    strataInfo.add(StrataInfo("Statewide", nmvrs = stateMvrCount, ballotCardCount= statewideBallots, ))

    return MergedInfo(mergedContestInfo, strataInfo, statewideContests)
}

fun CountyTabAllContests.makeContestTabs(canonicalContests: Map<String, CanonicalContest>,
                                         infos:Map<String, ContestInfo>, ncardsMap: Map<String, Int>,
): List<ContestTabulation> {

    return this.contests.values.map { countyContestVotes ->
        val info = infos[countyContestVotes.contestName]!!
        val ncards = ncardsMap[countyContestVotes.contestName] ?: 0 // eg Town of Lachbuie has all votes in Weld
        val canonicalContest = canonicalContests[countyContestVotes.contestName]!!
        countyContestVotes.makeContestTabulation(canonicalContest, info, ncards)
    }
}

// ContestTabByCounty (for one contest, all counties) vs CountyContestTab (for one county, one contest) (jeesh)
fun CountyContestVotes.makeContestTabulation(canonicalContest: CanonicalContest, info: ContestInfo, ncards: Int): ContestTabulation {
    val candidateVotes = this.choices.map { (choice, vote) ->
        val canonicalCandidateName = canonicalContest.matchCanonicalCandidate(choice)
        if (info.candidateNames[canonicalCandidateName] == null)
            logger.error{"contestTab candidate name $canonicalCandidateName not found in info"}
        Pair( info.candidateNames[canonicalCandidateName]!!, vote)
    }.toMap()

    return ContestTabulation(info, candidateVotes, ncards)
}

//////////////////////////////////////////////////////////////////////////////////////////

fun writeCountyData(topdir: String, strataInfo: List<StrataInfo>) {
    // misc data by county
    val outputFilename = "$topdir/${CountyAuditRecord.countyDataFile}"
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("county,   nmvrs, ballotCardCount\n")
    strataInfo.sortedBy { it.strataName }.forEach {
        writer.write("${it.strataName}, ${nfn(it.nmvrs, 5)}, ${nfn(it.ballotCardCount, 5)}\n")
    }
    writer.close()
    println("wrote ${strataInfo.size} countyData to $outputFilename")
}

// data class CountyContestTab(val countyName: String) {
//    val contests = mutableMapOf<String, ContestTab>()
// data class ContestTab(val contestName: String) {
//    val choices = mutableMapOf<String, Int>()

fun writeCountyContestData(topdir: String, contestMap: Map<String, ContestWithAssertions>, countyTabs: Map<String, CountyTabAllContests>) {
    // misc data by county
    val outputFilename = "$topdir/${CountyAuditRecord.countyContestDataFile}"
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("county, contest, id, voteDiff, votes,\n")

    var count = 0
    countyTabs.values.forEach { countyTab ->
        countyTab.contests.values.forEach { contestTab ->
            val contestUA = contestMap[contestTab.contestName]
            if (contestUA != null) {
                val contest = contestUA.contest
                val info = contest.info()
                val candidateVotes = contestTab.choices.map { (choice, vote) ->
                    Pair(info.candidateNames[choice] ?: 0, vote)
                }.toMap()

                // calculate the vote difference for the minimum assorter
                val minAssertion = contestUA.minAssertion()
                val voteDiff = if (minAssertion == null) 0
                    else minAssertion.assorter.calcMarginFromRegVotes(candidateVotes, 1).toInt()

                writer.write("${countyTab.countyName}, ${contestTab.contestName}, ${info.id}, $voteDiff, ")
                candidateVotes.forEach { (id, vote) ->
                    writer.write("$id:$vote, ")
                }
                writer.write("\n")
                count++
            }
        }
    }
    writer.close()
    println("wrote ${count}  countyContestData to $outputFilename")
}