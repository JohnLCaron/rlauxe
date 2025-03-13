package org.cryptobiotic.rlaux.corla

import kotlin.test.Test

// Not used, not sure its needed
// so far, only tested on "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/N19-manifest.csv"

class TestDominionBallotManifest {

    @Test
    fun testDominionBallotManifest() {
        val manifest = readDominionBallotManifest("/home/stormy/dev/github/rla/rlauxe/rla/src/test/data/raire/SFDA2019/N19-manifest.csv", 123L)
        println(manifest)
    }

}