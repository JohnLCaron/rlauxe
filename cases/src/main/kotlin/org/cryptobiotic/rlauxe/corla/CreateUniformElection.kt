package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.auditcenter.ColoradoInput
import org.cryptobiotic.rlauxe.auditcenter.CorlaContestBuilder
import org.cryptobiotic.rlauxe.auditcenter.CountyContestBuilder
import org.cryptobiotic.rlauxe.auditcenter.writeCountyContestData
import org.cryptobiotic.rlauxe.auditcenter.writeCountyData
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

// TODO replicate in CountyElection
open class CreateUniformElection (
    val coloradoInput: ColoradoInput,
    val stateElection: CountyContestBuilder,
    val auditType: AuditType,
    val topdir: String,
    val pollingMode: PollingMode?,
    val name: String? = null,
): ElectionBuilder {
    val publisher = Publisher(topdir)
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>

    init {
        // have to save the mvrs and generate the cardManifest from them.
        ncards = 0 // createAndSaveUnsortedMvrs(stateElection.contests, stateElection.cardPools, publisher)

        val builders: List<CorlaContestBuilder> = stateElection.corlaContestBuilders
        val npopMap: Map<Int, Int> = builders.associate { it.info.id to it.strata.ballotCardCount }.toMap()

        contestsUA = ContestWithAssertions.make(stateElection.contests(npopMap), npopMap, auditType.isClca(), hasStyle=false)
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

    override fun cards(): CloseableIterator<AuditableCard> {
        val unsortedMvrs: CloseableIterator<AuditableCard> = readCardsCsvIterator(publisher.unsortedMvrsFile(), styles = null)

        return TransformingIterator(unsortedMvrs) { cardm ->
            when {
                cardm.phantom -> cardm
                auditType.isClca() -> cardm.copy(poolId = null, styleId = CardStyle.fromCvrStyle.id) // TODO fishy
                // auditType.isClca() -> cardm.copy(poolId = null)
                (auditType.isPolling() && pollingMode!!.withoutBatches()) -> cardm.copy(
                    contestIds = IntArray(0), // might be safer to provide a function to remove all three
                    styleId = 0, // TODO "OneBatch",
                    poolId = 0
                )

                (auditType.isPolling()) -> cardm.copy(contestIds = IntArray(0))
                else -> throw IllegalStateException("Unknown what to do with mvr: $cardm")
            }
        }
    }

    // StartAuditFirstRound will create the sorted MVRs
    override fun unsortedMvrsExternal() = readCardsCsvIterator(publisher.unsortedMvrsFile(), styles = null)
    override fun unsortedMvrsInternal() = null
}

////////////////////////////////////////////////////////////////////
// Create audit using mvrs from Corla, dont write cards (!)
fun createUniformElection(
    topdir: String,
    coloradoInput: ColoradoInput,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean = true,
    name: String? = null,
) {
    val stopwatch = Stopwatch()
    require (roundConfig.sampling.sampling == Sampling.uniform)

    val countyElection = CountyContestBuilder(coloradoInput)

    val election =
        CreateUniformElection(coloradoInput, countyElection, creation.auditType, topdir, pollingMode=null, name=name)

    // skip the simulated cvrs
    val contestsUA = election.contestsUA()

    val results = VerifyResults()
    results.addMessage("---VerifyElection on $topdir")
    preAuditContestCheck(contestsUA, roundConfig.sampling, results)

    // write contests
    val publisher = Publisher(topdir)
    writeContestsJsonFile(contestsUA, publisher.contestsFile())
    logger.info{"createElectionRecord write ${contestsUA.size} contests to ${publisher.contestsFile()}"}

    // write config
    val electionInfo = election.electionInfo()
    writeElectionInfoJsonFile(electionInfo, publisher.electionInfoFile())
    logger.info{"createElectionRecord writeElectionInfoJsonFile to ${publisher.electionInfoFile()}\n  $electionInfo"}

    val config = Config(election.electionInfo(), creation, roundConfig)
    createAuditRecord(config, election, topdir = topdir, externalSortDir = null, sortManifest = false)

    writeCountyData(topdir, coloradoInput.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, coloradoInput)

    if (startFirstRound) {
        val result = startFirstRound(topdir)
        if (result.isErr) logger.error { result.toString() }
        logger.info { "createCorla took $stopwatch" }
    }

    println("that took $stopwatch")
}


