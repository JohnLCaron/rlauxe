package org.cryptobiotic.rlauxe.dominion

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.boulder.isEmpty
import org.cryptobiotic.rlauxe.boulder.parseContestNameAndVoteFor
import org.cryptobiotic.rlauxe.boulder.parseIrvContestName
import org.cryptobiotic.rlauxe.util.ZipReader
import org.cryptobiotic.rlauxe.util.nfn
import org.cryptobiotic.rlauxe.util.roundUp
import org.cryptobiotic.rlauxe.util.trunc
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.lang.StrictMath.sqrt
import java.nio.charset.Charset
import kotlin.collections.set
import kotlin.math.max

// this reads csv files from "Dominion CVR export files", a standard Dominion csv format.
// Note these record the cvr undervotes, but not for the redacted votes; a col is left empty when the ballot doesnt have that contest.
// how to deal with the names not matching ??

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

private val logger = KotlinLogging.logger("DominionCvrExportReader")

private val d3f = "%3d"
private val showHeader = false
private val showLines = false

private val showDontMatch = false
private val showBallotStyles = false
private val showRedactedGroups = false

// TODO extract the "Vote For" from the header, use to calculate the number of cards in the redacted lines. (method 1)
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

class DominionCvrExportReader(val filename: String, showHeaders: Boolean = false) {
    val ballotStyles = BallotStyles()
    val records: Iterator<CSVRecord>
    var lineno = 0
    val electionName: String
    val versionName: String
    val schema: Schema
    val nvotesMap: Map<Int, Int>
    val ballotTypeIdx: Int

    val cvrs = mutableListOf<CastVoteRecord>()
    var rcvRedacted = 0

    init {
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

        records = parser.iterator()
        lineno++

        // 1) we expect the first line to be the election name
        val electionLine = records.next()
        if (showHeaders) showLine("electionName", electionLine)
        lineno++
        electionName = electionLine.get(0).replace("[^ -~]".toRegex(), "")
        versionName = electionLine.get(1).trim()

        // 2) the contest name for that column
        val contestLine = records.next()
        val header1 = contestLine.toList().joinToString(", ")
        if (showHeaders) {
            println("header1 (contest names) has ${contestLine.toList().size} columns")
            println("header1 = $header1")
        }

        var my_first_contest_column = 0
        while ("" == contestLine[my_first_contest_column]) {
            my_first_contest_column++
        }
        lineno++

        try {
            // 3) the choice/candidate name for that column
            val choiceLine = records.next()
            if (showLines) showLine("choice/candidate", choiceLine)
            lineno++

            // 4) the header for the first 6 columns, then party affiliation
            val headerRecord = records.next()
            if (showLines) showLine("header", headerRecord)
            val header2 = headerRecord.toList().joinToString(", ")
            if (showHeaders) {
                println("header2 (party affiliation) has ${headerRecord.toList().size} columns")
                println(header2)
            }

            // make the column structure out of those 3 lines
            schema = makeSchema(contestLine, choiceLine, headerRecord)
            // println(schema.show())
            nvotesMap = schema.contests.associate { it.contestIdx to it.voteForN }
            // ballotTypeIdx = if (schema.nheaders == 6) 5 else 6
            ballotTypeIdx = schema.nheaders - 1 // TODO see if BallotType == header 6
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    fun read(showFirst: Int? = null, showAfter: Int? = null): DominionCvrExport {

        var cvrCount = 0
        while (records.hasNext()) {
            val line = records.next()
            if (line.isEmpty()) break
            if (!redaction(line)) {
                val cvr = CastVoteRecord(
                    cvrNumber = removeLeadingEquals(line.get(0)).toInt(),
                    tabulatorNum = removeLeadingEquals(line.get(1)).toInt(),
                    batchId = removeLeadingEquals(line.get(2)),
                    recordId = removeLeadingEquals(line.get(3)).toInt(),
                    imprintedId = removeLeadingEquals(line.get(4)),
                    ballotType = line.get(ballotTypeIdx),
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

        if (rcvRedacted > 0) println("  read $rcvRedacted RCV Redacted votes")
        if (showRedactedGroups) {
            println("Redacted Groups size = ${ballotStyles.redactedGroups.size}")
            ballotStyles.redactedGroups.toSortedMap().forEach { println("  ${it.value}") }
        }

        val ballotTypes = ballotStyles.ballotTypes.values.sortedBy { it.count }.reversed()
        return DominionCvrExport(
            electionName, versionName, filename, schema, cvrs,
            ballotStyles.redactedGroups.toSortedMap().values.toList(),
            ballotTypes
        )
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
            if (it.isNotEmpty()) println("  ${d3f.format(idx)}: $it")
        }
    }

    // "src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv"
    // "src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv"
    fun redaction(line: CSVRecord): Boolean {
        if (line.get(0).startsWith("Redacted")) { // Boulder >= 2024?; but not "RCV Redacted ..." which can be treated like a normal CVR
            val isA = line.get(0).contains("A cards")
            val isB = line.get(0).contains("B cards")
            val ballotStyle = line.get(ballotTypeIdx) + if (isA) "-A" else if (isB) "-B" else ""
            val redactedGroup = DominionRedactedGroup(ballotStyle).addVotes(schema, line)
            ballotStyles.add(redactedGroup, nvotesMap)
            return true

        } else if (line.get(0).startsWith("RCV Redacted")) { // Boulder >= 2024? IRV
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
            return true

        } else if (line.get(0).isEmpty()) { // Boulder, Dolores; has votes presumably of the sum of the redactions
            val ballotStyle = line.get(ballotTypeIdx)
            val redactedGroup = DominionRedactedGroup(ballotStyle).addVotes(schema, line)
            ballotStyles.add(redactedGroup, nvotesMap)
            return true

        } else if (line.get(schema.nheaders).lowercase().startsWith("redacted")) { // Eagle, Jefferson, Larimer
            val ballotStyle = line.get(ballotTypeIdx) + "fromRedacted"
            val redactedGroup = DominionRedactedGroup(ballotStyle)
            ballotStyles.add(redactedGroup, nvotesMap)
            return true
        }  else if (line.get(schema.nheaders).startsWith("X")) { // Douglas, Pitkin
            val ballotStyle = line.get(ballotTypeIdx) + "fromX"
            val redactedGroup = DominionRedactedGroup(ballotStyle)
            ballotStyles.add(redactedGroup, nvotesMap)
            return true
        }  else if (line.get(schema.nheaders).startsWith("*")) { // El Paso
            val ballotStyle = line.get(ballotTypeIdx) + "fromStar"
            val redactedGroup = DominionRedactedGroup(ballotStyle)
            ballotStyles.add(redactedGroup, nvotesMap)
            return true
        }
        return false
    }
}

private val regexLE = Regex("[=\"]") // matches a quote or equals
fun removeLeadingEquals(input: String): String {
    val result = if (input.startsWith("=")) input.replace(regexLE, "") else input
    return result
}

private val regexComma = Regex("[,]") // matchs a comma
fun removeCommas(originalString: String) = originalString.replace(regexComma, "")

fun truncateCommas(originalString: String): String {
    val commaPos = originalString.indexOf(",")
    return if (commaPos < 0) originalString else originalString.substring(0, commaPos)
}
