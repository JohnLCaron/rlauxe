package org.cryptobiotic.rlauxe.sampling

import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.raire.import
import org.cryptobiotic.rlauxe.raire.readRaireBallots
import org.cryptobiotic.rlauxe.raire.readRaireResults
import org.cryptobiotic.rlauxe.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TestComparisonSamplerForRaire {

    @Test
    fun testComparisonSamplerForRaire() {
        val raireResults =
            readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheetsAssertions.json").import()
        // val raireResults2 = readRaireResults("/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SF2019Nov8Assertions.json").import()
        val contestUA = raireResults.contests.first()

        // This single contest cvr file is the only real cvr data in SHANGRLA
        val cvrFile =
            "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs = readRaireBallots(cvrFile)
        val cvrs = raireCvrs.cvrs
        val cvrsUA = cvrs.map { CvrUnderAudit(it, false) }

        contestUA.makeComparisonAssertions(cvrsUA)
        println()
        //val assert = contestUA.comparisonAssertions.last()
        //run(cvrsUA, contestUA, assert)

        contestUA.comparisonAssertions.forEach { assert ->
            run(cvrsUA, contestUA, assert)
        }
    }

    fun run(cvrsUA: List<CvrUnderAudit>, contestUA: ContestUnderAudit, assert: ComparisonAssertion) {
        println("${assert.assorter.assorter.desc()}")

        val sampler = ComparisonSamplerForRaire(cvrsUA, contestUA, assert.assorter)

        val orgCvrs = cvrsUA.map { assert.assorter.assorter.assort(it) }.average()
        val sampleCvrs = sampler.cvrs.map { assert.assorter.assorter.assort(it) }.average()
        val sampleMvrs = sampler.mvrs.map { assert.assorter.assorter.assort(it) }.average()
        println(" orgCvrs=${df(orgCvrs)} sampleCvrs=${df(sampleCvrs)} sampleMvrs=${df(sampleMvrs)}")

        val before = cvrsUA.map { assert.assorter.bassort(it, it) }.average()
        sampler.reset()
        var welford = Welford()
        repeat(cvrsUA.size) {
            welford.update(sampler.sample())
        }

        println(" bassort expectedNoerror=${df(assert.assorter.noerror)} noerror=${df(before)} sampleMean = ${df(welford.mean)}")

    }

}