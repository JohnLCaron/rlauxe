package org.cryptobiotic.rlauxe.corla


import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.CardPoolWithBallotStyle
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContestIF
import org.cryptobiotic.rlauxe.oneaudit.distributeExpectedOvervotes
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.max
import kotlin.sequences.plus

private val logger = KotlinLogging.logger("ColoradoOneAudit")

// making OneAudit pools from the precinct results
// TODO vary percent cards in pools, show plot
class ColoradoOneAudit (
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    val isClca: Boolean,
    val hasStyle: Boolean = true,
): CreateElectionIF {
    val roundContests: List<ContestRoundCsv> = readColoradoContestRoundCsv(contestRoundFile)
    val electionDetailXml: ElectionDetailXml = readColoradoElectionDetail(electionDetailXmlFile)

    val oaContests = makeOneContestInfo(electionDetailXml, roundContests)
    val infoMap = oaContests.associate { it.info.id to it.info }
    val cardPools: List<CardPoolWithBallotStyle> = convertPrecinctsToCardPools(precinctFile, infoMap)

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

        contestsUA = makeUAContests()
    }

    private fun makeOneContestInfo(electionDetailXml: ElectionDetailXml, roundContests: List<ContestRoundCsv>): List<OneAuditContestCorla> {
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

    fun makeUAContests(): List<ContestUnderAudit> {
        val infoList= oaContests.map { it.info }.sortedBy { it.id }
        val contestMap= oaContests.associateBy { it.info.id }

        println("ncontests with info = ${infoList.size}")

        val regContests = infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val oaContest = contestMap[info.id]!!
            val candVotes = oaContest.candidateVotes.filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.poolTotalCards()
            val useNc = max( ncards, oaContest.Nc)
            val contest = Contest(info, candVotes, useNc, ncards)
            info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt()
            ContestUnderAudit(contest, hasStyle=hasStyle)
        }

        return regContests
    }

    override fun cardPools() = cardPools

    override fun contestsUA() = contestsUA

    // dont load into memory all at once, just one pool at a time
    inner class MakeCvrs(): Iterator<Cvr> {
        val oaContestMap = oaContests.associateBy { it.info.id }
        val cardPoolIter = cardPools.iterator()
        var innerIter: CardsFromPool

        init {
            innerIter = CardsFromPool(cardPoolIter.next(), oaContestMap, isClca)
        }

        override fun next(): Cvr {
            return innerIter.next()
        }

        override fun hasNext(): Boolean {
            if (innerIter.hasNext()) return true
            if (cardPoolIter.hasNext()) {
                innerIter = CardsFromPool(cardPoolIter.next(), oaContestMap, isClca)
                return hasNext()
            }
            return false
        }
    }

    // these are chosen randomly, so in order for mvrs and cvrs to match, the cvrs have to be made from the mvrs.
    class CardsFromPool(val cardPool: CardPoolWithBallotStyle, val oaContestMap: Map<Int, OneAuditContestCorla>, val isClca: Boolean) : Iterator<Cvr> {
        val cvrs: Iterator<Cvr>

        init {
            val contestVotes = mutableMapOf<Int, VotesAndUndervotes>() // contestId -> VotesAndUndervotes
            cardPool.voteTotals.forEach { (contestId, contestTab) ->
                val oaContest: OneAuditContestCorla = oaContestMap[contestId]!!
                val sumVotes = contestTab.nvotes()
                val underVotes = cardPool.ncards() * oaContest.info.voteForN - sumVotes
                contestVotes[contestId] = VotesAndUndervotes(contestTab.votes, underVotes, oaContest.info.voteForN)
            }

            cvrs = makeVunderCvrs(contestVotes, cardPool.poolName, poolId = if (isClca) null else cardPool.poolId).iterator()
        }

        override fun next() = cvrs.next()
        override fun hasNext() = cvrs.hasNext()
    }

    /* override fun hasTestMvrs() = isClca // TODO if you leave off mvrs, i think it will automatically use the cvrs (no error)
    override fun allCvrs(): Pair<CloseableIterable<AuditableCard>, CloseableIterable<AuditableCard>> {
        val poolCvrs = if (isClca) makeCvrs() else createCvrsFromPools(cardPools) // OOM error when both cvrs are made
        val phantoms = makePhantomCvrs(contestsUA.map { it.contest } )
        val cvrs = poolCvrs + phantoms
        val mvrs = if (isClca) poolCvrs + phantoms else emptyList()

        return Pair(
            CloseableIterable { CvrToAuditableCardClca(Closer(cvrs.iterator())) },
            CloseableIterable { CvrToAuditableCardClca(Closer(mvrs.iterator())) },
        )
    } */

    override fun allCvrs(): Pair<CloseableIterator<AuditableCard>?, CloseableIterator<AuditableCard>?> {
        val phantomCvrs = makePhantomCvrs(contestsUA().map { it.contest })
        val phantomSeq = phantomCvrs.mapIndexed { idx, cvr -> AuditableCard.fromCvr(cvr, idx, 0L) }.asSequence()

        val cvrIter: Iterator<Cvr> = MakeCvrs()  // "fake" truth
        val poolNameToId = cardPools.associate { it.poolName to it.poolId }
        val cardSeq = CvrToCardAdapter(Closer(cvrIter), poolNameToId).asSequence()

        val allCardsIter = (cardSeq + phantomSeq).iterator()

        return Pair(null, Closer( allCardsIter))
    }
}

class OneAuditContestCorla(val info: ContestInfo, val detailContest: ElectionDetailContest, val contestRound: ContestRoundCsv): OneAuditContestIF {
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

    override fun adjustPoolInfo(cardPools: List<CardPoolIF>){
        poolTotalCards = cardPools.filter{ it.contains(info.id) }.sumOf { it.ncards() }
    }

    fun poolUndervote(cardPools: List<CardPoolWithBallotStyle>): Int {
        return cardPools.sumOf { it.undervoteForContest(contestId) }
    }

    // expected total poolcards for this contest, making assumptions about missing undervotes
    override fun expectedPoolNCards() = Nc
}

////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createColoradoOneAudit(
    topdir: String,
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditConfigIn: AuditConfig? = null,
    isClca: Boolean,
    clear: Boolean = true)
{
    val stopwatch = Stopwatch()
    val election = ColoradoOneAudit(electionDetailXmlFile, contestRoundFile, precinctFile, isClca)

    val auditConfig = when {
        (auditConfigIn != null) -> auditConfigIn
        isClca -> AuditConfig(
            AuditType.CLCA, hasStyles = true, contestSampleCutoff = 20000, riskLimit = .03, nsimEst=10,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
        )
        else -> AuditConfig(
            AuditType.ONEAUDIT, hasStyles = true, riskLimit = .03, contestSampleCutoff = null, nsimEst = 1,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )
    }

    CreateAudit("corla", topdir, auditConfig, election, clear = clear)
    println("createColoradoOneAudit took $stopwatch")
}


