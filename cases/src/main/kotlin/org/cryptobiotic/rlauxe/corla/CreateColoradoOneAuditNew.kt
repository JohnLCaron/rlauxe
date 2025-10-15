package org.cryptobiotic.rlauxe.corla


import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makePhantomCvrs
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
import org.cryptobiotic.rlauxe.workflow.CreateAudit
import org.cryptobiotic.rlauxe.workflow.ElectionIF
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.mapValues
import kotlin.io.path.Path
import kotlin.math.max

private val logger = KotlinLogging.logger("ColoradoOneAudit")

// making OneAudit pools from the precinct results
class ColoradoOneAuditNew (
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    val isClca: Boolean,
): ElectionIF {
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

    override fun makeCardPools() = cardPools

    override fun makeContestsUA(hasStyles: Boolean): List<OAContestUnderAudit> {
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

    override fun makeCvrs(): List<Cvr> {
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

    override fun cvrExport() = Closer(emptyList<CvrExport>().iterator())
    override fun hasCvrExport() = false
}

////////////////////////////////////////////////////////////////////
// Create audit where pools are from the precinct total. May be CLCA or OneAudit
fun createColoradoOneAuditNew(
    auditDir: String,
    electionDetailXmlFile: String,
    contestRoundFile: String,
    precinctFile: String,
    auditConfigIn: AuditConfig? = null,
    isClca: Boolean,
    clear: Boolean = true)
{
    val election = ColoradoOneAuditNew(electionDetailXmlFile, contestRoundFile, precinctFile, isClca)

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

    CreateAudit("corla", auditDir, auditConfig, election, clear = clear)
}

