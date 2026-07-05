package org.cryptobiotic.rlauxe.variance

import org.cryptobiotic.rlauxe.cases
import org.cryptobiotic.rlauxe.persist.AuditRecord
import org.cryptobiotic.rlauxe.util.calcDecilesFromInt
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test

class MakeVarianceData {

    @Test
    fun createSFOaVariance() {
        val cvrDir = "$cases/sf/sf2024"

        val generator = VarianceDataGenerator(
            "sf2024",
            "$cases/sf/sf2024oaVariance/sf2024oaVariance",
            nruns = 2,
            nsimTrials = 10
        )
        generator.createAndRunTasks()
    }

    @Test
    fun createGaVariance() {
        val generator = VarianceDataGenerator(
            "ga2026",
            "$cases/ga/ga2026variance/ga2026variance",
            nruns = 100,
            nsimTrials = 10
        )
        generator.createAndRunTasks()
    }

    @Test
    fun readVariance() {
        val toptopdir = "$cases/ga/ga2026variance"
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
        println(calcDecilesFromInt(nrounds))
    }
}

