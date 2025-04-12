package org.cryptobiotic.rlauxe.raire

import java.io.File
import kotlin.test.Test

class TestReadAusElections {

    @Test
    fun testReadAusElections() {
        val dataDir = "/home/stormy/dev/github/rla/raire-java/Australian Examples/NSW Local Government/2021"
        val dataDirFile = File(dataDir)
        dataDirFile.listFiles().forEach {
            if (!it.isDirectory) {
                println(it.path)
                if (!it.path.contains("_out.json")) {
                    val rp = readRaireProblemJson(it.path)
                    //println(rp)
                } else {
                    val rr = readRaireSolutionJson(it.path)
                    //println(rr)
                }
            }
        }
    }

    @Test
    fun testReadAusProblem() {
        val filename = "src/test/data/raire/Aus/Eurobodalla Mayoral.json"
        println(filename)
            val rp = readRaireProblemJson(filename)
            println(rp)
    }
    @Test
    fun testReadAusResult() {
        val filename = "src/test/data/raire/Aus/Eurobodalla Mayoral_out.json"
        println(filename)
        val rr = readRaireSolutionJson(filename)
        println(rr)
    }

}