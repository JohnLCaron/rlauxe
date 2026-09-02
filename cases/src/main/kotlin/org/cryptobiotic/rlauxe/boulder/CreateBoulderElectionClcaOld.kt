package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.cvr.CorlaCvrsIF
import org.cryptobiotic.rlauxe.cvr.cleanCsvString
import org.cryptobiotic.rlauxe.cvr.parseContestNameAndVoteFor
import org.cryptobiotic.rlauxe.cvr.ContestVotes
import org.cryptobiotic.rlauxe.cvr.RedactedGroup
import org.cryptobiotic.rlauxe.cvr.parseIrvContestName
import org.cryptobiotic.rlauxe.estimate.Vunder
import org.cryptobiotic.rlauxe.estimate.makeCvrsForOnePool
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.verify.checkEquivilentVotes
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.math.max
import kotlin.random.Random

private val logger = KotlinLogging.logger("CreateBoulderElectionClca")
private val debugUndervotes = true
private val showCardStyles = false

// TODO this is old; merge boulder25
// Use OneAudit; redacted ballots are in pools. Cant do IRV because we dont have VoteConsolidators
// this version does a bunch of baloney to estimate the redacted undervotes
class CreateBoulderElectionClcaOld(
    val electionName: String,
    val auditType: AuditType,
    val corlaCvrs: CorlaCvrsIF,
    val sovo: BoulderStatementOfVotes,
    val distributeOvervotes: List<Int> = emptyList(), // maybe no default,
    val mvrSource: MvrSource = MvrSource.testPrivateMvrs,
    val hasStyle: Boolean = true,
): ElectionBuilder {
    val exportCvrs: List<Cvr> = corlaCvrs.cvrs().map { it.convertToCvr() }

    val infoList = makeContestInfo().sortedBy{ it.id }
    val infos = infoList.associateBy { it.id }

    val countCvrVotes = countCvrVotes()
    val countRedactedVotes = countRedactedVotes() // wrong
    val boulderContestBuilders: Map<Int, BoulderContestBuilderClca> = makeBoulderContestBuilders().associate { it.info.id to it}
    val cardPoolBuilders: List<CardPoolBuilder> = convertRedactedToCardPool()

    val ncards: Int

    val contests: List<ContestIF>
    val contestsUA : List<ContestWithAssertions>
    val simulatedCvrs: List<Cvr>  // redacted cvrs
    val allCvrs: List<Cvr>  // unredacted cvrs
    val cardStyles: List<StyleIF>
    val cardPools: List<CardPool>

    init {
        //// the redacted groups dont have undervotes, so we do some fancy dancing to generate reasonable undervote counts
        boulderContestBuilders.values.forEach { it.adjustPoolInfo(cardPoolBuilders)}

        // estimate undervotes based on each precinct having a single ballot style
        val undervotesByContest = mutableMapOf<BoulderContestBuilderClca, Int>() // contestId ->
        boulderContestBuilders.values.forEach {
            undervotesByContest[it] = it.poolTotalCards() - it.expectedPoolNCards()
        }

        // TODO used by 2024; do we need it for 2025 ?
        // first even up with contest 0, since it has the fewest undervotes for A cards.
        // then contest 63 has fewest undervotes for card Bs
        distributeOvervotes.forEach { contestId ->
            val contestBuilder = boulderContestBuilders[contestId]
            if (contestBuilder == null)
                print("NO contestBuilder $contestId")
            else {
                contestBuilder.distributeExpectedOvervotes(cardPoolBuilders)
                boulderContestBuilders.values.forEach { it.adjustPoolInfo(cardPoolBuilders) }
            }
        }

        if (debugUndervotes) {
            var sumAfter = 0
            undervotesByContest.forEach { (cb, before) ->
                val needAfter = cb.poolTotalCards() - cb.expectedPoolNCards()
                sumAfter += needAfter
                println("  ${cb.contestId} $before $needAfter ")
            }
            val sumBefore = undervotesByContest.values.sumOf { it }
            println(" undervote sumBefore = $sumBefore sumAfter = $sumAfter")
        }
        // now build the pools and be sone with the builders
        cardPools = cardPoolBuilders.map { it.build() }

        // we need to know the diluted Nb before we can create the UAs
        contests = makeContests()
        simulatedCvrs = makeRedactedCvrs()

        val phantoms = makePhantomCvrs(contests)
        allCvrs = exportCvrs + simulatedCvrs + phantoms // TODO leave out phantoms ??
        val cardStyleMap = makeCardStyles(allCvrs)
        cardStyles = cardStyleMap.values.toList()

        val npops = tabulateNpops(allCvrs, infoList)
        this.ncards = allCvrs.size

        contestsUA = if (auditType.isClca()) ContestWithAssertions.make(contests, npops, isClca=true, hasStyle)
            else makeOneAuditContests(contests, npops, cardPools, hasStyle=hasStyle)

        val totalRedactedBallots = cardPoolBuilders.sumOf { it.ncards() }
        logger.info { "number of redacted ballots = $totalRedactedBallots in ${cardPoolBuilders.size} cardPools"}

        // TODO put in verify
        // checkNpops(allCvrs, createCards(), infoList)
    }

    // TODO cant we use the ballot styles in the CvrExport ??
    fun makeCardStyles(cvrs: List<Cvr>): Map<Set<Int>, CardStyle> {
        val cardStyleMap = mutableMapOf<Set<Int>, CardStyle>()
        cvrs.forEach { cvr ->
            val csc = cardStyleMap.getOrPut(cvr.votes.keys) { CardStyle(cardStyleMap.size + 1, cvr.votes.keys, true) }
            csc.ncards++
        }

        if (showCardStyles) {
            println("\ncard styles  (${cardStyleMap.size})")
            println("id  count contests")
            val sortedCardStyles = cardStyleMap.toList().sortedBy { it.second.ncards }
            sortedCardStyles.forEach { (_, pv) ->
                println(pv)
            }
        }
        return cardStyleMap
    }

    // TODO difference with boulder25
    fun makeCardStyles25(): List<StyleIF> {
        val lastId = cardPools.map{ it.id() }.max()
        return cardPools + corlaCvrs.cardStyles().mapIndexed { idx, it ->
            CardStyle(
                it.name,
                lastId + idx + 1,
                it.contestIds.toList().toIntArray(),
                true
            )
        }
    }

    // make ContestInfo from BoulderStatementOfVotes, and matching export.schema.contests
    fun makeContestInfo(): List<ContestInfo> {
        val columns = corlaCvrs.schema.columns

        return sovo.contests.map { sovoContest ->
            val exportContest = corlaCvrs.schema.contests.find { it.contestName.startsWith(sovoContest.contestTitle) }!!

            val candidateMap = if (!exportContest.isIRV) {
                val candidateMap1 = mutableMapOf<String, Int>()
                var candIdx = 0
                for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                    if (columns[col].choice != "Write-in") { // remove write-ins
                        candidateMap1[columns[col].choice] = candIdx
                    }
                    candIdx++
                }
                candidateMap1

            } else { // there are ncand x ncand columns, so need something different here
                val candidates = mutableListOf<String>()
                for (col in exportContest.startCol..exportContest.startCol + exportContest.ncols - 1) {
                    candidates.add(columns[col].choice)
                }
                val pairs = mutableListOf<Pair<String, Int>>()
                repeat(exportContest.nchoices) { idx ->
                    pairs.add(Pair(candidates[idx], idx))
                }
                pairs.toMap()
            }

            val choiceFunction = if (exportContest.isIRV) SocialChoiceFunction.IRV else SocialChoiceFunction.PLURALITY
            val (name, nwinners) = if (exportContest.isIRV) parseIrvContestName(exportContest.contestName) else parseContestNameAndVoteFor(exportContest.contestName)
            ContestInfo( name, exportContest.contestIdx, candidateMap, choiceFunction, nwinners)
        }
    }

    private fun convertRedactedToCardPool(): List<CardPoolBuilder> {
        return corlaCvrs.redactedGroups().mapIndexed { redactedIdx, redacted: RedactedGroup ->
            // each group becomes a pool
            // correct bug adding contest 12 to pool 06: TODO Boulder24 only I assume
            val useContestVotes = if (redacted.ballotType.startsWith("06")) {
                    redacted.contestVotes.filter{ (key, _) -> key != 12 }
                } else redacted.contestVotes

            //// the redacted groups dont have undervotes, so we have to generate reasonable undervote counts
            // for this pass we are just setting the vote totals, ignoring ncards and undervotes.
            val contestTabs = useContestVotes.mapValues{ ContestTabulation(infos[it.key]!!, it.value, ncards=0) }

            val name = cleanCsvString(redacted.ballotType)
            val id = redactedIdx
            CardPoolBuilder.fromMinVotesNeeded(name, id, hasExactContests=true, infos, contestTabs)
        }
    }

    // make simulated CVRs for all the pools
    fun makeRedactedCvrs() : List<Cvr> { // contestId -> candidateId -> nvotes
        val rcvrs = mutableListOf<Cvr>()
        cardPoolBuilders.forEach { cardPool ->
            rcvrs.addAll(makeCvrsForOnePool(cardPool))
        }
        return rcvrs
    }

    // make simulated CVRs for one pool, all contests
    private fun makeCvrsForOnePool(cardPool: CardPoolBuilder) : List<Cvr> { // contestId -> candidateId -> nvotes
        val poolVunders = cardPool.possibleContests().map {  Pair(it, cardPool.votesAndUndervotesBoulder(it)) }.toMap()
        val cvrs = makeCvrsForOnePool(poolVunders, cardPool.poolName, poolId = cardPool.poolId, cardPool.hasExactContests)

        // TODO is it true that the number of cvrs can vary when there are multiple contests ?
        //if (cardPool.ncards() != cvrs.size)
        //    logger.warn{"cardPool.ncards ${cardPool.ncards()} != cvrs.size = ${cvrs.size}"}

        // check it
        val cvrTabs: Map<Int, ContestTabulation> = tabulateCvrs(cvrs.iterator(), infos)
        poolVunders.forEach { (contestId, vunder) ->
            val poolTab = cardPool.contestTabs[contestId]!!
            val cvrTab = cvrTabs[contestId]!!
            if (!checkEquivilentVotes(vunder.cands(), cvrTab.votes)) {
                logger.warn{"cvrs differ from cardPool"}
                println("  info=${infos[contestId]}")
                println("  cardPool.ncards=${cardPool.ncards()} cvrs.size=${cvrs.size}")
                println("  cardPoolTab=$poolTab")
                println("  cvrTab=$cvrTab")
                println("  vunder= ${vunder}")
                // TODO track down why this happens; maybe just inexact simulation? causes verification to fail?
                println("  checkEquivilentVotes=${checkEquivilentVotes(vunder.cands(), cvrTab.votes)}")
                println()
                throw RuntimeException("makeCvrsForOnePool fails")
            }
        }

        return cvrs
    }

    private fun checkVunderEquivilentTab(vunder: Vunder, contestTab: ContestTabulation): Boolean {
        // if hasExactContests, then missing has to be zero
        // val missing = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
        // 0 = npop - (undervotes + contestTab.votes.values.sum()) / contestTab.voteForN
        // val undervotes = npop * voteForN - voteSum
        val npop = (vunder.undervotes + vunder.nvotes) / vunder.voteForN

        var allOk = true
        allOk = allOk && checkEquivilentVotes(vunder.cands(), contestTab.votes)
        allOk = allOk && (vunder.nvotes == contestTab.nvotes())
        allOk = allOk && (vunder.undervotes == contestTab.undervotes) // no
        allOk = allOk && (npop == contestTab.ncards())
        return allOk
    }

    fun makeBoulderContestBuilders(): List<BoulderContestBuilderClca> {
        val oa2Contests = mutableListOf<BoulderContestBuilderClca>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null) {
                val redTab = countRedactedVotes[info.id] ?: ContestTabulation(info)
                if (countCvrVotes[info.id] != null) {
                    oa2Contests.add(
                        BoulderContestBuilderClca(info, sovoContest, countCvrVotes[info.id]!!, redTab)
                    )
                } else {
                    print("")
                }
            } else {
                logger.warn{"*** cant find contest '${info.name}' in BoulderStatementOfVotes"}
            }
        }

        return oa2Contests
    }

    // TODO move to test
    fun countVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val cvrVotes =  countCvrVotes()
        val redVotes =  countRedactedVotes()
        val allVotes = mutableMapOf<Int, ContestTabulation>()
        allVotes.sumContestTabulations(cvrVotes)
        allVotes.sumContestTabulations(redVotes)
        return allVotes
    }

    fun countCvrVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        corlaCvrs.cvrs().forEach { cvr ->
            cvr.contestVotes.forEach { contestVote: ContestVotes ->
                val tab = votes.getOrPut(contestVote.contestId) { ContestTabulation(infos[contestVote.contestId]!!) }
                tab.addVotes(contestVote.candVotes.toIntArray(), phantom=false)
            }
        }
        return votes
    }

    fun countRedactedVotes() : Map<Int, ContestTabulation> { // contestId -> candidateId -> nvotes
        val votes = mutableMapOf<Int, ContestTabulation>()

        corlaCvrs.redactedGroups().forEach { redacted: RedactedGroup ->
            redacted.contestVotes.entries.forEach { (contestId, contestVote) ->
                val tab = votes.getOrPut(contestId) { ContestTabulation(infos[contestId]!!) }
                contestVote.forEach { (cand, vote) -> tab.addVote(cand, vote) }
                val info = infos[contestId]

                // TODO approx
                tab.ncardsTabulated += contestVote.map { it.value }.sum() / info!!.voteForN // TODO wrong, dont use
            }
        }
        return votes
    }

    fun makeContests(): List<ContestIF> {
        val result = mutableListOf<ContestIF>()
        infoList.forEach { info ->
            val oaContest = boulderContestBuilders[info.id]
            if (oaContest == null)
                print("")
            else {
                val candVotes = oaContest.candVoteTotals().filter { info.candidateIds.contains(it.key) } // remove Write-Ins
                val ncards = oaContest.ncards()
                val useNc = max(ncards, oaContest.Nc())
                info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt().toString()
                result.add(Contest(info, candVotes, useNc, ncards))
            }
        }
        return result
    }

    override fun electionInfo() = ElectionInfo(electionName, auditType, ncards(), contestsUA.size,
        true, mvrSource=mvrSource)
    override fun contestsUA() = contestsUA
    override fun cardStyles() = cardStyles
    override fun cardPools() = if (auditType.isOA()) cardPools else null
    override fun unsortedMvrsInternal() = mvrsToAuditableCardsList(allCvrs, cardPools())
    override fun unsortedMvrsExternal() = null

    override fun cards() = createCards()
    override fun ncards() = ncards

    fun createCards(): CloseableIterator<AuditableCard> {
        // same cvrs for CLCA and OneAudit
        return CvrsToCardStylesIterator(
            auditType,
            Closer(allCvrs.iterator()), // use the mvrs as the cvrs
            null,
            styles = cardStyles // if (auditType.isClca()) null else cardPoolBuilders // integrate OA pools
        )
    }
}

//////////////////////////////
// TODO This is specific to distributeExpectedOvervotes. maybe if we can get undervotes correct we dont need that ?
// TODO overly complicated
class BoulderContestBuilderClca(val info: ContestInfo,
                                val sovoContest: SovoContestVotes,
                                val cvrTab: ContestTabulation,
                                val redTab: ContestTabulation) {

    // there are no overvotes in the Cvrs; we treat them as blanks (not divided by voteForN)
    val sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
    val phantoms = sovoContest.totalBallots - sovoCards

    // sovo gives us an expected undervote for each contest
    val sovoUndervotes = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN

    // missing undervotes we assume are in the redacted pools
    val redUndervotes = sovoUndervotes - cvrTab.undervotes
    val redVotes = redTab.nvotes()

    // then this is the total cards in the pools
    val redNcards = (redVotes + redUndervotes) / info.voteForN

    // then this is the total cards in the cvrs and the pools
    val totalCards = redNcards + cvrTab.ncardsTabulated
    val contestId: Int = info.id

    var poolTotalCards: Int = 0

    fun candVoteTotals(): Map<Int, Int> {
        val sum = mutableMapOf<Int, Int>()
        sum.mergeReduce(listOf(cvrTab.votes, redTab.votes))
        return sum
    }

    override fun toString() = buildString {
        appendLine(info)
        appendLine(" sovoContest=$sovoContest")
    }

    fun details() = buildString {
        appendLine(info)
        appendLine(" sovoContest=$sovoContest")
        // appendLine(" allTabulation=$all3")
        appendLine(" cvrTabulation=$cvrTab")
        appendLine(" redTabulation=$redTab")

        appendLine("  sovoCards= $sovoCards = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes")
        appendLine("  phantoms= $phantoms  = sovoContest.totalBallots - sovoCards")


        val redUnderPct = 100.0 * redUndervotes / (redVotes + redUndervotes)
        appendLine("  sovoUndervotes= ${sovoUndervotes} = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN")
        appendLine("  cvrUndervotes= ${cvrTab.undervotes}")
        appendLine("  redUndervotes= $redUndervotes  = sovoUndervotes - cvr.undervotes")
        appendLine("  redVotes= $redVotes = redacted.votes.map { it.value }.sum()")
        appendLine("  redNcards= $redNcards = (redVotes + redUndervotes) / info.voteForN")
        appendLine("  totalCards= ${totalCards} = redNcards + cvr.ncards")
        appendLine("  diff= ${sovoContest.totalBallots - totalCards} = sovoContest.totalBallots - totalCards")
        appendLine("  redUnderPct= 100.0 * redUndervotes / redNcards  = ${redUnderPct.toInt()}%")
    }

    // on contest 20, sovo.totalVotes and sovo.totalBallots is wrong vs the cvrs. (only one where voteForN=3, but may not be related)

    // contestTitle, precinctCount, activeVoters, totalBallots, totalVotes, totalUnderVotes, totalOverVotes
    //'Town of Superior - Trustee' (20) candidates=[0, 1, 2, 3, 4, 5, 6] choiceFunction=PLURALITY nwinners=3 voteForN=3
    // sovoContest=Town of Superior - Trustee, 7, 9628, 8254, 16417, 8246, 33
    // cvrTabulation={0=3121, 1=3332, 2=3421, 3=2097, 4=805, 5=657, 6=3137} nvotes=16570 ncards=7865 undervotes=7025 overvotes=0 novote=1484 underPct= 29%
    // redTabulation={0=130, 1=87, 2=111, 3=50, 4=25, 5=36, 6=101} nvotes=540 ncards=180 undervotes=0 overvotes=0 novote=0 underPct= 0%
    //  sovoCards= 8254 = (sovoContest.totalVotes + sovoContest.totalUnderVotes) / info.voteForN + sovoContest.totalOverVotes
    //  phantoms= 0  = sovoContest.totalBallots - sovoCards
    //  sovoUndervotes= 8345 = sovoContest.totalUnderVotes + sovoContest.totalOverVotes * info.voteForN
    //  cvrUndervotes= 7025
    //  redUndervotes= 1320  = sovoUndervotes - cvr.undervotes
    //  redVotes= 540 = redacted.votes.map { it.value }.sum()
    //  redNcards= 620 = (redVotes + redUndervotes) / info.voteForN
    //  totalCards= 8485 = redNcards + cvr.ncards
    //  diff= -231 = sovoContest.totalBallots - totalCards
    //  redUnderPct= 100.0 * redUndervotes / redNcards  = 70%

    // assume sovo.totalBallots is wrong
    // so nballotes uses max(totalCards, sovoContest.totalBallots)

    // take 2
    //
    // cvrTab.undervotes = 7025
    // redUndervotes= 1320 so contest should be 8345, which is what sumWithPools has
    // sumVotes = 17110, ncast = (17110 + 8345) / 3 = 8485, correct

    fun Nc(): Int {
        return sovoContest.totalBallots
        // return max(totalCards, sovoContest.totalBallots)
    }

    fun ncards(): Int {
        // for contest 20, correct the ncards, ignore undervote count
        return if (info.name == "Town of Superior - Trustee") 8256  // TODO fix this; Boulder 2024 only
        else sumAllCards()
    }

    // total number of cards for this contest in the pools. this is dynamic because the pools get adjusted
    fun poolTotalCards() = poolTotalCards

    fun adjustPoolInfo(cardPools: List<CardPoolBuilder>) {
        poolTotalCards = cardPools.filter { it.hasContest(info.id) }.sumOf { it.ncards() }
    }

    // calculated total cards in the pools
    fun expectedPoolNCards() = redNcards

    // ncards
    fun sumAllCards(): Int {
        return poolTotalCards() + cvrTab.ncardsTabulated
    }

    fun checkCvrs(contestTab: ContestTabulation) {
        sovoContest.candidateVotes.forEach { (sovoCandidate, sovoVote) ->
            val candidateId = info.candidateNames[sovoCandidate]
            val contestVote = contestTab.votes[candidateId] ?: 0
            if (contestVote != sovoVote) {
                println("*** ${info.name} '$sovoCandidate' $contestVote != $sovoVote")
                print("")
            }
        }
    }

    fun checkNcards(contestTab: ContestTabulation) {
        println("  ${info.id}: sovoContest.totalBallots=${sovoContest.totalBallots} - contestTab.ncards=${contestTab.ncardsTabulated} = ${sovoContest.totalBallots - contestTab.ncardsTabulated}")
        println("  ${info.id}: sumAllCards=${sumAllCards()} - contestTab.ncards=${contestTab.ncardsTabulated} = ${sumAllCards() - contestTab.ncardsTabulated}")
        println()
    }

    // TODO can we get rid of? overly complicated
    // we dont know how many cards are in the pool.
    // so adjust the number of cards in the pools so that the sum of pool.undervotes agrees with the refContest
    // this only works if the pool has a single style.
    fun distributeExpectedOvervotes(cardPools: List<CardPoolBuilder>) {
        val poolCards = this.poolTotalCards()
        val expectedCards = this.expectedPoolNCards()
        val need = expectedCards - poolCards
        println("${contestId} expectedCards=$expectedCards poolCards=$poolCards need = $need")

        var used = 0
        val allocDiffPool = mutableMapOf<Int, Int>() // poolId -> adjusted undervotes
        cardPools.forEach { pool ->
            val minCardsNeeded = pool.minCardsNeeded[contestId]
            if (minCardsNeeded != null) {
                // distribute cards as proportion of totalVotes
                val allocDiff = roundToClosest(need * (pool.maxMinCardsNeeded / poolCards.toDouble()))
                used += allocDiff
                allocDiffPool[pool.poolId] = allocDiff
            }
        }

        // adjust random pools until used == diff
        if (used < need) {
            val keys = allocDiffPool.keys.toList()
            while (used < need) {
                val chooseOne = keys[Random.nextInt(allocDiffPool.size)]
                val prev = allocDiffPool[chooseOne]!!
                allocDiffPool[chooseOne] = prev + 1
                used++
            }
        }
        if (used > need) {
            val keys = allocDiffPool.keys.toList()
            while (used > need) {
                val chooseOne = keys[Random.nextInt(allocDiffPool.size)]
                val prev = allocDiffPool[chooseOne]!!
                if (prev > 0) {
                    allocDiffPool[chooseOne] = prev - 1
                    used--
                }
            }
        }

        // check used == diff
        if (allocDiffPool.values.sum() != need) {
            println("distributeExpectedOvervotes: ${allocDiffPool.values.sum()} should equal == $need")
        }

        // adjust
        val cardPoolMap = cardPools.associateBy { it.poolId }
        allocDiffPool.forEach { (poolId, adjust) ->
            cardPoolMap[poolId]!!.adjustCards(adjust, contestId)
        }
    }
}




