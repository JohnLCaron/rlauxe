package org.cryptobiotic.rlauxe.raire

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.sampling.ComparisonSimulation
import org.cryptobiotic.rlauxe.util.*
import org.cryptobiotic.rlauxe.workflow.ComparisonErrorRates
import kotlin.test.Test
import kotlin.test.assertEquals

class TestComparisonSamplerWithRaire {

    @Test
    fun testComparisonSamplerForRaire() {
        // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile =
            "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallots(cvrFile)
        val cvrs = raireCvrs.cvrs
        // val cvrsUA = cvrs.map { CvrUnderAudit(it) }

        val ncs = raireCvrs.contests.map { Pair(it.contestNumber.toString(), it.ncvrs + 2)}.toMap()
        val nps = raireCvrs.contests.map { Pair(it.contestNumber.toString(), 2)}.toMap()
        val raireResults =
            readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json")
                .import(ncs, nps)
        // val raireResults2 = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json").import()
        val contestUA = raireResults.contests.first()

        contestUA.makeComparisonAssertions(cvrs)

        contestUA.comparisonAssertions.forEach { assert ->
            run(cvrs, contestUA, assert.cassorter)
        }
    }

    fun run(cvrs: List<Cvr>, contestUA: ContestUnderAudit, assorter: ComparisonAssorter) {
        println("\n${assorter.assorter.desc()}")

        val sampler = ComparisonSimulation(cvrs, contestUA.contest, assorter, ComparisonErrorRates.standard)

        val orgCvrs = cvrs.map { assorter.assorter.assort(it) }.average()
        val sampleCvrs = sampler.cvrs.map { assorter.assorter.assort(it) }.average()
        val sampleMvrs = sampler.mvrs.map { assorter.assorter.assort(it) }.average()
        println(" orgCvrs=${df(orgCvrs)} sampleCvrs=${df(sampleCvrs)} sampleMvrs=${df(sampleMvrs)}")

        val before = cvrs.map { assorter.bassort(it, it) }.average()
        sampler.reset()
        val welford = Welford()
        repeat(cvrs.size) {
            welford.update(sampler.sample())
        }

        println(" bassort expectedNoerror=${df(assorter.noerror)} noerror=${df(before)} sampleMean = ${df(welford.mean)}")
    }

}