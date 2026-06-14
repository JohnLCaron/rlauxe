package org.cryptobiotic.rlauxe.auditcenter

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.audit.*
import org.cryptobiotic.rlauxe.audit.AuditableCardIF
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.corla.CountyContestBuilder
import org.cryptobiotic.rlauxe.corla.writeCountyContestData
import org.cryptobiotic.rlauxe.corla.writeCountyData
import org.cryptobiotic.rlauxe.dominion.DominionCvrConverter
import org.cryptobiotic.rlauxe.dominion.DominionCvrExport
import org.cryptobiotic.rlauxe.dominion.DominionCvrExportReader
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.utils.tabulateCardsAndCount
import kotlin.Int
import kotlin.String

private val logger = KotlinLogging.logger("CreateColoradoElectionWithCvrs")

open class CountyElectionWithCvrs (
    val counties: Map<String, String>, // countyName -> exportFile
    val coloradoInput: ColoradoInput,
    val contestBuilder: CountyContestBuilder, // TODO how does export schema compare to this?
    val auditdir: String,
    val hasStyle: Boolean,
    val name: String,
): ElectionBuilder {
    val ncards: Int
    val contestsUA: List<ContestWithAssertions>
    val cardStyles = mutableListOf<CardStyle>()
    val countyCardPools = mutableListOf<CountyPoolsIF>()

    init {
        val contests = contestBuilder.contests
        val infos = contests.map { it.info() }.associateBy{ it.id }
        val phantoms = makePhantomCards(contests, 0) // TODO
        var totalCardCount = 0
        val totalTabs = mutableMapOf<Int, ContestTabulation>()

        counties.forEach { (county, exportFile) ->
            val export: DominionCvrExport = DominionCvrExportReader(exportFile).read()
            val dominionConverter = DominionCvrConverter(county, export, contests, coloradoInput)

            val exportCvrs: List<AuditableCardM> = export.cvrs.map { dominionConverter.convertToCard(it) }
            val cardIter = Closer (exportCvrs.iterator() )
            val (tabs, cardCount) = tabulateCardsAndCount(cardIter, infos)

            totalCardCount += cardCount
            totalTabs.sumContestTabulations(tabs)

            cardStyles.addAll(dominionConverter.cardStyles.values.toList())
            countyCardPools.add(
                CountyPools(county, 1, tabs.values.toList(), cardCount, cardStyles)
            )
        }

        this.ncards = totalCardCount
        val npopMap = totalTabs.mapValues { it.value.ncardsTabulated }
        contestsUA = ContestWithAssertions.make(contestBuilder.contests, npopMap, true, hasStyle)
    }

    override fun electionInfo() =
        ElectionInfo(
            name, AuditType.CLCA, ncards(),
            contestsUA.size,
            mvrSource = MvrSource.testPrivateMvrs
        )

    override fun contestsUA() = contestsUA
    override fun cardStyles() = cardStyles
    override fun cardPools() = null
    override fun countyCardPools(): List<CountyPoolsIF>? = countyCardPools

    override fun createUnsortedMvrsInternal() = null // allCards // mvrsToAuditableCardsListM(allCvrs, cardStyles)
    override fun createUnsortedMvrsExternal() = null

    override fun cards() = Closer (emptyList<AuditableCardIF>().iterator() )
    override fun ncards() = ncards
}

////////////////////////////////////////////////////////////////////

fun countyElectionWithCvrs(
    counties: Map<String, String>, // countyName -> exportFile
    coloradoInput: ColoradoInput,
    topdir: String,
    creation: AuditCreationConfig,
    roundConfig: AuditRoundConfig,
    startFirstRound: Boolean = true,
    name: String,
) {
    val stopwatch = Stopwatch()
    val auditdir = "$topdir/audit"

    val contestBuilder = CountyContestBuilder(coloradoInput)

    val election =
        CountyElectionWithCvrs(counties, coloradoInput, contestBuilder,
            auditdir, name=name, hasStyle = roundConfig.sampling.sampling == Sampling.consistent)

    createElectionRecord(election, auditDir = auditdir, roundConfig.sampling, clear = false)
    val config = Config(election.electionInfo(), creation, roundConfig)

    createAuditRecord(config, election, auditDir = auditdir, externalSortDir = topdir)

    writeCountyData(topdir, coloradoInput.strataMap.values.toList())
    val contestMap = election.contestsUA.associate { it.contest.info().name to it }
    writeCountyContestData(topdir, contestMap, coloradoInput.countyContestTabs)

    if (startFirstRound) {
        val result = startFirstRound(auditdir)
        if (result.isErr) logger.error { result.toString() }
    }
    logger.info { "createColorado2020 took $stopwatch" }
}

