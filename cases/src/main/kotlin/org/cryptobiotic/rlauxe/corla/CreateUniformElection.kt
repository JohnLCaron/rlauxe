package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeElectionInfoJsonFile
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.preAuditContestCheck
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("ColoradoOneAudit")

open class CreateUniformElection (
    val stateElection: CountyContestBuilder,
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

        val builders: List<CorlaContestBuilder> = stateElection.corlaContestBuilders
        val npopMap: Map<Int, Int> = builders.associate { it.info.id to it.Npop!! }.toMap()

        contestsUA = ContestWithAssertions.make(stateElection.contests, npopMap, auditType.isClca(), hasStyle)
    }

    override fun electionInfo() =
        ElectionInfo(
            name ?: "Corla24$auditType$pollingMode", auditType, ncards(),
            contestsUA.size, pollingMode = pollingMode
        )

    override fun cardStyles(): List<StyleIF>? = null
    override fun cardPools() = null
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
fun createUniformElection(
    topdir: String,
    auditdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    name: String? = null,
) {
    val stopwatch = Stopwatch()

    val (mergedContestInfo: List<MergedContestInfo>, mergedCountyInfo, statewideContests) = mergeContestInfo()

    val countyElection = CountyContestBuilder()

    val election =
        CreateUniformElection(countyElection, creation.auditType, auditdir, pollingMode=null, name=name,
        hasStyle = roundConfig.sampling.sampling == Sampling.consistent)

    // skip the simulated cvrs
    // createElectionRecord(election, auditDir = auditdir, clear = false)
    val contestsUA = election.contestsUA()

    val results = VerifyResults()
    results.addMessage("---VerifyElection on $auditdir")
    preAuditContestCheck(contestsUA, results)

    // write contests
    val publisher = Publisher(auditdir)
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"createElectionRecord write ${contestsUA.size} contests to ${publisher.contestsFile()}"}

    // write config
    val electionInfo = election.electionInfo()
    writeElectionInfoJsonFile(electionInfo, publisher.electionInfoFile())
    logger.info{"createElectionRecord writeElectionInfoJsonFile to ${publisher.electionInfoFile()}\n  $electionInfo"}

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, auditDir = auditdir, externalSortDir = null, sortManifest = false)

    writeCountyData(topdir, Colorado2024Input.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, Colorado2024Input.countyContestMap)

    println("that took $stopwatch")
}


