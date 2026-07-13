package org.cryptobiotic.create

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.util.calcDeciles
import org.cryptobiotic.rlauxe.util.calcDecilesFromInt
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test

class MakeVarianceData {

    @Test
    fun createBoulderOaVariance() {
        val cvrDir = "${cases}/sf/sf2024"

        val generator = VarianceDataGenerator(
            "boulder2024",
            "${cases}/boulder/boulder2024oaVariance/boulder2024oaVariance",
            otherParameters = arrayOf(
                "--input",
                cvrDir,
            ),
            nruns = 2,
            nsimTrials = 10
        )
        generator.createAndRunTasks()
    }

    @Test
    fun createSFOaVariance() {
        val cvrDir = "${cases}/sf/sf2024"

        val generator = VarianceDataGenerator(
            "sf2024",
            "${cases}/sf/sf2024oaVariance/sf2024oaVariance",
            otherParameters = arrayOf(
                "--input", cvrDir,
            ),
            nruns = 2,
            nsimTrials = 10
        )
        generator.createAndRunTasks()
    }

    @Test
    fun createGaVariance() {
        val generator = VarianceDataGenerator(
            case ="ga26p",
            toptopdir = "${cases}/ga/ga2026variance/ga2026variance",
            otherParameters = arrayOf(
                "--input", "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted",
            ),
            nruns = 100,
            nsimTrials = 100 // TODO
        )
        generator.createAndRunTasks()
    }

    @Test
    fun createGaPollingVariance() {
        val generator = VarianceDataGenerator(
            "ga26p",
            "${cases}/ga/ga2026pvariance/ga2026pollVariance",
            otherParameters = arrayOf(
                "--input", "/home/stormy/datadrive/github/nealmcb/rla-review-arlo/2026-05-19-primary/extracted",
                "--auditType", "poll"
            ),
            nruns = 100,
            nsimTrials = 100
        )
        generator.createAndRunTasks()
    }

    @Test
    fun readVariance() {
        val toptopdir = "${cases}/ga/ga2026pvariance"
        val path = Path(toptopdir)
        val nmvrs = mutableListOf<Int>()
        val nrounds = mutableListOf<Int>()
        path.listDirectoryEntries().sorted().forEach { entry ->
            val topdir = entry.toString()
            val ar = AuditRecord.read(topdir)!!
            nrounds.add(ar.rounds.size)
            var lastRound = ar.rounds.last()
            println(" nmvrs = ${lastRound.nmvrs}")
            nmvrs.add(lastRound.nmvrs)
        }
        println("nmvrs including overshoot")
        println(nmvrs)
        println(calcDecilesFromInt(nmvrs))
        println()
        println("nrounds")
        println(nrounds)
        println(calcDeciles(nrounds.map { it.toDouble() }))
    }
}