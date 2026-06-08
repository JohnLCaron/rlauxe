package org.cryptobiotic.rlauxe.datadrive

import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.corla.AuditReason
import org.cryptobiotic.rlauxe.corla.CanonicalContest
import org.cryptobiotic.rlauxe.corla.CardComparisonResults
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.corla.ContestMvrs
import org.cryptobiotic.rlauxe.corla.ContestTabByCounty
import org.cryptobiotic.rlauxe.corla.CorlaContestRoundCsv
import org.cryptobiotic.rlauxe.corla.CountyContestTab
import org.cryptobiotic.rlauxe.corla.CountyMvrs
import org.cryptobiotic.rlauxe.corla.CountyStyles
import org.cryptobiotic.rlauxe.corla.MergedContestInfo
import org.cryptobiotic.rlauxe.corla.MergedInfo
import org.cryptobiotic.rlauxe.corla.StrataInfo
import org.cryptobiotic.rlauxe.corla.convertToCountyContestTabs
import org.cryptobiotic.rlauxe.corla.mergeContestInfo
import org.cryptobiotic.rlauxe.corla.readColoradoContestRoundCsv
import org.cryptobiotic.rlauxe.corla.readContestComparisonCsv
import org.cryptobiotic.rlauxe.corla.readCountyTabulateCsv
import org.cryptobiotic.rlauxe.corla.readGeneralCanonicalList

/*
   1. the following files are sufficient for calculating the uniform audit risks:

        val tabulateCountyFile = "2024/general/tabulateCounty.csv"
        val contestRoundFile =   "2024/general/round1/contest.csv"
        val mvrComparisonFile =  "2024/general/round3/contestComparison.csv"

    2. generalCanonicalFile can be used for the canonical contestName, choiceNames, and counties (with 3 modifications)

    generalCanonicalFile = "2024/general/2024GeneralCanonicalList.csv"
       with following contests added:
         CanonicalContest("Bannock Ballot Issue 6A", choices = listOf("Yes", "No"), counties=listOf("Douglas")
         CanonicalContest("Spring Canyon Ballot Issue 6B", choices = listOf("Yes", "No"), counties=listOf("Douglas")
       and the following contest removed:
         CanonicalContest("La Plata County Surveyor", comment="There are no candidates for this office")

       the names in tabulateCountyFile, contestRoundFile, mvrComparisonFile agree with generalCanonicalFile
       tabulateCountyFile, contestRoundFile have every name in generalCanonicalFile
       mvrComparisonFile is missing 58 contests that are in generalCanonicalFile
 */

// create common with Colorado2024Input
object Colorado2020Input: ColoradoInput {
    private val auditcenter = "/home/stormy/dev/github/rla/nealmcb/auditcenter/2020/general"

    // canonical contests and choices
    override val generalCanonicalFile = "$auditcenter/canonicalTitleCase.csv"
    override val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result: MutableMap<String, CanonicalContest> =
            readGeneralCanonicalList(generalCanonicalFile).associateBy { it.contestName }.toMutableMap()

        // add these missing contests:
        val extras = listOf(
            CanonicalContest("Adams County Ballot Issue 1A", choices=listOf("Yes/For","No/Against",)).addCounties(listOf("Adams",))
        )
        extras.forEach { result[it.contestName] = it }

        // remove these contests
        result.remove("Gunnison County Commissioner - District 1")
        result.remove("Gunnison County Commissioner - District 2")
        result.remove("Gunnison County Court Judge - Burgemeister")
        result.remove("Town of Marble - Board of Trustees")
        result.remove("Town of Marble Ballot Issue 2A")
        result.remove("San Juan County Commissioner - District 1")
        result.remove("San Juan County Commissioner - District 2")
        result.remove("San Juan County Court Judge - Edwards")

        result.toSortedMap()
    }

    //// contest building
    override val tabulateCountyFile = "$auditcenter/tabulate_county.csv"
    override val contestTabsByCounty: Map<String, ContestTabByCounty> by lazy {
        readCountyTabulateCsv(tabulateCountyFile, { it }, { it })
    }

    override val contestRoundFile = "$auditcenter/round_1/contest.csv"
    override val roundContests: Map<String, CorlaContestRoundCsv> by lazy {
        readColoradoContestRoundCsv(contestRoundFile) { it }
    } // 725

    //// generating cvrs
    override val mvrComparisonFile = "$auditcenter/round_3/contestComparison.csv"
    override val cardComparison: CardComparisonResults by lazy {
        readContestComparisonCsv(mvrComparisonFile) { it }
    }

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

    override fun contestNameCleanup(name: String) = contestNameCleanup2020(name)
    override fun candidateNameCleanup(name: String) = candidateNameCleanup2020(name)
}

// seems like this is county specific ??

fun contestNameCleanup2020(name: String): String {
    return when (name) {
        "Representative to the 117th United States Congress-District 2" -> "Representative to the 117th United States Congress - District 2"
        "Representative to the 117th United States Congress-District 4" -> "Representative to the 117th United States Congress - District 4"
        "District Attorney-20th Judicial District" -> "District Attorney - 20th Judicial District"
        "Boulder County Court" -> "Boulder County Court Judge - Martin"
        "City of Louisville City Council Ward 3 (1-Year Term)" -> "City of Louisville City Council Ward 3"
        "Town of Superior - Trustee" -> "Town of Superior Trustee"
        "City Of Boulder Ballot Issue 2B" -> "City of Boulder Ballot Issue 2B"
        "Amendment B (Constitutional)" -> "Amendment B (CONSTITUTIONAL)"
        "Amendment C (Constitutional)" -> "Amendment C (CONSTITUTIONAL)"
        "Amendment 76 (Constitutional)" -> "Amendment 76 (CONSTITUTIONAL)"
        "Amendment 77 (Constitutional)" -> "Amendment 77 (CONSTITUTIONAL)"
        "Proposition EE (Statutory)" -> "Proposition EE (STATUTORY)"
        "Proposition 113 (Statutory)" -> "Proposition 113 (STATUTORY)"
        "Proposition 114 (Statutory)" -> "Proposition 114 (STATUTORY)"
        "Proposition 115 (Statutory)" -> "Proposition 115 (STATUTORY)"
        "Proposition 116 (Statutory)" -> "Proposition 116 (STATUTORY)"
        "Proposition 117 (Statutory)" -> "Proposition 117 (STATUTORY)"
        "Proposition 118 (Statutory)" -> "Proposition 118 (STATUTORY)"
        else -> name
    }
}

fun candidateNameCleanup2020(candName: String): String {
    return when (candName) {
        "Jordan 'Cancer' Scott / Jennifer Tepool" -> "Jordan \"Cancer\" Scott / Jennifer Tepool"
        "Roque 'Rocky' De La Fuente / Darcy G. Richardson" -> "Roque \"Rocky\" De La Fuente / Darcy G. Richardson"
        "Jo Jorgensen / Jeremy 'Spike' Cohen" -> "Jo Jorgensen / Jeremy \"Spike\" Cohen"
        "Stephan 'Seku' Evans" -> "Stephan \"Seku\" Evans"
        "Andrew J. O'Connor" -> "Andrew J. OConnor"
        "James T Crowder" -> "James T. Crowder"
        "James E. 'Jed' Gilman" -> "James E. \"Jed\" Gilman"
        "YES" -> "Yes"
        "NO" -> "No"
        "YES/FOR" -> "Yes/For"
        "NO/AGAINST" -> "No/Against"
        else -> candName
    }
}
