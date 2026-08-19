package org.cryptobiotic.rlauxe.auditcenter

// merge the contests back together
// could ignore mvrComparisonFile and contestRoundFile (maybe)
//
// the main issue is getting Nc and Npop
//
// we have La Plata, Morgan, Weld CVR_export files
class Colorado2026PMerged(ac:String?=auditcenter): ColoradoInput(
    generalCanonicalFile = "$ac/2026/primary/finalReports/CanonicalListOfContestsAndChoices.csv",
    contestRoundFile = "$ac/2026/primary/finalReports/ContestsListRound1.csv",
    tabulateCountyFile = "$ac/2026/primary/finalReports/CandidateVoteTotalsByCounty.csv",
    mvrComparisonFile = "$ac/2026/primary/finalReports/CVRtoAuditBoardInterpretationComparison.csv"
) {
    override val skipCounties = listOf<String>()
    val parent = Colorado2026Primary(ac)

    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val ccmap = mutableMapOf<String, CanonicalContest>()
        parent.canonicalContests().values.forEach {
            val key = contestNameMerge(it.contestName)
            val cc = ccmap.getOrPut(key) { it }
            cc.counties.addAll( it.counties )
        }
        ccmap.toSortedMap()
    }

    // contest_name,audit_reason,random_audit_status,winners_allowed,ballot_card_count,contest_ballot_card_count,winners,min_margin,risk_limit,audited_sample_count,two_vote_over_count,one_vote_over_count,zero_discrepancy_count,one_vote_under_count,two_vote_under_count,disagreement_count,gamma,overstatements,optimistic_samples_to_audit,estimated_samples_to_audit
    // Adams County Assessor - DEM,opportunistic_benefits,not_auditable,1,95537,64356,"""Thomas Swingle""",0,0.03000000,0,0,0,0,0,0,0,1.03905000,0,0,0
    // Adams County Clerk and Recorder - DEM,opportunistic_benefits,not_auditable,1,95537,64356,"""Josh Zygielbaum""",0,0.03000000,0,0,0,0,0,0,0,1.03905000,0,0,0
    // Adams County Clerk and Recorder - REP,opportunistic_benefits,not_auditable,1,95537,30876,"""Karen Hoopes""",0,0.03000000,0,0,0,0,0,0,0,1.03905000,0,0,0
    // data class CorlaContestRoundCsv(
    //    val contestName: String,
    //    val auditReason: AuditReason,
    //    val nwinners: Int,
    // SUM val ballotCardCount: Int,         // population size = eg county size when uniform audit
    // SUM val contestBallotCardCount: Int,  // Nc = number of cards with this contest on it
    //    val winners: String,
    //    val minMargin: Int,
    //    val riskLimit: Double,  // TODO use this
    //    val gamma: Double,      // and this ?? = 1.03905000
    //    val optimisticSamplesToAudit: Int, // check if these ever differ
    //    val estimatedSamplesToAudit: Int,
    //)
    override fun roundContests(): Map<String, CorlaContestRoundCsv> = roundContests
    private val roundContests: Map<String, CorlaContestRoundCsv> by lazy {
        val crmap = mutableMapOf<String, CorlaContestRoundAccum>()
        parent.roundContests().values.forEach {
            val key = contestNameMerge(it.contestName)
            val cr = crmap.getOrPut(key) { CorlaContestRoundAccum(it) }
            cr.add( it )
        }
        crmap.mapValues { it.value.build() }.toSortedMap()
    }

    override fun countyTabsAllContests(): Map<String, CountyTabAllContests> = countyTabsAllContests
    private val countyTabsAllContests: Map<String, CountyTabAllContests> by lazy {
        val ctmap = mutableMapOf<String, CountyTabAllContests>()
        parent.countyTabsAllContests().values.forEach { org ->
            val ct = ctmap.getOrPut(org.countyName ) { CountyTabAllContests(org.countyName) }
            org.contests.values.forEach { ccvOrg ->
                val key = contestNameMerge(ccvOrg.contestName)
                val ccv = ct.contests.getOrPut(key) { CountyContestVotes(key) }
                ccvOrg.choices.forEach { (choice, vote) ->
                    ccv.addChoice(choice, vote)
                }
            }
        }
        ctmap.toSortedMap()
    }

    override fun contestTabsAllCounties(): Map<String, ContestTabAllCounties> = contestTabsAllCounties
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

    private fun contestNameMerge(contestName: String): String {
        var name = merge(contestName, "Attorney General - DEM")
        name = merge(name, "Attorney General - REP")
        name = merge(name, "Governor - DEM")
        name = merge(name, "Governor - REP")
        name = merge(name, "Regent of the University of Colorado - Congressional District 2 - DEM")
        name = merge(name, "Regent of the University of Colorado - Congressional District 2 - REP")
        name = merge(name, "Regent of the University of Colorado - Congressional District 7 - REP")
        name = merge(name, "Representative to the 120th United States Congress - District 2 - REP")
        name = merge(name, "Representative to the 120th United States Congress - District 3 - DEM")
        name = merge(name, "Representative to the 120th United States Congress - District 3 - REP")
        name = merge(name, "Secretary of State - DEM")
        name = merge(name, "State Representative - District 19 - DEM")
        name = merge(name, "State Representative - District 33 - DEM")
        name = merge(name, "State Senator - District 21 - DEM")
        return name
    }

    fun merge(contest: String, root: String): String {
        return if (contest.startsWith(root)) root else contest
    }
}
