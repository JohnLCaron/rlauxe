package org.cryptobiotic.rlauxe.dhondt

import io.mockk.InternalPlatformDsl.toArray
import org.cryptobiotic.rlauxe.audit.AssertionRound
import org.cryptobiotic.rlauxe.audit.ContestRound
import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.persist.CompositeAuditRecord
import org.cryptobiotic.rlauxe.persist.validateOutputDir
import org.cryptobiotic.rlauxe.rlaplots.CatOrdering
import org.cryptobiotic.rlauxe.rlaplots.ScaleType
import org.cryptobiotic.rlauxe.rlaplots.genericErrorBarPlotter
import org.cryptobiotic.rlauxe.rlaplots.genericPlotter
import org.cryptobiotic.rlauxe.testdataDir
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.nfn
import kotlin.io.path.Path
import kotlin.test.Test

private val alpha = .05
private val show = false

class PlotRelaxedAssertions {
    val topdir = "$cases/belgium/belgium2024/"
    val auditRecord = AuditRecord.read(topdir)!! as CompositeAuditRecord
    val contests = auditRecord.contests
    val lastRound = auditRecord.rounds.last()
    val config = auditRecord.config
    val partyNames = auditRecord.readPartyNames()

    @Test
    fun plotRelaxedAssertions() {
        val stopwatch = Stopwatch()

        val ctrackers = lastRound.contestRounds.map { ContestTracker(it) }
        val madeSeatsWithMvrs = mutableListOf<MadeSeats>()
        val initialSamples: Map<Int, Int> = ctrackers.associate { it.id to it.getEstMvrs() }

        val startSeats = MadeSeats(ctrackers)
        if (show) println(startSeats.allSeats.showAllPartySeats(partyNames))
        println("totalMvrsNeeded=${nfn(startSeats.mvrsUsed, 6)} (all)")
        madeSeatsWithMvrs.add(startSeats)

        repeat(20) {
            val largestDelta = ctrackers.map { it.getDelta() }.maxBy { it.delta }
            largestDelta.largest.ctracker.setFailed(largestDelta)

            println("\n${it+1} remove=$largestDelta")

            val makeSeats = MadeSeats(ctrackers)
            println("${nfn(makeSeats.mvrsUsed, 6)} mvrsUsed")
            print("  failures = ${makeSeats.failures.filter { it.value > 0 }}")
            println("; tfailures = ${makeSeats.tfailures.filter { it.value > 0 }}")

            // could show delta
            if (show) println(makeSeats.allSeats.showAllPartySeats(partyNames))

            madeSeatsWithMvrs.add(makeSeats)
        }
        println("that took $stopwatch")


        println("| contest | initialSamples | relaxedSamples | failures |")
        println("| ------ | ------ | ------ |------ |")
        ctrackers.sortedByDescending { it.sampleLimit }. forEach{
            println("|${it.contestRound.name} | ${initialSamples[it.id]} | ${it.sampleLimit} | ${it.failures} |") }
        println("| Total | ${initialSamples.values.sum() } | ${ctrackers.sumOf{it.sampleLimit} } | ${ctrackers.sumOf { it.failures }} |")

        val name = "RelaxedSeats"
        val dirName = "$testdataDir/plots/dhondt/$name"
        validateOutputDir(Path(dirName))

        makeRelaxedSeatsPlot(
            writeFile = "$dirName/$name",
            title="Belgium 2020 Relaxed Assertions at 5% risk",
            subtitle="Party Seat ranges vs number of samples",
            madeSeats = madeSeatsWithMvrs,
            scaleType=ScaleType.Linear,
        )

    }

    data class Delta(val delta: Int, val largest: AssertionTracker, val next: AssertionTracker)

    data class ContestTracker(val contestRound: ContestRound) {
        val useAlpha = contestRound.auditorWantRisk ?: alpha
        val dcontest = contestRound.contestUA.contest as DhondtContest
        val id = contestRound.id

        // must get from contestRound.contestUA, not auditRecord.contests
        val assorters = contestRound.contestUA.clcaAssertions.map { it.assorter }
        val Npop = contestRound.contestUA.Npop
        val atrackers = contestRound.assertionRounds.map { AssertionTracker(this, it) }.sortedByDescending { it.estMvrs }

        var sampleLimit : Int = getEstMvrs()
        var failures : Int = 0

        fun getEstMvrs(): Int {
            return atrackers.filter { !it.failed }.maxOf { it.estMvrs }
        }

        fun getDelta(): Delta {
            val active = atrackers.filter { !it.failed }
            if (active.size < 2) return Delta(0, atrackers.last(), atrackers.last())
            val toptwo = atrackers.filter { !it.failed }.take(2)
            return Delta(toptwo[0].estMvrs - toptwo[1].estMvrs, toptwo[0], toptwo[1])
        }

        fun setFailed(delta: Delta) {
            delta.largest.failed = true
            sampleLimit = delta.next.estMvrs
            failures++
        }
    }

    data class AssertionTracker(val ctracker: ContestTracker, val assertionRound: AssertionRound) {
        val estMvrs = assertionRound.estNewMvrs
        var failed = false

        override fun toString(): String {
            return "'${ctracker.contestRound.name}': ${assertionRound.assertion.assorter.shortName()}, estMvrs=${nfn(estMvrs, 5)}, failed=$failed"
        }
    }

    class MadeSeats(ctrackers: List<ContestTracker>) {
        val failures = mutableMapOf<Int, Int>()
        val tfailures = mutableMapOf<Int, Int>()
        val allSeats: AllSeats
        var mvrsUsed: Int = 0

        init {
            val contestRanges = ctrackers.map { ctracker ->
                val relax: RelaxedAssertionsIF = makeRelaxedAssertions(ctracker.contestRound, alpha, mvrLimit = ctracker.sampleLimit)
                failures[ctracker.id] = relax.failures().size
                tfailures[ctracker.id] = relax.tfailures().size
                relax.totalContestRange()
            }
            allSeats = AllSeats(contestRanges)
            mvrsUsed = ctrackers.map { it.getEstMvrs() }.sum()
        }
    }

    data class SeatPlot(val party: String, val minSeats: Int, val reportedSeats: Int, val maxSeats: Int, val mvrsUsed: Int)

    fun makeRelaxedSeatsPlot(writeFile: String, title: String, subtitle: String, madeSeats: List<MadeSeats>, scaleType: ScaleType) {

        val data = mutableListOf<SeatPlot>()
        madeSeats.forEach { madeSeat ->
            madeSeat.allSeats.partySums.forEach { range ->
                if (range.minSeats > 0) {
                    data.add(SeatPlot(range.partyName, range.minSeats, range.reportedSeats, range.maxSeats, madeSeat.mvrsUsed))
                }
            }
        }

        val order: List<String> = madeSeats.first().allSeats.partySums.sortedByDescending { it.reportedSeats }.map { it.partyName }
        val catOrdering = CatOrdering(*order.toTypedArray())
        // println("order = ${order}")

        genericErrorBarPlotter(
            titleS = title,
            subtitleS = subtitle,
            writeFile = writeFile,
            scaleType = scaleType,
            data = data,
            xname = "number of samples", xfld = { it.mvrsUsed.toDouble() },
            yname = "nseats", yfld = { Triple(it.minSeats.toDouble(), it.reportedSeats.toDouble(), it.maxSeats.toDouble()) },
            catName = "party", catfld = { it.party },
            addPoints = true,
            catOrdering = catOrdering,
        )

    }

}
