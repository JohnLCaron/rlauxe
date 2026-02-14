package org.cryptobiotic.rlauxe.corla


import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import org.cryptobiotic.rlauxe.boulder.distributeExpectedOvervotes
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContestBuilderIF
// import org.cryptobiotic.rlauxe.oneaudit.OneAuditPool
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolWithBallotStyle
import org.cryptobiotic.rlauxe.oneaudit.makeOneAuditContests
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.max

private val logger = KotlinLogging.logger("ColoradoOneAudit")

// making OneAudit pools from the precinct results
// TODO vary percent cards in pools, show plot
open class CreateColoradoElection (
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    val config: AuditConfig,
    val poolsHaveOneCardStyle:Boolean = false,
): CreateElectionIF {
    val roundContests: List<CorlaContestRoundCsv> = readColoradoContestRoundCsv(contestRoundFile)
    val electionDetailXml: ElectionDetailXml = readColoradoElectionDetail(electionDetailXmlFile)

    val oaBuilders = makeOneAuditBuilders(electionDetailXml, roundContests)
    val infoMap = oaBuilders.associate { it.info.id to it.info }
    val cardPoolBuilders: List<OneAuditPoolWithBallotStyle> = convertPrecinctsToCardPools(precinctFile, infoMap)
    val ncards: Int

    val cardPools: List<PopulationIF>
    val contests: List<ContestIF>
    val contestsUA: List<ContestWithAssertions>

    init {
        // add pool counts into contests
        oaBuilders.forEach { it.adjustPoolInfo(cardPoolBuilders) }
        val undervotes = mutableMapOf<Int, MutableList<Int>>()
        oaBuilders.forEach {
            val undervote = undervotes.getOrPut(it.info.id) { mutableListOf() }
            undervote.add(it.oapoolUndervote(cardPoolBuilders))
        }

        // first do contest 0, since it likely has the fewest undervotes
        distributeExpectedOvervotes(oaBuilders[0], cardPoolBuilders)
        oaBuilders.forEach { it.adjustPoolInfo(cardPoolBuilders) }

        oaBuilders.forEach {
            val undervote = undervotes.getOrPut(it.info.id) { mutableListOf() }
            undervote.add(it.oapoolUndervote(cardPoolBuilders))
        }

        cardPools = cardPoolBuilders
        contests = makeContests()

        val infos = contests.map { it.info() }.associateBy { it.id }
        val (manifestTabs, count) = tabulateCardsAndCount(createCards(), infos)
        val npopMap = manifestTabs.mapValues { it.value.ncardsTabulated }
        this.ncards = count

        contestsUA = if (config.isOA) makeOneAuditContests(contests, npopMap, cardPoolBuilders)
                     else ContestWithAssertions.make(contests, npopMap, isClca=config.isClca, )
    }

    private fun makeOneAuditBuilders(electionDetailXml: ElectionDetailXml, roundContests: List<CorlaContestRoundCsv>): List<OneAuditBuilderCorla> {
        val roundContestMap = roundContests.associateBy { mutatisMutandi(contestNameCleanup(it.contestName)) }

        val contests = mutableListOf<OneAuditBuilderCorla>()
        electionDetailXml.contests.forEachIndexed { detailIdx, detailContest ->
            val contestName = contestNameCleanup(detailContest.text)
            var roundContest = roundContestMap[contestName]
            if (roundContest == null) {
                roundContest = roundContestMap[mutatisMutandi(contestName)]
                if (roundContest == null) {
                    val mname = mutatisMutandi(contestName)
                    println("*** Cant find ContestRoundCsv $mname")
                }
            } else {
                val candidates = detailContest.choices
                val candidateNames =
                    candidates.mapIndexed { idx, choice -> Pair(candidateNameCleanup(choice.text), idx) }.toMap()

                val info = ContestInfo(
                    contestName,
                    detailIdx,
                    candidateNames,
                    SocialChoiceFunction.PLURALITY,
                    detailContest.voteFor
                )
                info.metadata["CORLAsample"] = roundContest.optimisticSamplesToAudit

                // they dont have precinct data for contest >= 260, so we'll just skip them
                if (info.id < 260) {
                    val contest = OneAuditBuilderCorla(
                        info,
                        detailContest,
                        roundContest,
                    )
                    contests.add(contest)
                }
            }
        }

        return contests
    }

    private fun convertPrecinctsToCardPools(precinctFile: String, infoMap: Map<Int, ContestInfo>): List<OneAuditPoolWithBallotStyle> {
        val reader = ZipReader(precinctFile)
        val input = reader.inputStream("2024GeneralPrecinctLevelResults.csv")
        val precincts = readColoradoPrecinctLevelResults(input)
        println("precincts = ${precincts.size}")

        return precincts.mapIndexed { idx, precinct ->
            val contestTabs = mutableMapOf<Int, ContestTabulation>()
            precinct.contestChoices.forEach { (name, choices) ->
                val contestName = mutatisMutandi(contestNameCleanup(name))
                val info = infoMap.values.find { it.name == contestName }
                if (info != null) {
                    val contestTab = ContestTabulation(info)
                    contestTabs[info.id] = contestTab
                    choices.forEach { choice ->
                        val choiceName = candidateNameCleanup(choice.choice)
                        val candId = info.candidateNames[choiceName]
                        if (candId == null) {
                            // logger.warn{"*** precinct ${precinct} candidate ${choiceName} writein missing in info ${info.id} $contestName infoNames= ${info.candidateNames}"}
                        } else {
                            contestTab.addVote(candId, choice.totalVotes) // TODO use addVotes
                        }
                    }
                }
            }
            OneAuditPoolWithBallotStyle("${precinct.county}-${precinct.precinct}", idx, poolsHaveOneCardStyle,contestTabs, infoMap)
        }
    }

    fun makeContests(): List<ContestIF> {
        val infoList= oaBuilders.map { it.info }.sortedBy { it.id }
        val contestMap= oaBuilders.associateBy { it.info.id }
        println("ncontests with info = ${infoList.size}")

        return infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val oaContest = contestMap[info.id]!!
            val candVotes = oaContest.candidateVotes.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.poolTotalCards()
            val useNc = max( ncards, oaContest.Nc)
            info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt()
            Contest(info, candVotes, useNc, ncards)
        }
    }

    override fun populations() = if (config.isClca) emptyList() else cardPools
    override fun cardPools() = null
    override fun contestsUA() = contestsUA
    override fun cards() = createCards()
    override fun ncards() = ncards

    fun createCards(): CloseableIterator<AuditableCard> {
        return CvrsToCardsAddStyles(config.auditType,
            Closer(CvrIteratorfromPools()),
            makePhantomCvrs(contests),
            if (config.isClca) null else cardPoolBuilders,
        )
    }

    // dont load into memory all at once, just one pool at a time
    inner class CvrIteratorfromPools(): Iterator<Cvr> {
        val oaContestMap = oaBuilders.associateBy { it.info.id }
        val cardPoolIter = cardPoolBuilders.iterator()
        var innerIter: CardsFromPool

        init {
            innerIter = CardsFromPool(cardPoolIter.next(), oaContestMap)
        }

        override fun next(): Cvr {
            return innerIter.next()
        }

        override fun hasNext(): Boolean {
            if (innerIter.hasNext()) return true
            if (cardPoolIter.hasNext()) {
                innerIter = CardsFromPool(cardPoolIter.next(), oaContestMap)
                return hasNext()
            }
            return false
        }
    }

    // these are chosen randomly, so in order for mvrs and cvrs to match, the cvrs have to be made from the mvrs.
    inner class CardsFromPool(val cardPool: OneAuditPoolWithBallotStyle, val oaContestMap: Map<Int, OneAuditBuilderCorla>) : Iterator<Cvr> {
        val cvrs: Iterator<Cvr>

        init {
            val poolVunders = cardPool.contests().map {  Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()
            /* val contestVotes = mutableMapOf<Int, Vunder>() // contestId -> VotesAndUndervotes
            cardPool.voteTotals.forEach { (contestId, contestTab) ->
                val oaContest: OneAuditBuilderCorla = oaContestMap[contestId]!!
                val sumVotes = contestTab.nvotes()
                val underVotes = cardPool.ncards() * oaContest.info.voteForN - sumVotes
                contestVotes[contestId] = Vunder.fromNpop(contestId,  underVotes, cardPool.ncards(), contestTab.votes, oaContest.info.voteForN)
            }
            poolVunders.forEach{ (id, vunder) ->
                val otherVunder =  contestVotes[id]!!
                if (vunder != otherVunder)
                    print("why?")
            } */

            cvrs = makeVunderCvrs(poolVunders, cardPool.poolName, poolId = if (config.isClca) null else cardPool.poolId).iterator()
        }

        override fun next() = cvrs.next()
        override fun hasNext() = cvrs.hasNext()
    }
}

class OneAuditBuilderCorla(val info: ContestInfo, detailContest: ElectionDetailContest, contestRound: CorlaContestRoundCsv): OneAuditContestBuilderIF {
    override val contestId = info.id
    val Nc: Int
    val candidateVotes: Map<Int, Int>
    var poolTotalCards: Int = 0

    init {
        val candidates = detailContest.choices
        candidateVotes = candidates.mapIndexed { idx, choice -> Pair(idx, choice.totalVotes) }.toMap()

        val totalVotes = candidateVotes.map { it.value }.sum() / info.voteForN
        var useNc = contestRound.contestBallotCardCount
        if (useNc < totalVotes) {
            println("*** Contest ${info.name} has $totalVotes total votes, but contestBallotCardCount is ${contestRound.contestBallotCardCount} - using totalVotes")
            useNc = totalVotes // contestRound.ballotCardCount
        }
        Nc = useNc
    }

    // total cards in all pools for this contest
    override fun poolTotalCards(): Int  = poolTotalCards

    override fun adjustPoolInfo(cardPools: List<OneAuditPoolIF>){
        poolTotalCards = cardPools.filter{ it.hasContest(info.id) }.sumOf { it.ncards() }
    }

    fun oapoolUndervote(cardPools: List<OneAuditPoolWithBallotStyle>): Int {
        return cardPools.sumOf { it.undervoteForContest(contestId) }
    }

    // expected total poolcards for this contest, making assumptions about missing undervotes
    override fun expectedPoolNCards() = Nc
}


////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createColoradoElectionP(
    topdir: String,
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditConfigIn: AuditConfig? = null,
    auditType : AuditType,
    poolsHaveOneCardStyle:Boolean = false,
    clear: Boolean = true,
    )
{
    val stopwatch = Stopwatch()

    val config = when {
        (auditConfigIn != null) -> auditConfigIn

        auditType.isClca() -> AuditConfig(AuditType.CLCA, contestSampleCutoff = 20000, riskLimit = .03, nsimEst=10)

        else -> AuditConfig( // // TODO hasStyle=false
            AuditType.ONEAUDIT, riskLimit = .03, nsimEst = 10,
        )
    }
    val election = CreateColoradoElection(electionDetailXmlFile, contestRoundFile, precinctFile, config, poolsHaveOneCardStyle)

    CreateAudit("corla", config, election, auditDir = "$topdir/audit", clear = clear)
    println("createColoradoOneAudit took $stopwatch")
}


