package org.cryptobiotic.rlauxe.datadrive

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.corla.CountyContestBuilder
import org.cryptobiotic.rlauxe.corla.writeCountyContestData
import org.cryptobiotic.rlauxe.corla.writeCountyData
import org.cryptobiotic.rlauxe.dominion.DominionCvrConverter
import org.cryptobiotic.rlauxe.dominion.DominionCvrExport
import org.cryptobiotic.rlauxe.dominion.makeCardStyles
import org.cryptobiotic.rlauxe.dominion.readDominionCvrExportCsv
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.utils.tabulateNpopsFromCards
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("ColoradoOneAudit")

open class CreateColoradoElection (
    val county: String,
    val coloradoInput: ColoradoInput,
    val export: DominionCvrExport, // TODO use interator, currently only Boulder County
    val contestBuilder: CountyContestBuilder, // TODO how does export schema compare to this?
    val auditdir: String,
    val hasStyle: Boolean,
    val name: String? = null,
): ElectionBuilder {
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>
    val cardStyles: List<CardStyle>
    val allCards: List<AuditableCardM>

    init {
        val cardStyleMap = export.makeCardStyles(county)
        cardStyles = cardStyleMap.values.toList()
        val styleNameMap = cardStyleMap.mapValues { it.value.name }

        val contests = contestBuilder.contests
        val dominionConverter = DominionCvrConverter(export, contests, coloradoInput, styleNameMap)
        // why cvrs, why not cards ??
        val exportCvrs: List<AuditableCardM> = export.cvrs.map { dominionConverter.convertToCard(it) }

        val infos = contests.map { it.info() }
        val phantoms = makePhantomCards(contests, 0)
        allCards = exportCvrs + phantoms
        this.ncards = allCards.size

        val cardIter = Closer (allCards.iterator() )
        val (npopMap, count) = tabulateNpopsFromCards(cardIter, infos) // TODO check this, seems wrong
        contestsUA = ContestWithAssertions.make(contestBuilder.contests, npopMap, true, hasStyle)
    }

    override fun electionInfo() =
        ElectionInfo(
            name ?: "Colorado2020", AuditType.CLCA, ncards(),
            contestsUA.size,
            mvrSource = MvrSource.testPrivateMvrs
        )

    override fun contestsUA() = contestsUA
    override fun cardStyles() = cardStyles
    override fun cardPools() = null
    override fun createUnsortedMvrsInternal() = allCards // mvrsToAuditableCardsListM(allCvrs, cardStyles)
    override fun createUnsortedMvrsExternal() = null

    override fun cards() = Closer (allCards.iterator() )
    override fun ncards() = ncards

    /* fun createCards(): CloseableIterator<AuditableCardM> {
        // same cvrs for CLCA and OneAudit
        return CvrsToCardStylesIterator(
            AuditType.CLCA,
            Closer(allCvrs.iterator()), // use the mvrs as the cvrs
            null,
            styles = cardStyles // if (auditType.isClca()) null else cardPoolBuilders // integrate OA pools
        )
    } */
}

////////////////////////////////////////////////////////////////////

fun createColorado2020(
    county: String,
    topdir: String,
    cvrExportFile: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean = true,
    name: String? = null,
) {
    val stopwatch = Stopwatch()
    val auditdir = "$topdir/audit"

    val contestBuilder = CountyContestBuilder(Colorado2020Input)
    val export: DominionCvrExport = readDominionCvrExportCsv(cvrExportFile, county)

    val election =
        CreateColoradoElection(county,Colorado2020Input, export, contestBuilder,
            auditdir, name=name, hasStyle = roundConfig.sampling.sampling == Sampling.consistent)

    createElectionRecord(election, auditDir = auditdir, roundConfig.sampling, clear = false)
    val config = Config(election.electionInfo(), creation, roundConfig)

    createAuditRecord(config, election, auditDir = auditdir, externalSortDir = topdir)

    writeCountyData(topdir, Colorado2020Input.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, Colorado2020Input.countyContestMap)

    if (startFirstRound) {
        val result = startFirstRound(auditdir)
        if (result.isErr) logger.error { result.toString() }
    }
    logger.info { "createColorado2020 took $stopwatch" }
}

