package org.cryptobiotic.rlauxe.poi

import org.apache.poi.ss.usermodel.*
import java.io.Closeable
import java.io.File
import java.io.FileInputStream

// Note that a rowIterator and cellIterator iterate over rows or cells that have been created, skipping empty rows and cells.

class ExcelSpreadsheet(filename: String): Closeable {
    var excelFile: File = File(filename)
    var fis: FileInputStream = FileInputStream(excelFile)
    val workbook: Workbook = WorkbookFactory.create(fis)

    fun readSheet(index: Int): Iterator<Row?> {
        val sheet: Sheet = workbook.getSheetAt(index)
        return RowIterator(sheet)
    }

    fun readSheet(name: String): RowIterator {
        val sheet: Sheet = workbook.getSheet(name)
        return RowIterator(sheet)
    }

    override fun close() {
        fis.close()
        workbook.close()
    }

    fun readRows(name: String): List<Row?> {
        val sheet: Sheet = workbook.getSheet(name)
        val rows = mutableListOf<Row?>()
        RowIterator(sheet).forEach { rows.add(it) }
        return rows
    }

    fun readCells(row: Row?): List<Cell> {
        val cells = mutableListOf<Cell>()
        ColIterator(row).forEach { cells.add(it) }
        return cells
    }
}

class RowIterator(val sheet: Sheet): Iterator<Row?> {
    private var rowIdx: Int = sheet.getFirstRowNum()
    val rowEnd: Int = sheet.getLastRowNum()

    fun rowIdx() = rowIdx

    override fun next(): Row? {
        return sheet.getRow(rowIdx++)
    }

    override fun hasNext() = rowIdx < rowEnd
}

class ColIterator(val row: Row?): Iterator<Cell> {
    private var colIdx: Int = 0
    val lastCellNum: Int = if (row == null) 0 else row.getLastCellNum().toInt()

    fun colIdx() = colIdx

    override fun next(): Cell {
        return row!!.getCell(colIdx++, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
    }

    override fun hasNext() = colIdx < lastCellNum
}

private val regex = Regex("[,\n]") // Matches '!', ',' or any digit
fun cleanExcelValue(originalString: String) = originalString.replace("\n", " ")