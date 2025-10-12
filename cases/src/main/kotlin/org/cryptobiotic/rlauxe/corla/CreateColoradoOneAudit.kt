package org.cryptobiotic.rlauxe.corla


import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.oneaudit.CardPoolWithBallotStyle
import org.cryptobiotic.rlauxe.oneaudit.OAContestUnderAudit
import org.cryptobiotic.rlauxe.oneaudit.OneAuditContestIF
import org.cryptobiotic.rlauxe.oneaudit.addOAClcaAssortersFromMargin
import org.cryptobiotic.rlauxe.oneaudit.distributeExpectedOvervotes
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.*
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.mapValues
import kotlin.io.path.Path
import kotlin.math.max

private val logger = KotlinLogging.logger("ColoradoOneAudit")
private val showMissingCandidates = false

// making OneAudit pools from the precinct results
class ColoradoOneAudit (
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    val isClca: Boolean,
) {
    val roundContests: List<ContestRoundCsv> = readColoradoContestRoundCsv(contestRoundFile)
    val electionDetailXml: ElectionDetailXml = readColoradoElectionDetail(electionDetailXmlFile)

    val oaContests = makeOneContestInfo(electionDetailXml, roundContests)
    val infoMap = oaContests.associate { it.info.id to it.info }
    val cardPools = convertPrecinctsToCardPools(precinctFile, infoMap)

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

        println()
        oaContests.forEach { println("contest ${it.info.id} undervote = ${undervotes[it.info.id]}") }
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
            val voteTotals = mutableMapOf<Int, MutableMap<Int, Int>>()
            precinct.contestChoices.forEach { (name, choices) ->
                val contestName = mutatisMutandi(contestNameCleanup(name))
                val info = infoMap.values.find { it.name == contestName }
                if (info != null) {
                    voteTotals[info.id] = mutableMapOf()
                    val cands = voteTotals[info.id]!!
                    choices.forEach { choice ->
                        val choiceName = candidateNameCleanup(choice.choice)
                        val candId = info.candidateNames[choiceName]
                        if (candId == null) {
                            // logger.warn{"*** precinct ${precinct} candidate ${choiceName} writein missing in info ${info.id} $contestName infoNames= ${info.candidateNames}"}
                        } else {
                            cands[candId] = choice.totalVotes
                        }
                    }
                } else {
                    // probably > 260
                    // println("*** precinct ${precinct} contest ${contestName} missing in info")
                }
            }
            CardPoolWithBallotStyle("${precinct.county}-${precinct.precinct}", idx, voteTotals.toMap(), infoMap)
        }
    }

    fun makeContestsUA(hasStyles: Boolean): List<OAContestUnderAudit> {
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
            OAContestUnderAudit(contest, hasStyles)
        }

        return regContests
    }

    fun makeCvrsFromPools(show: Boolean) : List<Cvr> { // contestId -> candidateId -> nvotes
        val oaContestMap = oaContests.associateBy { it.info.id }

        val rcvrs = mutableListOf<Cvr>()
        cardPools.forEach { cardPool ->
            rcvrs.addAll(makeCvrsFromPool(cardPool, oaContestMap, isClca))
        }

        val rcvrTabs = tabulateCvrs(rcvrs.iterator(), infoMap).toSortedMap()
        rcvrTabs.forEach { contestId, contestTab ->
            val oaContest: OneAuditContestCorla = oaContestMap[contestId]!!
            require(checkEquivilentVotes(oaContest.candidateVotes, contestTab.votes))
            // if (voteForN[contestId] == 1) require(redUndervotes == contestTab.undervotes) // TODO
        }

        return rcvrs
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

fun makeCvrsFromPool(cardPool: CardPoolWithBallotStyle, oaContestMap: Map<Int, OneAuditContestCorla>, isClca: Boolean) : List<Cvr> {

    val contestVotes = mutableMapOf<Int, VotesAndUndervotes>() // contestId -> VotesAndUndervotes
    cardPool.voteTotals.forEach { (contestId, candVotes) ->
        val oaContest: OneAuditContestCorla = oaContestMap[contestId]!!
        val sumVotes = candVotes.map { it.value }.sum()
        val underVotes = cardPool.ncards() * oaContest.info.voteForN - sumVotes
        contestVotes[contestId] = VotesAndUndervotes(candVotes, underVotes, oaContest.info.voteForN)
    }

    val cvrs = makeVunderCvrs(contestVotes, cardPool.poolName, poolId = if (isClca) null else cardPool.poolId) // TODO test

    // check
    val tabVotes: Map<Int, Map<Int, Int>> = tabulateVotesFromCvrs(cvrs.iterator())
    contestVotes.forEach { (contestId, vunders) ->
        val tv = tabVotes[contestId] ?: emptyMap()
        if (!checkEquivilentVotes(vunders.candVotesSorted, tv)) {
            println("  contestId=${contestId}")
            println("  tabVotes=${tv}")
            println("  vunders= ${vunders.candVotesSorted}")
            require(checkEquivilentVotes(vunders.candVotesSorted, tv))
        }
    }

    val infos = oaContestMap.mapValues { it.value.info }
    val cvrTab = tabulateCvrs(cvrs.iterator(), infos).toSortedMap()
    cvrTab.forEach { contestId, contestTab ->
        val oaContest: OneAuditContestCorla = oaContestMap[contestId]!!
        require(checkEquivilentVotes(cardPool.voteTotals[contestId]!!, contestTab.votes))
    }

    return cvrs
}

////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createColoradoOneAudit(
    auditDir: String,
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditConfigIn: AuditConfig? = null,
    isClca: Boolean,
    clear: Boolean = true)
{
    if (clear) clearDirectory(Path(auditDir))
    val stopwatch = Stopwatch()
    val election = ColoradoOneAudit(electionDetailXmlFile, contestRoundFile, precinctFile, isClca)

    val publisher = Publisher(auditDir)
    val auditConfig = when {
        (auditConfigIn != null) -> auditConfigIn
        isClca -> AuditConfig(
            AuditType.CLCA, hasStyles = true, sampleLimit = 20000, riskLimit = .03, nsimEst=10,
            clcaConfig = ClcaConfig(strategy = ClcaStrategyType.previous)
        )
        else -> AuditConfig(
            AuditType.ONEAUDIT, hasStyles = true, riskLimit = .03, sampleLimit = -1, nsimEst = 1,
            oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
        )
    }
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    // write ballot pools
    val ballotPools = election.cardPools.map { it.toBallotPools() }.flatten()
    writeBallotPoolCsvFile(ballotPools, publisher.ballotPoolsFile())
    logger.info{ "write ${ballotPools.size} ballotPools to ${publisher.ballotPoolsFile()}" }

    // write cards TODO add phantoms
    val poolCvrs = election.makeCvrsFromPools(true)

    // this is what you would use in a test audit, write the votes so can be used as test mvrs
    val cards =  createSortedCards(poolCvrs, auditConfig.seed)

    // this is what you would use in a real audit, doesnt write the votes
    // val cards = createSortedCardsFromPools(emptyList(), election.cardPools, auditConfig.seed)

    writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
    createZipFile(publisher.cardsCsvFile(), delete = false)
    logger.info{"write ${cards.size} cvrs to ${publisher.cardsCsvFile()}"}

    val contestsUA= election.makeContestsUA(auditConfig.hasStyles)
    addOAClcaAssortersFromMargin(contestsUA, election.cardPools.associate { it.poolId to it })

    checkContestsCorrectlyFormed(auditConfig, contestsUA)

    // write contests
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"write ${contestsUA.size} contests to ${publisher.contestsFile()}"}
    logger.info{"took = $stopwatch\n"}
}

