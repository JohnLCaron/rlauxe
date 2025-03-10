package org.cryptobiotic.rlauxe.shangrla

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.ClcaSimulation
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.core.ClcaErrorTable
import org.cryptobiotic.rlauxe.raire.import
import org.cryptobiotic.rlauxe.raire.readRaireBallotsCsv
import org.cryptobiotic.rlauxe.raire.readRaireResultsJson
import kotlin.test.Test

class TestComparisonSamplerWithRaire {

    @Test
    fun testComparisonSamplerForRaire() {
        // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile =
            "src/test/data/raire/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallotsCsv(cvrFile)
        val cvrs = raireCvrs.cvrs
        // val cvrsUA = cvrs.map { CvrUnderAudit(it) }

        val ncs = raireCvrs.contests.map { Pair(it.contestNumber.toString(), it.ncvrs + 2)}.toMap()
        val nps = raireCvrs.contests.map { Pair(it.contestNumber.toString(), 2)}.toMap()
        val raireResults =
            readRaireResultsJson("src/test/data/raire/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json")
                .import(ncs, nps)
        // val raireResults2 = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json").import()
        val contestUA = raireResults.contests.first()

        contestUA.makeClcaAssertions(cvrs)

        contestUA.clcaAssertions.forEach { assert ->
            run(cvrs, contestUA, assert.cassorter as ClcaAssorter)
        }
    }

    fun run(cvrs: List<Cvr>, contestUA: ContestUnderAudit, cassorter: ClcaAssorter) {
        println("\n${cassorter.assorter().desc()}")

        val sampler = ClcaSimulation(cvrs, contestUA.contest, cassorter, ClcaErrorTable.standard)

        val orgCvrs = cvrs.map { cassorter.assorter().assort(it) }.average()
        val sampleCvrs = sampler.cvrs.map { cassorter.assorter().assort(it) }.average()
        val sampleMvrs = sampler.mvrs.map { cassorter.assorter().assort(it) }.average()
        println(" orgCvrs=${df(orgCvrs)} sampleCvrs=${df(sampleCvrs)} sampleMvrs=${df(sampleMvrs)}")

        val before = cvrs.map { cassorter.bassort(it, it) }.average()
        sampler.reset()
        val welford = Welford()
        repeat(cvrs.size) {
            welford.update(sampler.sample())
        }

        println(" bassort expectedNoerror=${df(cassorter.noerror())} noerror=${df(before)} sampleMean = ${df(welford.mean)}")
    }

}