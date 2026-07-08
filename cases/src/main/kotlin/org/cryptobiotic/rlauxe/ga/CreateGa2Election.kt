package org.cryptobiotic.rlauxe.ga

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditRoundConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.AuditableCard
import org.cryptobiotic.rlauxe.audit.CardPool
import org.cryptobiotic.rlauxe.audit.CardPoolIF
import org.cryptobiotic.rlauxe.audit.Config
import org.cryptobiotic.rlauxe.audit.ElectionBuilder
import org.cryptobiotic.rlauxe.audit.ElectionInfo
import org.cryptobiotic.rlauxe.audit.MvrSource
import org.cryptobiotic.rlauxe.audit.createAuditRecord
import org.cryptobiotic.rlauxe.audit.createElectionRecord
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.auditcenter.munge
import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestIF
import org.cryptobiotic.rlauxe.core.ContestInfo
import org.cryptobiotic.rlauxe.core.ContestWithAssertions
import org.cryptobiotic.rlauxe.estimate.VunderPool
import org.cryptobiotic.rlauxe.oneaudit.setPoolAssorterAverages
import org.cryptobiotic.rlauxe.util.AuditableCardBuilder
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.ContestTabulation
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.TransformingIterator
import org.cryptobiotic.rlauxe.util.makePhantomCvrs
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private val logger = KotlinLogging.logger("CreateGaElection")

class CreateGa2Election(
    val electionName: String,
    val contests: List<Contest>,
    val countias: List<CountyIA>,
    val gacounties: List<GaCounty>, // just need the county ncards = sum of batches
): ElectionBuilder {
    val infos: Map<Int, ContestInfo>
    val ncards: Int

    val contestsUA: List<ContestWithAssertions>
    val cardPools: List<CardPool>  // redacted cvrs

    // val cardStyles: List<StyleIF>
    val mvrs: List<AuditableCard>

    init {
        val contestMap = contests.associateBy { it.name }
        val gacountyMap = gacounties.associateBy { munge(it.countyName.lowercase()) }
        this.ncards = gacounties.sumOf { it.ncards() }
        infos = contests.associate { it.id to it.info }

        // only one CardStyle NO
        // this.cardStyles = listOf(CardStyle(1, infos.keys.toSet()))
        val phantoms = makePhantomCvrs(contests) // i dont think there are any

        // each county is a pool
        var poolid = 0
        val pools = mutableListOf<CardPool>()
        countias.forEach { countyia ->
            val countyNcards = gacountyMap[munge(countyia.county.lowercase())]!!.ncards()

            val contestTabs = mutableMapOf<Int, ContestTabulation>()
            countyia.contests.forEach { (adjname, countyContestia) ->
                val contest = contestMap[adjname]
                if (contest != null) {
                    val info = contest.info
                    // println("${info.name} has ${contestia.candCount} votes")
                    // we want the contest subtotals for this county
                    val votes = countyContestia.candCount.map { (cand, votes) -> info.candidateNames[cand]!! to votes }
                        .toMap() // candId -> votes
                    val tab =
                        ContestTabulation(info, votes, countyContestia.ncards)  // TODO  or is it the county ncards ??
                    contestTabs[contest.id] = tab
                } else {
                    println("Cant find '${adjname}'")
                }
            }
            pools.add(CardPool(countyia.county, poolid++, hasExactContests = false, infos, contestTabs, countyNcards))
        }
        this.cardPools = pools.toList()

        contestsUA = makeOneAuditContests(contests, pools)

        mvrs = makeMvrsFromPools(pools) // once only
    }

    fun makeOneAuditContests(
        wantContests: List<ContestIF>, // the contests you want to audit
        cardPools: List<CardPoolIF>,
    ): List<ContestWithAssertions> {

        val contestsUA = wantContests.filter { !it.isIrv() }.map { contest ->
            ContestWithAssertions(contest, isClca = true, hasStyle = false).addStandardAssertions()
        }

        // Its the OA assorters that make this a OneAudit contest
        setPoolAssorterAverages(contestsUA, cardPools)
        return contestsUA
    }

    override fun electionInfo() = ElectionInfo(
        electionName, AuditType.ONEAUDIT, ncards(), contestsUA.size,
        true, mvrSource = MvrSource.testPrivateMvrs
    )

    override fun contestsUA() = contestsUA
    override fun cardStyles() = cardPools
    override fun cardPools() = cardPools
    override fun unsortedMvrsInternal() = mvrs
    override fun unsortedMvrsExternal() = null

    override fun cards() = createCards2(mvrs)
    override fun ncards() = ncards
}
// TODO combine with createCards()
fun createCards2(mvrs: List<AuditableCard>): CloseableIterator<AuditableCard> {
    // remove cvrs for cards in the pools
    val mvrIter = Closer(mvrs.iterator())
    val transformer = TransformingIterator<AuditableCard, AuditableCard>(mvrIter) { org ->
        AuditableCard.removeVotes(org)
    }
    return transformer
}


////////////////////////////////////////////////////////////////////
// Clca: create simulated cvrs for the redacted groups, for a full CLCA audit with hasStyles=true.
// OA: Create a OneAudit where pools are from the redacted cvrs.
fun createGa2Election(
    electionName: String,
    contestsFile: String,
    inputdir: String,
    topdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean = true,
) {
    val stopwatch = Stopwatch()

    val (gacontests, gacounties) = readGaCountyInputCsv(inputdir)

    val (contests, countias) = makeContestsFromImageAuditFile(contestsFile)
    val election = CreateGa2Election(electionName, contests, countias, gacounties)

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
