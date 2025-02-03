package org.cryptobiotic.rlauxe.raire

import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import kotlin.test.Test

class TestRaireReader {
    // 1
    //Contest,1,11,1,2,3,4,5,6,7,8,9,10,11,winner,8
    //1,1,1,2,8
    //1,2,1,2,8
    // ...
    @Test
    fun testReadAspenCityCouncilCvrs() {
        val cvrFile = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/raire/Aspen_2009_CityCouncil.raire"
        val raireCvrs: RaireCvrs = readRaireBallotsCsv(cvrFile)
        assertEquals(1, raireCvrs.contests.size)
        val contest = raireCvrs.contests.first()
        assertEquals(1, contest.contestNumber)
        assertEquals(11, contest.candidates.size)
        assertEquals(listOf(1,2,3,4,5,6,7,8,9,10,11), contest.candidates)
        assertEquals(8, contest.winner)
        assertEquals(2487, contest.ncvrs)
    }

    // 1
    //Contest,339,4,15,16,17,18
    //339,99813_1_1,17
    //339,99813_1_3,16
    //339,99813_1_6,18,17,15,16

    @Test
    fun testReadSfdaCvrs() { // ??
        val cvrFile = "/home/stormy/dev/github/rla/shangrla-kotlin/src/test/data/rla/SFDA2019_PrelimReport12VBMJustDASheets.raire"
        val raireCvrs: RaireCvrs = readRaireBallotsCsv(cvrFile)
        assertEquals(1, raireCvrs.contests.size)
        val contest = raireCvrs.contests.first()
        assertEquals(339, contest.contestNumber)
        assertEquals(4, contest.candidates.size)
        assertEquals(listOf(15,16,17,18), contest.candidates)
        assertEquals(-1, contest.winner)
        assertEquals(146662, contest.ncvrs)
    }

    @Test
    fun testReadAspenCvrs() {
        val dataDir = "/home/stormy/dev/github/rla/rlauxe/core/src/test/data/raire/"
        val dataDirFile = File(dataDir)
        dataDirFile.listFiles().forEach {
            if (!it.isDirectory) {
                println(it.path)
                testReadRaireCvrs(it.path)
            }
        }
    }

    fun testReadRaireCvrs(cvrFile: String) {
        val raireCvrs: RaireCvrs = readRaireBallotsCsv(cvrFile)
        raireCvrs.contests.forEach {
            println("  ${it.show()}")
        }
    }
}