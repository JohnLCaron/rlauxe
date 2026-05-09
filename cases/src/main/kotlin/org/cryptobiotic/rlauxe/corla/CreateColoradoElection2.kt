package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makeCvrsForOnePool
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.preAuditContestCheck
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("ColoradoOneAudit")

open class CreateColoradoElection2 (
    val stateElection: ColoradoCountyElection,
    val auditType: AuditType,
    val auditdir: String,
    val hasStyle: Boolean,
    val pollingMode: PollingMode?,
    val name: String? = null,
): ElectionBuilder {
    val publisher = Publisher(auditdir)
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>

    init {
        // have to save the mvrs and generate the cardManifest from them.
        ncards = 0 // createAndSaveUnsortedMvrs(stateElection.contests, stateElection.cardPools, publisher)

        /* TODO Npop >= Nc
        val npopMap: Map<Int, Int> = if ((auditType.isPolling() && pollingMode!!.withoutBatches())) {
            countyElection.contests.associate { it.id to ncards } // then the population is the entire set of cards. (wont go well)
        } else {
            // read them back in as an Iterator, so we dont have to read all into memory
            val infos = countyElection.contests.map { it.info() }.associateBy { it.id }
            val mvrs: CloseableIterator<CardWithBatchName> = readCardsCsvIterator(publisher.unsortedMvrsFile())
            val auditableCardIter: CloseableIterator<AuditableCard> =
                MergeBatchesIntoCardManifestIterator(mvrs, countyElection.cardPools)
            // are we handling the batches correctly using mvrs?
            val (manifestTabs, count) = tabulateCardsAndCount(auditableCardIter, infos)
            require(ncards == count)
            manifestTabs.mapValues { it.value.ncardsTabulated }
        } */

        val builders: List<CorlaContestBuilder> = stateElection.corlaContestBuilders
        val npopMap: Map<Int, Int> = builders.associate { it.info.id to it.Npop!! }.toMap()

        contestsUA = ContestWithAssertions.make(stateElection.contests, npopMap, auditType.isClca(), hasStyle)
    }

    override fun electionInfo() =
        ElectionInfo(
            name ?: "Corla24$auditType$pollingMode", auditType, ncards(),
            contestsUA.size, pollingMode = pollingMode
        )

    override fun cardStyles(): List<StyleIF>? = stateElection.cardPools
    override fun cardPools() = stateElection.cardPools
    override fun contestsUA() = contestsUA
    override fun ncards() = ncards

    override fun cards(): CloseableIterator<CardWithBatchName> {
        val unsortedMvrs = readCardsCsvIterator(publisher.unsortedMvrsFile())
        return TransformingIterator(unsortedMvrs) { mvr ->
            when {
                mvr.phantom -> mvr
                auditType.isClca() -> mvr.copy(poolId = null, styleName = CardStyle.fromCvr)
                (auditType.isPolling() && pollingMode!!.withoutBatches()) -> mvr.copy(
                    votes = null,
                    styleName = "OneBatch",
                    poolId = 0
                )

                (auditType.isPolling()) -> mvr.copy(votes = null)
                else -> throw IllegalStateException("Unknown what to do with mvr: $mvr")
            }
        }
    }

    // StartAuditFirstRound will create the sorted MVRs
    override fun createUnsortedMvrsExternal() = readCardsCsvIterator(publisher.unsortedMvrsFile())
    override fun createUnsortedMvrsInternal() = null
}


////////////////////////////////////////////////////////////////////
// Create audit using mvrs from Corla, dont write cards (!)
fun createColoradoElection2(
    topdir: String,
    auditdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean = true,
    name: String? = null,
) {
    val stopwatch = Stopwatch()
    writeCountyData(topdir)

    val countyElection = ColoradoCountyElection()

    val election =
        CreateColoradoElection2(countyElection, creation.auditType, auditdir, pollingMode=null, name=name,
        hasStyle = roundConfig.sampling.sampling == Sampling.consistent)

    // skip the simulated cvrs
    //     createElectionRecord(election, auditDir = auditdir, clear = false)
    val contestsUA = election.contestsUA()

    val results = VerifyResults()
    results.addMessage("---VerifyElection on $auditdir")
    preAuditContestCheck(contestsUA, results)

    // write contests
    val publisher = Publisher(auditdir)
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"createElectionRecord write ${contestsUA.size} contests to ${publisher.contestsFile()}"}

    // write config
    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, auditDir = auditdir, externalSortDir = null, sortManifest = false)

    println("that took $stopwatch")
}

