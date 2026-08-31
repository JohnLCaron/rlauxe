package org.cryptobiotic.rlauxe.cvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.util.CvrBuilder2
import org.cryptobiotic.rlauxe.util.ZipReader
import org.cryptobiotic.rlauxe.util.nfn
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

// this reads CVRs from "Dominion CVR export files", a standard Dominion csv format.

private val logger = KotlinLogging.logger("CorlaCvrs")

fun readCorlaCvrsFromFile(filename: String, showHeaders: Boolean = false, showSchema: Boolean = false,
                          redaction: RedactionIF = Redaction()): CorlaCvrs {
    val parser = if (filename.endsWith(".zip")) {
        val zipReader = ZipReader(filename)
        // by convention, the file inside is the filename with zip replaced by csv
        val lastPart = filename.substringAfterLast("/")
        val innerFilename = lastPart.replace(".zip", ".csv")
        val inputStream = zipReader.inputStream(innerFilename)
        val reader: Reader = InputStreamReader(inputStream, "UTF-8")
        CSVParser.parse(reader, CSVFormat.DEFAULT)
        // TODO if we could look ahead, we could give them the first row

    } else {
        CSVParser.parse(File(filename), Charset.forName("UTF-8"), CSVFormat.DEFAULT)
    }

    val corlaCvrs = CorlaCvrs(filename, parser, showHeaders, showSchema, redaction = redaction)
    corlaCvrs.readRows()
    return corlaCvrs
}

fun readCorlaCvrsFromResource(resourcePath: String, showHeaders: Boolean = false, showSchema: Boolean = false,
                              redaction: RedactionIF = Redaction()): CorlaCvrs {
    val resourceStream = getCsvStreamFromResource(resourcePath)
    val reader: Reader = InputStreamReader(resourceStream, "UTF-8")
    val parser =  CSVParser.parse(reader, CSVFormat.DEFAULT)
    val corlaCvrs = CorlaCvrs(resourcePath, parser, showHeaders, showSchema, redaction = redaction)
    corlaCvrs.readRows()
    return corlaCvrs
}

fun getCsvStreamFromResource(resourcePath: String): InputStream {
    var resourceStream =
        object {}.javaClass.getResourceAsStream(resourcePath) ?: throw IOException("$resourcePath does not exist")
    if (resourcePath.endsWith(".zip")) {
        val innerStream = getZippedCsvResourceStream(resourcePath, resourceStream)
        if (innerStream == null) throw IOException("zipped $resourcePath does not have the csv file inside")
        resourceStream = innerStream
    }
    return resourceStream
}

// InputStream implement Closeable
fun getZippedCsvResourceStream(resourcePath: String, resourceStream: InputStream): InputStream? {
    val lastPart = resourcePath.substringAfterLast("/")
    val innerFilename = lastPart.replace(".zip", ".csv")
    val zipStream = ZipInputStream(resourceStream)
    var zipEntry = zipStream.nextEntry
    while (zipEntry != null) {
        if (!zipEntry.isDirectory && zipEntry.name == innerFilename) {
            return zipStream
        }
        zipStream.closeEntry()
        zipEntry = zipStream.nextEntry
    }
    return null
}

class CorlaCvrs(val inputSource: String, val parser: CSVParser,
                showHeaders: Boolean = false,
                showSchema: Boolean = false,
                val redaction: RedactionIF = Redaction(),
) {

    val records: Iterator<CSVRecord>  = parser.iterator()
    val electionName: String
    val versionName: String
    val schema: CvrSchema
    // val nvotesMap: Map<Int, Int> // not used ??
    //val ballotTypeIdx: Int

    val ballotStyles = BallotStyles()
    val cvrs = mutableListOf<CvrRow>()

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

    var lineno = 0
    init {
        try {
            // we expect the first line to be the election name
            val electionLine = records.next()
            lineno++
            if (showHeaders) showLine("electionName", electionLine)
            electionName = electionLine.get(0).replace("[^ -~]".toRegex(), "")
            versionName = electionLine.get(1).trim()

            // the contest names
            val contestLine = records.next()
            lineno++
            if (showHeaders) {
                println("contestLine has ${contestLine.toList().size} columns")
                println("contestLine = ${contestLine.toList().joinToString(", ")}")
            }

            // the choice/candidate names
            val choiceLine = records.next()
            lineno++

            // the header for the first columns, then (sometimes) the party affiliation of the candidates
            val headerRecord = records.next()
            lineno++
            if (showHeaders) {
                println("column headerRecord) has ${headerRecord.toList().size} columns")
                println(headerRecord.toList().joinToString(", "))
            }

            // make the schema out of those 3 lines
            schema = makeCvrSchema(inputSource, contestLine, choiceLine, headerRecord)
            if (showSchema) {
                println()
                println(schema.showColumns())
                println()
                println(schema.showContests())
            }

            // TODO count on (Vote For=n) ?
            // nvotesMap = schema.contests.associate { it.contestIdx to it.voteForN }
            // ballotTypeIdx = schema.nheaders - 1 // TODO see if BallotType == header 6

        } catch (e: Throwable) {
            e.printStackTrace()
            logger.error(e) {"Error on $inputSource"}
            throw e
        }
    }

    fun readRows(showFirst: Int? = null, showAfter: Int? = null,
                 showRedactedGroups: Boolean = false) {

        var cvrCount = 0
        while (records.hasNext()) {
            val line = records.next()
            if (line.isEmpty()) break
            if (!redaction.isRedaction(line, this)) {
                try {
                    val test = removeLeadingEquals(line.get(3)).toInt()
                } catch (e : Throwable) {
                    logger.error{"barf on cvrCount $cvrCount $line"}
                    break
                }

                val cvr = CvrRow(
                    cvrNumber = removeLeadingEquals(line.get(0)).toInt(),
                    tabulatorNum = removeLeadingEquals(line.get(1)).toInt(),
                    batchId = removeLeadingEquals(line.get(2)),
                    recordId = removeLeadingEquals(line.get(3)).toInt(),
                    imprintedId = removeLeadingEquals(line.get(4)),
                    ballotType = readColumn(line, "ballotType") ?: "noBallotType", // TODO
                    precinctPortion = readColumn(line, "precinctPortion"),
                ).addVotes(schema, line, lineno)

                if (cvr.contestVotes.isNotEmpty()) {
                    cvrs.add(cvr)
                    ballotStyles.add(cvr)
                }

                if (showFirst != null && cvrCount < showFirst) println(cvr.show())
                if (showAfter != null && cvrCount >= showAfter) println(cvr.show())
            }
            cvrCount++
            lineno++
        }
        parser.close()

        if (showRedactedGroups) {
            logger.info{"  read ${redaction.nlines} Redacted lines from ${inputSource}"}
            println("number of Redacted Groups = ${ballotStyles.redactedGroups.size}")
            ballotStyles.redactedGroups.toSortedMap().forEach { println("  ${it.value}") }
        }
    }

    fun readColumn(line: CSVRecord, colName: String): String? {
        val colIdx = schema.headerMap[colName.lowercase()]
        return if (colIdx != null) line.get(colIdx) else null
    }

    // 6/15/2026
    // some counties have first 5 fields of the vote rows as ="field". The quoting seems typical, the mistake is the leading =
    // "CvrNumber","TabulatorNum","BatchId","RecordId","ImprintedId","CountingGroup","PrecinctPortion","BallotType","","","","","","","","","","","","","","","","","","","","","","","","","","DEM","REP","APV","UNI","LBR","","","","","REP","DEM","LBR","UNI","DEM","REP","REP","DEM","DEM","REP","DEM","REP","DEM","DEM","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","",""
    //="1",="2",="1",="24",="2-1-24","Mail","3356255005 (3356255005)","1","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","1","0","1","0","0","1","0","1","1","0","1","0","1","0","0","1","0","1","0","1","1","0","",""
    //="2",="2",="1",="23",="2-1-23","Mail","3356255005

    fun showLine(what: String, line: CSVRecord) {
        println(what)
        val elems: List<String> = line.toList()
        elems.forEachIndexed { idx, it ->
            if (it.isNotEmpty()) println("  ${nfn(idx, 3)}: $it")
        }
    }

    fun redactedGroups() = ballotStyles.redactedGroups.values.toList()
    fun cardStyles() = ballotStyles.cardStyles()
}


////////////////////////////////////////////////////////////////////////////////

// note that these contestIds and candIds are internal to this file;  must cross reference with contest/candidate name
data class ContestVotes(val contestId: Int, val candVotes: List<Int>)
data class CvrCardStyle(val name: String, val contestIds: Set<Int>, var countCards: Int = 0)

private val showDontMatch = true

class BallotStyles {
    // keep track of all the card styles in the file
    val cardStyleMap = mutableMapOf<Set<Int>, CvrCardStyle>()
    val redactedGroups = mutableMapOf<String, RedactedGroup>()

    fun add(cvr:CvrRow) {
        val cvrContests = cvr.contestVotes.map { it.contestId }.toSet()
        val ballotType = cardStyleMap.getOrPut(cvrContests) { CvrCardStyle(cvr.ballotType, cvrContests) }
        ballotType.countCards++
    }

    // TODO we might want to remove contests with vote count == 0 ??
    fun add(redacted:RedactedGroup) {
        // keep the r ??
        val rname =  redacted.ballotType
        val group = redactedGroups[rname]
        if (group == null) {
            redactedGroups[rname] = RedactedGroup.makeAccumulator(redacted, rname)
        } else {
            if (group.contests() == redacted.contests()) {
                group.merge(redacted)
            } else if ((group.contests() - redacted.contests()).size == 0) {
                group.merge(redacted)
            } else if (showDontMatch) {
                println("    redacted ${redacted.ballotType} diff = ${redacted.contests() - group.contests()}, ${group.contests() - redacted.contests()}")
                println("doesnt match c31 = ${redacted.contestVotes[31]}")
            }
        }
    }

    // Boulder 25 has strange anomoly with contest 31 = Coal Creek Canyon Fire Protection District Ballot Issue 7B
    fun add31(redacted:RedactedGroup) {
        // keep the r ??
        val rname =  if (redacted.contestVotes.contains(31)) "${redacted.ballotType}+31" else redacted.ballotType
        val group = redactedGroups[rname]
        if (group == null) {
            redactedGroups[rname] = RedactedGroup.makeAccumulator(redacted, rname)
        } else {
            if (group.contests() == redacted.contests())
                group.merge(redacted)
            else if (showDontMatch)
                println("redacted $redacted doesnt match $group; c31 = ${redacted.contestVotes[31]}")
        }
    }

    fun cardStyles(): List<CvrCardStyle> {
        return cardStyleMap.values.sortedBy { it.countCards }.reversed()
    }
}

// TODO assign a CardStyle ??
// CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,PrecinctPortion,BallotType
data class CvrRow(
    val cvrNumber: Int,
    val tabulatorNum: Int,
    val batchId: String,
    val recordId: Int,
    val imprintedId: String,
    val ballotType: String, // might have to generate this ourselves?
    val precinctPortion: String?, // optional
) {
     var contestVotes =  mutableListOf<ContestVotes>() // equivilent to Map<contestId, IntArray>

    fun addVotes(schema: CvrSchema, line: CSVRecord, lineno: Int): CvrRow {
        var colidx = schema.nheaders // skip over the first n columns
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

    // assume a vote is 0 or 1
    // return list of candidates voted for; the candidates are numbered jithin this contest
    fun makeRegularVotes(schema: CvrSchema, line: CSVRecord, lineno: Int, exportContest: SchemaContestInfo): List<Int> {
        val votes = mutableListOf<Int>()
        for (i in 0 until exportContest.ncols) { // so i in [0..ncands)
            val colno = exportContest.startCol + i
            if (!schema.writeIns.contains(colno)) { // dont record write-ins
                val colValueS = line.get(colno)
                try {
                    val colValue = colValueS.toInt()
                    if (colValue > 0) votes.add(i)
                } catch (e: NumberFormatException) {
                    logger.warn{ "Cant parse '$colValueS' at col $colno line $lineno; probably didnt catch the redacted line; filename=${schema.inputSource}\n"+
                                 "       $line"}
                    return emptyList()
                }
            }
        }
        return votes
    }

    //                             exportContest.startCol, start
    //                            exportContest.ncols, count
    //                            exportContest.nchoices, ncands
    fun makeIrvVotes(schema: CvrSchema, line: CSVRecord, lineno: Int, exportContest: SchemaContestInfo): List<Int> {
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

    // this assumes that you have a single schema you use for the ContestInfo....
    fun convertToCvr(): Cvr {
        val cvrb = CvrBuilder2(this.imprintedId, false)
        this.contestVotes.forEach{
            cvrb.replaceContestVotes(it.contestId, it.candVotes.toIntArray())
        }
        return cvrb.build()
    }

    companion object {
        val header = "cvrNumber, tabulatorNum, batchId, recordId, imprintedId, ballotType, contest:votes"
    }

}

private val regexLE = Regex("[=\"]") // matches a quote or equals
fun removeLeadingEquals(input: String): String {
    val result = if (input.startsWith("=")) input.replace(regexLE, "") else input
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

fun parseContestNameAndVoteFor(name: String) : Pair<String, Int> {
    if (!name.contains("(Vote For=")) return Pair(name.trim(), 1)

    val tokens = name.split("(Vote For=")
    require(tokens.size == 2) { "unexpected contest name $name" }
    val namet = tokens[0].trim()
    val ncand = tokens[1].substringBefore(")").toInt()
    return Pair(namet, ncand)
}

// City of Boulder Mayoral Candidates (Number of positions=1, Number of ranks=4)
fun parseIrvContestName(name: String) : Pair<String, Int> {
    if (!name.contains("(Number of positions=")) return Pair(name.trim(), 1)

    val tokens = name.split("(Number of positions=")
    require(tokens.size == 2) { "unexpected contest name $name" }
    val namet = tokens[0].trim()
    val ncand = tokens[1].substringBefore(",").toInt()
    return Pair(namet, ncand)
}

private val regexComma = Regex("[,]") // Matches '!', ',' or any digit
fun cleanCsvString(originalString: String) = originalString.replace(regexComma, "")

fun truncateCommas(originalString: String): String {
    val commaPos = originalString.indexOf(",")
    return if (commaPos < 0) originalString else originalString.substring(0, commaPos)
}
