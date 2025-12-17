package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.audit.*

import org.cryptobiotic.rlauxe.core.Contest
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.estimate.MultiContestTestDataP
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.raire.RaireContestUnderAudit
import org.cryptobiotic.rlauxe.raire.simulateRaireTestContest
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.tabulateCvrs
import org.cryptobiotic.rlauxe.estimate.makeFuzzedCvrsFrom
import kotlin.io.path.Path
import kotlin.math.min

/** Create a multicontest audit, with fuzzed test data, TODO: stored in private record? */
object RunRlaStartFuzz {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("RunRlaStartFuzz")
        val topdir by parser.option(
            ArgType.String,
            shortName = "in",
            description = "Directory containing test election record"
        ).required()
        val isPolling by parser.option(
            ArgType.Boolean,
            shortName = "isPolling",
            description = "Polling election"
        ).default(false)
        val minMargin by parser.option(
            ArgType.Double,
            shortName = "minMargin",
            description = "contest minimum margin"
        ).default(0.04)
        val fuzzMvrs by parser.option(
            ArgType.Double,
            shortName = "fuzzMvrs",
            description = "Fuzz Mvrs by this factor (0.0 to 1.0)"
        ).default(0.0)
        val pctPhantoms by parser.option(
            ArgType.Double,
            shortName = "pctPhantoms",
            description = "Pct phantoms (0.0 to 1.0)"
        )
        val ncards by parser.option(
            ArgType.Int,
            shortName = "ncards",
            description = "Total number of ballot/cards"
        ).default(10000)
        val ncontests by parser.option(
            ArgType.Int,
            shortName = "ncontests",
            description = "Number of contests"
        ).default(11)
        val addRaireContest by parser.option(
            ArgType.Boolean,
            shortName = "addRaire",
            description = "Add a Raire Contest"
        ).default(false)
        val addRaireCandidates by parser.option(
            ArgType.Int,
            shortName = "rcands",
            description = "Number of candidates for raire contest"
        ).default(5)

        parser.parse(args)
        println("RunRlaStartFuzz on $topdir isPolling=$isPolling minMargin=$minMargin fuzzMvrs=$fuzzMvrs, pctPhantoms=$pctPhantoms, ncards=$ncards ncontests=$ncontests" +
                " addRaire=$addRaireContest addRaireCandidates=$addRaireCandidates")
        if (!isPolling) startTestElectionClca(topdir, minMargin, fuzzMvrs, pctPhantoms, ncards, ncontests, addRaireContest, addRaireCandidates)
            else startTestElectionPolling(topdir, minMargin, fuzzMvrs, pctPhantoms, ncards, ncontests)
    }
}

///////////////////////////////////////////////////////
fun startTestElectionClca(
    topdir: String,
    minMargin: Double,
    fuzzMvrs: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int,
    addRaire: Boolean,
    addRaireCandidates: Int,
) {
    val auditDir = "$topdir/audit"
    clearDirectory(Path(auditDir))

    val config = AuditConfig(
        AuditType.CLCA, hasStyle = true, nsimEst = 100, simFuzzPct = fuzzMvrs, quantile = .20,
    )

    clearDirectory(Path(auditDir))
    val election = TestClcaElection(
        config,
        minMargin,
        pctPhantoms,
        ncards,
        ncontests,
        addRaire,
        addRaireCandidates)

    CreateAuditP("startTestElectionClca", config, election, auditDir = "$topdir/audit", clear = false)
}

class TestClcaElection(
    val config: AuditConfig,
    minMargin: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int,
    addRaire: Boolean,
    addRaireCandidates: Int,
): CreateElectionPIF {
    val contestsUA = mutableListOf<ContestUnderAudit>()
    val allCvrs = mutableListOf<Cvr>()

    init {
        val maxMargin = .10
        val useMin = min(minMargin, maxMargin)
        val phantomPctRange: ClosedFloatingPointRange<Double> =
            if (pctPhantoms == null) 0.00..0.005 else pctPhantoms..pctPhantoms

        val testData =
            MultiContestTestDataP(ncontests, 4, ncards, marginRange = useMin..maxMargin, phantomPctRange = phantomPctRange)
        println("$testData")

        // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
        // includes phantom Cvrs
        allCvrs.addAll(testData.makeCvrsFromContests())
        println("ncvrs (not raire) = ${allCvrs.size}")

        if (addRaire) {
            val (rcontest: RaireContestUnderAudit, rcvrs: List<Cvr>) = simulateRaireTestContest(N=ncards/2, contestId=111, addRaireCandidates, minMargin=.04, quiet = true, hasStyle=config.hasStyle)
            contestsUA.add(rcontest)
            allCvrs.addAll(rcvrs)
        }

        val regularContests = testData.contests.map { ContestUnderAudit(it, isClca=true, hasStyle=config.hasStyle).addStandardAssertions() }
        contestsUA.addAll(regularContests)
        contestsUA.forEach { println("  $it") }
        println()
    }
    override fun populations() = null
    override fun contestsUA() = contestsUA

    override fun cardManifest() : CloseableIterator<AuditableCard> {
        return CvrsWithPopulationsToCardManifest(
            config.auditType,
            Closer(allCvrs.iterator()),
            null, null
        )
    }
}

/////////////////////////////////////////////////////

fun startTestElectionPolling(
    topdir: String,
    minMargin: Double,
    fuzzMvrs: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int = 11,
) {
    val auditDir = "$topdir/audit"
    clearDirectory(Path(auditDir))
    val config = AuditConfig(AuditType.POLLING, hasStyle = true, nsimEst = 100, simFuzzPct = fuzzMvrs)

    clearDirectory(Path(auditDir))
    val election = TestPollingElection(
        auditDir,
        config,
        minMargin,
        fuzzMvrs,
        pctPhantoms,
        ncards,
        ncontests,
    )

    // dont clear, weve already started writing
    CreateAuditP("startTestElectionPolling", config, election, auditDir = auditDir, clear=false)
}

class TestPollingElection(
    val auditdir: String,
    val config: AuditConfig,
    minMargin: Double,
    fuzzMvrs: Double,
    pctPhantoms: Double?,
    ncards: Int,
    ncontests: Int,
): CreateElectionPIF {
    val contestsUA = mutableListOf<ContestUnderAudit>()
    val cvrs: List<Cvr>
    val testMvrs: List<Cvr>
    val pops: List<Population>

    init {
        val maxMargin = .08
        val useMin = min(minMargin, maxMargin)
        val phantomPctRange: ClosedFloatingPointRange<Double> =
            if (pctPhantoms == null) 0.00..0.005 else pctPhantoms..pctPhantoms
        val testData = MultiContestTestDataP(ncontests, 4, ncards, marginRange = useMin..maxMargin, phantomPctRange = phantomPctRange)

        val contests: List<Contest> = testData.contests
        println("Start testPersistentWorkflowPolling $testData")
        contests.forEach { println("  $it") }
        println()
        val pop = Population("all", 1, contests.map { it.id }.toIntArray(), exactContests=false)
        pops = listOf(pop)

        // Synthetic cvrs for testing, reflecting the exact contest votes, plus undervotes and phantoms.
        cvrs = testData.makeCvrsFromContests()
        testMvrs = makeFuzzedCvrsFrom(contests.map{ it.info() }, cvrs, fuzzMvrs) // ??

        val makum = ContestUnderAudit.make(testData.contests, cardManifest(), isClca=false, hasStyle=false)
        // not setting Npop, so it defaults to Nc
        //val regularContests = testData.contests.map {
        //    ContestUnderAudit(it, isClca=true, hasStyle=config.hasStyle).addStandardAssertions()
        //}
        contestsUA.addAll(makum)
        contestsUA.forEach { println("  $it") }
        println()

        val infos = contestsUA().map { it.contest.info() }.associateBy { it.id }
        val mvrTabs = tabulateCvrs(testMvrs.iterator(), infos)
        println("testMvrs = ${mvrTabs}")
        println()

        // have to write this here, where we know the mvrs
        writeUnsortedMvrs(Publisher(auditdir), testMvrs, seed=config.seed)
    }

    override fun populations() = pops
    override fun contestsUA() = contestsUA

    override fun cardManifest() : CloseableIterator<AuditableCard> {
        return CvrsWithPopulationsToCardManifest(
            config.auditType,
            Closer(cvrs.iterator()),
            null,
            populations(),
        )
    }
}
