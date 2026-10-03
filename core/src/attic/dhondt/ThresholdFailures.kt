package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.Int

private val logger = KotlinLogging.logger("ThresholdFailure")

/* obsolete ?
class RelaxedThresholdAssertions(val orgContest: DhondtContest,
                                 val fromAssorters: List<AssorterIF>,
                                 override val Npop: Int,
                                 override val nsamples: Int,
                                 override val alpha: Double,
                                 val failures: List<DhondtFailure>,
                                 val tfailures: List<ThresholdFailure>,
): RelaxedAssertionsIF {
    val info = orgContest.info()
    var contestRanges = ContestRanges(orgContest, failures) // baseline, no threshold failures, may have DH failures
    var altContest: DhondtContest
    var altRelaxed: RelaxedDhAssertions? = null

    init {
        logger.debug { "Contest ${info.name} haveSampleSize=${nsamples}" }

        if (tfailures.size > 1) throw RuntimeException("Can only handle 1 threshold failure")
        val tfailure = tfailures.first()

        val belowThreshold = orgContest.partiesBelowThreshold - setOf(tfailure.btAssorter.partyId)

        altContest = DhondtBuilder(
            name = info.name,
            id = info.id,
            partyBs = orgContest.parties.map { DhondtPartyBuilder(it) },
            nseats = info.nwinners,
            Nc = orgContest.Nc,
            undervotes = orgContest.undervotes,
            minFraction = info.minFraction!!,
            thresholdOverride = belowThreshold).build()

        // what is the candidate range from regular and alt?

        // could have failures
        val failures = findDhondtFailures(altContest, altContest.assorters, Npop, nsamples, alpha)
        if (failures.isNotEmpty()) {
            altRelaxed = RelaxedDhAssertions(
                altContest,
                // altContest.assorters, TODO
                Npop, nsamples, alpha,
                failures
            )
            val altRanges = altRelaxed!!.contestRanges()
            contestRanges.mergeAltContest(altRanges) // or something
            println(" do something")
        } else {
            println(" do something else")
        }

        //println()
       // if (candidateRanges != null) println(candidateRanges!!.show())
    }

    override fun altContest() = orgContest // TODO
    override fun assortersForProof() = fromAssorters // TODO
    override fun contestRanges() = contestRanges
    override fun failures() = failures

    override fun show() = buildString {
        appendLine("Failures")
        appendLine(DhondtFailure.header())
        tfailures.forEach { appendLine(it) }
        appendLine()
        append(showCandidateSeatOrder(orgContest, fromAssorters, nsamples, alpha))
        appendLine("\nAltContest")
        append(showCandidateSeatOrder(altContest, altContest.assorters, nsamples, alpha))
        if (altRelaxed != null) {
            appendLine("\nAltRelaxedAssertions")
            altRelaxed!!.show()
        }
    }

} */