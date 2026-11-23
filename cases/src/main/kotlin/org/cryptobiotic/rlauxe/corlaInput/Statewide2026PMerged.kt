package org.cryptobiotic.rlauxe.corlaInput

import org.cryptobiotic.rlauxe.auditcenter.CanonicalContest
import org.cryptobiotic.rlauxe.auditcenter.CardComparisonResults
// import org.cryptobiotic.rlauxe.auditcenter.ContestMvrCount
import org.cryptobiotic.rlauxe.auditcenter.ContestTabAllCounties
import org.cryptobiotic.rlauxe.auditcenter.CorlaContestRoundAccum
import org.cryptobiotic.rlauxe.auditcenter.CorlaContestRoundCsv
import org.cryptobiotic.rlauxe.auditcenter.CountyContestVotes
import org.cryptobiotic.rlauxe.auditcenter.CountyStylesFromMvrs
import org.cryptobiotic.rlauxe.auditcenter.CountyTabAllContests
import org.cryptobiotic.rlauxe.co.auditcenter.TabulateCounty
import org.cryptobiotic.rlauxe.co.input.StrataInfo
import org.cryptobiotic.rlauxe.util.Locations.auditcenter

import kotlin.collections.forEach

// merge the contests back together
// could ignore mvrComparisonFile and contestRoundFile (maybe)
// the main issue is getting Nc and Npop
// we have La Plata, Morgan, Weld CVR_export files
open class Statewide2026PMerged(ac:String?=auditcenter): Statewide2026Primary(ac) {
    override val year = "2026pm"

    override fun skipCounties(countyName: String) = false

    // ?? override fun corlaCountyInput(countyName: String): CountyInput? = null

    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val ccmap = mutableMapOf<String, CanonicalContest>()
        super.canonicalContests().values.forEach {
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

    override fun roundContests(): Map<String, CorlaContestRoundCsv> = roundContests
    private val roundContests: Map<String, CorlaContestRoundCsv> by lazy {
        val crmap = mutableMapOf<String, CorlaContestRoundAccum>()
        super.roundContests().values.forEach {
            val key = contestNameMerge(it.contestName)
            val cr = crmap.getOrPut(key) { CorlaContestRoundAccum(it) }
            cr.add( it )
        }
        crmap.mapValues { it.value.build() }.toSortedMap()
    }

    // dunno if this is worth the trouble ...
    override fun countyTabsAllContests(): Map<String, Map<String, TabulateCounty>> = countyTabsAllContests
    private val countyTabsAllContests: Map<String, Map<String, TabulateCounty>> by lazy {
        val ctmap = mutableMapOf<String, MutableMap<String, TabulateCounty>>()
        TabulateCounty.readFromFile(tabulateCountyFile, this ).forEach { (county, orgContestMap) ->
            val contestMap = ctmap.getOrPut(county) { mutableMapOf<String, TabulateCounty>() }
            orgContestMap.forEach { (contestName, contestTab) ->
                val key = contestNameMerge(contestName)
                val ccv = contestMap.getOrPut(key) { TabulateCounty(contestName, key) }
                contestTab.choices.forEach { (choice, vote) ->
                    ccv.choices[choice] = vote
                }
            }
        }
        ctmap.toSortedMap()
    }

    /*
    override fun countyTabsAllContests1(): Map<String, CountyTabAllContests> = countyTabsAllContests
    private val countyTabsAllContests1: Map<String, CountyTabAllContests> by lazy {
        val ctmap = mutableMapOf<String, CountyTabAllContests>()
        super.countyTabsAllContests().values.forEach { org ->
            val ct = ctmap.getOrPut(org.countyName ) { CountyTabAllContests(org.countyName) }
            org.contests.values.forEach { ccvOrg ->
                val key = contestNameMerge(ccvOrg.contestName)
                val ccv = ct.contests.getOrPut(key) { CountyContestVotes(org.countyName, key) }
                ccvOrg.choices.forEach { (choice, vote) ->
                    ccv.addChoice(choice, vote)
                }
            }
        }
        ctmap.toSortedMap()
    } */

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
        val org = super.cardComparison()

        /* accumulate mvr counts by Contest
        val mergedMvrs = mutableMapOf<String, ContestMvrCount>()
        org.contestMvrs.forEach { orgCount: ContestMvrCount ->
            val key = contestNameMerge(orgCount.contestName)
            val contestMvr = mergedMvrs.getOrPut(key) { ContestMvrCount(key) }
            contestMvr.countMvr += orgCount.countMvr
        } */

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

        CardComparisonResults(org.countyMvrs, org.mvrs, countyStyles)
    }

    /* override fun mergedInfo() = mergedInfo
    private val mergedInfo: MergedInfo by lazy {
        val orgInfo = mergeContestInfo(this)
        val orgStrataInfo = orgInfo.strataInfo
        //     val strataName: String,
        //    val nmvrs: Int, // countyMvr.countMvr
        //    val ballotCardCount: Int,  // round.ballot_card_count
        val broomfield = StrataInfo("Broomfield", 0, 0, 22906) // nmvrs ??
        orgInfo.copy(strataInfo = orgStrataInfo + listOf(broomfield))
    }

    override fun strataPopulation() = strataPopulation
    private val strataPopulation: Map<String, Int> by lazy { mergedInfo().strataInfo.associate { it.strataName to it.population } } // county name to population
    */

    override fun contestNameCleanup(county: String, name: String): String {
        return contestNameMerge(name)
    }

    fun contestNameMerge(contestName: String): String {
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
