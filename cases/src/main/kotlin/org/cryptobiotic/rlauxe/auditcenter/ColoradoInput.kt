package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.persist.CountyAuditRecord
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.nfn
import java.io.FileOutputStream
import java.io.OutputStreamWriter

val auditcenter = "/home/stormy/datadrive/github/nealmcb/auditcenter"

private val logger = KotlinLogging.logger("ColoradoInput")

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

   2. use TestColoradoInputNames to cross check names with generalCanonicalFile

   additionally, we may need to make adjustments for cvrExport files, which tend to be divergent.
   subclasses provide contestNameCleanup and candidateNameCleanup
 */

abstract class ColoradoInput(
    val generalCanonicalFile: String,
    val contestRoundFile: String,
    val tabulateCountyFile: String,
    val mvrComparisonFile: String
) {
    abstract fun skipCounties(countyName: String): Boolean

    //
    // data class CanonicalContest(
    //    val contestName: String,
    //    val choices: List<String>
    //    val counties =  mutableSetOf<String>()

    abstract fun canonicalContests(): Map<String, CanonicalContest>

    fun counties(): List<String>  = canonicalContests().values.map { it.counties }
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
    //)
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
        readCountyTabulateCsv(tabulateCountyFile)
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
                val tab = tabs.getOrPut(contestName) { ContestTabAllCounties (contestName) }
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

    // for each contest, total mvrs over all counties
    // data class ContestMvrCount(val contestName: String) {
    //    var countMvr = 0
    //    var countStatewide = 0
    val contestsFromMvrs: List<ContestMvrCount> by lazy { cardComparison().contestMvrs }

    // for each county, over all contests
    // data class CountyMvrCount(val countyName: String) {
    //    var countMvr = 0
    val countiesFromMvrs: List<CountyMvrCount> by lazy { cardComparison().countyMvrs }

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
    val mergedContestMap: Map<String, MergedContestInfo> by lazy {
        mergedInfo().mergedContestInfo.associateBy { it.contestName }
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
    val statewideContests: List<CorlaContestRoundCsv> by lazy { mergedInfo().statewideContests }

    // dont use these directly, use matchCanonicalContest() and matchCanonicalCandidate()
    open fun contestNameCleanup(county: String, name: String) = name
    open fun candidateNameCleanup(county: String, name: String) = name

    //// needed to match the export contest/candidata names, all ColoradoInput classes should be consistent already

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

    // return canonical candidate name
    fun matchCandidate(county: String, contestName: String, candName: String): String {
        val canon = matchCanonicalContest(county, contestName)!!
        return matchCanonicalCandidate(county, canon, candName)!!
    }

    val canonicalContestMungedNames: Map<String, CanonicalContest> by lazy {
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

fun isWriteIn(candidateName: String) : Boolean {
    val cleanup = munge(candidateName)
    return cleanup.contains("writein")
}

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
    val npop:Int,       // ballot_card_count
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

// just use munge to match names, no county name cleanup
fun CanonicalContest.matchCandidateName(candidateName: String): String? {
    var match = this.choices.find { munge(it) == munge(candidateName) }
    if (match == null) match = this.choices.find { it == yesno(candidateName) }
    return match
}

fun mergeContestInfo(input: ColoradoInput): MergedInfo {
    val canonical: Map<String, CanonicalContest> = input.canonicalContests() // has canonical name

    val roundContests: Map<String, CorlaContestRoundCsv> = input.roundContests() // not canonical name
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
            npop = round?.ballotCardCount ?: 0,
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
    logger.info{"wrote ${strataInfo.size} countyData to $outputFilename"}
}

// data class CountyContestTab(val countyName: String) {
//    val contests = mutableMapOf<String, ContestTab>()
// data class ContestTab(val contestName: String) {
//    val choices = mutableMapOf<String, Int>()

fun writeCountyContestData(topdir: String, contestMap: Map<String, ContestWithAssertions>, coloradoInput: ColoradoInput) {
    val countyTabs = coloradoInput.countyTabsAllContests()
    // misc data by county
    val outputFilename = "$topdir/${CountyAuditRecord.countyContestDataFile}"
    val writer: OutputStreamWriter = FileOutputStream(outputFilename).writer()
    writer.write("county, contest, id, voteDiff, votes,\n")

    var count = 0
    countyTabs.values.forEach { countyTab ->
        countyTab.contests.values.forEach { ccv: CountyContestVotes ->
            val contestUA = contestMap[ccv.contestName]
            val canonicalContest = coloradoInput.canonicalContests()[ccv.contestName]
            if (contestUA != null && canonicalContest != null) {
                val contest = contestUA.contest
                val info = contest.info()
                val candidateVotes: Map<String, Int> = ccv.canonicalChoices(canonicalContest)
                val votesByCandId = candidateVotes.mapKeys { info.candidateNames[it.key]!! }

                // calculate the vote difference for the minimum assorter
                val minAssertion = contestUA.minAssertion()
                val voteDiff = if (minAssertion == null) 0
                    else minAssertion.assorter.calcMarginFromRegVotes(votesByCandId, 1).toInt()

                writer.write("${countyTab.countyName}, ${ccv.contestName}, ${info.id}, $voteDiff, ")
                votesByCandId.forEach { (id, vote) ->
                    writer.write("$id:$vote, ")
                }
                writer.write("\n")
                count++
            }
        }
    }
    writer.close()
    logger.info{"wrote ${count}  countyContestData to $outputFilename"}
}