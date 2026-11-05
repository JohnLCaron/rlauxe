package org.cryptobiotic.rlauxe.sf

import kotlin.test.Test

class TestReadSfAuxFiles {

    @Test
    fun testReadSfSummaryStax() {
        val staxContests: List<StaxReader.StaxContest> =
            StaxReader().read("src/test/data/SF2024/summary.xml") // sketchy
        staxContests.forEach { println(it)}
    }

}