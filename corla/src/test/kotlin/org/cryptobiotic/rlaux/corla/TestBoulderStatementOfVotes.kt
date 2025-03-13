package org.cryptobiotic.rlaux.corla

import kotlin.test.Test

// Not used, not sure its needed
// so far, only tested on "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/SFDA2019/N19-manifest.csv"

class TestBoulderStatementOfVotes {

    @Test
    fun testDominionBallotManifest() {
        val filename = "src/test/data/Boulder2024/2024G-Boulder-County-Official-Statement-of-Votes.csv"
        val sovo = readBoulderStatementOfVotes(filename)
        println(sovo.show())
    }

}