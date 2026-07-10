package org.cryptobiotic.rlauxe.dominion

import kotlin.test.Test

class CompareCvrExportSources {
    val show = false
    val maxShow = 100

    val adams = "/home/stormy/datadrive/votedatabase/cvr/Colorado/Adams/cvr.csv"

    // HEY theres redacted at the ends of the files, sometimes in the middle, like Eagle

    @Test
    fun compareBoulder20cvrs() {
        compareDominionCvrExport(
            // cvr has 29 redacted groups, cvrOrg has 0
            readCvrExportsFromFile("/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/cvr.csv"),
            readCvrExportsFromFile("/home/stormy/datadrive/votedatabase/cvr/Colorado/Boulder/cvrOrg.csv")
        )
    }

    @Test
    fun compareAdams20cvrs() {
        compareDominionCvrExport(
            readCvrExportsFromFile("/home/stormy/datadrive/votedatabase/cvr/Colorado/Adams/cvr.csv"),
            readCvrExportsFromFile("/home/stormy/datadrive/votedatabase/cvr/Colorado/Adams/Adams_2020G_CVR_REDACTED.csv")
        )
    }

    @Test // breakdown
    fun compareArapahoe20cvrs() {
        compareDominionCvrExport(
            readCvrExportsFromFile("/home/stormy/datadrive/votedatabase/cvr/Colorado/Arapahoe/cvr.csv"),
            readCvrExportsFromFile("/home/stormy/datadrive/votedatabase/cvr/Colorado/Arapahoe/CVR_EDITED.csv")
        )
    }

    fun compareDominionCvrExport(exp1: DominionCvrExportCsv, exp2: DominionCvrExportCsv) {
        println("electionName = '${exp1.electionName}', '${exp2.electionName}'")
        println("versionName = ${exp1.versionName}, ${exp2.versionName}")
        println("schema.contests.size = ${exp1.schema.contests.size}, ${exp2.schema.contests.size}")
        println("cvrs.size = ${exp1.cvrs.size}, ${exp2.cvrs.size}")
        println("redactedGroups.size = ${exp1.redactedGroups.size}, ${exp2.redactedGroups.size}")

        println("\nCompare columns")
        var countDiff = 0
        var skip = false
        var col2 = exp2.schema.columns.first()
        val cols2 = exp2.schema.columns.iterator()
        exp1.schema.columns.forEachIndexed { idx, col1 ->
            if (!skip) col2 = cols2.next()
            val same = compare(col1, col2)
            val star = if (same) "  " else "**"
            if (show || (!same && countDiff < maxShow)) {
                println("$star $col1 =!= $col2 ($idx)")
                countDiff++
            }
            skip = !same
        }

        // exp1 has extra column at 6: "PrecinctPortionID"
        // ** ColumnInfo(colno=6, contest=, choice=, header=PrecinctPortionID) == ColumnInfo(colno=6, contest=, choice=, header=BallotType)

        println("\nCompare contests")
        countDiff = 0
        var contest2 = exp2.schema.contests.first()
        val contests2 = exp2.schema.contests.iterator()
        exp1.schema.contests.forEachIndexed { idx, contest1 ->
            contest2 = contests2.next()
            val same = compare(contest1, contest2)
            val star = if (same) "  " else "**"
            if (show || (!same && countDiff < maxShow)) {
                println("($idx) $star $contest1")
                println("       $contest2")
                countDiff++
            }
        }

        // exp1 is missing ballot style names
        println("\nCompare CardStyle")
        countDiff = 0
        val styles2 = exp2.exportCardStyles.iterator()
        exp1.exportCardStyles.forEachIndexed { idx, style1 ->
            if (styles2.hasNext()) {
                val style2 = styles2.next()
                val same = compare(style1, style2)
                val star = if (same) "  " else "**"
                if (show || (!same && countDiff < maxShow)) {
                    println("($idx) $star $style1 =? $contest2")
                    countDiff++
                }
            }
        }

        // exp1 is missing ballot style names
        println("\nCompare Cvrs: ${exp1.cvrs.size} vs ${exp2.cvrs.size}")
        /*countDiff = 0
        val cvrs2 = exp2.cvrs.iterator()
        exp1.cvrs.forEachIndexed { idx, cvr1 ->
            val cvr2 = cvrs2.next()
            val same = compare(cvr1, cvr2)
            val star = if (same) "  " else "**"
            if (show || (!same && countDiff < maxShow)) {
                println("($idx) $star $cvr1 =? $cvr2")
                countDiff++
            }
            if (show) {
                if (idx % 1000 == 0) print(" $idx")
                if (idx % 10000 == 0) println()
            }
        }
        println("\nncvrs = ${exp2.cvrs.size}") */

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
        if (col1.countCards != col2.countCards) return false
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
