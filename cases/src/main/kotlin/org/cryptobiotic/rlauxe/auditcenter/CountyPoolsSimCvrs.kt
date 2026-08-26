package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CountyPools
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.df
import org.cryptobiotic.rlauxe.util.doubleIsClose
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.nfz
import org.cryptobiotic.rlauxe.util.roundToClosest
import kotlin.String
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger("CountyPoolsSimCvrs")

//// TODO break out of Corla, probably move to core
// TODO anticipate knowing styles or Nc(county, contest)

// cards are partitioned by county; make a CountyPools for each County; cvrs are generated independently for each CountyPools.
// generate cvrs with the constraint that they must agree with county subtotals and county ncards.

// We know Nc = the total number of cards for a Contest, the total number of cards for a County, and the vote subtotals by County.
// We dont know the styles, or the number of cards per contest per county.

class CountyPoolsSimCvrs(
    val infos: Map<String, ContestInfo>,
    val contestNcs: Map<String, Int>, // contest name -> contest Nc
    val countyPopulations: Map<String, Int>, // county name -> county ncards
    val countiesTabs: Map<String, CountyTabAllContestsIF>, // county -> CountyTabAllContests2
    val contestsTabs: Map<String, ContestTabAllCountiesIF>, // contest name -> CountyTabAllContests2
    val mvrStylesMap: Map<String, List<MvrStyle>>, // // county -> List<MvrStyle>
    val choiceMapper: (String, String, String) -> String, // TODO van we get rid of ??
    val onlyContest: String? = null
) {
    // val builders = contestBuilders.associateBy { it.info.name }
    val infosByName = infos.mapKeys { it.value.name }

    val countyPools: List<CountyPoolsBuilder>

    init {
        val distributeCardsOld: Map<String, Map<String, Int>> = distributeNc() // county -> contest -> pctCards in that county for that contest
        val distributeCards: Map<String, Map<String, Int>> = distributeCards() // county -> contest -> pctCards in that county for that contest

        /* if (distributeCardsOld != distributeCards) {
            distributeCards.forEach { (county, ncMap) ->
                val ncMapOld = distributeCardsOld[county]!!
                if (ncMap != ncMapOld) {
                    ncMap.forEach { (contest, nc) ->
                        if (nc != ncMapOld[contest])
                            println(" $county '$contest': $nc != ${ncMapOld[contest]}")
                    }
                }
            }
            println()
        }

        val d1 = distributeCards["Archuleta"] !!
        val d2 = distributeCardsOld["Archuleta"]!!
        if (d1 != d2) {
            d1.forEach { (contest, nc) ->
                if (nc != d2[contest])
                    println(" '$contest': $nc != ${d2[contest]}")
            }
            println()
        } */

        //     val countyName: String,
        //    val countyPopulation: Int,
        //    val contests: List<Contest>,
        //    val cct: CountyTabAllContests2, //  just the contests in the county
        //    val mvrStyles: List<MvrStyle>,
        countyPools = countiesTabs.map { (countyName, countyContest) ->
            if (distributeCards[countyName] == null)
                logger.error{"distributeCards doesnt have $countyName"}
            if (mvrStylesMap[countyName] == null)
                logger.error{"mvrStylesMap doesnt have $countyName"}
            CountyPoolsBuilder(
                countyName,
                countyPopulations[countyName]!!,
                distributeCards[countyName]!!,
                countyContest,
                mvrStylesMap[countyName]!!,
            )
        }
    }

    // from sans
    fun distributeNc(): Map<String, Map<String, Int>> { // county -> contest -> Nc
        val countyNc = mutableMapOf<String, MutableMap<String, Int>>() // county -> contest -> Nc
        contestsTabs.values.forEach { contestTabAllCounties ->
            val contestName = contestTabAllCounties.contestName
            val contestTotalVotes = contestTabAllCounties.sumVotes()
            val contestNc = contestNcs[contestName]!! // cards
            if (contestNc != null) {
                contestTabAllCounties.countyVotes.forEach { (countyName, countyVotes) ->
                    val countyContest = countyNc.getOrPut(countyName) { mutableMapOf() }
                    val fac = countyVotes / contestTotalVotes.toDouble()

                    if (countyName == "Archuleta" && contestName.startsWith("District Attorney"))
                        print("")
                    countyContest[contestName] = (contestNc * fac).roundToInt()
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

        contestsTabs.values.forEach { contestTabAllCounties ->
            val contestName = contestTabAllCounties.contestName
            val sum = contestSum[contestName]!!
            val contestNc = contestNcs[contestName]!! // cards
            if (abs(contestNc - sum) > 5)
                logger.warn { "makeCardPoolsFromCountyStyles has (contestNc-sum) ${abs(contestNc - sum)} > 5" }
        }
        return countyNc
    }

    fun distributeCards(): Map<String, Map<String, Int>> { // county -> contest -> Nc
        val countyNc = mutableMapOf<String, MutableMap<String, Int>>() // county -> contest -> Nc

        contestsTabs.values.forEach { contestTab -> // one contest
            val contestDist: Map<String, Int> = distributeContestCardsAcrossCounties(contestTab.contestName)
            contestDist.forEach { (countyName, nc) ->
                val countyDist = countyNc.getOrPut(countyName) { mutableMapOf<String, Int>() }
                countyDist[contestTab.contestName] = nc
            }
        }
        return countyNc
    }

    // do each contest seperately so we can move Nc to other counties if need be
    fun distributeContestCardsAcrossCounties(contestName: String): Map<String, Int> { // county -> contest -> Nc
        val countyContestNc = mutableMapOf<String, Int>() // county -> Nc
        val contestTab = contestsTabs[contestName]!!
        val contestName = contestTab.contestName
        val contestTotalVotes = contestTab.sumVotes() // votes
        val contestNc = contestNcs[contestName]!! // cards
        val info = infos[contestName]!!

        // first pass - check if Ncc is more than the ncards in the population
        var distCards1 = 0
        contestTab.countyVotes.forEach { (countyName, countyVotes) ->
            val fac = countyVotes / contestTotalVotes.toDouble() // convert from votes to cards
            val Ncc = (contestNc * fac).roundToInt() // Nc(County, contest)
            val Npop = countyPopulations[countyName]
            if (Npop == null)
                logger.error{"Npopulation is null for $countyName"}
            require (Npop != null)

            if (Ncc > Npop) {
                countyContestNc[countyName] = Npop // Ncc cant be bigger than the county population
                logger.debug{"distributeContestCardsAcrossCounties '$contestName'   $countyName :  est Ncc $Ncc > $Npop Npopulation; use $Npop"}
                distCards1 += Npop
            }
        }
        val ncToDistribute = contestNc - distCards1

        var sumCardsLeft = 0
        contestTab.countyVotes.forEach { (countyName, countyVotes) ->
            if (countyContestNc[countyName] == null ) { // the counties that havent been set yet
                sumCardsLeft += countyVotes / info.voteForN
            }
        }

        // second pass - distribute the remaining counties in proportion to sumCardsLeft
        var distCards2 = 0
        var sumFac = 0.0
        contestTab.countyVotes.forEach { (countyName, countyVotes) ->
            if (countyContestNc[countyName] == null ) {
                val fac = if (sumCardsLeft == 0) 0.0 else (countyVotes / info.voteForN) / sumCardsLeft.toDouble() // convert to cards
                val Ncc = (ncToDistribute * fac).roundToInt()

                countyContestNc[countyName] = Ncc
                //println(" $countyName $countyVotes $fac:  use $Ncc")
                distCards2 += Ncc
                sumFac += fac
            }
        }
        val allDist = distCards1 + distCards2

        // consistency check
        val contestSum = countyContestNc.values.sum()
        if (abs(contestNc - contestSum) > 5) {
            // TODO Nc should be ajusted ??
            logger.warn { "contest '$contestName' has distributed Nc sum = $contestSum should be Nc = $contestNc" }
        }
        return countyContestNc
    }

    // for a single county
    inner class CountyPoolsBuilder(
        val countyName: String,
        val countyPopulation: Int,
        distributeNc: Map<String, Int>, // contest name -> estimated contest ncards in this county
        val cct: CountyTabAllContestsIF, //  just the contests in the county
        mvrStyles: List<MvrStyle>, // Set<contestId> and reletive count within county
    ) {
        // results
        val pools = mutableListOf<CardPool>() // each style gets its own pool
        val totalCardsForContestMap = mutableMapOf<String, Int>() // sum across pool ofor this contest

        init {
            // this may add ncards in order to keep ncards > nvotes
            val styler = StyleCardAllocation(countyName, mvrStyles, cct.contests.values.toList(), distributeNc, infos, countyPopulation)
            styler.allocate()

            // sum of Style ncards that contain this contest
            styler.allStyles.forEach { style ->
                style.contests.forEach { contest ->
                    var totalCardsForContest = totalCardsForContestMap.getOrDefault(contest.name, 0)
                    totalCardsForContestMap[contest.name] = totalCardsForContest + style.ncards()
                }
            }

            val contestPcts = mutableMapOf<String, Double>() // checker

            // each style gets its own pool
            styler.allStyles.forEach { style: StyleCardAllocation.StyleAllocation ->
                val votesForStyle = mutableMapOf<Int, ContestTabulation>()

                style.contests.forEach { contest: StyleCardAllocation.ContestAllocation ->
                    val contestName = contest.name
                    val info = infosByName[contestName]
                    if (info == null)
                        throw Exception("cant find $contestName")

                    // divide up the votes among Styles in proportion to ncards in the pools
                    var denom = totalCardsForContestMap[contest.name]!!
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
                    val Nc = distributeNc[contestName]!! // TODO could have more cards than Nc ??
                    val ncards = (stylePct * Nc).roundToInt() // scale by stylePct

                    votesForStyle[info.id] = ContestTabulation(info, votes, ncards)
                }

                nextPoolId++
                val pool = CardPool(
                    "${countyName}-${nfz(style.id, 2)}",
                    nextPoolId,
                    hasExactContests = true,
                    infosByName.mapKeys { it.value.id },
                    contestTabs = votesForStyle,
                    style.ncards()
                )
                pools.add(pool)
            }

            // check
            contestPcts.forEach { contestName, pct ->
                if (!doubleIsClose(pct, 1.0) && (pct != 0.0))
                    logger.warn { "'$contestName' sum of style pcts ${pct} should be 1.0" }
            }
        }

        fun build(): CountyPools {
            // the number of cards in the pool for a contest is no longer distributeNc[name], recalculated in totalCardsForContestMap

            val contestTabs = cct.contests.map { (name, countyContestVotes) ->
                val info = infosByName[name]!!
                val ncards = totalCardsForContestMap[name] ?: 0
                // println("${name} $ncards was ${distributeNc[name]}")
                countyContestVotes.makeContestTabulation(info, ncards, choiceMapper)
            }.associateBy { it.contestId }

            // we dont know the actual number of cards, we only know the vote counts
            // if you change ncards, you change undervotes...
            val totalCards = pools.sumOf { it.ncards() }
            if (totalCards != countyPopulation)
                logger.warn{"county '$countyName' has totalCards $totalCards != $countyPopulation countyPopulation"}
            // require(totalCards == countyPopulation) // ??
            return CountyPools(countyName, countyPoolId++, contestTabs = contestTabs, styles = pools, cardCount = totalCards)
        }
    }

    companion object {
        var nextPoolId = 0
        var countyPoolId = 1
    }
}

class StyleCardAllocation(val countyName: String, mvrStyles: List<MvrStyle>, contests: List<CountyContestVotesIF>,
                          val distributeNc: Map<String, Int>, val infos: Map<String, ContestInfo>, val cardsinCountyPool: Int) {
    val show = false
    val allContests : List<ContestAllocation> = contests.map{ ContestAllocation(it) }
    val allStyles = mutableListOf<StyleAllocation>()

    var nextContestId = 0
    var nextStyleId = 0
    var missingStyleAdded: StyleAllocation? = null

    init {
        allContests.sumOf{ it.contestNc }

        // all contests not contained in a style are put into a single "missingStyle"
        val missingContests = mutableListOf<ContestAllocation>()
        allContests.forEach { contest ->
            val stylesForContest: List<MvrStyle> = mvrStyles.filter { it.contests.contains(contest.name) }
            if (stylesForContest.isEmpty()) {
                missingContests.add(contest)
            }
        }

        if (missingContests.isNotEmpty()) {
            val missingStyle = StyleAllocation(missingContests, 0)
            allStyles.add(missingStyle)
            missingContests.forEach { missingStyle.setMin(it.contestNormalizedVotes) }
            missingStyleAdded = missingStyle
        }
        mvrStyles.forEach{ allStyles.add(StyleAllocation(it)) }

        // TODO does this help ?? 2020 generalSim failing without it
        // see if we need to add more styles
        var sumSingletons = 0
        allContests.forEach { contest ->
            val useBy = allStyles.filter { it.contests.contains(contest) }
            if (useBy.size == 1) {
                sumSingletons += contest.contestNormalizedVotes
            }
        }
        val extraCards = cardsinCountyPool - sumSingletons
        if (extraCards < 0) {
            // add more styles by breaking existing styles in half
            val extraStyles = mutableListOf<StyleAllocation>()
            allStyles.forEach { style ->
                val ncontests = style.contests.size
                if (ncontests > 3) {
                    extraStyles.add(StyleAllocation(style.contests.subList(0, ncontests / 2), 1))
                    extraStyles.add(StyleAllocation(style.contests.subList(ncontests / 2, ncontests), 1))
                }
            }
            allStyles.addAll(extraStyles)
            allStyles.forEach { it .setMin(0) }
            logger.info{"add ${extraStyles.size} more styles for county $countyName"}
        }
        // end TODO

        val mvrTotal = allStyles.sumOf { it.mvrCount }.toDouble()
        allStyles.forEach { it.mvrPct = it.mvrCount / mvrTotal }
    }

    inner class ContestAllocation(countyContest: CountyContestVotesIF) {
        val name = countyContest.contestName
        val id = nextContestId++
        val contestNc = distributeNc[countyContest.contestName]!!
        val contestNormalizedVotes = countyContest.contestVotes() / infos[name]!!.voteForN // normalized by voteForN

        init {
            if (contestNormalizedVotes > contestNc) {
                logger.warn{" county $countyName contest '$name' has contestNormalizedVotes $contestNormalizedVotes > $contestNc contestNc" }
            }
        }

        fun hasCards(): Int {
            return allStyles.filter { it.contests.contains(this) }.sumOf { it.ncards() }
        }

        // positive if it needs cards, negetive if it has more cards than it needs to satisfy its vote count
        fun need(): Int {
            return contestNormalizedVotes - hasCards()
        }

        fun undervotePct() = (hasCards() - contestNormalizedVotes)  / contestNormalizedVotes.toDouble()

        override fun toString(): String {
            return "Contest($id, name='$name', contestNormalizedVotes=$contestNormalizedVotes, need=${need()} undervotePct=${undervotePct()})"
        }
    }

    inner class StyleAllocation(val contests: List<ContestAllocation>, val mvrCount: Int) {
        var mvrPct: Double = 0.0
        val id = nextStyleId++
        val contestV: Vector

        var minCards = 0  // minimum number of cards, eg from singletons
        var optCards = 0  // extra cards that can be adjusted

        constructor(mvrStyle: MvrStyle) : this(allContests.filter { mvrStyle.contests.contains(it.name)}, mvrStyle.cardCount )

        init {
            val contestVtemp = allContests.map { if (contests.contains(it)) 1 else 0 }
            contestV = Vector(contestVtemp)
        }

        fun ncards() = minCards + optCards

        fun contestIds(): List<Int> = contests.map { it.id }

        fun setMin(minCards: Int) {
            this.minCards = max(this.minCards, minCards)
        }

        fun scalarMult(v: Vector): Int {
            return contestV.dot(v)
        }

        // when used as an inclusion vector
        fun has(idx: Int) = contestV.elems[idx] > 0

        fun scoreFrom(take: Int): Int {
            if (optCards == 0) return Int.MAX_VALUE

            // cant take more than optCards
            val takeMax = min(take, optCards)

            // sum the projected contest.need() that go into the positive
            val score = allContests.mapIndexed { idx, contest ->
                if (this.has(idx)) {
                    val before = max(contest.need(), 0)
                    val after = max(contest.need() + takeMax, 0)
                    after - before
                } else 0
            }
            // println("  ${nfn(id,2)}: ${nfn(score.sum(),6)} ${Vector(score).show(6)}")
            return min(score.sum(),optCards)
        }

        fun scoreFromVector(take: Int): Vector {
            // cant take more than optCards
            val takeMax = min(take, optCards)

            // sum the projected contest.need() that go into the positive
            val score = allContests.mapIndexed { idx, contest ->
                if (this.has(idx)) {
                    val before = max(contest.need(), 0)
                    val after = max(contest.need() + takeMax, 0)
                    after - before
                } else 0
            }
            return Vector(score)
        }

        override fun toString(): String {
            return "Style($id, contests=${contests.map{it.id}}, ncards=${ncards()} optCards=$optCards minCards=$minCards)"
        }
    }

    fun allocate() {

        // find contests that are included in only one style
        allContests.forEach { contest ->
            val useBy = allStyles.filter { it.contests.contains(contest) }
            if (useBy.size == 1) {
                val singletonStyle = useBy.first()
                singletonStyle.setMin(contest.contestNormalizedVotes)
                // println("singleton ${singletonStyle.id}:  ${contest.id} ${contest.contestNormalizedVotes} ${contest.contestNc}")
            }
        }

        // how many cards we have to distribute
        val extraCards = cardsinCountyPool - allStyles.sumOf{ it.minCards }
        if (extraCards < 0) {
            logger.error { "extraCards under water cardsinCountyPool=$cardsinCountyPool" }
            allStyles.forEach { println("  $it") }
            throw RuntimeException("extraCards under water cardsinCountyPool=$cardsinCountyPool")
        }

        // start by allocating in proportion to mvrCount
        allStyles.forEach{ it.optCards = roundToClosest(it.mvrPct * extraCards) }
        val check = allStyles.sumOf{ it.optCards }
        val check2 = allStyles.sumOf{ it.minCards }

        // make sure ncards stays the same
        var checkNcardsInitial = allStyles.sumOf { it.ncards() }
        if (checkNcardsInitial != cardsinCountyPool ) {
            allStyles.last().optCards += (cardsinCountyPool - checkNcardsInitial)
            // println("correct ncards by ${cardsinCountyPool - checkNcardsInitial}" )
        }
        checkNcardsInitial = allStyles.sumOf { it.ncards() }
        require (checkNcardsInitial == cardsinCountyPool )

        var show = false
        var showAll = false
        if (showAll) {
            println("County $countyName")
            println("Contests")
            allContests.sortedBy { it.need() }.forEach { println(" Contest(${it.id}, need= ${it.need()}, uvPct= ${df(it.undervotePct())}, ${it.name})") }
            println("Styles")
            allStyles.forEach { println("  $it") }
            println()
        }

        var showFinal = false
        var prevNeed = -1
        val transferAtaTime = 100
        var count = 0
        var countStall = 0
        var iterLimit = 1111
        while (iterLimit > 0 && countStall < 10) {
            // only use contests that need cards
            // val contestNeedV = allContests.map { it.need() }
            // val contestNeedV = allContests.filter{ it.need() > 0}.map { max(min(it.need(), transferAtaTime), 0) }
            val contestNeedV = Vector(allContests.map { it.need() })
            val contestNeedPositiveV = contestNeedV.bounded(0, Int.MAX_VALUE) // (allContests.map { max(it.need(), 0) }
            val sumNeed = contestNeedPositiveV.sum()
            if (sumNeed <= 0) break

            val contestNeedBoundedV = contestNeedV.bounded(0, transferAtaTime)
            val toStyleScores =  allStyles.map{ it.scalarMult(contestNeedBoundedV) }
            if (showAll) println("$count: toStyleScores $toStyleScores")

            // choose style with largest optCards when score is tied
            val maxScore = toStyleScores.max()
            val stylesAndToScores = allStyles.zip(toStyleScores)
            val stylesWithMaxScore: List<StyleAllocation>  = stylesAndToScores.filter { it.second == maxScore }.map { it.first }
            val toStyle = stylesWithMaxScore.maxBy { it.optCards }

            // dont transfer more than we need
            var maxTransfer = min(contestNeedBoundedV.max(), transferAtaTime)

            // now that we have toStyle, where to take from?
            val fromStyleScores = allStyles.map {
                if (it.id == toStyle.id) Int.MAX_VALUE else it.scoreFrom(maxTransfer)
            }
            if (showAll) println("$count: fromStyleScores $fromStyleScores")

            val minScore = fromStyleScores.min()
            val stylesAndFromScores = allStyles.zip(fromStyleScores)
            // choose style with largest optCards when score is tied
            val stylesWithMinScores: List<StyleAllocation>  = stylesAndFromScores.filter { it.second == minScore }.map { it.first }
            val fromStyle = stylesWithMinScores.maxBy { it.optCards }

            // dont transfer more than we have
            maxTransfer = min(maxTransfer, fromStyle.optCards)
            val fromStyleScore = fromStyle.scoreFromVector(maxTransfer)

            if (show)
                println("$count: ** prevNeed= $prevNeed diff = ${prevNeed-sumNeed} sumNeed=$sumNeed;  transfer $maxTransfer from style ${fromStyle.id} ${fromStyleScore.sum()} to style ${toStyle.id} $maxScore")
            toStyle.optCards += maxTransfer
            fromStyle.optCards -= maxTransfer

            // after
            val contestNeedAfterV = Vector(allContests.map { it.need() })
            val contestNeedPositiveAfterV = contestNeedAfterV.bounded(0, Int.MAX_VALUE) // (allContests.map { max(it.need(), 0) }
            val sumNeedAfter = contestNeedPositiveAfterV.sum()

            val width = 6
            if (sumNeedAfter >= sumNeed) {
                countStall++
                if (showAll) {
                    val msg = buildString {
                        showFinal = true
                        append("$countyName $count: prevNeed= $prevNeed diff = ${prevNeed - sumNeed} sumNeed=$sumNeed; ")
                        append("  transfer $maxTransfer from style ${fromStyle.id} $minScore to style ${toStyle.id} $maxScore; ")
                        appendLine("sumNeedAfter=$sumNeedAfter diff = ${sumNeedAfter - sumNeed}")
                        // val contestNeedVP = Vector(contestNeed).project( fromStyle.contestVec.add(toStyle.contestVec))
                        appendLine("    needlimitV ${contestNeedV.show(width)}")
                        appendLine("  needBoundedV ${contestNeedBoundedV.show(width)}")
                        appendLine("fromStyleScore ${fromStyleScore.show(width)} = ${fromStyleScore.sum()}")

                        appendLine("    needAfterV ${contestNeedAfterV.show(width)}")
                        appendLine("needLimitAfter ${contestNeedPositiveAfterV.show(width)}")
                        val diffV = contestNeedPositiveAfterV.subtract(contestNeedPositiveV)
                        appendLine("           diff${diffV.show(width)}; sum = ${diffV.sum()}")
                    }
                logger.info{ msg }
                }
            }

            prevNeed = sumNeed
            iterLimit--
            count++
        }
        if (showFinal) {
            logger.info{"$countyName took ${count} iterations"}
        }
        if (showAll) {
            println("After")
            println("Contests")
            allContests.sortedBy { it.need() }.forEach { println(" Contest(${it.id}, need= ${it.need()}, ${it.name})") }
            println("Styles")
            allStyles.forEach { println("  $it") }
            println()
            // show = true
        }
        var checkNcards = allStyles.sumOf { it.ncards() }

        // TODO add the contestsNotDone to the missing style and run again
        val contestsNotDone = allContests.filter{ it.need() > 0 }
        if (contestsNotDone.isNotEmpty()) {
            // add these to the missing style
            if (missingStyleAdded == null) {
                val extraStyle = StyleAllocation(contestsNotDone, 0)
                extraStyle.optCards = contestsNotDone.maxOf { it.need() }
                allStyles.add(extraStyle)
                logger.warn { "county '$countyName' needs another Style '$extraStyle' so that ncards > nvotes" }
            } else {
                val missingStylePrev = missingStyleAdded!!
                val contestsForMissingStyle = missingStylePrev.contests + contestsNotDone
                val missingStyleUpdated = StyleAllocation( contests = contestsForMissingStyle, 0)
                missingStyleUpdated.minCards = missingStylePrev.minCards
                missingStyleUpdated.optCards = missingStylePrev.optCards
                allStyles.remove(missingStylePrev)
                allStyles.add(missingStyleUpdated)

                checkNcards = allStyles.sumOf { it.ncards() }
                val anyStillMissing = allContests.filter{ it.need() > 0 }
                require(anyStillMissing.isEmpty())
                logger.info { "county '$countyName' munged the missingStyle so that ncards > nvotes for all contests" }
            }
        }

        require(checkNcards == cardsinCountyPool)
        require(!allContests.any { it.need() > 0 })

        if (showAll) {
            println("$countyName: totalCards=$checkNcards population = $cardsinCountyPool diff=${checkNcards - cardsinCountyPool}")
            allStyles.forEach { println(it) }
        }
    }
}

/////////////////////////////////////////////////////////////
// maybe should be interfaces ?

interface ContestTabAllCountiesIF {
    val contestName: String 
    val choices:  Map<String, Int>// original choice name -> votes
    val counties: Set<String>    // countyNames
    val countyVotes: Map<String, Int>     // countyName -> total votes for this contest in this county
    
    fun sumVotes(): Int
}

// for one county, all contests
interface CountyTabAllContestsIF {
    val countyName: String
    val contests:  Map<String, CountyContestVotesIF>// contestName (canonical I think) -> CountyContestVotes
}

// we only know votes, not ncards or undervotes.
// for one county, one contest
interface CountyContestVotesIF { 
    val countyName: String
    val contestName: String
    val choices: Map<String, Int> // choice name  -> contest choice vote in this county

    fun contestVotes() = choices.values.sumOf { it }

    // convert to canonical choice names TODO get rid of
    fun canonicalChoices(choiceMapper: (String, String, String) -> String): Map<String, Int> {
        return choices.filter { !isWriteIn(it.key) }.mapKeys {
            choiceMapper(countyName, contestName, it.key )
        }
    }

    fun makeContestTabulation(info: ContestInfo, ncards: Int, choiceMapper: (String, String, String) -> String): ContestTabulation {
        val candidateVotes = canonicalChoices(choiceMapper).map { (canonicalChoiceName, vote) ->
            if (info.candidateNames[canonicalChoiceName] == null)
                logger.error{"contestTab candidate name $canonicalChoiceName not found in info"}
            Pair( info.candidateNames[canonicalChoiceName]!!, vote)
        }.toMap()

        return ContestTabulation(info, candidateVotes, ncards)
    }
}

/*
// ContestTabByCounty (for one contest, all counties) vs CountyContestTab (for one county, one contest) (jeesh)
fun CountyContestVotes.makeContestTabulation(canonicalContest: CanonicalContest, info: ContestInfo, ncards: Int): ContestTabulation {
    val candidateVotes = this.canonicalChoices(canonicalContest).map { (canonChoice, vote) ->
        if (info.candidateNames[canonChoice] == null)
            logger.error{"contestTab candidate name $canonChoice not found in info"}
        Pair( info.candidateNames[canonChoice]!!, vote)
    }.toMap()

    return ContestTabulation(info, candidateVotes, ncards)
}
 */

// styles derived from mvr cards
data class MvrStyle(val id: Int, val contests: Set<String>) {
    var cardCount = 0
    override fun toString()= buildString {
        append("style $id has ${contests.size} contests cardCount=$cardCount")
    }

    fun show(contestNameToId: Map<String, Int>, sort:Boolean = true): String {
        val contestIds = contests.map { contestNameToId[it]!! }
        val useIds = if (sort) contestIds.sorted() else contestIds
        return "  MvrStyle(${id}, contests=${useIds}, count= ${cardCount}"
    }
}

data class Vector(val n: Int) {
    val elems = IntArray(n)

    constructor(values: List<Int>) : this(values.size) {
        values.forEachIndexed { index, i -> elems[index] = i }
    }

    fun add(other: Vector): Vector {
        require(n == other.n)
        val result = Vector(n)
        repeat (n) { result.elems[it] = elems[it] + other.elems[it] }
        return result
    }

    fun subtract(other: Vector): Vector {
        require(n == other.n)
        val result = Vector(n)
        repeat (n) { result.elems[it] = elems[it] - other.elems[it] }
        return result
    }

    fun multiply(scalar: Int): Vector {
        val result = Vector(n)
        repeat (n) { result.elems[it] = elems[it] * scalar}
        return result
    }

    fun dot(other : Vector): Int {
        require(n == other.n)
        var sum = 0
        repeat(n) { sum += elems[it] * other.elems[it] }
        return sum
    }

    fun sum(): Int {
        var sum = 0
        repeat(n) { sum += elems[it] }
        return sum
    }

    fun max(): Int {
        var maxValue = -Int.MAX_VALUE
        repeat(n) { maxValue = max(maxValue, elems[it]) }
        return maxValue
    }

    fun min(): Int {
        var minValue = Int.MAX_VALUE
        repeat(n) { minValue = min(minValue, elems[it]) }
        return minValue
    }

    fun project(project: Vector): Vector {
        require(n == project.n)
        val result = Vector(n)
        repeat (n) {
            result.elems[it] = if (project.elems[it] == 0) 0 else elems[it]
        }
        return result
    }

    fun bounded(min: Int, max: Int): Vector {
        val result = Vector(n)
        repeat (n) {
            result.elems[it] = min(max(elems[it], min), max)
        }
        return result
    }

    fun show(width: Int = 4) = buildString {
        append("[")
        elems.forEach { append("${nfn(it, width)},") }
        append("]")
    }
}