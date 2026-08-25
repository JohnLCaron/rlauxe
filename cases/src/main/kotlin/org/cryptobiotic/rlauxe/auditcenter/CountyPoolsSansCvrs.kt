package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CountyPools
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.findDiscreteMaximum
import org.cryptobiotic.rlauxe.util.nfz
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.String
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger("MakeCountyPools")

// cards are partitioned by county.
// We know Nc = the total number of cards for a Contest, the total number of cards for a County, and the vote subtotals by County.
// We dont know the styles, or the number of cards per contest per county.

class CountyPoolsSansCvrs(
    corlaContestBuilders: List<CorlaContestBuilder>,
    val coloradoInput: ColoradoInput,
    onlyCounty: String? = null
) {
    val builders = corlaContestBuilders.associateBy { it.info.name }
    val infos = corlaContestBuilders.associate { it.info.id to it.info }
    val countyPools: List<CorlaCountyPoolsBuilder>

    init {
        val infosByName = corlaContestBuilders.associate { it.info.name to it.info }

        val distributeNc: Map<String, Map<String, Int>> = distributeNc() // county -> contest -> Nc for that contest in that county

        val contestTabByCounty: Map<String, CountyTabAllContests> = if (onlyCounty == null)
            coloradoInput.countyTabsAllContests()
        else
            mapOf(onlyCounty to coloradoInput.countyTabsAllContests()[onlyCounty]!!)

        val mvrStylesMap: Map<String, CountyStylesFromMvrs> = coloradoInput.stylesFromMvrs.associateBy { it.countyName }

        // the mvr styles are not complete. This seriously sucks.
        // pick out the contests that dont have styles that contain it
        val missingContestsByCounty =
            mutableMapOf<String, MutableList<CountyContestVotes>>() // countyName -> contestTab
        contestTabByCounty.map { (countyName, countyContest) ->
            val mvrStyles: CountyStylesFromMvrs = mvrStylesMap[countyName]!!
            countyContest.contests.forEach { (contestName, contestTab) ->
                val stylesForContest: List<MvrStyle> =
                    mvrStyles.styles.values.filter { it: MvrStyle -> it.contests.contains(contestName) }
                if (stylesForContest.isEmpty()) {
                    val missingStyles = missingContestsByCounty.getOrPut(countyName) { mutableListOf() }
                    missingStyles.add(contestTab)
                }
            }
        }

        countyPools = contestTabByCounty.filter { !coloradoInput.skipCounties(it.key) }
            .map { (countyName, countyContest) ->
                CorlaCountyPoolsBuilder(
                    countyName, countyContest, mvrStylesMap[countyName]!!,
                    distributeNc[countyName]!!,
                    infosByName,
                    coloradoInput
                )
            }

    }

    // for each contest, distribte Nc to the counties it is in, proportional to votesInCounty / totalVotes
    // but clipped at the county population
    fun distributeNc(): Map<String, Map<String, Int>> { // county -> contest -> Nc
        val countyNc = mutableMapOf<String, MutableMap<String, Int>>() // county -> contest -> Nc
        coloradoInput.contestTabsAllCounties().values.forEach { contestTabAllCounties ->
            val contestName = contestTabAllCounties.contestName
            val contestTotalVotes = contestTabAllCounties.sumVotes()
            val builder = builders[contestName]
            if (builder != null) {
                contestTabAllCounties.countyVotes.forEach { (countyName, countyVotes) ->
                    val countyContest = countyNc.getOrPut(countyName) { mutableMapOf() }
                    val fac = countyVotes / contestTotalVotes.toDouble()
                    countyContest[contestName] = (builder.Nc * fac).roundToInt()
                }
            }
        }

        //  consistency check
        // sum over counties to get the contest sum
        val contestSum = mutableMapOf<String, Int>()
        countyNc.forEach { (_, countyVotes) ->
            countyVotes.forEach { contestName, contestVotes ->
                val contestAccum = contestSum.getOrDefault(contestName, 0)
                contestSum[contestName] = contestAccum + contestVotes
            }
        }

        coloradoInput.contestTabsAllCounties().values.forEach { contestTabAllCounties ->
            val contestName = contestTabAllCounties.contestName
            val sum = contestSum[contestName]!!
            val builder = builders[contestName]!!
            val contestNc = builder.Nc
            if (abs(contestNc - sum) > 5)
                logger.warn { "makeCardPoolsFromCountyStyles has (contestNc-sum) ${abs(contestNc - sum)} > 5" }
        }
        return countyNc
    }
}

// we only know votes in the county, not ncards or undervotes.
// we know total contest Nc across counties
// look across all counties that have that contest and divide Nc in proportion to countyContest.totalVotes

// each countyStyle generates a Pool
// we have county styles and subtotals, which get distributed to the various county styles in (rough) proportion to their cardCount.
// as usual, we dont know the undervotes, so we will distribute that also in proportion

data class CorlaCountyPoolsBuilder(
    val countyName: String,
    val cct: CountyTabAllContests, // the votes subtotal for each contest in the county
    val mvrStyles: CountyStylesFromMvrs, // Set<contestId> and reletive count within county
    val contestNc: Map<String, Int>, // contest name -> contest Nc for the county
    val infos: Map<String, ContestInfo>, // contest name -> ContestInfoval
    val coloradoInput: ColoradoInput,
) {
    val pools = mutableListOf<CardPool>() // each style gets its own pool

    init {
        val strata = coloradoInput.strataMap[countyName]
        if (strata == null) {
            logger.warn{ "No strata info for $countyName"}
        }
        val countyPopulation = strata?.ballotCardCount ?: 9999 // TODO

        // class Solver(mvrStyles: List<MvrStyle>, contests: List<CountyContestVotes>, val totalCards: Int) {
        val styler =
            CorlaStyleCardAllocation(countyName, mvrStyles.styles.values.toList(), cct.contests.values.toList(), contestNc, countyPopulation)
        styler.allocate()

        // sum of ncards of Style's that contain this contest
        val totalCardsForContestMap = mutableMapOf<CorlaStyleCardAllocation.Contest, Int>()
        styler.allStyles.forEach { style ->
            style.contests.forEach { contest ->
                var totalCardsForContest = totalCardsForContestMap.getOrDefault(contest, 0)
                totalCardsForContestMap[contest] = totalCardsForContest + style.ncards()
            }
        }

        val contestPcts = mutableMapOf<String, Double>() // checker

        // each style gets its own pool
        styler.allStyles.forEach { style: CorlaStyleCardAllocation.Style ->
            val votesForStyle = mutableMapOf<Int, ContestTabulation>()

            style.contests.forEach { contest: CorlaStyleCardAllocation.Contest ->
                val contestName = contest.name
                val info = infos[contestName]
                if (info == null)
                    throw Exception("cant find $contestName")

                // divide up the votes among Styles in proportion to mvrStyles.ncards
                val denom = totalCardsForContestMap[contest]!!
                val stylePct = if (denom == 0) 0.0 else style.ncards() / denom.toDouble()
                val contestPct = contestPcts.getOrDefault(contestName, 0.0)
                contestPcts[contestName] = contestPct + stylePct

                val votes = mutableMapOf<Int, Int>() // this contest
                val contestTab = cct.contests[contestName]!!
                contestTab.choices.forEach { (choiceName, choiceVote) ->
                    val candId = info.candidateNames[choiceName]
                    if (candId != null) { // might be write in
                        votes[candId] = (stylePct * choiceVote).roundToInt() // scale by stylePct
                    }
                }
                // needs to be adjusted across the styles in proportion to how many cards used it
                val Nc = contestNc[contestName]!!
                val ncards = (stylePct * Nc).roundToInt() // scale by stylePct

                votesForStyle[info.id] = ContestTabulation(info, votes, ncards)
            }

            nextPoolId++
            val pool = CardPool(
                "${countyName}-${nfz(style.id, 2)}", nextPoolId,
                hasExactContests = true, infos.mapKeys { it.value.id }, contestTabs = votesForStyle, style.ncards()
            )
            pools.add(pool)
        }

        // check
        contestPcts.forEach { contestName, pct ->
            if (!doubleIsClose(pct, 1.0))
                logger.warn { "$contestName sum of style pctTotal ${pct} != 1.0" }
        }
    }

    fun build(): CountyPools {
        // for each contest
        val tabs = cct.contests.map { (name, countyContestVotes) ->
            val info = infos[name]!!
            val ncards = contestNc[name] ?: 0
            val canonicalContest = coloradoInput.canonicalContests()[name]!!
            countyContestVotes.makeContestTabulation(canonicalContest, info, ncards)
        }.associateBy { it.contestId }

        // we dont know the actual number of cards, we only know the candidate counts
        // if you change ncards, you change undervotes...
        val totalCards = pools.sumOf { it.ncards() }

        return CountyPools(countyName, countyPoolId++, contestTabs = tabs, styles = pools, cardCount = totalCards)
    }

    companion object {
        var nextPoolId = 0
        var countyPoolId = 1
    }
}


// data class MvrStyle(val id: Int, val contests: Set<String>) {
//    var cardCount = 0
// data class CountyContestVotes(val contestName: String) {
//    fun contestVotes() = choices.values.sumOf { it }

// TODO pass in Ncards[contest] for this pool, and use that, not contestVotes = countyContest.contestVotes()
class CorlaStyleCardAllocation(val countyName: String, mvrStyles: List<MvrStyle>, contests: List<CountyContestVotes>,
                               val contestNc: Map<String, Int>, val cardsinCountyPool: Int) {
    val show = false
    val allContests : List<Contest> = contests.map{ Contest(it) }
    val allStyles : List<Style>
    val mvrTotal = mvrStyles.sumOf { it.cardCount }

    var nextContestId = 0
    var nextStyleId = 0
    var totalCards = 0

    init {
        if (countyName == "Lake")
            print("")
        val styles = mutableListOf<Style>()

        // all contests not contained in a style are put into a single "missingStyle"
        val missingContests = mutableListOf<Contest>()
        allContests.forEach { contest ->
            val stylesForContest: List<MvrStyle> = mvrStyles.filter { it: MvrStyle -> it.contests.contains(contest.name) }
            if (stylesForContest.isEmpty()) {
                missingContests.add(contest)
            }
        }
        if (missingContests.isNotEmpty()) {
            val missingStyle = Style(missingContests, 0)
            styles.add(missingStyle)
            missingContests.forEach { missingStyle.setMin(it.contestVotes) }
        }

        mvrStyles.forEach{ styles.add(Style(it)) }
        allStyles = styles
    }

    inner class Contest(countyContest: CountyContestVotes) {
        val name = countyContest.contestName
        val id = nextContestId++
        val contestCards = contestNc[countyContest.contestName]!!
        val contestVotes = countyContest.contestVotes()

        init {
            if (contestVotes > contestCards) {
                logger.warn{" county $countyName has contestVotes $contestVotes > $contestCards contestCards"}
            }
        }

        fun hasVotes(): Int {
            return allStyles.filter { it.contests.contains(this) }.sumOf { it.ncards() }
        }

        // positive if it needs cards, negetive if it has more cards than it needs to satisfy its vote count
        fun need(): Int {
            return contestVotes - hasVotes()
        }

        fun undervotePct() = (hasVotes() - contestVotes)  / contestVotes.toDouble()

        var hasVotes = 0
        fun needs() = max(0, contestVotes - hasVotes)
        fun benefit(nvotes: Int): Int { // increases the votes used - overvotes added
            return if (nvotes < needs()) nvotes else 2 * needs() - nvotes
        }

        override fun toString(): String {
            return "Contest($id, name='$name', contestCards=$contestCards, contestVotes=$contestVotes, need=${need()} undervotePct=${undervotePct()})"
        }
    }

    inner class Style(val contests: List<Contest>, mvrCount: Int) {
        val mvrPct = mvrCount / mvrTotal.toDouble()
        val id = nextStyleId++
        val contestV = allContests.map { if (contests.contains(it)) 1 else 0 }

        var minCards = 0  // minimum number of cards, eg from singletons
        var optCards = 0  // extra cards that can be adjusted

        constructor(mvrStyle: MvrStyle) : this(allContests.filter { mvrStyle.contests.contains(it.name)}, mvrStyle.cardCount )

        fun ncards() = minCards + optCards

        fun contestIds(): List<Int> = contests.map { it.id }

        fun setMin(minCards: Int) {
            this.minCards = max(this.minCards, minCards)
        }

        fun scalarMult(v: List<Int>): Int {
            require (v.size == contestV.size)
            return contestV.mapIndexed{ idx, cv -> v[idx] * cv }.sum()
        }

        // search for ncards with maximum benefit
        fun optNCards(): Pair<Int, Double> {
           val optNCards = findDiscreteMaximum(0, cardsinCountyPool) { ncards -> benefit(ncards) }
            return Pair(optNCards, benefit(optNCards))
        }

        fun benefit(nvotes: Int): Double {
            return contests.sumOf{ it.benefit(nvotes) }.toDouble()
        }

        override fun toString(): String {
            return "Style($id, contests=${contests.map{it.id}}, ncards=${ncards()})"
        }
    }

    /*
    fun addCards(style: Style, ncards: Int, ) {
        style.contests.forEach { it.hasVotes += ncards }
        style.ncards += ncards
        totalCards += ncards
    } */

    fun allocate() {
        // find contests that are only included in one style
        allContests.forEach { contest ->
            val useBy = allStyles.filter { it.contests.contains(contest) }
            if (useBy.size == 1) {
                val singletonStyle = useBy.first()
                singletonStyle.setMin(contest.contestCards)
            }
        }

        // how many cards we have to distribute
        val extraCards = cardsinCountyPool - allStyles.sumOf{ it.minCards }

        // start by allocating in proportion to mvrCount
        allStyles.forEach{ it.optCards = roundToClosest(it.mvrPct * extraCards) }
        val check = allStyles.sumOf{ it.optCards }
        val check2 = allStyles.sumOf{ it.minCards }

        var show = false
        if (countyName == "Lake") {
            println("County $countyName")
            println("Contests")
            allContests.forEach { println(" Contest(${it.id}, need= ${it.need()}, uvPct= ${df(it.undervotePct())}, ${it.name})") }
            println("Styles")
            allStyles.forEach { println(" Style(${it.id}, ncontests=${it.contests.size}, minCards= ${it.minCards} optCards= ${it.optCards} ${it.contestIds()})") }
            println()
            // show = true
        }

        /*
        val transferAtaTime = 10
        var iterLimit = 10

        while (iterLimit > 0) {
            val beforeNeedV = allContests.map { it.need() }
            val beforeScores =  allStyles.map{ it.scalarMult(beforeNeedV) }

            val fromStyleIdx = beforeScores.withIndex().minBy { it.value }.index
            val fromStyle = allStyles[fromStyleIdx]
            val fromStyleScore = beforeScores[fromStyleIdx]

            val toStyleIdx = beforeScores.withIndex().maxBy { it.value }.index
            val toStyle = allStyles[toStyleIdx]
            val toStyleScore = beforeScores[toStyleIdx]
            toStyle.optCards += transferAtaTime
            fromStyle.optCards -= transferAtaTime

            val afterNeedV = allContests.map { it.need() }
            val afterScores =  allStyles.map{ it.scalarMult(afterNeedV) }

            if (show) {
                println(" before contest.need $beforeNeedV")
                println(" before style scores $beforeScores")
                println(" fromStyleIdx $fromStyleIdx, fromStyleScore $fromStyleScore")
                println(" toStyleIdx $toStyleIdx, toStyleScore $toStyleScore")
                println()

                println("contest need")
                println(" idx, before, after, diff")
                beforeNeedV.forEachIndexed { i, it ->
                    println(" $i, $it, ${afterNeedV[i]}, ${it - afterNeedV[i]}")
                }

                println("\nstyle score")
                println(" idx, before, after, diff")
                beforeScores.forEachIndexed { i, it ->
                    println(" $i, $it, ${afterScores[i]}, ${it - afterScores[i]}")
                }
                println()
            }

            iterLimit--
        } */

        /* TODO optNCards is too agressive in that it favors large styles, and smaller ones are starved.
        // each style gets one shot with optNCards
        var need = 1
        val useStyles = mutableSetOf<Style>()
        useStyles.addAll(allStyles)
        if (show) println("oneshot")
        while (useStyles.isNotEmpty() && need > 0) {
            val styleBenefits = useStyles.map {
                val (optNCards, optBenefit) = it.optNCards()
                Triple(it, optNCards, optBenefit)
            }
            val maxBenefits = styleBenefits.maxBy { it.third }
// break ties ?
            need = maxBenefits.second
            if (need > 0) addCards(maxBenefits.first, maxBenefits.second)
            val worked = useStyles.remove(maxBenefits.first)
        }

        // freeforall
        if (show) println("freeforall")
        need = 1
        while (need > 0) {
            val maxBenefits = allStyles.map {
                val (optNCards, optBenefit) = it.optNCards()
                Triple(it, optNCards, optBenefit)
            }.maxBy { it.third }

            need = maxBenefits.second
            if (need > 0) addCards(maxBenefits.first, maxBenefits.second)
        }

        // distribute overvotes
        val overvotes = population - totalCards
        val denom = totalCards.toDouble()
        if (overvotes > 0) {
            if (show) println("overvotes $overvotes")
            allStyles.forEach { style ->
                val frac = style.ncards / denom
                addCards(style, roundToClosest(frac * overvotes))
            }
        } */

        if (show) {
            println("$countyName: totalCards=$totalCards population = $cardsinCountyPool diff=${totalCards - cardsinCountyPool}")
            allStyles.forEach { println(it) }
        }
    }
}

/*
data class CountyPoolsBuilderOld(
    val countyName: String,
    val cct: CountyTabAllContests, // the votes subtotal for each contest in the county
    val mvrStyles: CountyStylesFromMvrs, // Set<contestId> and reletive count within county
    val missingPool: CardPoolBuilder?, // all the contests that werent in an mvrStyle TODO just their ids ??
    val contestNc: Map<String, Int>, // contest name -> contest Nc for the county
    val infos: Map<String, ContestInfo>, // contest name -> ContestInfoval
    val coloradoInput: ColoradoInput,
) {
    val adjContestNc = contestNc //    TODO style specific ??  .mapValues { it.value - missingNcards }
    val pools = mutableListOf<CardPoolBuilder>()

    init {
        if (missingPool != null)
            pools.add( missingPool)

        val total = mvrStyles.styles.values.sumOf { it.cardCount }
        if (total != mvrStyles.cardCount)
            logger.warn { "total != countyStyles.cardCount"}

        val contestPcts = mutableMapOf<String, Double>() // check

        // TODO use contestNc when contest is contained - likely for the case of missing contests

        if (countyName == "Pitkin")
            print("")

        // divide up the votes among mvrStyles in proportion to mvrStyles.cardCount (WRONG)
        mvrStyles.styles.values.forEach { style: MvrStyle ->
            val votesForStyle = mutableMapOf<Int, ContestTabulation>()

            style.contests.forEach { contestName: String ->
                // the denominator is sum of cardCounts of Style's that contain this contest; could do once above
                val totalCardsForContest = mvrStyles.styles.values.filter{ it.contests.contains(contestName) }.sumOf{ it.cardCount }
                val stylePct = style.cardCount / totalCardsForContest.toDouble()
                val contestPct = contestPcts.getOrDefault(contestName, 0.0)
                contestPcts[contestName] = contestPct + stylePct

                val info = infos[contestName]
                if (info == null)
                    throw Exception("cant find $contestName")
                val votes = mutableMapOf<Int, Int>() // this contest
                val contestTab = cct.contests[contestName]!!
                contestTab.choices.forEach { (choiceName, choiceVote) ->
                    val candId = info.candidateNames[choiceName]
                    if (candId != null) { // might be write in
                        votes[candId] = (stylePct * choiceVote).roundToInt() // scale by stylePct
                    }
                }
                // needs to be adjusted across the styles in proportion to how many cards used it
                val Nc = adjContestNc[contestName]!!  // total Nc for this contest over all styles
                val ncards = (stylePct * Nc).roundToInt() // scale by stylePct

                votesForStyle[info.id] = ContestTabulation(info, votes, ncards)
            }

            nextPoolId++
            pools.add( CardPoolBuilder.fromMinCardsNeeded( "${countyName}-${nfz(style.id,2)}", nextPoolId,
                hasExactContests = true, infos.mapKeys { it.value.id }, contestTabs=votesForStyle))
        }

        // check
        contestPcts.forEach { contestName, pct ->
            if (!doubleIsClose(pct, 1.0))
                logger.warn { "$contestName sum of style pctTotal ${pct} != 1.0"}
        }
    }

    fun build(): CountyPools {
        // for each contest
        val tabs = cct.contests.map { (name, countyContestVotes) ->
            val info = infos[name]!!
            val ncards = contestNc[name] ?: 0
            val canonicalContest = coloradoInput.canonicalContests()[name]!!
            countyContestVotes.makeContestTabulation(canonicalContest, info, ncards)
        }.associateBy { it.contestId }

        // we dont know the actual number of cards, we only know the candidate counts
        // if you change ncards, you change undervotes...
        val totalCards = pools.sumOf { it.ncards() }

        return CountyPools(countyName, countyPoolId++,
            contestTabs = tabs, styles = pools.map { it.build() }, cardCount = totalCards)
    }

    companion object {
        var nextPoolId = 0
        var countyPoolId = 1
    }
}

// TODO may not need to be a pool, just something to adjust the style counts
// TODO same as OneAuditPoolBuilder

// a pool of cards based on a card style
// we dont actually know ncards - initial estimate from voteTotals, then can adjust cards
data class AdjustableStylePool(
    val countyName: String,
    override val poolName: String,
    override val poolId: Int,
    val hasExactContests: Boolean,
    val infos: Map<Int, ContestInfo>,
    val contestTabs: Map<Int, ContestTabulation>, // contestId -> candidateId -> nvotes; must include contests and candidates with no votes
): CardPoolIF {

    // val minCardsNeeded = mutableMapOf<Int, Int>() // TODO do we need to save this beyonf init ?
    val maxMinCardsNeeded: Int
    private var adjustCards = 0 // adjusted number of cards, using distributeExpectedOvervotes() on one or more contests

    init {
        val minCardsNeeded = mutableMapOf<Int, Int>()
        contestTabs.forEach { (contestId, contestTab) ->
            val ncards = contestTab.ncards() // nvotes was scaled by stylePct
            val info = infos[contestId]!!
            // based on the contest's votes, you need at least this many cards for this contest
            minCardsNeeded[contestId] = roundUp(ncards.toDouble() / info.voteForN)
        }
        if (minCardsNeeded.size == 0)
            print("")
        // you need at least this many cards for this pool
        val fromMaxMin = minCardsNeeded.values.max()
        // val fromTabs = voteTotals.values.maxOf { it.nvotes() }
        maxMinCardsNeeded = fromMaxMin
    }

    override fun name() = poolName
    override fun id() = poolId
    override fun hasExactContests() = hasExactContests

    override fun hasContest(contestId: Int) = contestTabs.contains(contestId)
    override fun possibleContests() = contestTabs.map { it.key }.toSortedSet().toIntArray()

    override fun ncards() = (maxMinCardsNeeded + adjustCards)

    // modifying ncards just changes the undervotes
    fun adjustCards(adjust: Int, contestId : Int) {
        if (!hasContest(contestId)) throw RuntimeException("NO CONTEST")
        adjustCards = max( adjust, adjustCards)
    }

    override fun contestTab(contestId: Int) = contestTabs[contestId]

    override fun votesAndUndervotes(contestId: Int): Vunder {
        val contestTab = contestTabs[contestId]!!
        return contestTab.votesAndUndervotes(poolId, ncards(), hasExactContests)
    }

    override fun toString(): String {
        return "AdjustableStylePool(poolName='$poolName', poolId=$poolId, #contests=${contestTabs.size}, maxMinCardsNeeded=$maxMinCardsNeeded)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdjustableStylePool) return false

        if (poolId != other.poolId) return false
        if (hasExactContests != other.hasExactContests) return false
        if (maxMinCardsNeeded != other.maxMinCardsNeeded) return false
        if (adjustCards != other.adjustCards) return false
        if (poolName != other.poolName) return false
        if (contestTabs != other.contestTabs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = poolId
        result = 31 * result + hasExactContests.hashCode()
        result = 31 * result + maxMinCardsNeeded
        result = 31 * result + adjustCards
        result = 31 * result + poolName.hashCode()
        result = 31 * result + contestTabs.hashCode()
        return result
    }
} */

/* ======================================
contest.csv

District Court Judge - 5th Judicial District - Granger,opportunistic_benefits,in_progress,1,88150,58756,"""Yes""",25669,0.04000000,0,0,0,0,0,0,0,1.03905000,0,23,23



What we know
1. Nc = 58756 from contest.csv:

   District Court Judge - 5th Judicial District - Granger,opportunistic_benefits,in_progress,1,88150,58756,"""Yes""",25669,0.04000000,0,0,0,0,0,0,0,1.03905000,0,23,23

2. The vote count for each contest in each County, from tabulate_county.csv:

  Clear Creek,District Court Judge - 5th Judicial District - Granger,Yes,3937
    Clear Creek,District Court Judge - 5th Judicial District - Granger,No,1300

    Eagle,District Court Judge - 5th Judicial District - Granger,Yes,17813
    Eagle,District Court Judge - 5th Judicial District - Granger,No,4425

    Lake,District Court Judge - 5th Judicial District - Granger,Yes,2192
    Lake,District Court Judge - 5th Judicial District - Granger,No,926

    Summit,District Court Judge - 5th Judicial District - Granger,Yes,11120
    Summit,District Court Judge - 5th Judicial District - Granger,No,2742

distribute cards in the same proportion as votes to the county pools (np)

58756  * (5237/44455) = 6922 = Ncards(Contest, County)
58756  * (3118/44455) = 4121
...

Ideally we could solve for how many cards each style in the pool has.
But we cant solve that equation because we dont even know what the styles actually are.

So we estimate the styles for the county, and estimate the number of cards each style has Ncards(Style, County), subject to constraint:

    Sum(cards(Style, County))) >=

So we let the number of cards for a contest in the county pool Ncards(Contest, County)  not agree with  Ncp(county)


But we have to insist that Ncards(Contest, County) >=  Nvotes(Contest, County), so that we have enough cards to exhaust the votes in the cvrs for that pool.
ie  NVotes(Contest, County) cvrs == ac. So let the undervotes be different.

when we look at this table:

         county 	auditcenter   	cvrs
                		ncards nvotes 	ncards nvotes
    Clear Creek   	  6922      5237   	6608   4994
          Eagle  	29392    22238  	34639  22238
           Lake   	  4121      3118   	4010   3038
         Summit  	18321    13862  	18217  13774
          Total 		 58756   44455  	63474  44044

we see cvr ncards >= ac ncards, but nvotes doesnt agree.
we must be setting undervotes wrong  in vunderpools ?


 */