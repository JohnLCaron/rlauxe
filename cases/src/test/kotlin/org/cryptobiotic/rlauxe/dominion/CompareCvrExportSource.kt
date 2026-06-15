package org.cryptobiotic.rlauxe.dominion

import org.cryptobiotic.rlauxe.util.Stopwatch
import kotlin.test.Test
import kotlin.test.assertEquals

class CompareCvrExportSources {
    val show = false

    val adams = "/home/stormy/datadrive/votedatabase/cvr/Colorado/Adams/cvr.csv"

    // HEY theres redacted at the ends of the files, sometimes in the middle, like Eagle

    @Test
    fun compareBoulder20cvrs() {
        compareDominionCvrExport(
            DominionCvrExportReader("/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/cvr.csv").read(),
            DominionCvrExportReader("/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/Boulder CO.csv").read()
        )
    }

    @Test
    fun compareAdams20cvrs() {
        compareDominionCvrExport(
            DominionCvrExportReader("/home/stormy/datadrive/votedatabase/cvr/Colorado/Adams/cvr.csv").read(),
            DominionCvrExportReader("/home/stormy/datadrive/votedatabase/cvr/Colorado/Adams/Adams_2020G_CVR_REDACTED.csv").read()
        )
    }

    @Test // breakdown
    fun compareArapahoe20cvrs() {
        compareDominionCvrExport(
            DominionCvrExportReader("/home/stormy/datadrive/votedatabase/cvr/Colorado/Arapahoe/cvr.csv").read(),
            DominionCvrExportReader("/home/stormy/datadrive/votedatabase/cvr/Colorado/Arapahoe/CVR_EDITED.csv").read()
        )
    }

    fun compareDominionCvrExport(exp1: DominionCvrExport, exp2: DominionCvrExport) {
        println("electionName = '${exp1.electionName}', '${exp2.electionName}'")
        assertEquals(exp1.electionName, exp2.electionName)

        assertEquals(exp1.electionName, exp2.electionName)
        // assertEquals(exp1.versionName, exp2.versionName)
        assertEquals(exp1.schema.contests.size, exp2.schema.contests.size)
        assertEquals(exp1.cvrs.size, exp2.cvrs.size)
        assertEquals(exp1.redacted.size, exp2.redacted.size)

        println("\nCompare columns")
        var skip = false
        var col2 = exp2.schema.columns.first()
        val cols2 = exp2.schema.columns.iterator()
        exp1.schema.columns.forEachIndexed { idx, col1 ->
            if (!skip) col2 = cols2.next()
            val same = compare(col1, col2)
            val star = if (same) "  " else "**"
            if (show || !same) {
                println("$star $col1 =!= $col2 ($idx)")
            }
            skip = !same
        }
        // exp1 has extra column at 6: "PrecinctPortionID"
        // ** ColumnInfo(colno=6, contest=, choice=, header=PrecinctPortionID) == ColumnInfo(colno=6, contest=, choice=, header=BallotType)

        println("\nCompare contests")
        var contest2 = exp2.schema.contests.first()
        val contests2 = exp2.schema.contests.iterator()
        exp1.schema.contests.forEachIndexed { idx, contest1 ->
            contest2 = contests2.next()
            val same = compare(contest1, contest2)
            val star = if (same) "  " else "**"
            if (show || !same) {
                println("($idx) $star $contest1")
                println("       $contest2")
            }
        }

        // exp1 is missing ballot style names
        println("\nCompare CardStyle")
        val styles2 = exp2.exportCardStyles.iterator()
        exp1.exportCardStyles.forEachIndexed { idx, style1 ->
            if (styles2.hasNext()) {
                val style2 = styles2.next()
                val same = compare(style1, style2)
                val star = if (same) "  " else "**"
                if (show || !same) {
                    println("($idx) $star $style1 =? $contest2")
                }
            }
        }

        // exp1 is missing ballot style names
        println("\nCompare Cvrs")
        val cvrs2 = exp2.cvrs.iterator()
        exp1.cvrs.forEachIndexed { idx, cvr1 ->
            val cvr2 = cvrs2.next()
            val same = compare(cvr1, cvr2)
            val star = if (same) "  " else "**"
            if (show || !same) {
                println("($idx) $star $cvr1 =? $cvr2")
            }
            if (idx % 1000 == 0) print(" $idx")
            if (idx % 10000 == 0) println()
        }
        println("\nncvrs = ${exp2.cvrs.size}")

        println("\nContests (size = ${exp2.schema.contests.size})")
        exp2.schema.contests.forEach { println(it) }

        println("\nBallotTypes 1 (size = ${exp1.exportCardStyles.size})")
        exp1.exportCardStyles.forEach { println(it) }

        println("\nBallotTypes 2 (size = ${exp2.exportCardStyles.size})")
        exp2.exportCardStyles.forEach { println(it) }
    }

    fun compare(col1: SchemaColumnInfo, col2: SchemaColumnInfo): Boolean {
        if (col1.contest != col2.contest) return false
        if (col1.choice != col2.choice) return false
        if (col1.header != col2.header) return false
        if (col1.contestIdx != col2.contestIdx) return false
        return true
    }

    fun compare(col1: SchemaContestInfo, col2: SchemaContestInfo): Boolean {
        if (col1.contestName != col2.contestName) return false
        if (col1.ncols != col2.ncols) return false
        if (col1.nchoices != col2.nchoices) return false
        if (col1.voteForN != col2.voteForN) return false
        return true
    }

    // data class BallotType(val name: String, val contests: Set<Int>, var count: Int = 0)
    fun compare(col1: ExportCardStyle, col2: ExportCardStyle): Boolean {
        if (col1.contests != col2.contests) return false
        if (col1.count != col2.count) return false
        return true
    }

    // data class CastVoteRecord(
    //    val cvrNumber: Int,
    //    val tabulatorNum: Int,
    //    val batchId: String,
    //    val recordId: Int,
    //    val imprintedId: String,
    //    val ballotType: String,
    //)
    fun compare(col1: CastVoteRecord, col2: CastVoteRecord): Boolean {
        if (col1.cvrNumber != col2.cvrNumber) return false
        if (col1.tabulatorNum != col2.tabulatorNum) return false
        if (col1.batchId != col2.batchId) return false
        if (col1.recordId != col2.recordId) return false
        if (col1.imprintedId != col2.imprintedId) return false
        if (col1.contestVotes != col2.contestVotes) return false
        return true
    }
}

fun DominionCvrExportCsv(filename:String, show: Boolean): DominionCvrExport {
    val stopwatch = Stopwatch()
    // redaction lines are present
    val export: DominionCvrExport = DominionCvrExportReader(filename).read()
    if (show) println(export.summary())
    println("took = $stopwatch")

    // assertEquals("2020 Boulder County General Election", export.electionName)
    assertEquals("5.11.3.1", export.versionName)
    assertEquals(49, export.schema.contests.size)
    assertEquals(205796, export.cvrs.size)
    assertEquals(0, export.redacted.size)

    if (show) {
        println()
        println(export.schema.show())
    }
    if (show) {
        println("ncolumns = ${export.schema.columns.size}")
        println(SchemaColumnInfo.header)
        export.schema.columns.forEach { println(it) }
    }
    return export
}
