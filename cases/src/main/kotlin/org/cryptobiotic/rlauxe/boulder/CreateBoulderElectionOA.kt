package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeBallotPoolCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.map
import kotlin.collections.set
import kotlin.collections.sortedBy
import kotlin.io.path.Path
import kotlin.math.max
import kotlin.random.Random

private val logger = KotlinLogging.logger("BoulderElectionOA")

// Use OneAudit, redacted ballots are in pools. use redacted vote counts for pools' assort averages.
// No redacted CVRs. Cant do IRV.
open class BoulderElectionOA(
    export: DominionCvrExportCsv,
    sovo: BoulderStatementOfVotes,
    clca: Boolean = false,
    quiet: Boolean = true,
): BoulderElection(export, sovo, quiet) {

    val cardPools: List<CardPool> = convertRedactedToCardPool2() // convertRedactedToCardPoolPaired(export.redacted, infoMap) // convertRedactedToCardPool2()
    val oaContests: Map<Int, OneAuditContestInfo> = makeOAContest2().associate { it.info.id to it}

    init {
        val cardPoolMap = cardPools.associateBy { it.poolId }

        // first do contest 0, since it has the fewest undervotes
        val oaContest0 = oaContests[0]!!
        distributeRedactedNcardsDiff(oaContest0, cardPoolMap)

        // card B
        val oaContest63 = oaContests[63]!!
        distributeRedactedNcardsDiff(oaContest63, cardPoolMap)
        val totalRedactedBallots = cardPools.sumOf { it.ncards() }
        logger.info { "number of redacted ballots = $totalRedactedBallots in ${cardPools.size} cardPools"}
    }

    private fun convertRedactedToCardPool2(): List<CardPool> {
        return export.redacted.mapIndexed { redactedIdx, redacted: RedactedGroup ->
            // each group becomes a pool
            // correct bug adding contest 12 to pool 06
            val useContestVotes =  if (redacted.ballotType.startsWith("06")) {
                redacted.contestVotes.filter{ (key, value) -> key != 12 }
            } else redacted.contestVotes

            CardPool(redacted.ballotType, redactedIdx, useContestVotes.toMap(), infoMap)
        }
    }

    // put the A and B into the same pool, so we can count undervotes accurately
    private fun convertRedactedToCardPoolPaired(groups: List<RedactedGroup>, infoMap: Map<Int, ContestInfo>): List<CardPool> {
        val aandbs = mutableMapOf<String, MutableList<RedactedGroup>>()
        groups.forEach { redacted: RedactedGroup ->
            val name = redacted.ballotType.substring(0, redacted.ballotType.lastIndexOf('-'))
            val rlist = aandbs.getOrPut( name, { mutableListOf() })
            rlist.add(redacted)
        }

        var poolIdx = 0
        return aandbs.map { (name, aandb: List<RedactedGroup>) ->
            val contestVotesSummed = mutableMapOf<Int, MutableMap<Int, Int>>()  // contestId -> candidateId -> nvotes

            aandb.forEach { sumContestVotes(it.contestVotes, contestVotesSummed) }

            CardPool(name, poolIdx++, contestVotesSummed, infoMap)
        }
    }

    fun makeOAContest2(): List<OneAuditContestInfo> {
        val countCvrVotes = countCvrVotes()
        val countRedactedVotes = countRedactedVotes()

        val oa2Contests = mutableListOf<OneAuditContestInfo>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null) {
                oa2Contests.add( OneAuditContestInfo(info, sovoContest, countCvrVotes[info.id]!!, countRedactedVotes[info.id]!!, cardPools))
            }
            else logger.warn{"*** cant find contest '${info.name}' in BoulderStatementOfVotes"}
        }

        return oa2Contests
    }

    // distribute diff to pools proportionate to pool.maxMinCardsNeeded
    fun distributeRedactedNcardsDiff() {
        val cardPoolMap = cardPools.associateBy { it.poolId }

        // first do contest 0, since it has the fewest undervotes
        val oaContest0 = oaContests[0]!!
        distributeRedactedNcardsDiff(oaContest0, cardPoolMap)

        oaContests.forEach { (contestId, oaContest) ->
            distributeRedactedNcardsDiff(oaContest, cardPoolMap)
        }
    }

    fun distributeRedactedNcardsDiff(oaContest: OneAuditContestInfo, cardPoolMap: Map<Int, CardPool>) {
        val contestId = oaContest.info.id
        val poolCards = oaContest.poolTotalCards()
        val totalCards = oaContest.redNcards
        val diff = totalCards - poolCards

        var used = 0
        val allocDiffPool = mutableMapOf<Int, Int>()
        cardPools.forEach { pool ->
            val minCardsNeeded = pool.minCardsNeeded[contestId]
            if (minCardsNeeded != null) {
                // distribute cards as proportion of totalVotes
                val allocDiff = roundToClosest(diff * (pool.maxMinCardsNeeded / poolCards.toDouble()))
                used += allocDiff
                allocDiffPool[pool.poolId] = allocDiff
            }
        }

        // adjust some pool so sum undervotes = redUndervotes
        if (used < diff) {
            val keys = allocDiffPool.keys.toList()
            while (used < diff) {
                val chooseOne = keys[Random.nextInt(allocDiffPool.size)]
                val prev = allocDiffPool[chooseOne]!!
                allocDiffPool[chooseOne] = prev + 1
                used++
            }
        }
        if (used > diff) {
            val keys = allocDiffPool.keys.toList()
            while (used > diff) {
                val chooseOne = keys[Random.nextInt(allocDiffPool.size)]
                val prev = allocDiffPool[chooseOne]!!
                if (prev > 0) {
                    allocDiffPool[chooseOne] = prev - 1
                    used--
                }
            }
        }

        // check
        require(allocDiffPool.values.sum() == diff)

        // adjust
        allocDiffPool.forEach { (poolId, adjust) ->
            cardPoolMap[poolId]!!.adjustCards(adjust, contestId)
        }
    }

    open fun makeContestsUA(hasStyles: Boolean): List<ContestUnderAudit> {
        if (!quiet) println("ncontests with info = ${infoList.size}")

        val regContests = infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val oaContest = oaContests[info.id]!!
            val candVotes = oaContest.candVoteTotals().filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.sumAllCards()
            val useNc = max( ncards, oaContest.Nc())
            val contest = Contest(info, candVotes, useNc, oaContest.sumAllCards())
            info.metadata["PoolPct"] = (100.0 * oaContest.poolTotalCards() / useNc).toInt()
            OAContestUnderAudit(contest, hasStyles)
        }

        return regContests
    }

}

////////////////////////////////////////////////////////////////////
// Create a OneAudit where pools are from the redacted cvrs, use pool vote totals for assort average
fun createBoulderElectionOA(
    cvrExportFile: String,
    sovoFile: String,
    auditDir: String,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .005,
    auditConfigIn: AuditConfig? = null)
{
    clearDirectory(Path(auditDir))
    val stopwatch = Stopwatch()

    val variation = if (sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)

    println("readDominionCvrExport $cvrExportFile")
    val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrExportFile, "Boulder")
    val election = BoulderElectionOA(export, sovo)

    val publisher = Publisher(auditDir)
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles=true, riskLimit=riskLimit, sampleLimit=20000, minRecountMargin=minRecountMargin, nsimEst=10,
        oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    // write ballot pools
    val ballotPools = election.cardPools.map { it.toBallotPools() }.flatten()
    writeBallotPoolCsvFile(ballotPools, publisher.ballotPoolsFile())
    logger.info{"write ${ballotPools.size} ballotPools to ${publisher.ballotPoolsFile()}"}

    // write cards TODO add phantoms
    val cards = createSortedCards(election.cvrs, election.cardPools, auditConfig.seed)
    writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
    logger.info{"write ${cards.size} cvrs to ${publisher.cardsCsvFile()}"}

    val contestsUA= election.makeContestsUA(auditConfig.hasStyles)
    addOAClcaAssortersFromMargin(contestsUA, election.cardPools.associate { it.poolId to it })

    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    checkContestsWithCvrs(contestsUA, CvrIteratorAdapter(cards.iterator()), ballotPools=ballotPools, show = false)
    checkVotesVsSovo(contestsUA.map { it.contest as Contest}, sovo, mustAgree = false)

    // write contests
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"write ${contestsUA.size} contests to ${publisher.contestsFile()}"}
    logger.info{"took = $stopwatch\n"}
}

fun createSortedCards(cvrs: List<Cvr>, pools: List<CardPool>, seed: Long) : List<AuditableCard> {
    val prng = Prng(seed)
    val cards = mutableListOf<AuditableCard>()
    var idx = 0
    cvrs.forEach { cards.add(AuditableCard.fromCvr(it, idx++, prng.next())) }
    // add the redacted votes
    pools.forEach { pool ->
        val ncards = pool.ncards()
        val cleanName = cleanCsvString(pool.poolName)
        repeat(ncards) { poolIndex ->
            //     val location: String, // info to find the card for a manual audit. Aka ballot identifier.
            //    val index: Int,  // index into the original, canonical list of cards
            //    val prn: Long,   // psuedo random number
            //    val phantom: Boolean,
            //    val contests: IntArray, // list of contests on this ballot. TODO optional when !hasStyles ??
            //    val votes: List<IntArray>?, // contest -> list of candidates voted for; for IRV, rankeballotPools=ballotPools, cleand first to last
            //    val poolId: Int?, // for OneAudit
            cards.add(
                AuditableCard(
                    location = "pool${cleanName} card ${poolIndex + 1}",
                    index = idx++,
                    prn = prng.next(),
                    phantom = false,
                    contests = pool.contests(),
                    votes = null,
                    poolId = pool.poolId
                )
            )
        }
    }

    return cards.sortedBy { it.prn }
}

