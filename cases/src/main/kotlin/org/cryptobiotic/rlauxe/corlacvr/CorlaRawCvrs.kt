package org.cryptobiotic.rlauxe.corlacvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.sfn
import org.cryptobiotic.rlauxe.util.trunc
import kotlin.text.isEmpty

// this reads CVRs from "Dominion CVR export files", maybe a standard Dominion csv format.
// This class stays low-level and tries not to muck with the data
// TODO perhaps there should be a separate object using cvrs: CloseableIterable ??
//      and read through all the cvrs to implement cardStyles, nrows,  redaction, ballotStyleUnique
private val logger = KotlinLogging.logger("CorlaRawCvrs")

class CorlaRawCvrs(override val inputSource: String,
                   val parser: CSVParser,
                   showHeaders: Boolean = false,
                   showSchema: Boolean = false,
                   val redaction: Redaction = Redaction(),
): CorlaCvrsIF {

    override val electionName: String
    override val schema: CvrSchema
    override val versionName: String

    val headers = mutableListOf<String>()
    val records: Iterator<CSVRecord>  = parser.iterator()
    val ballotStyles = BallotStyles()
    val cvrs = mutableListOf<CvrRow>()

    var countBlankPrecincts = 0
    var mungedCount = 0
    var rowCount = 0
    init {
        try {
            // we expect the first line to be the election name
            val electionLine = records.next()
            headers.add(escHeaders(electionLine))
            if (showHeaders) showLine("electionName", electionLine)
            electionName = electionLine.get(0).replace("[^ -~]".toRegex(), "")
            versionName = electionLine.get(1).trim()

            // the contest names
            val contestLine = records.next()
            headers.add(escHeaders(contestLine))
            if (showHeaders) {
                println("contestLine has ${contestLine.toList().size} columns")
                println("contestLine = ${contestLine.toList().joinToString(", ")}")
            }

            // the choice/candidate names
            val choiceLine = records.next()
            headers.add(choiceLine.values().joinToString(","))

            // the header for the first columns, then (sometimes) the party affiliation of the candidates
            val headerLine = records.next()
            headers.add(escHeaders(headerLine))
            if (showHeaders) {
                println("column headerRecord) has ${headerLine.toList().size} columns")
                println(headerLine.toList().joinToString(", "))
            }

            if (showHeaders) {
                println("${sfn("contest", 50)}, ${sfn("choice", -40)}, ${sfn("header", -30)}")
                repeat(contestLine.size()) {
                    println("${trunc(contestLine.get(it), 50)}, ${trunc(choiceLine.get(it), -40)}, ${trunc(headerLine.get(it), -30)}")
                }
            }

            // make the schema out of those 3 lines
            schema = makeCvrSchema(inputSource, contestLine, choiceLine, headerLine)
            if (showSchema) {
                println()
                println(schema.showColumns())
                println()
                println(schema.showContests())
            }

        } catch (e: Throwable) {
            e.printStackTrace()
            logger.error(e) {"Error on $inputSource"}
            throw e
        }
    }

    override fun redaction() = redaction
    override fun cardStyleMap() = ballotStyles.cardStyleMap
    override fun cardStyles() = ballotStyles.cardStyles()
    override fun cvrs() = cvrs
    override fun nrows() = rowCount
    override fun headers() = headers
    override fun hasBallotType() = schema.headerIdx[CvrHeader.ballottype] != null
    override fun countBlankPrecincts() = countBlankPrecincts
    override fun ballotStyleUnique() = ballotStyles.ballotStylesUnique

    // enum class CvrHeader { cvrnumber, tabulatornum, batchid, recordid, imprintedid, precinctportion, ballottype }

    fun readRows(showFirst: Int? = null, showAfter: Int? = null,
                 showRedactedGroups: Boolean = false) {

        try {
            while (records.hasNext()) {
                val line = records.next()
                if (line.isEmpty()) break
                rowCount++

                if (!redaction.isRedaction(line, this)) {
                    try {
                        val cvr = parseHeader(line, rowCount)
                        cvr.addVotes(schema, line, rowCount)

                        cvrs.add(cvr)
                        ballotStyles.add(cvr)

                        if (showFirst != null && rowCount < showFirst) println(cvr.show())
                        if (showAfter != null && rowCount >= showAfter) println(cvr.show())

                    } catch (e: Throwable) {
                        logger.error(e) { "rowCount=$rowCount $line" }
                        throw e
                    }
                }
            }
        } finally {
            parser.close()
        }

        if (showRedactedGroups) {
            logger.info{"  read ${redaction.nRedactedRows} Redacted lines from ${inputSource}"}
            println("number of Redacted Groups = ${redaction.groups().size}")
            redaction.groups().sortedBy{it.groupName}.forEach { println("  $it") }
        }
        if (mungedCount > 0) logger.warn { "$inputSource has $mungedCount munged imprintedIds out of $rowCount"}
    }

    fun parseHeader(line: CSVRecord, defCvrNumber: Int = -1): CvrRow {

        var cvr = CvrRow(
            line.values().take(schema.nheaders),
            cvrNumber = parseColAsInt(CvrHeader.cvrnumber, line, defCvrNumber),
            tabulatorNum = parseColAsInt(CvrHeader.tabulatornum, line),
            batchId = parseColAsString(CvrHeader.batchid, line),
            recordId = parseColAsInt(CvrHeader.recordid, line),
            imprintedId = parseColAsString(CvrHeader.imprintedid, line),
            ballotType = parseColAsString(CvrHeader.ballottype, line),
            precinctPortion = parseColAsString(CvrHeader.precinctportion, line),
        )

        if (parseColAsString(CvrHeader.precinctportion, line).trim().isEmpty()) countBlankPrecincts++

        if (!cvr.testImprintedIdFormat()) {
            val munged = reverseMungeDate(cvr.imprintedId)
            val mungedCvr = cvr.copy(imprintedId=munged)
            if (mungedCvr.testImprintedIdFormat()) {
                if (mungedCount % 1000 == 0)
                    logger.warn { "$inputSource (${defCvrNumber}) has incorrect imprintedId='${cvr.imprintedId}' replace with '$munged' count=$mungedCount"}
                cvr = mungedCvr
                mungedCount++
            }
        }
        return cvr
    }

    fun parseColAsInt(header: CvrHeader, line: CSVRecord, def: Int = -1): Int {
        val colidx = schema.headerIdx[header]
        return if (colidx != null && colidx < line.size()) {
            val col = line.get(colidx)
            // TODO doesnt actually guard against not being an Int, just if its empty
            if (col.isEmpty()) def else removeLeadingEquals(col).toInt()
        } else def
    }

    fun parseColAsString(header: CvrHeader, line: CSVRecord): String {
        val colidx = schema.headerIdx[header]
        return if (colidx != null && colidx < line.size()) {
            val col = line.get(colidx)
            removeLeadingEquals(col)
        } else ""
    }

    fun getBallotType(line: CSVRecord): String {
        val ballottype = parseColAsString(CvrHeader.ballottype, line)
        return ballottype.ifEmpty { "NoBallotType" }
    }

    fun showLine(what: String, line: CSVRecord) {
        println(what)
        val elems: List<String> = line.toList()
        elems.forEachIndexed { idx, it ->
            if (it.isNotEmpty()) println("  ${nfn(idx, 3)}: $it")
        }
    }

    // used by anonymizer
    override fun csvHeader(row: CvrRow, redactPrecinct: Boolean) = buildString {
        repeat(schema.nheaders) { idx ->
            val header = schema.headerAt[idx]
            val useValue = when (header) {
                // cvrnumber, tabulatornum, batchid, recordid, imprintedid, precinctportion, ballottype
                CvrHeader.cvrnumber -> row.cvrNumber
                CvrHeader.tabulatornum -> row.tabulatorNum
                CvrHeader.batchid -> row.batchId
                CvrHeader.recordid -> row.recordId
                CvrHeader.imprintedid -> quoteString(row.imprintedId)
                CvrHeader.ballottype -> quoteString(row.ballotType)
                CvrHeader.precinctportion -> if (redactPrecinct) "" else row.precinctPortion ?: row.headerValues[idx]
                else -> row.headerValues[idx]
            }
            append("$useValue,")
        }
    }

    /*
    inner class CvrRowIterable(val parser: CSVParser): Iterable<CvrRow> {
        override fun iterator() = CvrRowIterator(parser.iterator())

        inner class CvrRowIterator(val records: Iterator<CSVRecord>) : Iterator<CvrRow> {
            var rowCount = 0

            override fun next(): CvrRow {
                val record = records.next()
                if (record.isEmpty()) break
                rowCount++

                //  this is skipping redactions, you could also pass them on with empty votes or someting
                if (!redaction.isRedaction(record, this)) {
                    try {
                        val cvr = parseHeader(record, rowCount)
                        cvr.addVotes(schema, record, rowCount)

                        return cvr
                    } catch (e: Throwable) {
                        logger.error(e) { "rowCount=$rowCount $record" }
                        throw e
                    }
                }
            }

            override fun hasNext() = records.hasNext()
        }
    } */
}

fun quoteString(sin: String) : String {
    return "\"$sin\""
}

fun escHeaders(header: CSVRecord) = buildString {
    val last = header.size()-1
    header.values().forEachIndexed { idx, col ->
        if (col.contains(",")) append("\"$col\"") else append("$col")
        if (idx != last) append(",")
    }
}


data class CvrCardStyle(val ballotType: String, val contestIds: Set<Int>) {
    var ncards: Int = 0 // not part of equals
    fun contains(contestId: Int) = contestIds.contains(contestId)
}

// scenario_ballot_type_present
// has two ballot styles with same contest set [0,1], these generate a single CardStyle
// and ballot style [1] has rows with different set [0], [0,1]
// row 19 could create a second CardStyle with name "1+1" meaning  balot type 1, variant1, with different contest set
//                    type votes
// 9,1,1,9,1-1-9,P1,    1, 1,0,1,0
// 12,1,1,12,1-1-12,P1, 2, 0,1,0,1
// 19,1,1,19,1-1-19,P1, 1, 1,0,,

data class CardStyleId(val ballotType: String, val contestIds: Set<Int>)
class BallotStyles {
    val cardStyleMap = mutableMapOf<CardStyleId, CvrCardStyle>()
    val ballotTypes = mutableSetOf<String>()
    var ballotStylesUnique = true

    fun add(cvr:CvrRow) {
        val cvrStyleId = CardStyleId(cvr.ballotType, cvr.contests())
        val cardStyle = cardStyleMap.getOrPut(cvrStyleId) {
            // if already been added, then there are duplicate contestId sets for this ballotType
            if (!ballotTypes.add(cvr.ballotType)) ballotStylesUnique = false
            CvrCardStyle(cvr.ballotType, cvr.contests())
        }
        cardStyle.ncards++
    }

    fun cardStyles(): List<CvrCardStyle> {
        return cardStyleMap.values.sortedBy { it.ncards }.reversed()
    }
}

// 6/15/2026
// some counties have first 5 fields of the vote rows as ="field". The quoting seems typical, the mistake is the leading =
// "CvrNumber","TabulatorNum","BatchId","RecordId","ImprintedId","CountingGroup","PrecinctPortion","BallotType","","","","","","","","","","","","","","","","","","","","","","","","","","DEM","REP","APV","UNI","LBR","","","","","REP","DEM","LBR","UNI","DEM","REP","REP","DEM","DEM","REP","DEM","REP","DEM","DEM","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","",""
//="1",="2",="1",="24",="2-1-24","Mail","3356255005 (3356255005)","1","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","1","0","1","0","0","1","0","1","1","0","1","0","1","0","0","1","0","1","0","1","1","0","",""
//="2",="2",="1",="23",="2-1-23","Mail","3356255005
private val regexLE = Regex("[=\"]") // matches a quote or equals
fun removeLeadingEquals(input: String): String {
    val result = if (input.startsWith("="))
            input.replace(regexLE, "")
        else input
    return result
}

fun CSVRecord.isEmpty(): Boolean {
    val iter = this.iterator()
    while (iter.hasNext()) {
        val value = iter.next()
        if (!value.isEmpty()) return false
    }
    return true
}

private val regexComma = Regex("[,]") // Matches comma
fun cleanCsvString(originalString: String) = originalString.replace(regexComma, "")

fun truncateCommas(originalString: String): String {
    val commaPos = originalString.indexOf(",")
    return if (commaPos < 0) originalString else originalString.substring(0, commaPos)
}

// Boulder2020:  9/1/1986 != 9-1-86; went through Excel spreadsheet and got munged
// spreadsheet saved the imprintedId as a date. jeesh
fun reverseMungeDate(id: String): String {
    val count = id.count { it == '/' }
    return if (count != 2) id else {
        val tokens = id.split("/")
        try {
            val day = tokens[0].trim().toInt()
            val month = tokens[1].trim().toInt()
            var year = tokens[2].trim().toInt()
            if (year > 2000) year -= 2000
            else if (year > 1900) year -= 1900
            return "$day-$month-$year"
        } catch (ex: NumberFormatException) {
            return id
        }
    }
}

//////////////////////////////////////////////////////////////////////////////////
//// Colorado auditcenter
// 2026 Morgan County Primary,5.17.17.1,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
//,,,,,,United States Senator - DEM (Vote For=1),United States Senator - DEM (Vote For=1),Representative to the 120th United States Congress - District 4 - DEM (Vote For=1),Representative to the 120th United States Congress - District 4 - DEM (Vote For=1),Representative to the 120th United States Congress - District 4 - DEM (Vote For=1),Governor - DEM (Vote For=1),Governor - DEM (Vote For=1),Secretary of State - DEM (Vote For=1),Secretary of State - DEM (Vote For=1),State Treasurer - DEM (Vote For=1),Attorney General - DEM (Vote For=1),Attorney General - DEM (Vote For=1),Attorney General - DEM (Vote For=1),Attorney General - DEM (Vote For=1),State Senator - District 1 - DEM (Vote For=1),Secretary of State - LBR (Vote For=1),Secretary of State - LBR (Vote For=1),United States Senator - REP (Vote For=1),Representative to the 120th United States Congress - District 4 - REP (Vote For=1),Governor - REP (Vote For=1),Governor - REP (Vote For=1),Governor - REP (Vote For=1),Governor - REP (Vote For=1),Governor - REP (Vote For=1),Secretary of State - REP (Vote For=1),State Treasurer - REP (Vote For=1),Attorney General - REP (Vote For=1),Attorney General - REP (Vote For=1),State Senator - District 1 - REP (Vote For=1),State Representative - District 63 - REP (Vote For=1),Morgan County Commissioner District 2 - REP (Vote For=1),Morgan County Clerk and Recorder - REP (Vote For=1),Morgan County Treasurer - REP (Vote For=1),Morgan County Assessor - REP (Vote For=1),Morgan County Sheriff - REP (Vote For=1),Morgan County Coroner - REP (Vote For=1),Governor - UNI (Vote For=1),Governor - UNI (Vote For=1)
//,,,,,,Julie Gonzales,John Hickenlooper,Eileen Laubacher,Write-in,Jenna Preston,Phil Weiser,Michael Bennet,Amanda Gonzalez,Jessie Danielson,Jeff Bridges,Jena Griswold,David Seligman,Michael Dougherty,Hetal Doshi,Jamie Jeffery,Sean Vadney,Alex Astley,Mark Baisley,Lauren Boebert,Scott Bottoms,Victor Marx,Barb Kirkmeyer,Write-in,"Kelvin ""K-Man"" Wimberly",James Wiley,Kevin Grantham,Michael J. Allen,David Willson,Byron Pelton,Dusty Johnson,Robert W Pennington,Kevin Strauch,Kirstin M Watson,Tim Amen,Dave (David) D. Martin,Mike Dahl,Paul Noël Fiorino,Jeff Peckman
//CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,BallotType,DEM,DEM,DEM,,,DEM,DEM,DEM,DEM,DEM,DEM,DEM,DEM,DEM,DEM,LBR,LBR,REP,REP,REP,REP,REP,,,REP,REP,REP,REP,REP,REP,REP,REP,REP,REP,REP,REP,UNI,UNI
//1,102,1,50,102-1-50,02-REP,,,,,,,,,,,,,,,,,,1,1,0,0,1,0,0,1,1,0,1,1,1,1,1,1,1,1,1,,
//2,102,1,49,102-1-49,02-REP,,,,,,,,,,,,,,,,,,1,1,1,0,0,0,0,1,1,0,1,1,1,1,1,1,1,1,1,,

//// Boulder 2023 election with IRV: (Number of positions=1, Number of ranks=4) // TODO
// "2023 Coordinated Election","5.17.17.1",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
//,,,,,,"City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Boulder Council Candidates (Vote For=4)","City of Lafayette City Council Candidates (Vote For=4)","City of Lafayette City Council Candidates (Vote For=4)","City of Lafayette City Council Candidates (Vote For=4)","City of Lafayette City Council Candidates (Vote For=4)","City of Lafayette City Council Candidates (Vote For=4)","City of Lafayette City Council Candidates (Vote For=4)","City of Lafayette City Council Candidates (Vote For=4)","City of Longmont - Mayor (Vote For=1)","City of Longmont - Mayor (Vote For=1)","City of Longmont - Mayor (Vote For=1)","City of Longmont - City Council Member At-Large (Vote For=1)","City of Longmont - City Council Member At-Large (Vote For=1)","City of Longmont - City Council Member At-Large (Vote For=1)","City of Longmont - Council Member Ward 1 (Vote For=1)","City of Longmont - Council Member Ward 1 (Vote For=1)","City of Longmont - Council Member Ward 1 (Vote For=1)","City of Longmont - Council Member Ward 3 (Vote For=1)","City of Longmont - Council Member Ward 3 (Vote For=1)","City of Longmont - Council Member Ward 3 (Vote For=1)","City of Longmont - Council Member Ward 3 (Vote For=1)","City of Louisville Mayor At-Large (4 Year Term) (Vote For=1)","City of Louisville Mayor At-Large (4 Year Term) (Vote For=1)","City of Louisville Mayor At-Large (4 Year Term) (Vote For=1)","City of Louisville City Council Ward 1 (4-year term) (Vote For=1)","City of Louisville City Council Ward 2 (4-year term) (Vote For=1)","City of Louisville City Council Ward 2 (4-year term) (Vote For=1)","City of Louisville City Council Ward 3 (Vote For=2)","City of Louisville City Council Ward 3 (Vote For=2)","Boulder Valley School District RE-2 Director District A (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District A (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District C (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District C (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District C (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District D (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District D (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District G (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District G (4 Years) (Vote For=1)","Boulder Valley School District RE-2 Director District G (4 Years) (Vote For=1)","Estes Park School District R-3 School Board Director At Large (4 Year) (Vote For=2)","Estes Park School District R-3 School Board Director At Large (4 Year) (Vote For=2)","Estes Park School District R-3 School Board Director At Large (4 Year) (Vote For=2)","Estes Park School District R-3 School Board Director At Large (4 Year) (Vote For=2)","Thompson R2-J School District Board of Education Director District A (4 Year Term) (Vote For=1)","Thompson R2-J School District Board of Education Director District A (4 Year Term) (Vote For=1)","Thompson R2-J School District Board of Education Director District C (4 Year Term) (Vote For=1)","Thompson R2-J School District Board of Education Director District C (4 Year Term) (Vote For=1)","Thompson R2-J School District Board of Education Director District D (4 Year Term) (Vote For=1)","Thompson R2-J School District Board of Education Director District D (4 Year Term) (Vote For=1)","Thompson R2-J School District Board of Education Director District G (4 Year Term) (Vote For=1)","Thompson R2-J School District Board of Education Director District G (4 Year Term) (Vote For=1)","City of Longmont Municipal Court Judge - Frick (Vote For=1)","City of Longmont Municipal Court Judge - Frick (Vote For=1)","Proposition HH (Statutory) (Vote For=1)","Proposition HH (Statutory) (Vote For=1)","Proposition II (Statutory) (Vote For=1)","Proposition II (Statutory) (Vote For=1)","Boulder County Ballot Issue 1A (Vote For=1)","Boulder County Ballot Issue 1A (Vote For=1)","Boulder County Ballot Issue 1B (Vote For=1)","Boulder County Ballot Issue 1B (Vote For=1)","City of Boulder Ballot Issue 2A (Vote For=1)","City of Boulder Ballot Issue 2A (Vote For=1)","City of Boulder Ballot Question 2B (Vote For=1)","City of Boulder Ballot Question 2B (Vote For=1)","City of Boulder Ballot Question 302 (Vote For=1)","City of Boulder Ballot Question 302 (Vote For=1)","Town of Erie Ballot Question 3A (Vote For=1)","Town of Erie Ballot Question 3A (Vote For=1)","Town of Erie Ballot Question 3B (Vote For=1)","Town of Erie Ballot Question 3B (Vote For=1)","City of Longmont Ballot Issue 3C (Vote For=1)","City of Longmont Ballot Issue 3C (Vote For=1)","City of Longmont Ballot Issue 3D (Vote For=1)","City of Longmont Ballot Issue 3D (Vote For=1)","City of Longmont Ballot Issue 3E (Vote For=1)","City of Longmont Ballot Issue 3E (Vote For=1)","City of Louisville Ballot Issue 2C (Vote For=1)","City of Louisville Ballot Issue 2C (Vote For=1)","Town of Superior Ballot Question 301 (Vote For=1)","Town of Superior Ballot Question 301 (Vote For=1)","Town of Superior - Home Rule Charter Commission (Vote For=9)","Town of Superior - Home Rule Charter Commission (Vote For=9)","Town of Superior - Home Rule Charter Commission (Vote For=9)","Town of Superior - Home Rule Charter Commission (Vote For=9)","Town of Superior - Home Rule Charter Commission (Vote For=9)","Town of Superior - Home Rule Charter Commission (Vote For=9)","Town of Superior - Home Rule Charter Commission (Vote For=9)","Town of Superior - Home Rule Charter Commission (Vote For=9)","Town of Superior - Home Rule Charter Commission (Vote For=9)","Town of Superior - Home Rule Charter Commission (Vote For=9)","Town of Superior - Home Rule Charter Commission (Vote For=9)","Nederland Eco Pass Public Improvement District Ballot Issue 6A (Vote For=1)","Nederland Eco Pass Public Improvement District Ballot Issue 6A (Vote For=1)","North Metro Fire Rescue District Ballot Issue 7A (Vote For=1)","North Metro Fire Rescue District Ballot Issue 7A (Vote For=1)"
//,,,,,,"Aaron Brockett(1)","Nicole Speer(1)","Bob Yates(1)","Paul Tweedlie(1)","Aaron Brockett(2)","Nicole Speer(2)","Bob Yates(2)","Paul Tweedlie(2)","Aaron Brockett(3)","Nicole Speer(3)","Bob Yates(3)","Paul Tweedlie(3)","Aaron Brockett(4)","Nicole Speer(4)","Bob Yates(4)","Paul Tweedlie(4)","Terri Brncic","Jenny Robins","Aaron Gabriel Neyer","Jacques Decalo","Silas Atkins","Waylon Lewis","Ryan Schuchard","Tara Winer","Tina Marquis","Taishya Adams","Tim Barnes","JD Mangat","Eric Ryant","John W. Watson","Gala W. Orba","David Fridland","Crystal Gallegos","Ethan Augreen","Joan Peck","Terri Goon","Sean P. McCoy","Steve Altschuler","Beka Venturella","Nia Wassink","Diane Crist","Harrison Earl","Ron Gallegos","Gary Hodges","Susie Hidalgo-Fahring","Spencer Adams","Sherry Sommer","Chris Leh","Josh Cooperman","J. Caleb Dickinson","Deborah Fahey","George Colbert","Dietrich Hoefner","Barbara Hamlington","Jason Unger","Neil Fishman","Andrew Steffl","Alex Medler","Cynthia Nevison","Andrew Brandt","Lalenia Quinlan Aweida","Anil Kiran Pesaramelli","Stuart Lord","Jorge Chávez","Kevin G. Morris","Kyri Cox","Brenda L. Wyss","Brad Shochat","Ryan Wilcken","Dawn Kirk","Nancy Rumfelt","Briah Freeman","Denise Alvine Chapman","Yazmin Navarro","Stu Boyd","Elizabeth Kearney","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Yes/For","No/Against","Dalton Valette","Heather Cracraft","Ryan Hitchler","Claire Dixon","Ryan Welch","Jeff Chu","Sean Maday","Clint Folsom","Chris Hanson","Stephanie Schader","Mike Foster","Yes/For","No/Against","Yes/For","No/Against"
//"CvrNumber","TabulatorNum","BatchId","RecordId","ImprintedId","BallotType",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
//"1","108","1","104","108-1-104","DS-01",1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,1,1,0,1,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,0,0,1,0,1,0,0,0,1,,,,,,,,,,,,,,,1,0,1,0,1,0,1,0,1,0,1,0,0,1,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

//// votecenter
// 2020 Boulder County General Election,5.11.3.1,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
//,,,,,,,Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),Presidential Electors (Vote For=1),
//,,,,,,,Joseph R. Biden / Kamala D. Harris,Donald J. Trump / Michael R. Pence,Don Blankenship / William Mohr,Bill Hammons / Eric Bodenstab,Howie Hawkins / Angela Nicole Walker,Blake Huber / Frank Atwood,
//CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,CountingGroup,BallotType,DEM,REP,ACN,UNI,GRN,APV,LBR,AMS,UAF,PRB,ALL,PRO,UAF,SWP,SOE,IAM,SLB,UAF,UAF,UAF,UAF,,,,,

//// Neals' test files
// Test Election 2024,V1,,,,,,
//,,,,,,,,A,A,B,B
//,,,,,,,,A0,A1,B0,B1
//CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,CountingGroup,PrecinctPortion,BallotType,A0,A1,B0,B1
//1,1,1,1,1-1-1,cg,1R1,,1,0,,
//2,1,1,2,1-1-2,cg,2S2,,1,0,1,0

//// Garfield
// RowNumber	BoxID	BoxPosition	BallotID	PrecinctID	BallotStyleID	PrecinctStyleName	ScanComputerName	Status	Remade	Choice_18_1:Presidential Electors:Vote For 1:Write-in:Non-Partisan

