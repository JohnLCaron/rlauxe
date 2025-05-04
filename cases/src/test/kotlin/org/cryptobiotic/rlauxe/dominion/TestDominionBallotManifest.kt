package org.cryptobiotic.rlauxe.dominion

import kotlin.test.Test

// Not used, probably not needed

class TestDominionBallotManifest {

    @Test
    fun testDominionBallotManifest() {
        val manifest = readDominionBallotManifest("../rla/src/test/data/raire/SFDA2019/N19-manifest.csv", 123L)
        println(manifest)
    }

}