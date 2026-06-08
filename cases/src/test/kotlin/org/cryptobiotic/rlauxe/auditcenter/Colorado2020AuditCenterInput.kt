package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.corla.CanonicalContest
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.corla.readGeneralCanonicalList

class Colorado2020AuditCenterInput(): ColoradoInput(
    generalCanonicalFile = "$auditcenter/canonicalTitleCase.csv",
    contestRoundFile = "$auditcenter/round_1/contest.csv",
    tabulateCountyFile = "$auditcenter/tabulate_county.csv",
    mvrComparisonFile = "$auditcenter/round_3/contestComparison.csv"
) {
    // canonical contests and choices
    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result: MutableMap<String, CanonicalContest> =
            readGeneralCanonicalList(generalCanonicalFile).associateBy { it.contestName }.toMutableMap()

        // add these missing contests:
        val extras = listOf(
            CanonicalContest("Adams County Ballot Issue 1A", choices = listOf("Yes/For", "No/Against",)).addCounties(
                listOf("Adams",)
            )
        )
        extras.forEach { result[it.contestName] = it }

        // remove these contests: TODO some? are in cvrs; see TestContestNames
        // Gunnison County Commissioner - District 1
        // Gunnison County Commissioner - District 2
        // Gunnison County Library District Ballot Issue 6A

        // these are from checkCountyTabulateHasCanonical() and checkContestRoundHasCanonical()
        result.remove("Gunnison County Commissioner - District 1")
        result.remove("Gunnison County Commissioner - District 2")
        result.remove("Gunnison County Court Judge - Burgemeister") // ?? check, also signal when not present in file
        result.remove("Town of Marble - Board of Trustees")
        result.remove("Town of Marble Ballot Issue 2A")
        result.remove("San Juan County Commissioner - District 1")
        result.remove("San Juan County Commissioner - District 2")
        result.remove("San Juan County Court Judge - Edwards")

        result.toSortedMap()
    }

    // county specific ??
    override fun contestNameCleanup(name: String): String {
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

    override fun candidateNameCleanup(candName: String): String {
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

    companion object {
        private val auditcenter = "/home/stormy/dev/github/rla/nealmcb/auditcenter/2020/general"
    }
}
