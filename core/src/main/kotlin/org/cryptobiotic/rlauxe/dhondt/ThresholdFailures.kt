package org.cryptobiotic.rlauxe.dhondt

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.betting.estSampleSizeStandardBet
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.core.BelowThreshold
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.Int

private val logger = KotlinLogging.logger("ThresholdFailure")

class ThresholdFailure(
    val dcontest: DhondtContest,
    val Npop: Int,
    val assorter: BelowThreshold, // what about AboveThreshold ??
    val risk: Double,
    val samplesUsed: Int,
    val alpha: Double
) {
    val noerror = assorter.noerror(true)
    val nmvrs = samplesUsed ?: 0

    fun estMvrs(): Int {
        return estSampleSizeStandardBet(Npop, noerror, alpha)
    }

    override fun toString() = buildString {
        append("${assorter.shortName()}: ")
        append(" ${nfn(dcontest.marginInVotes(assorter), 7)}, ${dfn(noerror, 6)}, ")
        append(" ${nfn(estMvrs(), 8)}, ${nfn(nmvrs, 8)},    ${dfn(risk, 4)},")
    }
}

class RelaxedThresholdAssertions(override val dcontest: DhondtContest,
                              override val fromAssorters: List<AssorterIF>,
                              override val Npop: Int,
                              override val nsamples: Int,
                              override val alpha: Double,
                              val failures: List<DhondtFailure>,
                              val tfailures: List<ThresholdFailure>,
): RelaxedAssertionsIF {
    val info = dcontest.info()
    var contestRanges = ContestRanges(dcontest, failures) // baseline, no threshold failures, may have DH failures
    var altContest: DhondtContest
    var altRelaxed: RelaxedDhondtAssertions? = null

    init {
        logger.debug { "Contest ${info.name} haveSampleSize=${nsamples}" }

        if (tfailures.size > 1) throw RuntimeException("Can only handle 1 threshold failure")
        val tfailure = tfailures.first()

        val belowThreshold = dcontest.partiesBelowThreshold - setOf(tfailure.assorter.partyId)

        // class DhondtBuilder(  // TODO ok to not be data class ??
        //    val name: String,
        //    val id: Int,
        //    val partyBs: List<DhondtPartyBuilder>,
        //    val nseats: Int,
        //    val Nc: Int, // trusted upper limit; // TODO need phantoms also
        //    val undervotes: Int,
        //    val minFraction: Double,
        //    thresholdOverride: Set<Int>? = null,
        //    flip: Boolean = false,
        //)        : this(info.name, info.id, partyBs, nseats=info.nwinners, Nc=Nc, undervotes=undervotes, minFraction = info.minFraction!!)

        altContest = DhondtBuilder(
            name = info.name,
            id = info.id,
            partyBs = dcontest.parties.map { DhondtPartyBuilder(it) },
            nseats = info.nwinners,
            Nc = dcontest.Nc,
            undervotes = dcontest.undervotes,
            minFraction = info.minFraction!!,
            thresholdOverride = belowThreshold).build()

        // what is the candidate range from regular and alt?

        // could have failures
        val failures = findDhondtFailures(altContest, altContest.assorters, Npop, nsamples, alpha)
        if (failures.isNotEmpty()) {
            altRelaxed = RelaxedDhondtAssertions(
                altContest,
                altContest.assorters,
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

    override fun contestRanges() = contestRanges
    override fun failures() = failures

    override fun show() = buildString {
        appendLine("Failures")
        appendLine(DhondtFailure.header())
        tfailures.forEach { appendLine(it) }
        appendLine()
        append(showRelaxedAssertions(dcontest, fromAssorters, nsamples, alpha))
        appendLine("\nAltContest")
        append(showRelaxedAssertions(altContest, altContest.assorters, nsamples, alpha))
        if (altRelaxed != null) {
            appendLine("\nAltRelaxedAssertions")
            altRelaxed!!.show()
        }
    }

}