package org.cryptobiotic.rlauxe.corla


import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.audit.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.CardPoolWithBallotStyle
import org.cryptobiotic.rlauxe.oneaudit.CvrsWithStylesToCardManifest
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContestIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolIF
import org.cryptobiotic.rlauxe.oneaudit.OneAuditPoolWithBallotStyle
import org.cryptobiotic.rlauxe.oneaudit.distributeExpectedOvervotes
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
    val hasStyle: Boolean = true,
): CreateElectionIF {
    val roundContests: List<CorlaContestRoundCsv> = readColoradoContestRoundCsv(contestRoundFile)
    val electionDetailXml: ElectionDetailXml = readColoradoElectionDetail(electionDetailXmlFile)

    val oaContests = makeOneContestInfo(electionDetailXml, roundContests)
    val infoMap = oaContests.associate { it.info.id to it.info }
    val cardPools: List<CardPoolWithBallotStyle> = convertPrecinctsToCardPools(precinctFile, infoMap)

    val contests: List<ContestIF>
    val contestsUA: List<ContestUnderAudit>

    init {
        // add pool counts into contests
        oaContests.forEach { it.adjustPoolInfo(cardPools) }
        val undervotes = mutableMapOf<Int, MutableList<Int>>()
        oaContests.forEach {
            val undervote = undervotes.getOrPut(it.info.id) { mutableListOf() }
            undervote.add(it.poolUndervote(cardPools))
        }

        // first do contest 0, since it likely has the fewest undervotes
        distributeExpectedOvervotes(oaContests[0], cardPools)
        oaContests.forEach { it.adjustPoolInfo(cardPools) }

        oaContests.forEach {
            val undervote = undervotes.getOrPut(it.info.id) { mutableListOf() }
            undervote.add(it.poolUndervote(cardPools))
        }
        contests = makeContests()
        contestsUA = ContestUnderAudit.make(contests, createCardManifest(), isClca=true)
    }

    private fun makeOneContestInfo(electionDetailXml: ElectionDetailXml, roundContests: List<CorlaContestRoundCsv>): List<OneAuditContestCorla> {
        val roundContestMap = roundContests.associateBy { mutatisMutandi(contestNameCleanup(it.contestName)) }

        val contests = mutableListOf<OneAuditContestCorla>()
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
                    val contest = OneAuditContestCorla(
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

    private fun convertPrecinctsToCardPools(precinctFile: String, infoMap: Map<Int, ContestInfo>): List<CardPoolWithBallotStyle> {
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
                            contestTab.addVote(candId, choice.totalVotes)
                        }
                    }
                }
            }
            CardPoolWithBallotStyle("${precinct.county}-${precinct.precinct}", idx, contestTabs, infoMap)
        }
    }

    fun makeContests(): List<ContestIF> {
        val infoList= oaContests.map { it.info }.sortedBy { it.id }
        val contestMap= oaContests.associateBy { it.info.id }
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

    override fun cardPools(): List<CardPoolIF>?  = cardPools
    override fun contestsUA() = contestsUA
    override fun cardManifest() = createCardManifest()

    fun createCardManifest(): CloseableIterator<AuditableCard> {
        return CvrsWithStylesToCardManifest(
            config.auditType, hasStyle,
            Closer(CvrIteratorfromPools()),
            makePhantomCvrs(contests),
            if (config.isOA) cardPools else null,
        )
    }

    // dont load into memory all at once, just one pool at a time
    inner class CvrIteratorfromPools(): Iterator<Cvr> {
        val oaContestMap = oaContests.associateBy { it.info.id }
        val cardPoolIter = cardPools.iterator()
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
    inner class CardsFromPool(val cardPool: CardPoolWithBallotStyle, val oaContestMap: Map<Int, OneAuditContestCorla>) : Iterator<Cvr> {
        val cvrs: Iterator<Cvr>

        init {
            val contestVotes = mutableMapOf<Int, Vunder>() // contestId -> VotesAndUndervotes
            cardPool.voteTotals.forEach { (contestId, contestTab) ->
                val oaContest: OneAuditContestCorla = oaContestMap[contestId]!!
                val sumVotes = contestTab.nvotes()
                val underVotes = cardPool.ncards() * oaContest.info.voteForN - sumVotes
                contestVotes[contestId] = Vunder(contestTab.votes, underVotes, oaContest.info.voteForN)
            }

            cvrs = makeVunderCvrs(contestVotes, cardPool.poolName, poolId = if (config.isClca) null else cardPool.poolId).iterator()
        }

        override fun next() = cvrs.next()
        override fun hasNext() = cvrs.hasNext()
    }
}

class OneAuditContestCorla(val info: ContestInfo, detailContest: ElectionDetailContest, contestRound: CorlaContestRoundCsv): OneAuditContestIF {
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

    fun poolUndervote(cardPools: List<CardPoolWithBallotStyle>): Int {
        return cardPools.sumOf { it.undervoteForContest(contestId) }
    }

    fun oapoolUndervote(cardPools: List<OneAuditPoolWithBallotStyle>): Int {
        return cardPools.sumOf { it.undervoteForContest(contestId) }
    }

    // expected total poolcards for this contest, making assumptions about missing undervotes
    override fun expectedPoolNCards() = Nc
}

////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createColoradoElection(
    topdir: String,
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditConfigIn: AuditConfig? = null,
    auditType : AuditType,
    clear: Boolean = true)
{
    val stopwatch = Stopwatch()

    val config = when {
        (auditConfigIn != null) -> auditConfigIn

        auditType.isClca() -> AuditConfig(AuditType.CLCA, hasStyle = true, contestSampleCutoff = 20000, riskLimit = .03, nsimEst=10)

        else -> AuditConfig( // TODO NOSTYLE
            AuditType.ONEAUDIT, hasStyle = false, riskLimit = .03, contestSampleCutoff = null, nsimEst = 1,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )
    }
    val election = CreateColoradoElection(electionDetailXmlFile, contestRoundFile, precinctFile, config)

    CreateAudit("corla", config, election, auditDir = "$topdir/audit", clear = clear)
    println("createColoradoOneAudit took $stopwatch")
}


