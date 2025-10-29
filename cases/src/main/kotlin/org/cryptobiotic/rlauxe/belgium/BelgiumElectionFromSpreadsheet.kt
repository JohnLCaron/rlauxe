package org.cryptobiotic.rlauxe.belgium

import org.apache.poi.ss.usermodel.Row
import org.cryptobiotic.rlauxe.dhondt.DhondtScore
import org.cryptobiotic.rlauxe.poi.ColIterator
import org.cryptobiotic.rlauxe.poi.ExcelSpreadsheet
import org.cryptobiotic.rlauxe.poi.RowIterator
import org.cryptobiotic.rlauxe.poi.cleanExcelValue
import kotlin.Int

class BelgiumElectionFromSpreadsheet(val Afile: String, val Bfile: String, val sheetName: String) {
    val A: ExcelSpreadsheet = ExcelSpreadsheet(Afile)
    val B: ExcelSpreadsheet = ExcelSpreadsheet(Bfile)

    fun showSheet(excel: ExcelSpreadsheet, name: String) {
        val rows: RowIterator = excel.readSheet(name)
        while (rows.hasNext()) {
            val row = rows.next()
            if (row != null) {
                val cols = ColIterator(row)
                while (cols.hasNext()) {
                    val cell = cols.next()
                    val cellValue = cleanExcelValue(cell.toString())
                    // val cellType = if (cell.cellType == CellType.NUMERIC) "" else "*"
                    // print("${cellValue}$cellType, ");
                    print("${cellValue}, ");
                }
                println()
            }
        }
    }

    fun readA(): Ainfo {
        val parties = mutableListOf<AParty>()

        val rows: RowIterator = A.readSheet(sheetName)
        while (rows.hasNext()) {
            val rowIdx = rows.rowIdx()
            val row = rows.next()
            if (row != null && rowIdx > 0) { // skip first Row
                var partyNum = 0
                var partyName = ""
                var partyTotal = 0

                val counts = mutableListOf<Int>()
                val cols = ColIterator(row)
                while (cols.hasNext()) {
                    val colIdx = cols.colIdx()
                    val cell = cols.next()
                    if (colIdx == 0) partyNum = cell.numericCellValue.toInt()
                    else if (colIdx == 1) partyName = cell.toString()
                    else if (colIdx == cols.lastCellNum-1) partyTotal = cell.numericCellValue.toInt()
                    else counts.add(cell.numericCellValue.toInt())
                }
                parties.add(AParty(partyName, partyNum, counts, partyTotal))
            }
        }
        return Ainfo(parties)
    }

    fun readB(): Binfo {
        var electionName = ""
        val partyNames = mutableListOf<String>()
        val partyTotal = mutableListOf<Int>()
        val rounds = mutableListOf<BRound>()

        val rows: List<Row?> = B.readRows("Seat_C$sheetName")
        var rowno = 0
        while (rowno < rows.size) {
            val row = rows[rowno]

            if (rowno == 0) {
                val cells = B.readCells(row)
                electionName = cleanExcelValue(cells[0].toString())
                cells.subList(1, cells.size).forEach { cell -> partyNames.add(cleanExcelValue(cell.toString())) }

            } else if (rowno == 1) {
                val cells = B.readCells(row)
                cells.subList(1, cells.size).forEach { cell -> partyTotal.add(cell.numericCellValue.toInt()) }

            } else if (rowno % 2 == 0) {
                val roundCount = mutableListOf<Int>()
                val cells = B.readCells(row)
                val cell0 = cells[0].toString()
                cells.subList(1, cells.size).forEach { cell -> roundCount.add(cell.numericCellValue.toInt()) }

                rowno++
                val row2 = rows[rowno]
                if (cell0.startsWith(":") && row2 != null) {
                    val roundWinner = mutableListOf<String>()
                    val cells = B.readCells(row2)

                    for (cellno in (1 .. cells.size - 1)) {
                        roundWinner.add(cells[cellno].toString())
                    }

                    if (roundCount.size < roundWinner.size)
                        println("bad")
                    rounds.add(BRound(roundCount, roundWinner))
                }
            }
            rowno++
        }
        val parties = partyNames.zip(partyTotal).map { (name, total) -> BParty(name, total)}
        return Binfo(electionName, parties, rounds)
    }
}

data class AParty(val name: String, val num: Int, val counts: List<Int>, val total: Int) {
    fun show() = buildString {
        append(" Party $num '$name'")
        append(" count total= ${counts.sum()} total = $total")
    }
}

data class Ainfo(val parties: List<AParty>) {
    fun show() = buildString {
        appendLine("infoA")
        parties.forEach { party ->
            appendLine(party.show())
        }
    }
}

data class Binfo(val electionName: String, val parties: List<BParty>, val rounds: List<BRound>) {
    val winners = mutableListOf<DhondtScore>()

    init {
        rounds.forEachIndexed { ridx, round ->
            round.winners.forEachIndexed { widx, winn ->
                if (winn.isNotEmpty()) {
                    val winningSeat = cleanWinningSeat(winn).toDouble().toInt()
                    val winningContest = parties[widx]
                    val seatno = ridx+1
                    val count = round.counts[widx]
                    val fes = DhondtScore(candidate = winningContest.num, score = count.toDouble(), divisor = seatno)
                    fes.winningSeat = winningSeat
                    winners.add(fes)
                }
            }
            winners.sortBy{ it.winningSeat }

        }
    }

    fun cleanWinningSeat(originalString: String) = originalString.replace("-", "").trim()

    fun show() = buildString {
        appendLine("infoB")
        appendLine(" ${electionName} Parties (${parties.size})")
        parties.forEach { party ->
            appendLine("  ${party.show()}")
        }
        appendLine("Rounds (${rounds.size})")
        rounds.forEach { round ->
            appendLine("  $round")
        }
        appendLine("Winners (${winners.size})")
        winners.forEach { winner ->
            appendLine("  $winner")
        }
    }
}

data class BParty(val name: String, val total: Int) {
    val num : Int = name.split(' ').first().toInt()

    fun show() = buildString {
        append("Party '$name' total = $total")
    }
}

data class BRound(val counts: List<Int>, val winners: List<String>)