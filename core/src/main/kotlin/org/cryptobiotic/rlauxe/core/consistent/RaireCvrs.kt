package org.cryptobiotic.rla.csv

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class RaireCvrs(
    val contests: List<RaireContest>,
    val filename: String,
)

// 1
//Contest,339,4,15,16,17,18
//339,99813_1_1,17
//339,99813_1_3,16
//339,99813_1_6,18,17,15,16

data class RaireContest(
    val contestNumber: Int,
    val choices: List<Int>,
    val cvrs: List<RaireCvr>,
)

data class RaireCvr(
    val contestNumber: Int,
    val wtf: String,
    val rankedChoices: List<Int>,
)

fun readRaireCvrs(filename: String): RaireCvrs {
    val path: Path = Paths.get(filename)
    val reader: Reader = Files.newBufferedReader(path)
    val parser = CSVParser(reader, CSVFormat.DEFAULT)
    val records = parser.iterator()

    // we expect the first line to be the number of contests
    val ncontests = records.next().get(0).toInt()
    val contests = mutableListOf<RaireContest>()
    var cvrs = mutableListOf<RaireCvr>()
    var contestId = 0
    var nchoices = 0
    var choices = emptyList<Int>()

    while (records.hasNext()) {
        val line = records.next()
        val first = line.get(0)
        if (first.equals("Contest")) {
            if (cvrs.isNotEmpty()) {
                contests.add(RaireContest(contestId, choices, cvrs))
            }
            // start a new contest
            contestId = line.get(1).toInt()
            nchoices = line.get(2).toInt()
            choices = readVariableListOfInt(line,3)
            require(nchoices == choices.size)
            cvrs = mutableListOf()
        } else {
            val cid = line.get(0).toInt()
            val wtf = line.get(1)
            val rankedChoices = readVariableListOfInt(line, 2)
            require(cid == contestId)
            require(choices.containsAll(rankedChoices))
            cvrs.add(RaireCvr(cid, wtf, rankedChoices))
        }
    }
    if (cvrs.isNotEmpty()) {
        contests.add(RaireContest(contestId, choices, cvrs))
    }

    return RaireCvrs(contests, filename)
}

fun readVariableListOfInt(line: CSVRecord, startPos: Int): List<Int> {
    val result = mutableListOf<Int>()
    while (startPos + result.size < line.size()) {
        val s = line.get(startPos + result.size)
        if (s.isEmpty()) break
        result.add(line.get(startPos + result.size).toInt())
    }
    return result
}
