package org.cryptobiotic.rlauxe.sf

import kotlin.test.Test

class StaxReaderTest {
    @Test
    fun testReadSFsummary() {
        val xmlFile = "src/test/data/SF2024/summary.xml"
        val reader = StaxReader()
        val contests = reader.read(xmlFile)
        contests.forEach {
            println(" ${it.id} ncards = ${it.ncards()} underVotes = ${it.undervotes()} overvotes = ${it.overvotes()} blanks = ${it.blanks()}")
            println(it)
        }
    }
}
