package org.cryptobiotic.rlauxe.corla

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIteratorM
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeElectionInfoJsonFile
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.preAuditContestCheck
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("ColoradoOneAudit")

open class CreateUniformElection (
    val coloradoInput: ColoradoInput,
    val stateElection: CountyContestBuilder,
    val auditType: AuditType,
    val auditdir: String,
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

        contestsUA = ContestWithAssertions.make(stateElection.contests, npopMap, auditType.isClca(), hasStyle=false)
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

    override fun cards(): CloseableIterator<AuditableCardM> {
        val unsortedMvrs: CloseableIterator<AuditableCardM> = readCardsCsvIteratorM(publisher.unsortedMvrsFile(), styles = null)

        return TransformingIterator(unsortedMvrs) { cardm ->
            when {
                cardm.phantom -> cardm
                auditType.isClca() -> cardm.copy(poolId = null, styleName = CardStyle.fromCvr) // TODO fishy
                // auditType.isClca() -> cardm.copy(poolId = null)
                (auditType.isPolling() && pollingMode!!.withoutBatches()) -> cardm.copy(
                    contestIds = IntArray(0), // might be safer to provide a function to remove all three
                    styleName = "OneBatch",
                    poolId = 0
                )

                (auditType.isPolling()) -> cardm.copy(contestIds = IntArray(0))
                else -> throw IllegalStateException("Unknown what to do with mvr: $cardm")
            }
        }
    }

    // StartAuditFirstRound will create the sorted MVRs
    override fun createUnsortedMvrsExternal() = readCardsCsvIteratorM(publisher.unsortedMvrsFile(), styles = null)
    override fun createUnsortedMvrsInternal() = null
}

////////////////////////////////////////////////////////////////////
// Create audit using mvrs from Corla, dont write cards (!)
fun createUniformElection(
    topdir: String,
    auditdir: String,
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
        CreateUniformElection(coloradoInput, countyElection, creation.auditType, auditdir, pollingMode=null, name=name)

    // skip the simulated cvrs
    // createElectionRecord(election, auditDir = auditdir, clear = false)
    val contestsUA = election.contestsUA()

    val results = VerifyResults()
    results.addMessage("---VerifyElection on $auditdir")
    preAuditContestCheck(contestsUA, roundConfig.sampling, results)

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

    writeCountyData(topdir, coloradoInput.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, coloradoInput.countyContestMap)

    if (startFirstRound) {
        val result = startFirstRound(auditdir)
        if (result.isErr) logger.error { result.toString() }
        logger.info { "createCorla took $stopwatch" }
    }

    println("that took $stopwatch")
}


