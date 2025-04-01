package org.cryptobiotic.rlauxe.persist.json

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.*
import org.cryptobiotic.rlauxe.estimate.makeCvr
import org.cryptobiotic.rlauxe.util.listToMap
import org.cryptobiotic.rlauxe.util.makeContestFromCvrs
import kotlin.test.Test
import kotlin.test.assertTrue

import kotlin.test.assertNotNull

class TestContestJson {
    val filename = "/home/stormy/temp/persist/runBoulder23/contests.json"

    @Test
    fun testReadContest() {
        val contestsResults = readContestsJsonFile(filename)
        val contests = if (contestsResults is Ok) contestsResults.unwrap()
        else throw RuntimeException("Cannot read contests from ${filename} err = $contestsResults")
        contests.forEach{
            println(" $it")
        }
    }
}