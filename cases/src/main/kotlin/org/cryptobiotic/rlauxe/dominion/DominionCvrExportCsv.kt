package org.cryptobiotic.rlauxe.dominion

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.util.ZipReader
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.trunc
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.lang.StrictMath.sqrt
import java.nio.charset.Charset

// this reads csv files from "Dominion CVR export files", maybe standard Dominion format ?
// We are getting these files from Boulder County, used by createBoulderElection()
// Note these record the cvr undervotes, but not for the redacted votes

// 2024 Boulder County GE Recounts,5.17.17.1,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
//,,,,,,,Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Representative to the 119th United States Congress - District 2 (Vote For=1),Representative to the 119th United States Congress - District 2 (Vote For=1),Representative to the 119th United States Congress - District 2 (Vote For=1),Representative to the 119th United States Congress - District 2 (Vote For=1),Representative to the 119th United States Congress - District 2 (Vote For=1),Representative to the 119th United States Congress - District 2 (Vote For=1),Representative to the 119th United States Congress - District 2 (Vote For=1),State Board of Education Member - Congressional District 2 (Vote For=1),State Board of Education Member - Congressional District 2 (Vote For=1),State Board of Education Member - Congressional District 2 (Vote For=1),Regent of the University of Colorado - At Large (Vote For=1),Regent of the University of Colorado - At Large (Vote For=1),Regent of the University of Colorado - At Large (Vote For=1),Regent of the University of Colorado - At Large (Vote For=1),State Senator - District 17 (Vote For=1),State Senator - District 17 (Vote For=1),State Senator - District 18 (Vote For=1),State Senator - District 18 (Vote For=1),State Representative - District 10 (Vote For=1),State Representative - District 10 (Vote For=1),State Representative - District 11 (Vote For=1),State Representative - District 11 (Vote For=1),State Representative - District 12 (Vote For=1),State Representative - District 12 (Vote For=1),State Representative - District 19 (Vote For=1),State Representative - District 19 (Vote For=1),State Representative - District 49 (Vote For=1),State Representative - District 49 (Vote For=1),District Attorney - 20th Judicial District (Vote For=1),Regional Transportation District Director - District I (Vote For=1),Boulder County Commissioner - District 1 (Vote For=1),Boulder County Commissioner - District 1 (Vote For=1),Boulder County Commissioner - District 1 (Vote For=1),Boulder County Commissioner - District 2 (Vote For=1),Boulder County Commissioner - District 2 (Vote For=1),Boulder County Coroner (Vote For=1),City of Louisville City Council Ward 1 (Vote For=1),City of Louisville City Council Ward 1 (Vote For=1),Town of Erie - Mayor (Vote For=1),Town of Erie - Mayor (Vote For=1),Town of Erie - Council Member District 1 (Vote For=2),Town of Erie - Council Member District 1 (Vote For=2),Town of Erie - Council Member District 1 (Vote For=2),Town of Erie - Council Member District 1 (Vote For=2),Town of Erie - Council Member District 2 (Vote For=2),Town of Erie - Council Member District 2 (Vote For=2),Town of Erie - Council Member District 2 (Vote For=2),Town of Erie - Council Member District 2 (Vote For=2),Town of Superior - Trustee (Vote For=3),Town of Superior - Trustee (Vote For=3),Town of Superior - Trustee (Vote For=3),Town of Superior - Trustee (Vote For=3),Town of Superior - Trustee (Vote For=3),Town of Superior - Trustee (Vote For=3),Town of Superior - Trustee (Vote For=3),Justice of the Colorado Supreme Court - Berkenkotter (Vote For=1),Justice of the Colorado Supreme Court - Berkenkotter (Vote For=1),Justice of the Colorado Supreme Court - Boatright (Vote For=1),Justice of the Colorado Supreme Court - Boatright (Vote For=1),Justice of the Colorado Supreme Court - Marquez (Vote For=1),Justice of the Colorado Supreme Court - Marquez (Vote For=1),Colorado Court of Appeals Judge - Dunn (Vote For=1),Colorado Court of Appeals Judge - Dunn (Vote For=1),Colorado Court of Appeals Judge - Jones (Vote For=1),Colorado Court of Appeals Judge - Jones (Vote For=1),Colorado Court of Appeals Judge - Kuhn (Vote For=1),Colorado Court of Appeals Judge - Kuhn (Vote For=1),Colorado Court of Appeals Judge - Roman (Vote For=1),Colorado Court of Appeals Judge - Roman (Vote For=1),Colorado Court of Appeals Judge - Schutz (Vote For=1),Colorado Court of Appeals Judge - Schutz (Vote For=1),District Court Judge - 20th Judicial District - Collins (Vote For=1),District Court Judge - 20th Judicial District - Collins (Vote For=1),District Court Judge - 20th Judicial District - Gunning (Vote For=1),District Court Judge - 20th Judicial District - Gunning (Vote For=1),District Court Judge - 20th Judicial District - Lindsey (Vote For=1),District Court Judge - 20th Judicial District - Lindsey (Vote For=1),District Court Judge - 20th Judicial District - Mulvahill (Vote For=1),District Court Judge - 20th Judicial District - Mulvahill (Vote For=1),Boulder County Court - Martin (Vote For=1),Boulder County Court - Martin (Vote For=1),Amendment G (Constitutional) (Vote For=1),Amendment G (Constitutional) (Vote For=1),Amendment H (Constitutional) (Vote For=1),Amendment H (Constitutional) (Vote For=1),Amendment I (Constitutional) (Vote For=1),Amendment I (Constitutional) (Vote For=1),Amendment J (Constitutional) (Vote For=1),Amendment J (Constitutional) (Vote For=1),Amendment K (Constitutional) (Vote For=1),Amendment K (Constitutional) (Vote For=1),Amendment 79 (Constitutional) (Vote For=1),Amendment 79 (Constitutional) (Vote For=1),Amendment 80 (Constitutional) (Vote For=1),Amendment 80 (Constitutional) (Vote For=1),Proposition JJ (Statutory) (Vote For=1),Proposition JJ (Statutory) (Vote For=1),Proposition KK (Statutory) (Vote For=1),Proposition KK (Statutory) (Vote For=1),Proposition 127 (Statutory) (Vote For=1),Proposition 127 (Statutory) (Vote For=1),Proposition 128 (Statutory) (Vote For=1),Proposition 128 (Statutory) (Vote For=1),Proposition 129 (Statutory) (Vote For=1),Proposition 129 (Statutory) (Vote For=1),Proposition 130 (Statutory) (Vote For=1),Proposition 130 (Statutory) (Vote For=1),Proposition 131 (Statutory) (Vote For=1),Proposition 131 (Statutory) (Vote For=1),City of Boulder Ballot Question 2C (Vote For=1),City of Boulder Ballot Question 2C (Vote For=1),City of Boulder Ballot Question 2D (Vote For=1),City of Boulder Ballot Question 2D (Vote For=1),City of Boulder Ballot Question 2E (Vote For=1),City of Boulder Ballot Question 2E (Vote For=1),City of Lafayette Ballot Question 2A (Vote For=1),City of Lafayette Ballot Question 2A (Vote For=1),City of Longmont Ballot Issue 3A (Vote For=1),City of Longmont Ballot Issue 3A (Vote For=1),Town of Erie Ballot Issue 3C (Vote For=1),Town of Erie Ballot Issue 3C (Vote For=1),Town of Lyons Ballot Question 2B (Vote For=1),Town of Lyons Ballot Question 2B (Vote For=1),Town of Superior Ballot Issue 3B (Vote For=1),Town of Superior Ballot Issue 3B (Vote For=1),St. Vrain Valley School District RE-1J Ballot Issue 5C (Vote For=1),St. Vrain Valley School District RE-1J Ballot Issue 5C (Vote For=1),Thompson School District R2-J Ballot Issue 5A (Vote For=1),Thompson School District R2-J Ballot Issue 5A (Vote For=1),Thompson School District R2-J Ballot Issue 5B (Vote For=1),Thompson School District R2-J Ballot Issue 5B (Vote For=1),Eldorado Springs Public Improvement District Ballot Question 6C (Vote For=1),Eldorado Springs Public Improvement District Ballot Question 6C (Vote For=1),Homestead Public Improvement District of Boulder County Ballot Issue 6D (Vote For=1),Homestead Public Improvement District of Boulder County Ballot Issue 6D (Vote For=1),Lafayette Downtown Development Authority Ballot Question 6A (Vote For=1),Lafayette Downtown Development Authority Ballot Question 6A (Vote For=1),Lafayette Downtown Development Authority Ballot Issue 6B (Vote For=1),Lafayette Downtown Development Authority Ballot Issue 6B (Vote For=1),Regional Transportation District Ballot Issue 7A (Vote For=1),Regional Transportation District Ballot Issue 7A (Vote For=1),St. Vrain and Left Hand Water Conservancy District Ballot Issue 7C (Vote For=1),St. Vrain and Left Hand Water Conservancy District Ballot Issue 7C (Vote For=1)
//,,,,,,,Kamala D. Harris / Tim Walz,Donald J. Trump / JD Vance,Blake Huber / Andrea Denault,Chase Russell Oliver / Mike ter Maat,Jill Stein / Rudolph Ware,Randall Terry / Stephen E Broden,Cornel West / Melina Abdullah,Robert F. Kennedy Jr. / Nicole Shanahan,Write-in,Chris Garrity / Cody Ballard,Claudia De la Cruz / Karina GarcÃ­a,Shiva Ayyadurai / Crystal Ellis,Peter Sonski / Lauren Onak,Bill Frankel / Steve Jenkins,Brian Anthony Perry / Mark Sbani,Joe Neguse,Marshall Dawson,Cynthia Munhos de Aquino Sirianni,Jan Kok,Gaylon Kent,Write-in,Mike Watson,Kathy Gebhardt,Write-in,Ethan Augreen,Eric Rinard,Elliott Hood,Thomas Reasoner,T.J. Cole,Tom Van Lone,Sonya Jaquez Lewis,Judy Amabile,Gary Swing,William B. DeOreo,Junie Joseph,Karen McCormick,Kathy Reeves,Kyle Brown,Mark Milliman,Dan Woog,Jillaire McMillan,Steve Ferrante,Lesley Smith,Michael T. Dougherty,Karen Benker,Claire Levy,Write-in,Raphael Minot,Marta Loachamin,"Donald ""Don"" Lewis",Jeff Martin,Joshua Sroge,Josh Cooperman,Andrew Moore,Justin Brooks,John Mortellaro,Andrew Sawusch,Anil Pesaramelli,Richard Garcia,Dan Hoback,Brandon M. Bell,Dan Maloit,Ben Hemphill,Heather Cracraft,Jason Serbu,Mike Foster,Gregory D. Horowitz,George A. Kupfner,Bob McCool,Sandie Hammerly,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against
//CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,CountingGroup,BallotType,DEM,REP,APV,LBR,GRN,ACN,UNI,UNA,,,,,,,,DEM,REP,UNI,APV,LBR,,,DEM,,,REP,DEM,APV,UNI,REP,DEM,DEM,UNI,REP,DEM,DEM,REP,DEM,REP,REP,DEM,REP,DEM,DEM,,DEM,,,DEM,REP,DEM,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
//9,105,1,165,105-1-165,Regular,09,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,,,,,,,,,0,1,,,1,1,1,0,0,1,0,1,,,,,,,,,,,,,,,,,,,,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,0,1,1,0,1,0,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
//10,105,1,163,105-1-163,Regular,09,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,,,,,,,,,0,1,,,1,1,1,0,0,1,0,1,,,,,,,,,,,,,,,,,,,,0,1,0,1,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,0,1,0,1,1,0,1,0,0,1,1,0,0,1,1,0,1,0,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
// ...
// Redacted and Aggregated,,,,,,7,265,104,0,0,2,1,1,5,2,0,0,0,0,0,0,228,74,6,2,5,0,0,233,12,0,89,209,2,5,,,227,38,,,,,212,83,,,,,228,,223,4,0,207,79,216,,,,,,,,,,,,,130,87,111,50,25,36,101,175,74,147,91,163,75,167,70,145,89,162,63,150,69,152,67,147,55,148,55,150,54,141,58,149,60,223,73,212,58,195,93,261,53,133,135,255,68,170,137,263,40,204,104,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
// ...

// electionName, version?
// ,,,,,,,contestName...
// ,,,,,,,candidateName...
// columnHeader: CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,CountingGroup,BallotType,partyName ...
// ... (cvrs)
// Redacted and Aggregated,,,,,,BallotType,DEM,REP,APV,LBR,GRN,ACN,UNI,UNA ...

private val showRaw = false
private val showHeader = false
private val showLines = false

data class DominionCvrExportCsv(
    val countyId: String,
    val electionName: String,
    val versionName: String,
    val filename: String,
    val schema: Schema,
    val cvrs: List<CastVoteRecord>, // includes both regular and IRV votes
    val redacted: List<RedactedGroup>,
) {
    fun show() = buildString {
        appendLine("filename = $filename")
        appendLine(" countyId = $countyId")
        appendLine(" electionName = $electionName")
        appendLine(" versionName = $versionName")
        appendLine("$schema")
        cvrs.forEach { appendLine(it.show()) }
        redacted.forEach { appendLine(it.toString()) }
    }

    fun summary() = buildString {
        appendLine("filename = $filename")
        appendLine(" countyId = $countyId")
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
    val ballotType: String,
) {
    var precinctPortion: String? = null
    var contestVotes =  mutableListOf<ContestVotes>() // equivilent to Map<contestId, IntArray>

    fun addVotes(schema: Schema, line: CSVRecord): CastVoteRecord {
        var colidx = schema.nheaders // skip over the first 6 or 7 columns
        while (colidx < line.size()) {
            if (line.get(colidx).isNotEmpty()) {
                val useContestIdx = schema.columns[colidx].contestIdx
                val useContest: ExportContestInfo = schema.contests[useContestIdx]
                if (useContest.isIRV) {
                    // cvr.raw = makeRaw(line, useContest.startCol, useContest.ncols)
                    val candVotes = makeIrvVotes(schema, line, useContest)
                    contestVotes.add(ContestVotes(useContestIdx, candVotes))
                } else {
                    val candVotes  = makeRegularVotes(schema, line, useContest)
                    contestVotes.add( ContestVotes(useContestIdx, candVotes))
                }
                colidx += useContest.ncols
            } else {
                colidx++
            }
        }
        return this
    }

    fun voteFor(contest: Int): ContestVotes? = contestVotes.find { it.contestId == contest}

    fun show() = buildString {
        append("$cvrNumber, ")
        append("$tabulatorNum, ")
        append("$batchId, ")
        append("$recordId, ")
        append("$imprintedId, ")
        append("$precinctPortion, ")
        appendLine("$ballotType")
        contestVotes.forEach {
            appendLine("    ${it.contestId} ${it.candVotes.joinToString(",")}")
        }
    }

    fun convert(): Cvr {
        val cvrb = CvrBuilder2(this.cvrNumber.toString(),  false)
        this.contestVotes.forEach{
            cvrb.addContest(it.contestId, it.candVotes.toIntArray())
        }
        return cvrb.build()
    }
}

// use colIdx to eliminate write-ins.
data class ContestVotes(val contestId: Int, val candVotes: List<Int>)

// raw data from Boulder, before we start to adjust it. Turn into a CardPool,
// unfortunately, we dont know how many ballots this group represents, nor the number of undervotes
class RedactedGroup(val ballotType: String) {
    val contestVotes = mutableMapOf<Int, MutableMap<Int, Int>>()  // contestId -> candidateId -> nvotes
    var csvRecord : CSVRecord? = null // debugging

    fun addVotes(schema: Schema, line: CSVRecord): RedactedGroup {
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
                }
                colidx += useContest.ncols
            } else {
                colidx++
            }
        }
        csvRecord = line
        return this
    }

    override fun toString() = buildString {
        val contestIds = contestVotes.map { it.key }.sorted()
        val totalVotes = contestVotes.values.map{ it.values }.flatten().sum()
        append("RedactedGroup '$ballotType', contestIds=$contestIds, totalVotes=$totalVotes")
        // appendLine(csvRecord.toString())
    }

    // TODO divide by voteForN?
    // TODO to get this right, the A and B should be merged into one group
    fun maxCards(): Int {
        return contestVotes.values.maxOfOrNull { it.values.sum() }!!
    }

    fun undervote(): Map<Int, Int> {  // contest -> undervote
        val maxc = maxCards()
        val undervote = contestVotes.map { (id, cands) ->
            val sum = cands.map { it.value }.sum()
            Pair(id, maxc - sum)
        }
        return undervote.toMap()
    }

    fun showVotes(wantIds: List<Int>) = buildString {
        val maxc = maxCards()
        append("${trunc(ballotType, 9)}:")
        wantIds.forEach { id ->
            val contestVote = contestVotes[id]
            if (contestVote == null)
                append("    |")
            else {
                val sum = contestVote.map { it.value } .sum()
                append("${nfn(sum, 4)}|")
            }
        }
        appendLine()

        append("${trunc("", 9)}:")
        wantIds.forEach { id ->
            val contestVote = contestVotes[id]
            if (contestVote == null)
                append("    |")
            else {
                val sum = contestVote.map { it.value } .sum()
                append("${nfn(maxc-sum, 4)}|")
            }
        }
        appendLine()
    }
}

fun readDominionCvrExportCsv(filename: String, countyId: String): DominionCvrExportCsv {

    val parser = if (filename.endsWith(".zip")) {
        val zipReader = ZipReader(filename)
        // by convention, the file inside is the filename with zip replaced by csv
        val lastPart = filename.substringAfterLast("/")
        val innerFilename = lastPart.replace(".zip", ".csv")
        val inputStream = zipReader.inputStream(innerFilename)
        val reader: Reader = InputStreamReader(inputStream, "UTF-8")
        CSVParser(reader, CSVFormat.DEFAULT)

    } else {
        CSVParser.parse(File(filename), Charset.forName("UTF-8"), CSVFormat.DEFAULT)
    }

    var my_record_count = 0
    val records: Iterator<CSVRecord> = parser.iterator()
    var lineNum = 1

    // 1) we expect the first line to be the election name
    val electionLine = records.next()
    if (showLines) showLine("electionName", electionLine)
    lineNum++
    val electionName = electionLine.get(0)
    val versionName = electionLine.get(1)

    // 2) the contest name for that column
    val contestLine = records.next()
    if (showLines) showLine("contest", contestLine)

    var my_first_contest_column = 0
    while ("" == contestLine[my_first_contest_column]) {
        my_first_contest_column++
    }
    lineNum++

    // 3) the choice/candidate name for that column
    val choiceLine = records.next()
    if (showLines) showLine("choice/candidate", choiceLine)
    lineNum++

    // 4) the header for the first 6 columns, then party affiliation
    val headerRecord = records.next()
    if (showLines) showLine("header", headerRecord)
    val header = headerRecord.toList().joinToString(", ")
    if (showHeader) println("header = $header")

    // make the column structure out of those 3 lines
    val schema = makeSchema(contestLine, choiceLine, headerRecord)
    val ballotTypeIdx = if (schema.nheaders == 6) 5 else 6 // TODO see if BallotType == header 6

    val cvrs = mutableListOf<CastVoteRecord>()
    val redacted = mutableListOf<RedactedGroup>()

    var rcvRedacted = 0
    while (records.hasNext()) {
        val line = records.next()
        // showLine("line", line)
        if (line.get(0).startsWith("Redacted")) { // but not "RCV Redacted ..." which can be treated like a normal CVR
            val isA =  line.get(0).contains("A cards")
            val ballotStyle = line.get(ballotTypeIdx) + if (isA) "-A" else "-B"
            redacted.add(RedactedGroup(ballotStyle).addVotes(schema, line)) // "redacted" group of votes

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
            cvrs.add(cvr.addVotes(schema, line))  // IRV redacted vote

        } else {
            val cvr = CastVoteRecord(
                cvrNumber = line.get(0).toInt(),
                tabulatorNum = line.get(1).toInt(),
                batchId = line.get(2),
                recordId = line.get(3).toInt(),
                imprintedId = line.get(4),
                ballotType = line.get(ballotTypeIdx),
            )
            cvrs.add(cvr.addVotes(schema, line)) // regular vote
        }
    }
    if (rcvRedacted > 0) println("  read $rcvRedacted RCV Redacted votes")
    return DominionCvrExportCsv(countyId, electionName, versionName, filename, schema, cvrs, redacted)
}

val d3f = "%3d"
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
}

//                             exportContest.startCol, start
//                            exportContest.ncols, count
//                            exportContest.nchoices, ncands
fun makeIrvVotes(schema: Schema, line: CSVRecord, exportContest: ExportContestInfo): List<Int> {
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

fun makeRegularVotes(schema: Schema, line: CSVRecord, exportContest: ExportContestInfo): List<Int> {
    val votes = mutableListOf<Int>()
    for (i in 0 until exportContest.ncols) {
        val colno = exportContest.startCol + i
        if (!schema.writeIns.contains(colno)) { // dont record write-ins
            val colValue = line.get(colno).toInt()
            if (colValue > 0) votes.add(i)
        }
    }
    return votes
}

/////////////////////////////////////////////////////////////////////////

class Schema(val columns: List<ColumnInfo>, val nheaders: Int, val contests: List<ExportContestInfo> ) {
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

class ColumnInfo(val colno:Int, val contest: String, val choice: String, val header: String) {
    var contestIdx: Int = -1
}

class ExportContestInfo(val contestIdx: Int, val contestName: String, val startCol: Int, val ncols: Int) {
    val isIRV: Boolean
    val nchoices: Int

    init {
        isIRV = contestName.contains("Number of ranks=")
        nchoices = if (isIRV) {
            // val pos = contestName.("Number of ranks=") + "Number of ranks=".length
            sqrt(ncols.toDouble()).toInt()
        } else ncols
    }
}

fun makeSchema(contests: CSVRecord, choices: CSVRecord, headers: CSVRecord): Schema {
    require(contests.size() == choices.size())
    require(headers.size() == choices.size())

    val columns = mutableListOf<ColumnInfo>()
    for (idx in 0 until contests.size()) {
        columns.add( ColumnInfo(idx, contests.get(idx).trim(), cleanName(choices.get(idx)), headers.get(idx).trim() ) )
    }
    val nheaders = columns.first { it.contest.isNotEmpty() }.colno

    val skipIdx = nheaders
    var startIdx = nheaders
    var currContestIdx = 0
    var currContestName = ""
    val ccontests = mutableListOf<ExportContestInfo>()
    for (colidx in skipIdx until columns.size) { // skip the first 7
        val col = columns.get(colidx)
        if (colidx != skipIdx && col.contest != currContestName) {
            ccontests.add( ExportContestInfo(currContestIdx, currContestName, startIdx,colidx-startIdx) )
            startIdx = colidx
            currContestIdx++
        }
        col.contestIdx = currContestIdx
        currContestName = col.contest
    }
    ccontests.add( ExportContestInfo(currContestIdx, currContestName, startIdx, columns.size-startIdx) )

    return Schema(columns, nheaders, ccontests)
}

fun cleanName(col: String) : String {
    val pos = col.indexOf("(")
    return if (pos > 0) col.substring(0, pos).trim() else col.trim()
}

