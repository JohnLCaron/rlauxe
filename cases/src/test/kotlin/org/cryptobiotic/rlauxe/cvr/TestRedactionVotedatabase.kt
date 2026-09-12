package org.cryptobiotic.rlauxe.cvr

import org.cryptobiotic.rlauxe.votedatabase.votedatabase2020
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test

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

    // TODO these arent redactions, these are subtotals (!)
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

    // In the 2020 General, Eagle County had one ballot style where only nine voters returned a ballot.
    // Thus, per statute, we have redacted the votes cast by those voters AND redacted one additional
    // ballot style to ensure the anonymity of those voters' votes.
    // Redacted ballot styles are highlighted in yellow in the CVR.
    // ## Reading cvrs from /home/stormy/datadrive/votedatabase/cvr/Colorado/Eagle/cvr.csv
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=9224, values=[9220, 2, 197, 34, 2-197-34, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=9225, values=[9221, 2, 197, 33, 2-197-33, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=26221, values=[26217, 2, 559, 25, 2-559-25, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=26231, values=[26227, 2, 559, 26, 2-559-26, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=28319, values=[28315, 2, 606, 19, 2-606-19, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=28334, values=[28330, 2, 606, 20, 2-606-20, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=31772, values=[31768, 2, 682, 39, 2-682-39, Mail, 2052619019 - 4 (2052619019 - 4), 4, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=31773, values=[31769, 2, 682, 38, 2-682-38, Mail, 2052619019 - 4 (2052619019 - 4), 4, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=50432, values=[50428, 2, 1098, 11, 2-1098-11, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=50455, values=[50451, 2, 1098, 12, 2-1098-12, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=55221, values=[55217, 2, 1204, 6, 2-1204-6, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=55241, values=[55237, 2, 1204, 5, 2-1204-5, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=58525, values=[58521, 2, 1288, 40, 2-1288-40, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=58526, values=[58522, 2, 1288, 39, 2-1288-39, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=58574, values=[58570, 2, 1289, 12, 2-1289-12, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=58575, values=[58571, 2, 1289, 8, 2-1289-8, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=58578, values=[58574, 2, 1289, 9, 2-1289-9, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=58581, values=[58577, 2, 1289, 10, 2-1289-10, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=58584, values=[58580, 2, 1289, 11, 2-1289-11, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  ** hasRedacted: CSVRecord [comment='null', recordNumber=58593, values=[58589, 2, 1289, 7, 2-1289-7, Mail, 2052619019 - 2 (2052619019 - 2), 2, Redacted per 24-27-205.5 (4)(b)(III) C.R.S. , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , , ]]
    //  nrows = 58591 redacted lines = 20 redacted groups = 0
    @Test
    fun testEagle() { // redaction
        lookForRedactions("$votedatabase2020/Eagle/cvr.csv", true)
    }

    @Test
    fun testElPaso() { // redaction
        lookForRedactions("$votedatabase2020/El Paso/cvr.csv", true)
    }
    @Test
    fun testJefferson() { // redaction
        lookForRedactions("$votedatabase2020/Jefferson/cvr.csv")
    }

    @Test
    fun testJefferson2() { // redaction
        lookForRedactions("$votedatabase2020/Jefferson/JeffCO_2020_CVR_Redacted.csv")
    }

    @Test
    fun testLarimer() { // redaction
        lookForRedactions("$votedatabase2020/Larimer/cvr.csv")
    }

    // TODO these arent redactions, these are subtotals (!)
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

fun lookForRedactions(filename: String,show: Boolean = false) {
    println("## Reading cvrs from $filename")

    val redaction = if (filename.lowercase().contains("boulder")) RedactionBoulder(show) else Redaction(show)

    try {
        val corlaCvrs: CorlaCvrs = if (filename.startsWith("/resources/"))
            readCorlaCvrsFromResource(filename, redaction = redaction)
        else readCorlaCvrsFromFile(filename, redaction = redaction)

        println("  nrows = ${corlaCvrs.nrows()} redacted cvrs = ${corlaCvrs.redactedCvrs().size} redacted groups = ${corlaCvrs.redactedGroups().size} ")

        if (corlaCvrs.redactedGroups().size > 0) {
            var sumInGroups = 0
            corlaCvrs.redactedGroups().forEach { group ->
                println("  $group")
                sumInGroups += group.ncards()
            }
            println("sumInGroups = $sumInGroups")
        }

    } catch (e: Throwable) {
        println("$filename got error ${e.message}")
    }
}