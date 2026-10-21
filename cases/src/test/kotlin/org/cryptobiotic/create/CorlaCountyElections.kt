package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditCreationConfig
import org.cryptobiotic.rlauxe.audit.AuditType
import org.cryptobiotic.rlauxe.audit.runAllRoundsAndVerify
import org.cryptobiotic.rlauxe.audit.startFirstRound
import org.cryptobiotic.rlauxe.corlaInput.Statewide2024General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2026PwithCvrs
import org.cryptobiotic.rlauxe.corlaInput.StatewideInput
import org.cryptobiotic.rlauxe.boulder.Boulder24Input
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.corlaInput.CountyInput
import org.cryptobiotic.rlauxe.corlaCounty.ElectionVariantEnum
import org.cryptobiotic.rlauxe.corlaCounty.createCorlaCountyElection
import org.cryptobiotic.rlauxe.corlaInput.CountyInput2026
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020General
import org.cryptobiotic.rlauxe.corlaInput.Statewide2020Redacted
import org.cryptobiotic.rlauxe.persist.AuditRecord
import kotlin.test.Test

// create election out of a single county. Creates AuditRecord not CountyAuditRecord.
class CorlaCountyElections {

    @Test
    fun createOne() {
        val toptopdir = "$cases/corlaCounty/morgan26p"
        val stateInput = Statewide2026PwithCvrs()
        val population = stateInput.strataPopulation()["Morgan"]!!
        val countyInput = CountyInput2026("Morgan", population)

        // fun createCorlaCountyElection(
        //    countyInput: CountyInput,
        //    stateInput: StatewideInput,
        //    topdir: String,
        //    creation: AuditCreationConfig,
        //    roundConfig: AuditRoundConfig,
        //    mvrSource: MvrSource = MvrSource.testPrivateMvrs,
        //    hasStyle: Boolean = true, // TODO wtf ??
        //    variant: ElectionVariantEnum,
        createCorlaCountyElection(
            countyInput,
            stateInput,
            topdir = "$toptopdir/styles",
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.Styles,
        )
    }

    @Test
    fun createBoulder26p() {
        val stateInput = Statewide2026PwithCvrs()
        val population = stateInput.strataPopulation()["Boulder"]!!
        val countyInput = CountyInput2026("Boulder", population)

        createCorlaCountyVariants("$cases/corlaCounty/boulder26p",
            countyInput,
            stateInput,
        )
    }

    @Test
    fun createBoulder24() {
        createCorlaCountyVariants("$cases/corlaCounty/boulder24",
            Boulder24Input(),
            Statewide2024General()
        )
    }

    @Test
    fun createBoulder20() {
        val stateInput = Statewide2020General()
        createCorlaCountyVariants("$cases/corlaCounty/boulder20",
            stateInput.corlaCountyInput("Boulder")!!,
            stateInput,
        )
    }

    @Test
    fun createPitkin20() {
        val stateInput = Statewide2020General()
        createCorlaCountyVariants("$cases/corlaCounty/pitkin20",
            stateInput.corlaCountyInput("Pitkin")!!,
            stateInput,
        )
    }

    @Test
    fun createDenver20r() {
        val stateInput = Statewide2020Redacted()
        createCorlaCountyVariants("$cases/corlaCounty/denver20r",
            stateInput.corlaCountyInput("Denver")!!,
            stateInput,
        )
    }

    @Test
    fun createEagle20r() {
        val stateInput = Statewide2020Redacted()
        createCorlaCountyVariants("$cases/corlaCounty/eagle20r",
            stateInput.corlaCountyInput("Eagle")!!,
            stateInput,
        )
    }

    @Test
    fun createLaPlata26p() {
        val stateInput = Statewide2026PwithCvrs()
        val population = stateInput.strataPopulation()["La Plata"]!!
        val countyInput = CountyInput2026("La Plata", population)

        createCorlaCountyVariants("$cases/corlaCounty/laplata26p",
            countyInput,
            stateInput,
        )
    }

    @Test
    fun createMorgan26p() {
        val stateInput = Statewide2026PwithCvrs()
        val population = stateInput.strataPopulation()["Morgan"]!!
        val countyInput = CountyInput2026("Morgan", population)
        createCorlaCountyVariants("$cases/corlaCounty/morgan26p",
            countyInput,
            stateInput,
        )
    }

    @Test
    fun createWeld26p() {
        val stateInput = Statewide2026PwithCvrs()
        val population = stateInput.strataPopulation()["Weld"]!!
        val countyInput = CountyInput2026("Weld", population)
        createCorlaCountyVariants("$cases/corlaCounty/weld26p",
            countyInput,
            stateInput,
        )
    }

    fun createCorlaCountyVariants(toptopdir: String, countyInput: CountyInput, stateInput: StatewideInput) {
        /* redacted ballots are simulated
        createCorlaCountyElection(
            countyInput,
            stateInput,
            topdir = "$toptopdir/sim",
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.Sim,
        )

        // redacted ballots are turned into phantoms
        createCorlaCountyElection(
            countyInput,
            stateInput,
            topdir = "$toptopdir/phantoms",
            creation = AuditCreationConfig(AuditType.CLCA, riskLimit = .03),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.Phantoms,
        ) */

        // the ballots for each redacted group are placed in one pool
        createCorlaCountyElection(
            countyInput,
            stateInput,
            topdir = "$toptopdir/onepool",
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.OnePool,
        )

        /* the ballots for each redacted group are placed in seperate pools.
        // to use this the redacted ballots would have to be identified by ballot style and identifier.
        createCorlaCountyElection(
            countyInput,
            stateInput,
            topdir = "$toptopdir/styles",
            creation = AuditCreationConfig(AuditType.ONEAUDIT, riskLimit = .03),
            roundConfig = corlaRoundSettings(),
            variant = ElectionVariantEnum.Styles,
        ) */
    }

    @Test
    fun compareToPhantoms() {
        val toptopdir = "$cases/corlaCounty/boulder24"
        createBoulder24()
        compareVariants(toptopdir, "phantoms", listOf("sim", "onepool", "styles"))
    }

    @Test
    fun compareToSim() {
        val toptopdir = "$cases/corlaCounty/boulder24"
        createBoulder24()
        compareVariants(toptopdir, "sim", listOf("phantoms", "onepool", "styles"))
    }
}

// we want to get the audited contests from one variant, and run that with the other variants
fun compareVariants(toptopdir: String, from: String, compareOthers: List<String>) {
    val results = mutableListOf<String>()

    val fromtopdir = "$toptopdir/$from"
    startFirstRound(fromtopdir) // does this work if first round already ran ?
    runAllRoundsAndVerify(fromtopdir, 5, false)

    // fetch list of successful contests
    val fromAuditRecord = AuditRecord.read(fromtopdir)!! as AuditRecord
    val (successfulContests, nmvrs) = fromAuditRecord.contestRounds(successOnly = true)
    val result = "$from has ${successfulContests.size} contests, nmvrs = $nmvrs"
    results.add(result)
    println(">>>>>>>>>>>>>>>>>>>>>>> $result")

    // run the other variants with those contests
    val onlyContests = successfulContests.keys.toList()
    compareOthers.forEach { other ->
        val othertopdir = "$toptopdir/$other"

        startFirstRound(othertopdir, onlyContests = onlyContests)
        runAllRoundsAndVerify(othertopdir, 5, false)

        val fromAuditRecord = AuditRecord.read(othertopdir)!! as AuditRecord
        val (otherContests, nmvrs)  = fromAuditRecord.contestRounds(successOnly = true)
        val result = "$other has ${otherContests.size} contests, nmvrs=$nmvrs"
        results.add(result)
        println(">>>>>>>>>>>>>>>>>>>>>>> $result")
    }
    results.forEach { println(it)}
}