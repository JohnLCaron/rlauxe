package org.cryptobiotic.rlauxe.corlaInput

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.auditcenter.AuditReason
import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.CardComparisonResults
import org.cryptobiotic.rlauxe.auditcenter.ContestMvrCount
import org.cryptobiotic.rlauxe.auditcenter.ContestTabAllCounties
import org.cryptobiotic.rlauxe.auditcenter.CorlaContestRoundCsv
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.auditcenter.CountyMvrCount
import org.cryptobiotic.rlauxe.auditcenter.CountyStylesFromMvrs
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.auditcenter.readColoradoContestRoundCsv
import org.cryptobiotic.rlauxe.auditcenter.readContestComparisonCsv
import org.cryptobiotic.rlauxe.auditcenter.readCountyManifestCsv
import org.cryptobiotic.rlauxe.auditcenter.readCountyTabulateCsvFromFile
import org.cryptobiotic.rlauxe.auditcenter.readGeneralCanonicalList
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.corlaCounty.CountyManifest
import org.cryptobiotic.rlauxe.corlaCounty.StateManifest
import org.cryptobiotic.rlauxe.util.ContestTabulation
import kotlin.collections.forEach

val defaultCharset = "ISO-8859-1" // TODO something
private val logger = KotlinLogging.logger("StatewideInput")

abstract class StatewideInputWithCvrs(
    generalCanonicalFile: String,
    contestRoundFile: String,
    tabulateCountyFile: String,
    mvrComparisonFile: String,
): StatewideInput(
    generalCanonicalFile, contestRoundFile, tabulateCountyFile, mvrComparisonFile)
{
    abstract fun corlaCountyInput(countyName: String): CountyInput?
}

/*
   1. Identify the following 4 files in auditcenter

   1a. generalCanonicalFile is used for the canonical contestName, choiceNames, and counties
        CountyName,ContestName,ContestChoices
        Adams,17th Judicial District Ballot Question 7B,"Yes/For,No/Against"

   1b. tabulateCountyFile has the totals by county
        county_name,contest_name,choice,votes
        Adams,Presidential Electors,Kamala D. Harris / Tim Walz,124050
        Adams,Presidential Electors,Donald J. Trump / JD Vance,103011

   1c. contestRoundFile has the selected contests
        contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,one_vote_under_count,two_vote_under_count,disagreement_count,other_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
        17th Judicial District Ballot Question 7B,opportunistic_benefits,in_progress,1,516401,279529,"""No/Against""",37549,0.03000000,0,0,0,0,0,0,0,1.03905000,0,101,101
        Adams 12 Five Star Schools Ballot Issue 5D,opportunistic_benefits,in_progress,1,516401,117043,"""No/Against""",12622,0.03000000,0,0,0,0,0,0,0,1.03905000,0,299,299
        Adams 12 Five Star Schools Ballot Issue 5E,opportunistic_benefits,in_progress,1,516401,117043,"""Yes/For""",10481,0.03000000,0,0,0,0,0,0,0,1.03905000,0,360,360

   1d. mvrComparisonFile has the selected mvrs
        county_name,contest_name,imprinted_id,ballot_type,choice_per_voting_computer,audit_board_selection,consensus,record_type,audit_board_comment,timestamp,cvr_id,audit_reason
        Adams,17th Judicial District Ballot Question 7B,101-101-7,52,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:44:18.62646,178977,
        Adams,17th Judicial District Ballot Question 7B,101-130-14,14,"""Yes/For""","""Yes/For""",YES,uploaded,"",2024-11-19 09:49:44.148182,240137,
        Adams,17th Judicial District Ballot Question 7B,101-146-54,65,"""No/Against""","""No/Against""",YES,uploaded,"",2024-11-19 09:54:41.65526,250284,

   2. use CheckCanonicalNames to cross check names with generalCanonicalFile

   additionally, we may need to make adjustments for cvrExport files, which tend to be divergent.
   subclasses provide contestNameCleanup and candidateNameCleanup
 */

// could call it AuditCenterInput
abstract class StatewideInput(
    val generalCanonicalFile: String,
    val contestRoundFile: String,
    val tabulateCountyFile: String,
    val mvrComparisonFile: String,
): ColoradoInput {

    override fun name() = this.javaClass.simpleName
    override fun ncontests() = canonicalContests().size
    override fun nuncontested() = canonicalContests().count() { it.value.choices.size < 2 }

    abstract fun manifestFile(county: String): String

    open fun statewideManifest() = statewideManifest
    private val statewideManifest: StateManifest by lazy {
        val countyManifests = counties().map { county ->
            val mainfestList = readCountyManifestCsv(manifestFile(county))
            CountyManifest(county, mainfestList)
        }
        StateManifest(countyManifests)
    }

    fun countyManifest(countyName: String): CountyManifest? {
        return statewideManifest().manifests.find { it.county == countyName }
    }

    //////////////////////////////////////////////////////
    abstract fun skipCounties(countyName: String): Boolean

    //
    // data class CanonicalContest(
    //    val contestName: String,
    //    val choices: List<String>
    //    val counties =  mutableSetOf<String>()

    abstract fun canonicalContests(): Map<String, CanonicalContest>
    fun initializeCanonicalContests(): MutableMap<String, CanonicalContest> {
        val result: List<CanonicalContest> = readGeneralCanonicalList(generalCanonicalFile, this)
        return result.associateBy { it.contestName }.toMutableMap()
    }

    fun addCandidates(result: MutableMap<String, CanonicalContest>, contestName: String, addCandidates: List<String>) {
        val current = result[contestName]!!
        val achoices = current.choices + addCandidates
        result[contestName] = CanonicalContest(current.stateInput, current.contestName, choicesIn = achoices).addCounties(current.counties.toList())
    }

    // in canonical manifest order TODO perhaps read the file with county ids ContestIdsByCounties (20216)
    open fun counties(): List<String>  = canonicalContests().values.map { it.counties }
        .flatten()
        .filter { !skipCounties(it) }
        .toSet()
        .toList()
        .sorted()

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
    // )
    open fun roundContests(): Map<String, CorlaContestRoundCsv> = roundContests
    private val roundContests: Map<String, CorlaContestRoundCsv> by lazy {
        readColoradoContestRoundCsv(contestRoundFile)
    }

    // data class CountyTabAllContests(val countyName: String) {
    //    val contests = Map<String, CountyContestVotes>() // contestName (canonical I think) -> CountyContestVotes
    //    var ncards = 0
    // data class CountyContestVotes(val contestName: String) {
    //    val choices = Map<String, Int>() // choice name (not canonical) -> votes in this county and contest
    //    var ncards = 0
    open fun countyTabsAllContests(): Map<String, CountyTabAllContests> = countyTabsAllContests
    private val countyTabsAllContests: Map<String, CountyTabAllContests> by lazy {
        readCountyTabulateCsvFromFile(tabulateCountyFile) // county -> CountyTabAllContests
    }

    // data class ContestTabAllCounties(val contestName: String) {
    //    val choices = Map<String, Int>() // // canonical choice name -> votes
    //    val counties = Set<String>()
    //    var totalCardsInContest: Int
    open fun contestTabsAllCounties(): Map<String, ContestTabAllCounties> = contestTabsAllCounties
    private val contestTabsAllCounties: Map<String, ContestTabAllCounties> by lazy {
        val tabs = mutableMapOf<String, ContestTabAllCounties>()
        countyTabsAllContests().values.forEach { countyTabAllContests ->
            countyTabAllContests.contests.forEach { (contestName, countyContestVotes) ->
                val tab = tabs.getOrPut(contestName) { ContestTabAllCounties(contestName) }
                tab.add(countyTabAllContests.countyName, countyContestVotes)
            }
        }
        tabs.toMap()
    }

    //////////
    // from the list of mvr, cvr comparisions, we derive the following:
    open fun cardComparison(): CardComparisonResults = cardComparison
    private val cardComparison: CardComparisonResults by lazy {
        readContestComparisonCsv(mvrComparisonFile)
    }

    // TODO needed ?
    // for each contest, total mvrs over all counties
    // data class ContestMvrCount(val contestName: String) {
    //    var countMvr = 0
    //    var countStatewide = 0
    val contestsFromMvrs: List<ContestMvrCount> by lazy { cardComparison().contestMvrs }

    // TODO needed ?
    // for each county, over all contests
    // data class CountyMvrCount(val countyName: String) {
    //    var countMvr = 0
    val countiesFromMvrs: List<CountyMvrCount> by lazy { cardComparison().countyMvrs }

    // TODO needed ?
    // data class CountyStylesFromMvrs(
    //    val countyName: String
    //    val styles = Map<Set<String>, MvrStyle>
    // data class MvrStyle(val id: Int, val contests: Set<String>) {
    //    var cardCount = 0
    val stylesFromMvrs: List<CountyStylesFromMvrs> by lazy { cardComparison().stylesByCounty }

    ///////////////////
    // merge info from all the above, derive the following
    open fun mergedInfo() = mergedInfo
    private val mergedInfo: MergedInfo by lazy {
        mergeContestInfo(this)
    } // mergedContestInfo, strataInfo, statewideContests

    val mergedContestMap: Map<String, MergedContestInfo> by lazy {
        mergedInfo().mergedContestInfo.associateBy { it.contestName }
    }
    val contestNc: Map<String, Int> by lazy {
        mergedInfo().mergedContestInfo.associate { it.contestName to it.nc }
    }

    // strata ~= county
    // data class StrataInfo(
    //    val strataName: String,
    //    val nmvrs: Int, // countyMvr.countMvr
    //    val ncards: Int,  // round.ballotCardCount
    //)
    val strataMap: Map<String, StrataInfo> by lazy { mergedInfo().strataInfo.associateBy { it.strataName } }
    open fun strataPopulation() = strataPopulation
    private val strataPopulation: Map<String, Int> by lazy { mergedInfo().strataInfo.associate { it.strataName to it.ballotCardCount } } // county name to population

    //// TODO all StatewideInput classes should be consistent already
    //// needed to match the export contest/candidata names, all StatewideInput classes should be consistent already
    fun matchCanonicalContest(county: String, exportContestName: String): CanonicalContest? {
        val cleaned = contestNameCleanup(county, exportContestName)
        val munged = munge(cleaned)
        val result = canonicalContestMungedNames[munged]
        return result
    }

    fun matchCanonicalContest(exportContestName: String): CanonicalContest? {
        val munged = munge(exportContestName)
        return canonicalContestMungedNames[munged]
    }

    // return canonical candidate name
    fun matchCanonicalCandidate(county: String, contest: CanonicalContest, choice: String): String? {
        val cleaned = candidateNameCleanup(county, choice)
        var match = contest.choices.find { munge(it) == munge(cleaned) }
        if (match == null) match = contest.choices.find { it == yesnoCanon(choice) }
        return match
    }

    // return canonical candidate name
    fun matchCanonicalCandidate(county: String, contestName: String, candName: String): String {
        val canon = matchCanonicalContest(county, contestName)!!
        return matchCanonicalCandidate(county, canon, candName)!!
    }

    val canonicalContestMungedNames: Map<String, CanonicalContest> by lazy {
        canonicalContests().mapKeys { munge(it.key) }
    }

    fun contestNameMunged(countyName: String, contestName: String): String {
        val transform = contestNameCleanup(countyName, contestName)
        return munge(transform)
    }

    ///////// TODO these could be on the CountyInput
    // dont use these directly, use matchCanonicalContest() and matchCanonicalCandidate()
    open fun contestNameCleanup(county: String, name: String) = name
    open fun candidateNameCleanup(county: String, name: String) = name
}

private val alphnumRE = "[^A-Za-z0-9]".toRegex()
fun munge(name: String): String {
    // var munge = removeParen(name) //  not sure
    var munge = name.replace(alphnumRE, "").lowercase()
    return munge
}

fun yesno(candName:String):String {
    return when (candName.lowercase()) {
        "yes/for" -> "Yes"
        "no/against" -> "No"
        "yes" -> "Yes/For"
        "no" -> "No/Against"
        else -> candName
    }
}
fun yesnoCanon(candName:String):String {
    return when (candName.lowercase()) {
        "for the measure", "yes/for", "yes / for", "yes" -> "Yes"
        "against the measure", "no/against", "no / against", "no" -> "No"
        else -> candName
    }
}

fun isWriteIn(candidateName: String) : Boolean {
    val cleanup = munge(candidateName)
    return cleanup.contains("writein")
}

fun removeParen(name: String): String {
    val pos1 = name.indexOf("(")
    val pos2 = name.indexOf(")")
    if (pos1 > 0 && pos2 > 0) {
        return (name.substring(0, pos1) +
                if (pos2 == name.length-1) "" else name.substring(pos2+1, name.length)).trim()
    }
    return name
}

/////////////////////////////////////////////////////////////////////////////////////////

data class MergedContestInfo(
    // canonical
    val canonicalContest: CanonicalContest,
    val contestName: String,
    val choices: List<String>, // TODO why cant we convert to canonical choices immediately ??
    val counties: Set<String>,

    // data class CorlaContestRoundCsv(
    //    val contestName: String,
    //    val auditReason: AuditReason,
    //    val nwinners: Int,
    //    val ballotCardCount: Int,         // population size = eg county size when uniform audit
    //    val contestBallotCardCount: Int,  // Nc = number of cards with this contest on it
    //    val winners: String,
    //    val minMargin: Int,
    //    val riskLimit: Double,  // TODO use this
    //    val gamma: Double,      // and this ?? = 1.03905000
    //    val optimisticSamplesToAudit: Int, // check if these ever differ
    //    val estimatedSamplesToAudit: Int,
    //)
    val auditReason: AuditReason,
    val corlaPop:Int,   // ballot_card_count = corla population
    val nc:Int,         // contest_ballot_card_count
    val voteForN: Int,  // winners_allowed
    val nsamples: Int,  // optimistic_samples_to_audit
    val marginInVotes: Int, // min_margin
    val riskLimit: Double, // risk_limit

    // mvr file
    val countyMvrs: Int,
    val statewideMvrs: Int,
)

data class StrataInfo(
    val strataName: String,
    val nmvrs: Int, // countyMvr.countMvr
    val ballotCardCount: Int,  // round.ballot_card_count
)

data class MergedInfo(
    val mergedContestInfo: List<MergedContestInfo>,
    val strataInfo: List<StrataInfo>,
    val statewideContests: List<CorlaContestRoundCsv>,
)

fun mergeContestInfo(input: StatewideInput): MergedInfo {
    val canonical: Map<String, CanonicalContest> = input.canonicalContests() // has canonical name

    val roundContests: Map<String, CorlaContestRoundCsv> = input.roundContests() // not canonical name
    val compareMap: Map<String, ContestMvrCount> = input.contestsFromMvrs.associateBy { it.contestName }
    val countyMap: Map<String, CountyMvrCount>  = input.countiesFromMvrs.associateBy { it.countyName }

    // mergedContestInfo in canonical list order, which seems to be alphabetic; this determines contest id ordering.
    val mergedContestInfo = canonical.values.map { cc: CanonicalContest ->
        val round = roundContests[cc.contestName]
        val compare = compareMap[cc.contestName]

        MergedContestInfo(
            cc,
            cc.contestName,
            cc.choices,
            cc.counties,

            // TODO can we really tolerate missing the contestRound ?? nc ?
            round?.auditReason ?: AuditReason.none,
            corlaPop = round?.ballotCardCount ?: 0,
            nc = round?.contestBallotCardCount ?: 0,
            round?.nwinners ?: 1,
            round?.optimisticSamplesToAudit ?: 0,
            round?.minMargin ?: 0,
            round?.riskLimit ?: 0.0,

            compare ?. countMvr ?: 0,    // ContestMvrCount.countMvr
            compare ?. countStatewide ?: 0,   // ContestMvrCount.countStatewide
        )
    }

    // create a strata for each county
    val strataMap = mutableMapOf<String, StrataInfo>()
    val statewideContests = mutableListOf<CorlaContestRoundCsv>()
    canonical.values.forEach { canonicalContest ->
        val contestRound: CorlaContestRoundCsv? = roundContests[canonicalContest.contestName]
        if (contestRound != null && canonicalContest.counties.size == 1) {
            val county: String = canonicalContest.counties.first() // use the first county
            val countyMvr: CountyMvrCount = countyMap[county]!!

            if (strataMap[county] != null) {
                val old = strataMap[county]?.ballotCardCount ?: 0
                if (old != contestRound.ballotCardCount) {
                    logger.warn{"*** contest ${canonicalContest.contestName} county $county has ballotCardCount $old != ${contestRound.ballotCardCount}"}
                }
            } else {
                strataMap[county] = StrataInfo(county, countyMvr.countMvr, contestRound.ballotCardCount)
            }
        }
        if (contestRound != null && contestRound.auditReason == AuditReason.state_wide_contest) {
            statewideContests.add(contestRound)
        }
    }

    val statewideBallots = if (statewideContests.size > 0) statewideContests.first().ballotCardCount else 0
    val stateMvrCount = mergedContestInfo.filter { it.auditReason == AuditReason.state_wide_contest}.maxOf {
        it.statewideMvrs
    }
    strataMap["Statewide"] = StrataInfo("Statewide", nmvrs = stateMvrCount, ballotCardCount= statewideBallots)

    return MergedInfo(mergedContestInfo, strataMap.values.toList(), statewideContests)
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
    val candidateVotes = this.canonicalChoices(canonicalContest).map { (canonChoice, vote) ->
        if (info.candidateNames[canonChoice] == null)
            logger.error{"contestTab candidate name $canonChoice not found in info"}
        Pair( info.candidateNames[canonChoice]!!, vote)
    }.toMap()

    return ContestTabulation(info, candidateVotes, ncards)
}
