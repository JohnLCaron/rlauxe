package org.cryptobiotic.rlauxe.auditcenter

// merge the contests back together
// could ignore mvrComparisonFile and contestRoundFile (maybe)
//
// the main issue is getting Nc and Npop
//
// we have La Plata, Morgan, Weld CVR_export files
class Colorado2026PwithCvrs(ac:String?=auditcenter): ColoradoInput(
    generalCanonicalFile = "$ac/2026/primary/finalReports/CanonicalListOfContestsAndChoices.csv",
    contestRoundFile = "$ac/2026/primary/finalReports/ContestsListRound1.csv",
    tabulateCountyFile = "$ac/2026/primary/finalReports/CandidateVoteTotalsByCounty.csv",
    mvrComparisonFile = "$ac/2026/primary/finalReports/CVRtoAuditBoardInterpretationComparison.csv"
) {
    val parent = Colorado2026PMerged(ac)

    val useCounties = setOf("Boulder", "La Plata", "Morgan","Weld") // the counties we have cvrs for
    override fun skipCounties(countyName: String) = !useCounties.contains(countyName)

    // not needed
    val countyPopulations = mapOf( "Boulder" to 100423, "La Plata" to 16146, "Morgan" to 5220,"Weld" to 69640)

    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val ccmap = mutableMapOf<String, CanonicalContest>()
        parent.canonicalContests().values.forEach {
            val included = it.counties.any { useCounties.contains(it) }
            if (included) {
                val key = contestNameMerge(it.contestName)
                val cc = ccmap.getOrPut(key) { it }
                cc.counties.addAll(it.counties)
            }
        }
        ccmap.toSortedMap()
    }

    override fun roundContests(): Map<String, CorlaContestRoundCsv> = roundContests
    private val roundContests: Map<String, CorlaContestRoundCsv> by lazy {
        val crmap = mutableMapOf<String, CorlaContestRoundAccum>()
        parent.roundContests().values.forEach {
            if (canonicalContests.contains(it.contestName)) {
                val key = contestNameMerge(it.contestName)
                val cr = crmap.getOrPut(key) { CorlaContestRoundAccum(it) }
                cr.add( it )
            }
        }
        crmap.mapValues { it.value.build() }.toSortedMap()
    }

    override fun countyTabsAllContests(): Map<String, CountyTabAllContests> = countyTabsAllContests
    private val countyTabsAllContests: Map<String, CountyTabAllContests> by lazy {
        val ctmap = mutableMapOf<String, CountyTabAllContests>()
        parent.countyTabsAllContests().values.forEach { org ->
            if (useCounties.contains(org.countyName)) {
                val ct = ctmap.getOrPut(org.countyName) { CountyTabAllContests(org.countyName) }
                org.contests.values.forEach { ccvOrg ->
                    if (canonicalContests.contains(ccvOrg.contestName)) {
                        val key = contestNameMerge(ccvOrg.contestName)
                        val ccv = ct.contests.getOrPut(key) { CountyContestVotes(org.countyName, key) }
                        ccvOrg.choices.forEach { (choice, vote) ->
                            ccv.addChoice(choice, vote)
                        }
                    }
                }
            }
        }
        ctmap.toSortedMap()
    }

    override fun contestTabsAllCounties(): Map<String, ContestTabAllCounties> = contestTabsAllCounties
    private val contestTabsAllCounties: Map<String, ContestTabAllCounties> by lazy {
        val tabs = mutableMapOf<String, ContestTabAllCounties>()
        countyTabsAllContests().values.forEach { countyTabAllContests ->
            if (useCounties.contains(countyTabAllContests.countyName)) {
                countyTabAllContests.contests.forEach { (contestName, countyContestVotes) ->
                    if (canonicalContests.contains(contestName)) {
                        val tab = tabs.getOrPut(contestName) { ContestTabAllCounties(contestName) }
                        tab.add(countyTabAllContests.countyName, countyContestVotes)
                    }
                }
            }
        }
        tabs.toMap()
    }

    // data class CardComparisonResults(
    //    val contestMvrs: List<ContestMvrCount>,
    //    val countyMvrs: List<CountyMvrCount>,
    //    val stylesByCounty: List<CountyStylesFromMvrs>
    //)
    // data class ContestMvrCount(val contestName: String) {
    //    var countMvr = 0
    //    var countStatewide = 0
    //}
    override fun cardComparison(): CardComparisonResults = cardComparison
    private val cardComparison: CardComparisonResults by lazy {
        val org = parent.cardComparison()

        // accumulate mvr counts by Contest
        val mergedMvrs = mutableMapOf<String, ContestMvrCount>()
        org.contestMvrs.forEach { orgCount: ContestMvrCount ->
            val key = contestNameMerge(orgCount.contestName)
            val contestMvr = mergedMvrs.getOrPut(key) { ContestMvrCount(key) }
            contestMvr.countMvr += orgCount.countMvr
        }

        // convert contest names in countyStyles
        // data class CountyStylesFromMvrs(val countyName: String) {
        //    val styles = mutableMapOf<Set<String>, MvrStyle>()
        //    var cardCount = 0
        // data class MvrStyle(val id: Int, val contests: Set<String>) {
        //    var cardCount = 0
        //    override fun toString()= buildString {
        //        append("style $id has ${contests.size} contests cardCount=$cardCount")
        //    }
        val countyStyles = mutableListOf<CountyStylesFromMvrs>()
        org.stylesByCounty.forEach { orgCountyStyle ->
            val countyStyle = CountyStylesFromMvrs(orgCountyStyle.countyName)
            orgCountyStyle.styles.forEach { (orgContests, orgMvrStyle) ->
                val contests = orgContests.map { contestNameMerge(it)  }.toSet()
                countyStyle.add(contests)
            }
            countyStyles.add(countyStyle)
        }

        CardComparisonResults(mergedMvrs.values.toList(), org.countyMvrs, countyStyles)
    }

    // data class MergedInfo(
    //    val mergedContestInfo: List<MergedContestInfo>,
    //    val strataInfo: List<StrataInfo>,
    //    val statewideContests: List<CorlaContestRoundCsv>,
    //)

    override fun mergedInfo() = mergedInfo
    private val mergedInfo: MergedInfo by lazy {
        val contestTabs = contestTabsAllCounties()
        val orgInfo = mergeContestInfo(this)
        val fixContestInfo = orgInfo.mergedContestInfo.map { info ->
            val contestTab = contestTabs[info.contestName]!!
            var sumCards = 0
            contestTab.counties.forEach {
                sumCards += (countyPopulations[it] ?: 0)
            }
            info.copy(nc = sumCards) // replace Nc
        }
        orgInfo.copy(mergedContestInfo = fixContestInfo) // replace MergedInfo
    }

    override fun strataPopulation() = strataPopulation
    private val strataPopulation: Map<String, Int> by lazy { mergedInfo.strataInfo.associate { it.strataName to it.ballotCardCount } } // county name to population

    override fun contestNameCleanup(county: String, name: String): String {
        if (county == "La Plata" && name == "Secretary of State") return "Secretary of State - LBR"
        return parent.contestNameMerge(name)
    }

    fun contestNameMerge(name: String): String {
        return parent.contestNameMerge(name)
    }

    override fun candidateNameCleanup(county: String, name: String): String {
        return parent.candidateNameCleanup(county, name)
    }
}
