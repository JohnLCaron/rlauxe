package org.cryptobiotic.rlauxe.cvr

import io.kotest.matchers.comparables.shouldBeLessThan
import org.cryptobiotic.rlauxe.votedatabase.votedatabase2020
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertTrue

class TestRedactionVotedatabase {
    val show = false

    @Test
    fun testBoulder22Primary() {
        lookForRedactions("/home/stormy/datadrive/votedatabase/cvr/2022Primaries/Colorado/Boulder CO '22 Primary.csv")
    }

    @Test
    fun testBoulder() {
        lookForRedactions("$votedatabase2020/Boulder/cvr.csv")
    }

    @Test
    fun testDolores() {
        lookForRedactions("$votedatabase2020/Dolores/cvr.csv")
    }

    // @Test special format
    fun testGarfield20() {
        lookForRedactions("$votedatabase2020/Garfield/cvr.csv")
    }

    @Test
    fun testDouglas() { // redaction
        lookForRedactions("$votedatabase2020/Douglas/cvr.csv")
    }

    @Test
    fun testEagle() { // redaction
        lookForRedactions("$votedatabase2020/Eagle/cvr.csv")
    }

    @Test
    fun testJefferson() { // redaction
        lookForRedactions("$votedatabase2020/Jefferson/cvr.csv")
    }

    @Test
    fun testPhillips() { // redaction
        lookForRedactions("$votedatabase2020/Phillips/cvr.csv")
    }

    @Test
    fun testPitkin() { // redaction
        lookForRedactions("$votedatabase2020/Pitkin/cvr.csv")
    }

    // Boulder, Doloros, Pitkin, possibly Jefferson
    // $votedatabase2020/Jefferson/JeffCO_2020_CVR_Redacted.csv has columns shifted by 1
    // $votedatabase2020/Phillips/cvr.csv
    //$votedatabase2020/Phillips/Phillips 2020  cvr phillips county.csv
    //  RedactedGroup('r', ncards=1, contests=[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29] totalVotes=62726)
    @Test
    fun allColorado2020Counties() {
        val path = Path(votedatabase2020)
        path.listDirectoryEntries().sorted().filter { it.isDirectory() && !it.fileName.toString().startsWith("202") }
            .forEach { subdir ->
                val county = subdir.fileName.toString()
                if (county !in listOf("Monroe", "Roosevelt", "Garfield")) {
                    subdir.listDirectoryEntries().filter {
                        !it.isDirectory() && it.fileName.toString().endsWith(".csv")
                                && it.fileName.toString() != "summary.csv"
                                && !it.fileName.toString().contains("Manifest")
                    }.forEach { entry ->
                        try {
                            val filename = entry.toString()
                            lookForRedactions(filename)

                        } catch (e: Exception) {
                            println(e.message)
                            throw e
                        }
                    }
                }
            }
    }
}

fun lookForRedactions(filename: String) {
    println("## Reading cvrs from $filename")

    val redaction = if (filename.lowercase().contains("boulder")) RedactionBoulder(true) else Redaction(true)
    val corlaCvrs: CorlaCvrs = if (filename.startsWith("/resources/"))
            readCorlaCvrsFromResource(filename, redaction = redaction)
        else readCorlaCvrsFromFile(filename, redaction = redaction)

    if (corlaCvrs.redaction.nlines > 0)
        println("  redacted lines = ${corlaCvrs.redaction.nlines} redacted groups = ${corlaCvrs.redactedGroups().size} ")

    if (corlaCvrs.redactedGroups().size > 0) {
        var sumInGroups = 0
        corlaCvrs.redactedGroups().forEach { group ->
            println("  $group")
            sumInGroups += group.ncards()
        }
        println("sumInGroups = $sumInGroups")
    }
}