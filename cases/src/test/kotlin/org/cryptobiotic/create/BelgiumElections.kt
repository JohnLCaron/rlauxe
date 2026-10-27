package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.audit.AuditRoundIF
import org.cryptobiotic.rlauxe.audit.runRound
import org.cryptobiotic.rlauxe.belgium.belgiumInput
import org.cryptobiotic.rlauxe.belgium.createOneContestFromInputs
import org.cryptobiotic.rlauxe.belgium.makeAllBelgiumContests
import org.cryptobiotic.rlauxe.util.Locations.cases
import org.cryptobiotic.rlauxe.core.AssorterIF
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.util.dfn
import org.cryptobiotic.rlauxe.util.roundToClosest
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.math.ln
import kotlin.test.Test


class BelgiumElections {
    val year = 2019
    val toptopdir = "$cases/belgium/belgium$year"
    val belgiumInput = belgiumInput(year)

    @Test
    fun createOneBelgiumElection() {
        val name = "BrabantWallon"
        createOneContestFromInputs(
            year,
            name,
            toptopdir,
            contestId = 11
        )
    }

    @Test
    fun runBelgiumElection() {
        runBelgiumElection("Namur")
    }

    @Test
    fun createAllBelgiumElections() {
        makeAllBelgiumContests(year, toptopdir)
    }

    @Test
    fun showBelgiumElection() {
        val electionName = "Limbourg"
        val result = showBelgiumElection(electionName)
        val pct = (100.0 * result.second) / result.first.toDouble()
        println(
            "${sfn(electionName, 15)}: Nc= ${trunc(result.first.toString(), 10)} " +
                    " nmvrs= ${trunc(result.second.toString(), 6)} pct= ${dfn(pct, 2)} %"
        )
    }

    @Test
    fun showAllBelgiumElection() {
        val allResults = mutableMapOf<String, Triple<Int, Int, AssorterIF>>()
        belgiumInput.contests.keys.forEach {
            allResults[it] = showBelgiumElection(it)
        }

        println(
            "${sfn("", 15)} | ${trunc("minAssorter", 42)} | " +
                    "${trunc("noerror", 8)} | " +
                    "${trunc("mean", 8)} | " +
                    "${trunc("upper", 8)} | " +
                    "${trunc("nmvrs", 6)} | " +
                    "${trunc("minMvrs", 7)} | " +
                    "${sfn("pct", 3)} % |"
        )

        allResults.forEach {
            val (Nc, nmvrs, minAssorter) = it.value
            val pct = (100.0 * nmvrs) / Nc.toDouble()
            val minSamples = -ln(.05) / ln(2 * minAssorter.noerror(true))

            println(
                "${sfn(it.key, 15)} | " +
                        "${sfn(minAssorter.shortName(), 42)} | " +
                        "${dfn(minAssorter.noerror(true), 6)} | " +
                        "${dfn(minAssorter.dilutedMean(), 6)} | " +
                        "${dfn(minAssorter.upperBound(), 6)} | " +
                        "${trunc(nmvrs.toString(), 6)} | " +
                        "${trunc(roundToClosest(minSamples).toString(), 7)} | " +
                        "${dfn(pct, 2)} % |"
            )
        }
    }


    private fun runBelgiumElection(electionName: String, stopRound: Int = 0): Int {
        val topdir = "$toptopdir/$electionName"

        var done = false
        var finalRound: AuditRoundIF? = null
        while (!done) {
            val lastRound = runRound(inputDir = topdir)
            if (lastRound != null) finalRound = lastRound
            done =
                lastRound == null || lastRound.auditIsComplete || lastRound.roundIdx > 5 || lastRound.roundIdx == stopRound
        }

        return if (finalRound != null) {
            println("$electionName: ${finalRound.show()}")
            finalRound.nmvrs
        } else 0
    }


    // Npop, nmvrs, minAssorter
    private fun showBelgiumElection(electionName: String): Triple<Int, Int, AssorterIF> {
        println("======================================================")
        println("showBelgiumElection $electionName")
        val topdir = "$toptopdir/$electionName"

        val auditRecord = AuditRecord.read(topdir)
            ?: throw RuntimeException("directory '$topdir' does not contain an audit record")

        val contestUA = auditRecord.contests.first()
        println(contestUA.show())
        val minAssertion = contestUA.minAssertion()
        val minAssorter = minAssertion!!.assorter
        println("minAssorter: ${minAssorter}")
        println("  ${contestUA.minAssertionDifficulty()}")
        print(contestUA.contest.showCandidates())

        if (auditRecord.rounds.isEmpty())
            print("")
        val finalRound = auditRecord.rounds.last()
        val Nb = finalRound.contestRounds.first().Npop
        return Triple(Nb, finalRound.nmvrs, minAssorter)
    }
}
