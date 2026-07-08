package org.cryptobiotic.rlauxe.ga

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.audit.CardStyle
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ElectionBuilder
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MvrSource
import org.cryptobiotic.rlauxe.audit.PollingMode
import org.cryptobiotic.rlauxe.audit.StyleIF
import org.cryptobiotic.rlauxe.audit.createAuditRecord
import org.cryptobiotic.rlauxe.audit.createElectionRecord
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.core.SocialChoiceFunction
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.oneaudit.setPoolAssorterAverages
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.TransformingIterator
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import java.nio.file.Path
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("CreateGaElection")

// Ga2026Primary from manifests and candidate_totals
class CreateGaElection(
    val electionName: String,
    val auditType: AuditType,
    val gacontests: List<GaContest>,
    val counties: List<GaCounty>,
    val pollingMode: PollingMode? = null
): ElectionBuilder {
    val infos: Map<Int, ContestInfo>
    val ncards: Int

    val contests: List<ContestIF>
    val contestsUA: List<ContestWithAssertions>
    val cardPools: List<CardPool>
    val cardStyles: List<StyleIF>
    val mvrs: List<AuditableCard> // TODO in memory

    init {
        this.ncards = counties.sumOf { it.ncards() }
        contests = makeContests(this.ncards)

        infos = contests.associate { it.id to it.info }

        // only one CardStyle
        this.cardStyles = listOf(CardStyle(1, infos.keys.toSet()))
        val phantoms = makePhantomCvrs(contests) // i dont think there are any

        // each batch is a pool
        var poolid = 0
        val pools = mutableListOf<CardPool>()
        counties.forEach { county: GaCounty ->
            county.batches.forEach { batch: CountyBatch ->
                val contestTabs: Map<Int, ContestTabulation> = contests.map { contest ->
                    // (info: ContestInfo, votes: Map<Int, Int>, ncards: Int)
                    val info = contest.info
                    val votes = batch.candCount.filter { it.key.contest == contest.name }.map { (cand, votes) ->
                        info.candidateNames[cand.candName]!! to votes
                    }.toMap() // candId -> votes
                    val tab = ContestTabulation(contest.info, votes, batch.nballots)
                    Pair(contest.id, tab)
                }.toMap()
                pools.add(
                    CardPool(
                        "${county.countyName}-${batch.name}",
                        poolid++,
                        true,
                        infos,
                        contestTabs,
                        batch.nballots
                    )
                )
            }
        }
        this.cardPools = pools.toList()

        contestsUA = if (auditType.isOA()) makeOneAuditContests(contests, pools) else
            makePollingContests(contests)
        mvrs = makeMvrsFromPools(pools) // once only
    }

    fun makeContests(useNc: Int): List<Contest> {
        var contestId = 1
        return gacontests.map { gacontest ->
            val candidateMap =
                gacontest.candCount.keys.mapIndexed { idx, candidate -> Pair(candidate.candName, idx) }.toMap()
            val info = ContestInfo(
                gacontest.contestName, contestId++, candidateMap, SocialChoiceFunction.RUNOFF,
                nwinners = 2, voteForN = 1, minFraction = 0.5
            )

            val candidateVotes =
                gacontest.candCount.map { (candidate, votes) -> Pair(info.candidateNames[candidate.candName]!!, votes) }
                    .toMap()
            Contest(info, candidateVotes, useNc, ncards)
        }
    }

    fun makeOneAuditContests(
        wantContests: List<ContestIF>, // the contests you want to audit
        cardPools: List<CardPoolIF>,
    ): List<ContestWithAssertions> {

        val contestsUA = wantContests.filter { !it.isIrv() }.map { contest ->
            ContestWithAssertions(contest, isClca = true, hasStyle = true).addStandardAssertions()
        }

        // Its the OA assorters that make this a OneAudit contest
        setPoolAssorterAverages(contestsUA, cardPools)
        return contestsUA
    }

    fun makePollingContests(
        wantContests: List<ContestIF>, // the contests you want to audit
    ): List<ContestWithAssertions> {
        return wantContests.filter { !it.isIrv() }.map { contest ->
            ContestWithAssertions(contest, isClca = false, hasStyle = false).addStandardAssertions()
        }
    }

    override fun electionInfo() = ElectionInfo(
        electionName, auditType, ncards(), contestsUA.size,
        true, mvrSource = MvrSource.testPrivateMvrs, pollingMode = pollingMode
    )

    override fun contestsUA() = contestsUA
    override fun cardStyles() = cardStyles
    override fun cardPools() = cardPools
    override fun unsortedMvrsInternal() = mvrs
    override fun unsortedMvrsExternal() = null

    override fun cards() = createCards(mvrs)
    override fun ncards() = ncards
}

// the card manifest: munge the mvrs
fun createCards(mvrs: List<AuditableCard>): CloseableIterator<AuditableCard> {
    // remove cvrs for cards in the pools
    val mvrIter = Closer(mvrs.iterator())
    val transformer = TransformingIterator<AuditableCard, AuditableCard>(mvrIter) { org ->
        AuditableCard.removeVotesReplaceStyle(org, 1)
    }
    return transformer
}

// this assigns votes, so its the mvrs and can only be done once;
fun makeMvrsFromPools(cardPools: List<CardPool>) : List<AuditableCard> { // contestId -> candidateId -> nvotes
    val cards = mutableListOf<AuditableCard>()
    var cardIndex = 0
    cardPools.forEach { cardPool ->
        var poolIndex = 0
        val poolVunders = cardPool.possibleContests().map {  Pair(it, cardPool.votesAndUndervotes(it)) }.toMap()
        val vunderPool = VunderPool(poolVunders, cardPool.poolName, cardPool.poolId, cardPool.hasExactContests)
        val poolCards = vunderPool.makeCardsForOneAuditPool {
            poolIndex++
            val cvrId = "${cardPool.poolName}-${poolIndex}"
            AuditableCardBuilder(cvrId, null, cardIndex++, 0, phantom = false, styleId=cardPool.poolId, poolId=cardPool.poolId, votesIn=null)
        }
        cards.addAll( poolCards)
    }

    return cards
}

////////////////////////////////////////////////////////////////////
fun createGaElection(
    electionName: String,
    inputDir: String,
    topdir: String,
    auditType: AuditType,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean = true,
    pollingMode: PollingMode? = null,
) {
    val stopwatch = Stopwatch()

    val (contests, counties) = readGaCountyInputCsv(inputDir)

    validateOutputDir(Path.of(topdir))
    val election = CreateGaElection(electionName, auditType, contests, counties, pollingMode)

    createElectionRecord(election, topdir = topdir)
    println("createGaElection took $stopwatch")

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, topdir = topdir)

    if (startFirstRound) {
        val result = startFirstRound(topdir)
        if (result.isErr) logger.error { result.toString() }
    }
    logger.info{"startFirstRound took $stopwatch"}
}
