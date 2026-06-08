package org.cryptobiotic.rlauxe.corla

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.corla.Colorado2024Input.mergedInfo
import org.cryptobiotic.rlauxe.persist.CountyAudit
import org.cryptobiotic.rlauxe.util.nfn
import java.io.FileOutputStream
import java.io.OutputStreamWriter

interface ColoradoInput {
    val generalCanonicalFile: String
    val canonicalContests: Map<String, CanonicalContest>

    val contestRoundFile: String
    val roundContests: Map<String, CorlaContestRoundCsv>

    val tabulateCountyFile: String
    val contestTabsByCounty: Map<String, ContestTabByCounty>

    val mvrComparisonFile: String
    val cardComparison: CardComparisonResults

    // merged
    val contestMvrs: List<ContestMvrs>
    val countyMvrs: List<CountyMvrs>
    val countyStyles: List<CountyStyles>

    val mergedContestMap: Map<String, MergedContestInfo>
    val strataMap: Map<String, StrataInfo>

    // use these to match with ColoradoInput, which has the canonical naming
    fun contestNameCleanup(name: String): String
    fun candidateNameCleanup(name: String): String
}

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
object Colorado2024Input: ColoradoInput {
     // canonical contests and choices
    override val generalCanonicalFile = "src/test/data/corla/2024audit/2024GeneralCanonicalList.csv"
    override val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result: MutableMap<String, CanonicalContest> =
            readGeneralCanonicalList(generalCanonicalFile).associateBy { it.contestName }.toMutableMap()

        //add these missing contests:
        val extras = listOf(
            CanonicalContest("Bannock Ballot Issue 6A", choices = listOf("Yes", "No")).addCounties(listOf("Douglas")),
            CanonicalContest("Spring Canyon Ballot Issue 6B", choices = listOf("Yes", "No")).addCounties(listOf("Douglas")),
        )
        extras.forEach { result[it.contestName] = it }

        // remove these contests
        result.remove("La Plata County Surveyor")

        result.toSortedMap()
    }

    //// contest building

    override val contestRoundFile = "src/test/data/corla/2024audit/round1/contest.csv"
    override val roundContests: Map<String, CorlaContestRoundCsv> by lazy {
        readColoradoContestRoundCsv(contestRoundFile) { it }
    } // 725

    override val tabulateCountyFile = "src/test/data/corla/2024audit/tabulateCounty.csv"
    override val contestTabsByCounty: Map<String, ContestTabByCounty> by lazy {
        readCountyTabulateCsv(tabulateCountyFile, { it }, { it })
    }

    override val mvrComparisonFile = "src/test/data/corla/2024audit/round3/contestComparison.csv"
    override val cardComparison: CardComparisonResults by lazy {
        readContestComparisonCsv(mvrComparisonFile) { it }
    }

    //// who uses the following?
    // data class CardComparisonResults(
    //    val contestMvrs: List<ContestMvrs>,
    //    val countyMvrs: List<CountyMvrs>,
    //    val stylesByCounty: List<CountyStyles>
    //)
    override val contestMvrs: List<ContestMvrs> by lazy { cardComparison.contestMvrs }
    override val countyMvrs: List<CountyMvrs> by lazy { cardComparison.countyMvrs }
    override val countyStyles: List<CountyStyles> by lazy { cardComparison.stylesByCounty }

    val mergedInfo: MergedInfo by lazy { mergeContestInfo(this) } // mergedContestInfo, strataInfo, statewideContests
    override val mergedContestMap: Map<String, MergedContestInfo> by lazy { mergedInfo.mergedContestInfo.associateBy { it.contestName } }
    override val strataMap: Map<String, StrataInfo> by lazy { mergedInfo.strataInfo.associateBy { it.strataName } }
    val statewideContests: List<CorlaContestRoundCsv> by lazy { mergedInfo.statewideContests }

    val countyContestMap: List<CountyContestTab> by lazy { convertToCountyContestTabs(contestTabsByCounty.values.toList()) }

    //// not used
    val precinctFile = "src/test/data/corla/2024election/2024GeneralPrecinctLevelResults.zip"

    val detailXmlFile = "src/test/data/corla/2024election/detail.xml"
    val detailXmlContests: ElectionDetailXml by lazy { readColoradoElectionDetail(detailXmlFile) }

    val resultsReportSummaryFile = "src/test/data/corla/2024audit/round1/ResultsReportSummary.csv"
    val resultsContests: List<ResultsReportContest> by lazy {
        readResultsReportContest(resultsReportSummaryFile) { it }
    }

    override fun contestNameCleanup(name: String) = contestNameCleanup2024(name)
    override fun candidateNameCleanup(name: String) = candidateNameCleanup2024(name)
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
    val canonical = input.canonicalContests
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

////////////////////////////////////////////////////////////////////////////////
// TODO specific to Corla 2024
// maybe not needed ??

fun contestNameCleanup2024(name: String): String {
    var working = name
    // if (working.contains(" -")) working = working.replace(" -", "")
    // if (working.contains("-")) working = working.replace("-", " ")
    if (working.contains("Colorado Court of Appeals Judge - Román")) working = working.replace("Colorado Court of Appeals Judge - Román", "Colorado Court of Appeals Judge Roman")
    if (working.contains("County Court Judge Cheyenne")) working = working.replace("County Court Judge Cheyenne", "Cheyenne County Court Judge")
    if (working.contains("County Court Judge Denver")) working = working.replace("County Court Judge Denver", "Denver County Court Judge")
    if (working.contains("County Court Judge Jefferson")) working = working.replace("County Court Judge Jefferson", "Jefferson County Court Judge")
    if (working.contains("County Court Judge Gunnison")) working = working.replace("County Court Judge Gunnison", "Gunnison County Court Judge")
    if (working.contains("County Court Judge Routt")) working = working.replace("County Court Judge Routt", "Routt County Court Judge")
    if (working.contains("Jefferson County Court- ")) working = working.replace("Jefferson County Court- ", "Jefferson County Court ")
    if (working.contains("Jefferson County Court-")) working = working.replace("Jefferson County Court-", "Jefferson County Court ")
    if (working.equals("BRUSH RURAL FIRE PROTECTION DISTRICT BALLOT ISSUE 7A")) working = "Brush Rural Fire Protection District Ballot Issue 7A"
    if (working.equals("Cheyenne County Court Judge")) working = "Cheyenne County Court Judge Eiring"
    if (working.equals("Gunnison County Court Judge")) working = "Gunnison County Court Judge Burgemeister"
    if (working.equals("Mesa County Court Judge Grattan III")) working = "Mesa County Court Judge Grattan"
    if (working.equals("Routt County Court Judge")) working = "Routt County Court Judge Wilson"
    if (working.equals("City of Aurora Question 3A")) working = "City of Aurora Ballot Question 3A"
    if (working.equals("Byers School District No. 32J Ballot Issue 5C")) working = "Byers School District 32J Ballot Issue 5C"
    if (working.equals("Holyoke School District RE-1J Ballot Issue 5K Bonds")) working = "Holyoke School District RE-1J Ballot Issue 5K"
    if (working.equals("Montrose School District RE-1J Ballot Issue 5A")) working = "Montrose County School District RE-1J Ballot Issue 5A"
    if (working.equals("Norwood School District R-2J Issue 5B")) working = "Norwood School District R-2J Ballot Issue 5B"
    if (working.equals("Weld County School District RE-8 Ballot Issue 5G Override")) working = "Weld County School District RE-8 Ballot Issue 5G"
    if (working.equals("Weld County School District RE-8 Ballot Issue 5H Bonds")) working = "Weld County School District RE-8 Ballot Issue 5H"
    if (working.equals("Weld County School District RE-10J Ballot Issue 5D Bonds")) working = "Weld County School District RE-10J Ballot Issue 5D"
    if (working.equals("Weld County School District RE-3J Ballot Issue 5F Override")) working = "Weld County School District RE-3J Ballot Issue 5F"
    if (working.equals("Weld County School District No. RE-9 Ballot Issue 4C Bonds")) working = "Weld County School District No. RE-9 Ballot Issue 4C"
    if (working.equals("Weld County School District No. RE-7 Ballot Issue 4B Bonds")) working = "Weld County School District No. RE-7 Ballot Issue 4B"
    if (working.equals("Weld County School District No. RE-7 Ballot Issue 4A Mill Levy Override")) working = "Weld County School District No. RE-7 Ballot Issue 4A"
    // these are from corla/2024audit/targetedContests.csv 11 out of 63 mistyped
    if (working.equals("City and County of Broomfield Ballot Question 2G")) working = "Broomfield Ballot Question 2G"
    if (working.equals("Custer County Commissioner District 2")) working = "Custer County Board of County Commissioners District 2"
    if (working.equals("City and County of Denver Ballot Issue 2Q")) working = "Denver Ballot Issue 2Q"
    if (working.equals("Fremont County Commissioner District 3")) working = "Fremont County Board of County Commissioners District 3"
    if (working.equals("County Commissioner District 2")) working = "Garfield County Commissioner District 2"
    if (working.equals("NORTH PARK SCHOOL DISTRICT R 1 BALLOT ISSUE 4A")) working = "North Park School District R 1 Ballot Issue 4A"
    // if (working.equals("Proposition 130 (STATUTORY) - Kit Carson")) working = "Proposition 130 (STATUTORY)"
    if (working.equals("Montezuma County Ballot Issue 1 A")) working = "Montezuma County Ballot Issue 1A"
    if (working.equals("Pitkin County Ballot Issue 1A")) working = "Pitkin County Ballot Issue 1A: Affordable and Workforce Housing Mill Levy"
    // if (working.equals("Amendment 80 (CONSTITUTIONAL) - Rio Blanco")) working = "Amendment 80 (CONSTITUTIONAL)"
    if (working.equals("San Miguel County Ballot Measure 1A")) working = "San Miguel County Ballot Question 1A"
    if (working.equals("Bent County Commissioner-District 1")) working = "Bent County Commissioner - District 1"
    if (working.equals("Cheyenne,County Court Judge - Cheyenne")) working = "Cheyenne County Court - Eiring"

    return working.trim()
}

// Weld County School District No. RE-7 Ballot Issue 4A Mill Levy Override,opportunistic_benefits,in_progress,1,182397,2729,"""Yes/For""",731,0.03000000,0,0,0,0,0,0,0,1.03905000,0,1819,1819
//Weld County School District No. RE-7 Ballot Issue 4B Bonds,opportunistic_benefits,in_progress,1,182397,2729,"""Yes/For""",720,0.03000000,0,0,0,0,0,0,0,1.03905000,0,1847,1847

fun candidateNameCleanup2024(name: String): String {
    var working = name
    if (working.contains("''")) working = working.replace("''", "'")
    if (working.contains("\"")) working = working.replace("\"", "'")
    if (working.equals("Seth Ryan")) working = "Anna Cooling"  // WTF ?
    return mutatisMutandi(working.trim())
}

private fun mutatisMutandi(choiceName: String): String {
    return when (choiceName) {
        "Randall Terry / Stephen E. Broden" -> "Randall Terry / Stephen E Broden"
        "Claudia De la Cruz / Karina García" -> "Claudia De la Cruz / Karina Garcia"
        "Colorado Supreme Court Justice Márquez" -> "Colorado Supreme Court Justice Marquez"
        "Colorado Court of Appeals Judge Román" -> "Colorado Court of Appeals Judge Roman"
        "Daniel Campaña" -> "Daniel Campana"
        "Yes/For" -> "Yes"
        "No/Against" -> "No"
        "Yes" -> "Yes/For"
        "No" -> "No/Against"
        else -> {
            if (choiceName.contains("Judge ")) choiceName.replace("Judge ", "")
            else {
                // println("HEY $choiceName")
                choiceName
            }
        }
    }
}