package org.cryptobiotic.rlauxe.boulder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportCsv
import org.cryptobiotic.rlauxe.dominion.RedactedGroup
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.oneaudit.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.util.*
import kotlin.collections.map
import kotlin.collections.set
import kotlin.collections.sortedBy
import kotlin.math.max
import kotlin.random.Random

private val logger = KotlinLogging.logger("BoulderElectionOA")

// Use OneAudit, redacted ballots are in pools. use redacted vote counts for pools' assort averages.
// No redacted CVRs. Cant do IRV.
class BoulderElectionOA(
    export: DominionCvrExportCsv,
    sovo: BoulderStatementOfVotes,
    quiet: Boolean = true,
): BoulderElection(export, sovo, quiet)
{
    val cardPools: List<CardPool2> = convertRedactedToCardPool2() // convertRedactedToCardPoolPaired(export.redacted, infoMap) // convertRedactedToCardPool2()
    val oaContests = makeOAContest2().associate { it.info.id to it}

    init {
        val cardPoolMap = cardPools.associateBy { it.poolId }

        // first do contest 0, since it has the fewest undervotes
        val oaContest0 = oaContests[0]!!
        distributeRedactedNcardsDiff(oaContest0, cardPoolMap)

        // card B
        val oaContest63 = oaContests[63]!!
        distributeRedactedNcardsDiff(oaContest63, cardPoolMap)
    }

    private fun convertRedactedToCardPool2(): List<CardPool2> {
        return export.redacted.mapIndexed { redactedIdx, redacted: RedactedGroup ->
            // each group becomes a pool
            // correct bug adding contest 12 to pool 06
            val useContestVotes =  if (redacted.ballotType.startsWith("06")) {
                redacted.contestVotes.filter{ (key, value) -> key != 12 }
            } else redacted.contestVotes

            CardPool2(redacted.ballotType, redactedIdx, useContestVotes.toMap(), infoMap)
        }
    }

    // put the A and B into the same pool, so we can count undervotes accurately
    private fun convertRedactedToCardPoolPaired(groups: List<RedactedGroup>, infoMap: Map<Int, ContestInfo>): List<CardPool2> {
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

            CardPool2(name, poolIdx++, contestVotesSummed, infoMap)
        }
    }

    fun makeOAContest2(): List<OneAuditContest2> {
        val countCvrVotes = countCvrVotes()
        val countRedactedVotes = countRedactedVotes()

        val oa2Contests = mutableListOf<OneAuditContest2>()
        infoList.forEach { info ->
            val sovoContest = sovo.contests.find { it.contestTitle == info.name }
            if (sovoContest != null) {
                oa2Contests.add( OneAuditContest2(info, sovoContest, countCvrVotes[info.id]!!, countRedactedVotes[info.id]!!, cardPools))
            }
            else logger.warn{"*** cant find contest '${info.name}' in BoulderStatementOfVotes"}
        }

        return oa2Contests
    }

    // // so distribute diff to pools proportionate to sumPoolCards
    fun distributeRedactedNcardsDiff() {
        val cardPoolMap = cardPools.associateBy { it.poolId }

        // first do contest 0, since it has the fewest undervotes
        val oaContest0 = oaContests[0]!!
        distributeRedactedNcardsDiff(oaContest0, cardPoolMap)

        oaContests.forEach { (contestId, oaContest) ->
            distributeRedactedNcardsDiff(oaContest, cardPoolMap)
        }
    }

    fun distributeRedactedNcardsDiff(oaContest: OneAuditContest2, cardPoolMap: Map<Int, CardPool2>) {
        val contestId = oaContest.info.id
        val poolCards = oaContest.poolTotals()
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

        if (allocDiffPool.values.sum() != diff)
            print("whw")

        // check
        require(allocDiffPool.values.sum() == diff)

        // adjust
        allocDiffPool.forEach { (poolId, adjust) ->
            cardPoolMap[poolId]!!.adjustCards(adjust, contestId)
        }
    }


    fun makeContestsUA(): Pair<List<Contest>, List<RaireContestUnderAudit>> {
        if (!quiet) println("ncontests with info = ${infoList.size}")

        val regContests = infoList.filter { it.choiceFunction != SocialChoiceFunction.IRV }.map { info ->
            val oaContest = oaContests[info.id]!!
            val candVotes = oaContest.candVoteTotals().filter { info.candidateIds.contains(it.key) } // remove Write-Ins
            val ncards = oaContest.sumAllCards()
            val useNc = max( ncards, oaContest.Nc())
            Contest(info, candVotes, useNc, oaContest.sumAllCards())
        }

        if (!quiet) {
            println("Regular contests (No IRV)) = ${regContests.size}")
            regContests.forEach { contest ->
                println(contest.show2())
            }
        }
        return Pair(regContests, emptyList()) // no IRV contests
    }

}

////////////////////////////////////////////////////////////////////
// Create a OneAudit where pools are from the redacted cvrs, use pool vote totals for assort average
fun createBoulderElectionOA(
    cvrExportFile: String,
    sovoFile: String,
    auditDir: String,
    riskLimit: Double = 0.03,
    minRecountMargin: Double = .01,
    auditConfigIn: AuditConfig? = null)
{
    //  TODO clearDirectory(Path.of(auditDir))
    val stopwatch = Stopwatch()

    val variation = if (sovoFile.contains("2024")) "Boulder2024" else "Boulder2023"
    val sovo = readBoulderStatementOfVotes(sovoFile, variation)

    println("readDominionCvrExport $cvrExportFile")
    val export: DominionCvrExportCsv = readDominionCvrExportCsv(cvrExportFile, "Boulder")
    val election = BoulderElectionOA(export, sovo)

    val (contests, irvContests) = election.makeContestsUA()

    val publisher = Publisher(auditDir)
    val auditConfig = auditConfigIn ?: AuditConfig(
        AuditType.ONEAUDIT, hasStyles=true, riskLimit=riskLimit, sampleLimit=20000, minRecountMargin=minRecountMargin, nsimEst=10,
        oaConfig = OneAuditConfig(OneAuditStrategyType.optimalComparison, useFirst = true)
    )
    writeAuditConfigJsonFile(auditConfig, publisher.auditConfigFile())

    // TODO add phantoms
    val cards = createSortedCards(election.cvrs, election.cardPools, auditConfig.seed)
    writeAuditableCardCsvFile(cards, publisher.cardsCsvFile())
    println("   writeCvrsCvsFile ${publisher.cardsCsvFile()} cvrs = ${cards.size}")

    /////////////////
    // TODO attach (abstraction of) OneAuditContest ?
    val contestsUA = contests.map {
        OAContestUnderAudit(it, auditConfig.hasStyles)
    }
    addOAClcaAssorters2(contestsUA, election.cardPools.associate { it.poolId to it })

    checkContestsCorrectlyFormed(auditConfig, contestsUA)
    // checkContestsWithCvrs(contestsUA, CvrIteratorAdapter(cards.iterator()), show = false)
    checkVotesVsSovo(contests, sovo, mustAgree = false)

    writeContestsJsonFile(contestsUA + irvContests, publisher.contestsFile())
    println("   writeContestsJsonFile ${publisher.contestsFile()}")
    println("took = $stopwatch\n")
}

fun createSortedCards(cvrs: List<Cvr>, pools: List<CardPool2>, seed: Long) : List<AuditableCard> {
    val prng = Prng(seed)
    val cards = mutableListOf<AuditableCard>()
    var idx = 0
    cvrs.forEach { cards.add(AuditableCard.fromCvr(it, idx++, prng.next())) }
    // add the redacted votes
    pools.forEach { pool ->
        val ncards = pool.maxMinCardsNeeded + pool.adjustCards
        val cleanName = cleanCsvString(pool.poolName)
        repeat(ncards) { poolIndex ->
            //     val location: String, // info to find the card for a manual audit. Aka ballot identifier.
            //    val index: Int,  // index into the original, canonical list of cards
            //    val prn: Long,   // psuedo random number
            //    val phantom: Boolean,
            //    val contests: IntArray, // list of contests on this ballot. TODO optional when !hasStyles ??
            //    val votes: List<IntArray>?, // contest -> list of candidates voted for; for IRV, ranked first to last
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

