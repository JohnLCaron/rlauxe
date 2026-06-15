package org.cryptobiotic.rlauxe.dominion

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.boulder.parseContestNameAndVoteFor
import org.cryptobiotic.rlauxe.boulder.parseIrvContestName
import org.cryptobiotic.rlauxe.corla.munge
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.trunc
import java.lang.StrictMath.sqrt
import kotlin.collections.set
import kotlin.math.max

// these are the data structures used for DominionCvrExportReader

private val logger = KotlinLogging.logger("DominionCvrExport")

data class DominionCvrExport(
    // val county: String,  TODO
    val electionName: String,
    val versionName: String,
    val filename: String,
    val schema: Schema,
    val cvrs: List<CastVoteRecord>, // includes both regular and IRV votes, but not redacted groups
    val redacted: List<DominionRedactedGroup>,
    val exportCardStyles: List<ExportCardStyle>,
) {
    fun show() = buildString {
        appendLine("filename = $filename")
        appendLine(" electionName = $electionName")
        appendLine(" versionName = $versionName")
        appendLine("$schema")
        cvrs.forEach { appendLine(it.show()) }
        redacted.forEach { appendLine(it.toString()) }
    }

    fun summary() = buildString {
        appendLine("filename = $filename")
        appendLine(" electionName = $electionName")
        appendLine(" versionName = $versionName")
        appendLine(" ncvrs = ${cvrs.size}")
        val ccount = mutableMapOf<Int, Int>()
        cvrs.forEach { cvr ->
            cvr.contestVotes.forEach { contestVote ->
                ccount.getOrPut(contestVote.contestId, {0})
                ccount[contestVote.contestId] = ccount[contestVote.contestId]!! + 1
            }
        }
        ccount.toSortedMap().forEach { (k, v) ->
            appendLine("  contest ${k} has $v votes")
        }
    }
}

// CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,PrecinctPortion,BallotType
data class CastVoteRecord(
    val cvrNumber: Int,
    val tabulatorNum: Int,
    val batchId: String,
    val recordId: Int,
    val imprintedId: String,
    val ballotType: String, // might have to generate this ourself
) {
    var precinctPortion: String? = null
    var contestVotes =  mutableListOf<ContestVotes>() // equivilent to Map<contestId, IntArray>

    fun addVotes(schema: Schema, line: CSVRecord, lineno: Int): CastVoteRecord {
        var colidx = schema.nheaders // skip over the first 6 or 7 columns
        while (colidx < schema.columns.size) {
            if (line.get(colidx).isNotEmpty()) {
                val useContestIdx = schema.columns[colidx].contestIdx
                val useContest: SchemaContestInfo = schema.contests[useContestIdx]
                if (useContest.isIRV) {
                    // cvr.raw = makeRaw(line, useContest.startCol, useContest.ncols)
                    val candVotes = makeIrvVotes(schema, line, lineno, useContest)
                    contestVotes.add(ContestVotes(useContestIdx, candVotes))
                } else {
                    val candVotes  = makeRegularVotes(schema, line, lineno, useContest)
                    contestVotes.add( ContestVotes(useContestIdx, candVotes))
                }
                colidx += useContest.ncols
            } else {
                colidx++
            }
        }
        if (contestVotes.isEmpty())
            print("") // should remove cvr i think
        return this
    }

    fun makeRegularVotes(schema: Schema, line: CSVRecord, lineno: Int, exportContest: SchemaContestInfo): List<Int> {
        val votes = mutableListOf<Int>()
        for (i in 0 until exportContest.ncols) { // so i in [0..ncands)
            val colno = exportContest.startCol + i
            if (!schema.writeIns.contains(colno)) { // dont record write-ins
                val colValueS = line.get(colno)
                try {
                    val colValue = colValueS.toInt()
                    if (colValue > 0) votes.add(i)
                } catch (e: NumberFormatException) {
                    logger.warn{ "Cant parse '$colValueS' at col $colno line $lineno; probably didnt catch the redacted line"}
                    return emptyList()
                }
            }
        }
        return votes
    }

    //                             exportContest.startCol, start
    //                            exportContest.ncols, count
    //                            exportContest.nchoices, ncands
    fun makeIrvVotes(schema: Schema, line: CSVRecord, lineno: Int, exportContest: SchemaContestInfo): List<Int> {
        val raw = mutableListOf<Int>()
        for (i in 0 until exportContest.ncols) {
            raw.add( line.get(exportContest.startCol + i).toInt() )
        }
        val cands = mutableListOf<IntArray>()
        for (cand in 0 until exportContest.nchoices) {
            val candArray = IntArray(exportContest.nchoices) { i -> raw[cand + exportContest.nchoices*i] }
            cands.add(candArray)
        }
        val ranked = mutableListOf<Int>()
        for (rank in 0 until exportContest.nchoices) {
            for (cand in 0 until exportContest.nchoices) {
                if (cands[cand][rank] == 1) ranked.add(cand)
            }
        }
        return ranked
    }

    fun voteFor(contest: Int): ContestVotes? = contestVotes.find { it.contestId == contest}

    fun show() = buildString {
        append("$cvrNumber, ")
        append("$tabulatorNum, ")
        append("$batchId, ")
        append("$recordId, ")
        append("$imprintedId, ")
        append("$precinctPortion, ")
        append("$ballotType, ")
        contestVotes.forEach {
            append("${it.contestId}: ${it.candVotes.joinToString(",")}, ")
        }
    }

    /*
    // this assumes that you have a single schema you use for the ContestInfo....
    fun convertToCvr(): Cvr {
        val cvrb = CvrBuilder2(this.imprintedId,  false)
        this.contestVotes.forEach{
            cvrb.replaceContestVotes(it.contestId, it.candVotes.toIntArray())
        }
        return cvrb.build()
    } */

    companion object {
        val header = "cvrNumber, tabulatorNum, batchId, recordId, imprintedId, ballotType, contest:votes"
    }

}

// note that these contestIds are internal to this file (!)
// contestIdx = contestId. Need to cross reference with contest name in the header to get that right
// use colIdx to eliminate write-ins.
data class ContestVotes(val contestId: Int, val candVotes: List<Int>)

// TODO merge groups of the same ballotType ??
class DominionRedactedGroup(var ballotType: String) {
    // dont have ContestInfos yet
    val contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>()  // contestId -> candidateId -> nvotes
    private var csvRecord : CSVRecord? = null // debugging
    var ncards: Int = 1  //used by the accumulating group

    fun contests() = contestVotes.keys.toSet()

    fun addVotes(schema: Schema, line: CSVRecord): DominionRedactedGroup {
        var colidx = schema.nheaders // skip over the first 6 or 7 columns
        while (colidx < line.size()) {
            val valueAtIdx = line.get(colidx)
            if (valueAtIdx.isNotEmpty()) {
                val useContestIdx = schema.columns[colidx].contestIdx  // same as contestID?
                val useContest = schema.contests[useContestIdx]
                if (useContest.isIRV) {
                    // I think these are just regular Cvrs but the IRV contest was made seperate for privacy reasons
                    // "RCV Redacted & Randomly Sorted",,,,,"DS-01",0,0,1,0,0,0,0,1,1,0,0,0,0,1,0,0,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
                    println("*** IRV RedactedVotes shouldnt get here!")
                } else {
                    val candidateVotes = contestVotes.getOrPut(useContestIdx, { mutableMapOf() })
                    for (candIdx in 0 until useContest.ncols) {
                        val nvotes = line.get(useContest.startCol + candIdx).toInt()
                        val prev = candidateVotes[candIdx] ?: 0
                        candidateVotes[candIdx] = prev + nvotes
                    }
                    if (useContest.contestIdx == 31 && candidateVotes.values.sum() == 1)
                        print("")
                }
                colidx += useContest.ncols
            } else {
                colidx++
            }
        }
        csvRecord = line
        return this
    }

    fun merge(other: DominionRedactedGroup, voteForNmap: Map<Int, Int>): DominionRedactedGroup {
        other.contestVotes.forEach { (contestId, otherCands) ->
            val mycands = contestVotes.getOrPut(contestId, { mutableMapOf() })

            otherCands.forEach { (cand, otherVote) ->
                val myvotes = mycands[cand] ?: 0
                mycands[cand] = myvotes + otherVote
            }
        }
        this.ncards += other.ncards // other.minCards(voteForNmap)

        return this
    }

    // method #1
    // based on votes and voteForN, calculates the minimum number of cards that in this redacted line
    fun minCards(voteForNmap: Map<Int, Int>): Int {
        var minCards = 0
        contestVotes.forEach { (contestId, cands) ->
            val voteForN = voteForNmap[contestId]!!
            val minCardsForContest = roundUp(cands.values.sum() / voteForN.toDouble())
            minCards = max(minCards, minCardsForContest)
        }
        return minCards
    }

    override fun toString() = buildString {
        val contests = contestVotes.map { it.key }.sorted()
        val totalVotes = contestVotes.values.map{ it.values }.flatten().sum()
        append("RedactedGroup('$ballotType', ncards=$ncards, contests=$contests totalVotes=$totalVotes)")
        // appendLine(csvRecord.toString())
    }

    companion object {

        fun makeAccumulator(starting: DominionRedactedGroup, accumName:String, voteForNmap: Map<Int, Int>): DominionRedactedGroup {
            val accum = DominionRedactedGroup(accumName)
            accum.merge(starting, voteForNmap)

            // override with method #2
            if (starting.csvRecord != null) {
                accum.ncards = parseNCards(starting.csvRecord!!.values()[0])
            }
            return accum
        }

        // method #2: specific to Boulder25; "Redacted and Consolidated 10 Ballots"
        fun parseNCards(line:String): Int {
            if (!line.contains("Redacted and Consolidated")) return 1

            val tokens = line.split(" ")
            require(tokens.size > 3) { "unexpected redacted line $line" }
            val ncards = tokens[3].toInt()
            return ncards
        }
    }
}

private val showDontMatch = false
private val showBallotStyles = false
private val showRedactedGroups = false

data class ExportCardStyle(val name: String, val contests: Set<Int>, var count: Int = 0)

class BallotStyles {
    val ballotTypes = mutableMapOf<Set<Int>, ExportCardStyle>()
    val redactedGroups = mutableMapOf<String, DominionRedactedGroup>()

    fun add(cvr:CastVoteRecord) {
        val cvrContests = cvr.contestVotes.map { it.contestId }.toSet()
        val ballotType = ballotTypes.getOrPut(cvrContests) { ExportCardStyle(cvr.ballotType, cvrContests) }
        ballotType.count++
    }

    // TODO we might want to remove contests with vote count == 0 ??
    fun add(redacted:DominionRedactedGroup, voteForNmap: Map<Int, Int>) {
        // this is Boulder specific: val rname = if (redacted.contestVotes.contains(31)) "r${redacted.ballotType}+31"
        val rname = "r${redacted.ballotType}"
        val group = redactedGroups[rname]
        if (group == null) {
            redactedGroups[rname] = DominionRedactedGroup.makeAccumulator(redacted, rname, voteForNmap)
        } else {
            if (group.contests() == redacted.contests())
                group.merge(redacted, voteForNmap)
            else if (showDontMatch)
                println("redacted $redacted doesnt match $group; c31 = ${redacted.contestVotes[31]}")
        }
    }
}

/* TODO extract the "Vote For" from the header, use to calculate the number of cards in the redacted lines. (method 1)
// "Boulder County 2025 Coordinated","5.17.17.1",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
//,,,,,,,"City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Lafayette City Council (Vote For=4)","City of Lafayette City Council (Vote For=4)","City of Lafayette City Council (Vote For=4)","City of Lafayette City Council (Vote For=4)","City of Lafayette City Council (Vote For=4)","City of Lafayette City Council (Vote For=4)","City of Lafayette City Council (Vote For=4)","City of Lafayette City Council (Vote For=4)","City of Lafayette City Council (Vote For=4)","City of Lafayette City Council (Vote For=4)","City of Longmont Mayor (Vote For=1)","City of Longmont Mayor (Vote For=1)","City of Longmont Mayor (Vote For=1)","City of Longmont Mayor (Vote For=1)","City of Longmont City Council At Large (Vote For=2)","City of Longmont City Council At Large (Vote For=2)","City of Longmont City Council At Large (Vote For=2)","City of Longmont City Council At Large (Vote For=2)","City of Longmont City Council At Large (Vote For=2)","City of Longmont City Council At Large (Vote For=2)","City of Longmont City Council Ward 2 (Vote For=1)","City of Longmont City Council Ward 2 (Vote For=1)","City of Louisville City Council Ward 1 (4-year term) (Vote For=1)","City of Louisville City Council Ward 1 (4-year term) (Vote For=1)","City of Louisville City Council Ward 2 (4-year term) (Vote For=1)","City of Louisville City Council Ward 3 (4-year term) (Vote For=1)","Boulder Valley School District RE-2 Director District B (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District E (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District E (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District F (4 Years) (Vote For=1)","St. Vrain Valley School District RE-1J Board of Education Director in Director District B (Vote For=1)","St. Vrain Valley School District RE-1J Board of Education Director in Director District B (Vote For=1)","St. Vrain Valley School District RE-1J Board of Education Director in Director District D (Vote For=1)","St. Vrain Valley School District RE-1J Board of Education Director in Director District D (Vote For=1)","St. Vrain Valley School District RE-1J Board of Education Director in Director District E (Vote For=1)","St. Vrain Valley School District RE-1J Board of Education Director in Director District F (Vote For=1)","Thompson School District R2-J Board of Education Director District B (Vote For=1)","Thompson School District R2-J Board of Education Director District E (Vote For=1)","Thompson School District R2-J Board of Education Director District E (Vote For=1)","Thompson School District R2-J Board of Education Director District F (Vote For=1)","Thompson School District R2-J Board of Education Director District F (Vote For=1)","City of Longmont Municipal Court Judge - Frick (Vote For=1)","City of Longmont Municipal Court Judge - Frick (Vote For=1)","Proposition LL (Statutory) (Vote For=1)","Proposition LL (Statutory) (Vote For=1)","Proposition MM (Statutory) (Vote For=1)","Proposition MM (Statutory) (Vote For=1)","Boulder County Ballot Issue 1A (Vote For=1)","Boulder County Ballot Issue 1A (Vote For=1)","Boulder County Ballot Issue 1B (Vote For=1)","Boulder County Ballot Issue 1B (Vote For=1)","City of Boulder Ballot Issue 2A (Vote For=1)","City of Boulder Ballot Issue 2A (Vote For=1)","City of Boulder Ballot Issue 2B (Vote For=1)","City of Boulder Ballot Issue 2B (Vote For=1)","City of Lafayette Ballot Issue 2C (Vote For=1)","City of Lafayette Ballot Issue 2C (Vote For=1)","City of Louisville Ballot Question 300 (Vote For=1)","City of Louisville Ballot Question 300 (Vote For=1)","City of Louisville Ballot Question 301 (Vote For=1)","City of Louisville Ballot Question 301 (Vote For=1)","Thompson School District R2-J Ballot Issue 5A (Vote For=1)","Thompson School District R2-J Ballot Issue 5A (Vote For=1)","Hygiene Fire Protection District Ballot Issue 6A (Vote For=1)","Hygiene Fire Protection District Ballot Issue 6A (Vote For=1)","Sunshine Fire Protection District Ballot Issue 6B (Vote For=1)","Sunshine Fire Protection District Ballot Issue 6B (Vote For=1)","Coal Creek Canyon Fire Protection District Ballot Issue 7B (Vote For=1)","Coal Creek Canyon Fire Protection District Ballot Issue 7B (Vote For=1)"
//,,,,,,,"Nicole Speer","Rob Kaplan","Montserrat Palacios","Rob Smoke","Maxwell Lord","Jennifer Robins","Aaron Stone","Lauren Folkerts","Mark Wallach","Matt Benjamin","Rachel Rose Isaacson","Adam Gianola","Luke Arrington","Josh Beryl","Eric Ryant","Saul Tapia Vega","Rob Glenn","Kyle Beaulieu","Annmarie Jensen","Crystal Gallegos","Michael Watson","Susie Hidalgo-Fahring","Sarah Levison","Diane Crist","Shakeel Dalal","Crystal Prieto","Alex Kalkhofer","John Lembke","Jake Marsing","Riegan Sage","Steven Altschuler","Matthew Popkin","Teresa Simpkins","Joshua H. Cooperman","Denise Montagu","Judi Kern","Dietrich Hoefner","Nicole Rajpal","Jeffrey Lowe Anderson","Deann Bucher","Ana Temu Otting","Peggy A. Kelly","Hadley Solomon","Meosha Babbs","John Ahrens","Jocelyn Gilligan","Sarah Hurianek","Mike Scholl","Alexandra Lessem","Mary Buchanan","Dmitri Atrash","Lori Goebel","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against"
//"CvrNumber","TabulatorNum","BatchId","RecordId","ImprintedId","CountingGroup","BallotType",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
//"1","102","1","82","102-1-82","Regular","03",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,0,1,1,,,,,,,,,,,,,,1,0,1,0,1,0,1,0,,,,,,,,,,,,,,,,,,
//"2","102","1","81","102-1-81","Regular","03",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,0,1,1,,,,,,,,,,,,,,1,0,1,0,1,0,1,0,,,,,,,,,,,,,,,,,,

// TODO extract the number of ballots from the "Consolidated 10 Ballots", and compare with method 1 (method 2)
// "Redacted and Consolidated 10 Ballots",,,,,,"11 & PO-CC",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,4,1,2,4,,,,,,,,,,,,,,6,2,3,6,3,5,3,5,,,,,,,,,,,,,,,,,4,5
//"Redacted and Consolidated 18 Ballots",,,,,,"14 & PO-SF",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,8,9,1,8,,,,,,,,,,,,,,10,3,6,6,8,5,4,9,,,,,,,,,,,,,,,16,2,,
//"Redacted and Consolidated 20 Ballots",,,,,,"18 & 12 & PO-HF",,,,,,,,,,,,,,,,,,,,,,2,0,3,1,0,3,1,1,2,5,3,3,,,,,,,,,5,1,1,5,3,3,,,,,,2,4,11,3,11,3,9,5,9,5,,,,,,,,,,,,,5,7,,,,
//"Redacted",,,,,,"02",,,,,,,,,,,,,,,,,,,,,,0,0,0,0,0,0,0,1,0,0,,,,,,,,,,,0,0,0,0,0,0,,,,,,0,0,1,0,1,0,1,0,1,0,,,,,,,,,,,,,,,,,,
//"Redacted",,,,,,"09",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,0,0,,,0,0,1,0,,,,,,,,,,,,,,1,0,1,0,1,0,1,0,,,,,,,0,1,0,1,,,,,,,,

// method 1 vs 2: 9 should be 10; 18 should be 18; 14 should be 20

// TODO add the ncards foreach Redacted group. that lets us calculate the missing votes in the pool.

fun readDominionCvrExportCsv(filename: String, showFirst: Int? = null): DominionCvrExport {
    val ballotStyles = BallotStyles()

    val parser = if (filename.endsWith(".zip")) {
        val zipReader = ZipReader(filename)
        // by convention, the file inside is the filename with zip replaced by csv
        val lastPart = filename.substringAfterLast("/")
        val innerFilename = lastPart.replace(".zip", ".csv")
        val inputStream = zipReader.inputStream(innerFilename)
        val reader: Reader = InputStreamReader(inputStream, "UTF-8")
        CSVParser(reader, CSVFormat.DEFAULT)
        // dunno CSVParser.Builder.get()

    } else {
        CSVParser.parse(File(filename), Charset.forName("UTF-8"), CSVFormat.DEFAULT)
    }

    val records: Iterator<CSVRecord> = parser.iterator()
    var lineno = 1

    // 1) we expect the first line to be the election name
    val electionLine = records.next()
    if (showHeader) showLine("electionName", electionLine)
    lineno++
    val electionName = electionLine.get(0).replace("[^ -~]".toRegex(), "")
    val versionName = electionLine.get(1).trim()

    // 2) the contest name for that column
    val contestLine = records.next()
    val header1 = contestLine.toList().joinToString(", ")
    if (showHeader) {
        println("header1 (contest names) has ${contestLine.toList().size} columns")
        println("header1 = $header1")
    }

    var my_first_contest_column = 0
    while ("" == contestLine[my_first_contest_column]) {
        my_first_contest_column++
    }
    lineno++

    // 3) the choice/candidate name for that column
    val choiceLine = records.next()
    if (showLines) showLine("choice/candidate", choiceLine)
    lineno++

    // 4) the header for the first 6 columns, then party affiliation
    val headerRecord = records.next()
    if (showLines) showLine("header", headerRecord)
    val header2 = headerRecord.toList().joinToString(", ")
    if (showHeader) {
        println("header2 (party affiliation) has ${headerRecord.toList().size} columns")
        println(header2)
    }

    // make the column structure out of those 3 lines
    val schema = makeSchema(contestLine, choiceLine, headerRecord)
    val nvotesMap = schema.contests.associate { it.contestIdx to it.voteForN }
    val ballotTypeIdx = if (schema.nheaders == 6) 5 else 6 // TODO see if BallotType == header 6

    val cvrs = mutableListOf<CastVoteRecord>()

    var cvrCount = 0
    var rcvRedacted = 0
    while (records.hasNext()) {
        val line = records.next()
        if (line.isEmpty()) break

        // probably Boulder specific ??
        if (line.get(0).startsWith("Redacted")) { // but not "RCV Redacted ..." which can be treated like a normal CVR
            val isA =  line.get(0).contains("A cards")
            val isB =  line.get(0).contains("B cards")
            val ballotStyle = line.get(ballotTypeIdx) + if (isA) "-A" else if (isB) "-B" else ""
            val redactedGroup = DominionRedactedGroup(ballotStyle).addVotes(schema, line)
            ballotStyles.add(redactedGroup, nvotesMap)

        } else if (line.get(0).startsWith("RCV Redacted")) {
            val cvr = CastVoteRecord(
                rcvRedacted,
                0,
                "N/A",
                0,
                "N/A",
                line.get(ballotTypeIdx),
            )
            rcvRedacted++
            cvrs.add(cvr.addVotes(schema, line, lineno))  // IRV redacted vote
            ballotStyles.add(cvr)

        } else {
            try {
                line.get(0).toInt()
            } catch (e: Exception) {
                // println(line) // assume thats the end
                break
            }
            val cvr = CastVoteRecord(
                cvrNumber = line.get(0).toInt(),
                tabulatorNum = line.get(1).toInt(),
                batchId = line.get(2),
                recordId = line.get(3).toInt(),
                imprintedId = line.get(4),
                ballotType = line.get(ballotTypeIdx),
            )
            cvrs.add(cvr.addVotes(schema, line, lineno)) // regular vote
            ballotStyles.add(cvr)
            if (showFirst != null && cvrCount < showFirst) println(cvr.show())
        }
        cvrCount++
        lineno++
    }
    if (rcvRedacted > 0) println("  read $rcvRedacted RCV Redacted votes")

    if (showRedactedGroups) {
        println("Redacted Groups size = ${ballotStyles.redactedGroups.size}")
        ballotStyles.redactedGroups.toSortedMap().forEach { println("  ${it.value}") }
    }

    val ballotTypes = ballotStyles.ballotTypes.values.sortedBy { it.count }.reversed()
    return DominionCvrExport(electionName, versionName, filename, schema, cvrs,
        ballotStyles.redactedGroups.toSortedMap().values.toList(),
        ballotTypes)
}

private val d3f = "%3d"
fun showLine(what: String, line: CSVRecord) {
    println(what)
    val elems: List<String> = line.toList()
    elems.forEachIndexed { idx, it ->
        if (it.isNotEmpty()) println("  ${d3f.format(idx)}: $it")
    }
}

fun makeRaw(line: CSVRecord, start: Int, count: Int): List<Int> {
    val raw = mutableListOf<Int>()
    for (i in 0 until count) {
        raw.add( line.get(start + i).toInt() )
    }
    return raw
} */

/////////////////////////////////////////////////////////////////////////

class Schema(val columns: List<SchemaColumnInfo>, val nheaders: Int, val contests: List<SchemaContestInfo> ) {
    val writeIns : Set<Int> = columns.filter{ it.choice == "Write-in" }.map { it.colno }.toSet()

    fun choices(contestId: Int): List<String> {
        val contest = contests.find{ it.contestIdx == contestId }
        val result = mutableListOf<String>()
        if (contest != null) {
            for (colIdx in contest.startCol until contest.startCol + contest.nchoices) {
                result.add( columns[colIdx].choice )
            }
        }
        return result
    }

    fun voteFor(contestId: Int, cvr: CastVoteRecord): List<String> {
        val choices = choices(contestId)
        val contestVotes = cvr.voteFor(contestId)
        val result = mutableListOf<String>()
        contestVotes?.candVotes?.forEach { result.add( choices[it]) } // could barf if malformed
        return result
    }

    fun show() = buildString {
        contests.forEach {
            with (it) {
                appendLine("Contest $contestIdx '$contestName' cols=($startCol, ${startCol+ncols-1}) isIRV=$isIRV nchoices=$nchoices")
                for (colIdx in startCol until startCol+ncols) {
                    val col = columns[colIdx]
                    appendLine("    ${col.choice} ${col.header}")
                }
            }
        }
    }
}

private val contestWidth = 50
private val choiceWidth = 20
data class SchemaColumnInfo(val colno:Int, val contest: String, val choice: String, val header: String, var contestIdx: Int = -1) {
    init {
        if (colno == 58)
            print("")
    }

    fun show(): String {
        return "${nfn(colno, 5)}, ${trunc(contest, contestWidth)}, ${trunc(choice, choiceWidth)}, $header, $contestIdx)"
    }

    companion object {
        val header = "colno, ${trunc("contest", contestWidth)}, ${trunc("choice", choiceWidth)}, header, contestIdx)"
    }
}

data class SchemaContestInfo(val contestIdx: Int, val contestName: String, val startCol: Int, val ncols: Int) {
    val isIRV: Boolean
    val nchoices: Int
    val voteForN: Int

    init {
        isIRV = contestName.contains("Number of ranks=")
        nchoices = if (isIRV) {
            // val pos = contestName.("Number of ranks=") + "Number of ranks=".length
            sqrt(ncols.toDouble()).toInt() // WTF ?
        } else ncols
        val (name, nwinners) = if (isIRV) parseIrvContestName(contestName) else parseContestNameAndVoteFor(contestName)
        voteForN = nwinners
    }
}

// headers are the parties
fun makeSchema(contests: CSVRecord, choices: CSVRecord, headers: CSVRecord): Schema {
    /* println("contest,   choice,    headers")
    choices.values().forEachIndexed { idx, choice ->
        val contest = if (idx < contests.values().size) contests.get(idx) else ""
        val header = if (idx < headers.values().size) headers.get(idx) else ""
        println("$contest,   $choice,     $header")
    } */

    require(contests.size() <= choices.size())
    require(headers.size() == choices.size())

    val columns = mutableListOf<SchemaColumnInfo>()
    for (idx in 0 until contests.size()) {
        columns.add( SchemaColumnInfo(idx, contests.get(idx).trim(), choices.get(idx).trim(), headers.get(idx).trim() ) )
    }
    val nheaders = columns.first { it.contest.isNotEmpty() }.colno

    val skipIdx = nheaders
    var startIdx = nheaders
    var currContestIdx = 0
    var currContestName = ""
    val ccontests = mutableListOf<SchemaContestInfo>()
    for (colidx in skipIdx until columns.size) { // skip the first 7
        val col = columns.get(colidx)
        if (colidx != skipIdx && munge(col.contest) != munge(currContestName)) {
            ccontests.add( SchemaContestInfo(currContestIdx, currContestName, startIdx,colidx-startIdx) )
            startIdx = colidx
            currContestIdx++
        }
        col.contestIdx = currContestIdx
        currContestName = col.contest
    }
    ccontests.add( SchemaContestInfo(currContestIdx, currContestName, startIdx, columns.size-startIdx) )

    return Schema(columns, nheaders, ccontests)
}

fun cleanContestName(col: String) : String {
    val pos = col.indexOf("(Vote")
    return if (pos > 0) col.substring(0, pos).trim() else col.trim()
}

