package org.cryptobiotic.rlauxe.corlacvr

import com.jsoizo.kotlincsv.csvReader
import com.jsoizo.kotlincsv.reader.readFromFile
import org.cryptobiotic.rlauxe.util.trunc
import org.cryptobiotic.rlauxe.votedatabase.votedatabase
import kotlin.test.Test

class TestGarfield2020 {
    val firstfile = "$votedatabase/cvr/Colorado/Garfield/6-8-22.cvr.csv"
    val usedfile = "$votedatabase/cvr/Colorado/Garfield/cvr.csv"
    var lastContest = ""
    var contestColSum = mutableMapOf<Int, Int>()

    @Test
    fun testRegular() {
        val regularFile = "$votedatabase/cvr/Colorado/Bent/cvr.csv"
        val countyCvrs = readCountyCvrs(regularFile)
        countyCvrs.cvrs().forEach { row ->
            row.votes().values.forEach { cands: IntArray ->
                if (cands.size > 1)
                    println("${row.imprintedId} == ${cands}")
            }
        }
    }

    @Test
    fun testUsed() {
        val countyCvrs = Garfield2020CountyCvrs(usedfile)
        countyCvrs.cvrs.forEach { row ->
            row.votes().values.forEach { cands: IntArray ->
                if (cands.size > 1)
                    println("${row.imprintedId} == ${cands}")
            }
        }
    }

    @Test
    fun testFirstOne() {
        val skip = 71
        val reader = csvReader()
        reader.readFromFile(firstfile) { rows ->
            val rowcolseq = rows.filterIndexed { idx, row -> idx % skip == 0 }
            val rowcol = rowcolseq.toList()
            val colrow = rowcol.transpose()
            val ncols = rowcol.first().size
            repeat(ncols) {
                val row = colrow[it]
                println(show(row))
            }
        }
    }

    fun <T> List<List<T>>.transpose(): List<List<T>> {
        if (this.isEmpty() || this[0].isEmpty()) return emptyList()

        return this[0].indices.map { col ->
            this.map { row -> row[col] }
        }
    }

    fun show(row:List<String>) = buildString {
        val header = row.first()
        val parts = header.split(":")
        if (parts.size == 1) {
            append("${trunc(header, 21)}: ")
            repeat(row.size - 1) {
                append("${row[it + 1]}, ")
            }
        } else {

            val contest = parts[1]
            if (contest != lastContest) {
                val allgood = contestColSum.values.any{ it < 2}
                appendLine("    allgood=$allgood")
                contestColSum = mutableMapOf()
            }
            lastContest = contest

            val cand = parts[3]
            append("${trunc("$contest:$cand", 80)}: ")
            repeat(row.size - 1) {
                append("${row[it + 1]}, ")
                val s = row[it + 1].trim()
                if (s.isNotBlank()) {
                    val vote = s.toInt()
                    val accum = contestColSum.getOrDefault(it, 0)
                    contestColSum[it] = accum + vote
                }
            }
        }

    }
}