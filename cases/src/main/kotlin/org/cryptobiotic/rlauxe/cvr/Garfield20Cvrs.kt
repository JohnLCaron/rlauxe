package org.cryptobiotic.rlauxe.cvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.util.ZipReader
import org.cryptobiotic.rlauxe.util.nfn
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset

// this reads csv files from a "Dominion CVR export files", for Garfield County, Colorado 2020 General election.
// perhaps some earlier version of the export file ??

private val logger = KotlinLogging.logger("GarfieldCsvReader")

private val showLines = false

// original files
// cvr1
// RowNumber,BoxID,BoxPosition,BallotID,PrecinctID,BallotStyleID,PrecinctStyleName,ScanComputerName,Status,Remade,
// cvr2
// RowNumber,Tabulator,BoxID,BoxPosition,BallotID,CG,PrecinctID,BallotStyleID,Donald J. Trump / Michael R. Pence:Republican,Joseph R. Biden / Kamala D. Harris:Democratic,Bill Hammons / Eric Bodenstab:Unity,Blake Huber / Frank Atwood:Approval Voting,"Jo Jorgensen / Jeremy """"Spike"""" Cohen:Libertarian""",Brian Carroll / Amar Patel:American Solidarity,Mark Charles / Adrian Wallace:Unaffiliated,Phil Collins / Billy Joe Parker:Prohibition,"Roque """"Rocky"""" De La Fuente / Darcy G. Richardson:Alliance""",Dario Hunter / Dawn Neptune Adams:Progressive,Princess Khadijah Maryam Jacob-Fambro/Khadijah Maryam Jacob :Unaffiliated,Alyson Kennedy / Malcolm Jarrett:Socialist Workers,Joseph Kishore / Norissa Santa Cruz:Socialist Equality,Kyle Kenley Kopitke / Nathan Re Vo Sorenson:Independent American,Gloria La Riva / Sunil Freeman:Socialism and Liberation,Joe McHugh / Elizabeth Storm:Unaffiliated,Brock Pierce / Karla Ballard:Unaffiliated,"Jordan """"Cancer"""" Scott / Jennifer Tepool:Unaffiliated""",Kanye West / Michelle Tidball:Unaffiliated,Don Blankenship / William Mohr:American Constitution,Howie Hawkins / Angela Nicole Walker:Green,Write-in,John W. Hickenlooper:Democratic,Cory Gardner:Republican,Daniel Doyle:Approval Voting,"Stephan """"Seku"""" Evans:Unity""",Raymon Anthony Doane:Libertarian,Lauren Boebert:Republican,Diane E. Mitsch Bush:Democratic,John Ryan Keil:Libertarian,Critter Milton:Unity,Mayling Simpson:Democratic,Joyce Rankin:Republican,Karl Hanlon:Democratic,Bob Rankin:Republican,Perry Will:Republican,Colin Wilhelm:Democratic,Jefferson J. Cheney:Republican,John Martin:Republican,Beatriz Soto:Democratic,Brian Bark:Unaffiliated,Leslie Robinson:Democratic,Mike Samson:Republican,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against
// regular
// CvrNumber,TabulatorNum,BatchId,RecordId,ImprintedId,BallotType,,,,,,,,,,,,,,,,,,,,,,,,,,DEM,REP,APV,UNI,LBR,,,,,REP,DEM,LBR,UNI,UAF,REP,DEM,REP,DEM,REP,DEM,REP,REP,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

// cvr.csv
// ,,,,,,,,,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,United States Senator,United States Senator,United States Senator,United States Senator,United States Senator,United States Senator,Representative to the 117th US Congress Dist 3,Representative to the 117th US Congress Dist 3,Representative to the 117th US Congress Dist 3,Representative to the 117th US Congress Dist 3,Board of Education District 3,Board of Education District 3,State Senate District 8,State Senate District 8,State Representative - District 57,State Representative - District 57,District Attorney - 9th Judicial District,County Commissioner District 2,County Commissioner District 2,County Commissioner District 2,County Commissioner District 3,County Commissioner District 3,Colorado Supreme Court- Hart,Colorado Supreme Court- Hart,Colorado Supreme Court-Samour,Colorado Supreme Court-Samour,Court of Appeals-Tow,Court of Appeals-Tow,Court of Appeals-Welling,Court of Appeals-Welling,9th Judicial Judge-Lynch,9th Judicial Judge-Lynch,Amendment B - Local District Funding,Amendment B - Local District Funding,Amendment C - Gaming,Amendment C - Gaming,Amendment 76 - Citizen to Vote,Amendment 76 - Citizen to Vote,Amendment 77  -Towns and Gaming,Amendment 77  -Towns and Gaming,Proposition EE - Taxes on Vaping,Proposition EE - Taxes on Vaping,Proposition 113- Popular Vote,Proposition 113- Popular Vote,Proposition 114 - Gray Wolf,Proposition 114 - Gray Wolf,Proposition 115 - Late term abortion,Proposition 115 - Late term abortion,Proposition 116-State Income Tax Rate Reduction,Proposition 116-State Income Tax Rate Reduction,Proposition 117 - New Enterprise,Proposition 117 - New Enterprise,Proposition 118 - Paid FMLA,Proposition 118 - Paid FMLA,Issue 2A Glenwood Springs,Issue 2A Glenwood Springs,Issue 5B Eagle School District RE50J,Issue 5B Eagle School District RE50J,Issue 6A Glenwood Springs Rural Fire,Issue 6A Glenwood Springs Rural Fire,Issue 7A Colorado River Water Consveration Dist,Issue 7A Colorado River Water Consveration Dist,Issue 7B Carbondale & Rural Fire Protection,Issue 7B Carbondale & Rural Fire Protection
//RowNumber,BoxID,BoxPosition,BallotID,PrecinctID,BallotStyleID,PrecinctStyleName,ScanComputerName,Status,Remade,Write-in,Donald J. Trump / Michael R. Pence:Republican,Joseph R. Biden / Kamala D. Harris:Democratic,Bill Hammons / Eric Bodenstab:Unity,Blake Huber / Frank Atwood:Approval Voting,Jo Jorgensen / Jeremy ""Spike"" Cohen:Libertarian",Brian Carroll / Amar Patel:American Solidarity,Mark Charles / Adrian Wallace:Unaffiliated,Phil Collins / Billy Joe Parker:Prohibition,Roque ""Rocky"" De La Fuente / Darcy G. Richardson:Alliance",Dario Hunter / Dawn Neptune Adams:Progressive,Princess Khadijah Maryam Jacob-Fambro/Khadijah Maryam Jacob :Unaffiliated,Alyson Kennedy / Malcolm Jarrett:Socialist Workers,Joseph Kishore / Norissa Santa Cruz:Socialist Equality,Kyle Kenley Kopitke / Nathan Re Vo Sorenson:Independent American,Gloria La Riva / Sunil Freeman:Socialism and Liberation,Joe McHugh / Elizabeth Storm:Unaffiliated,Brock Pierce / Karla Ballard:Unaffiliated,Jordan ""Cancer"" Scott / Jennifer Tepool:Unaffiliated",Kanye West / Michelle Tidball:Unaffiliated,Don Blankenship / William Mohr:American Constitution,Howie Hawkins / Angela Nicole Walker:Green,Write-in,John W. Hickenlooper:Democratic,Cory Gardner:Republican,Daniel Doyle:Approval Voting,Stephan ""Seku"" Evans:Unity",Raymon Anthony Doane:Libertarian,Lauren Boebert:Republican,Diane E. Mitsch Bush:Democratic,John Ryan Keil:Libertarian,Critter Milton:Unity,Mayling Simpson:Democratic,Joyce Rankin:Republican,Karl Hanlon:Democratic,Bob Rankin:Republican,Perry Will:Republican,Colin Wilhelm:Democratic,Jefferson J. Cheney:Republican,John Martin:Republican,Beatriz Soto:Democratic,Brian Bark:Unaffiliated,Leslie Robinson:Democratic,Mike Samson:Republican,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against
//1,VBM-0001,1,VBM-0001+10003,17,3,3085723017,ScanStation01,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,0,1,1,0,1,0,1,0,1,0,0,1,0,1,0,1,0,1,0,1,,,,,,,1,0,,
//2,VBM-0001,2,VBM-0001+10005,9,4,3085723009 [PREC9IN],ScanStation01,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,1,0,1,0,1,0,1,0,1,1,0,0,0,1,0,1,0,0,1,1,0,1,0,1,0,1,0,1,0,1,0,1,0,,,,,1,0,,

// cvr2.csv
// ,,,,,,,,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,Presidential Electors,United States Senator,United States Senator,United States Senator,United States Senator,United States Senator,United States Senator,Representative to the 117th US Congress Dist 3,Representative to the 117th US Congress Dist 3,Representative to the 117th US Congress Dist 3,Representative to the 117th US Congress Dist 3,Board of Education District 3,Board of Education District 3,State Senate District 8,State Senate District 8,State Representative - District 57,State Representative - District 57,District Attorney - 9th Judicial District,County Commissioner District 2,County Commissioner District 2,County Commissioner District 2,County Commissioner District 3,County Commissioner District 3,Colorado Supreme Court- Hart,Colorado Supreme Court- Hart,Colorado Supreme Court-Samour,Colorado Supreme Court-Samour,Court of Appeals-Tow,Court of Appeals-Tow,Court of Appeals-Welling,Court of Appeals-Welling,9th Judicial Judge-Lynch,9th Judicial Judge-Lynch,Amendment B - Local District Funding,Amendment B - Local District Funding,Amendment C - Gaming,Amendment C - Gaming,Amendment 76 - Citizen to Vote,Amendment 76 - Citizen to Vote,Amendment 77  -Towns and Gaming,Amendment 77  -Towns and Gaming,Proposition EE - Taxes on Vaping,Proposition EE - Taxes on Vaping,Proposition 113- Popular Vote,Proposition 113- Popular Vote,Proposition 114 - Gray Wolf,Proposition 114 - Gray Wolf,Proposition 115 - Late term abortion,Proposition 115 - Late term abortion,Proposition 116-State Income Tax Rate Reduction,Proposition 116-State Income Tax Rate Reduction,Proposition 117 - New Enterprise,Proposition 117 - New Enterprise,Proposition 118 - Paid FMLA,Proposition 118 - Paid FMLA,Issue 2A Glenwood Springs,Issue 2A Glenwood Springs,Issue 5B Eagle School District RE50J,Issue 5B Eagle School District RE50J,Issue 6A Glenwood Springs Rural Fire,Issue 6A Glenwood Springs Rural Fire,Issue 7A Colorado River Water Consveration Dist,Issue 7A Colorado River Water Consveration Dist,Issue 7B Carbondale & Rural Fire Protection,Issue 7B Carbondale & Rural Fire Protection,
//RowNumber,Tabulator,BoxID,BoxPosition,BallotID,CG,PrecinctID,BallotStyleID,Donald J. Trump / Michael R. Pence:Republican,Joseph R. Biden / Kamala D. Harris:Democratic,Bill Hammons / Eric Bodenstab:Unity,Blake Huber / Frank Atwood:Approval Voting,"Jo Jorgensen / Jeremy """"Spike"""" Cohen:Libertarian""",Brian Carroll / Amar Patel:American Solidarity,Mark Charles / Adrian Wallace:Unaffiliated,Phil Collins / Billy Joe Parker:Prohibition,"Roque """"Rocky"""" De La Fuente / Darcy G. Richardson:Alliance""",Dario Hunter / Dawn Neptune Adams:Progressive,Princess Khadijah Maryam Jacob-Fambro/Khadijah Maryam Jacob :Unaffiliated,Alyson Kennedy / Malcolm Jarrett:Socialist Workers,Joseph Kishore / Norissa Santa Cruz:Socialist Equality,Kyle Kenley Kopitke / Nathan Re Vo Sorenson:Independent American,Gloria La Riva / Sunil Freeman:Socialism and Liberation,Joe McHugh / Elizabeth Storm:Unaffiliated,Brock Pierce / Karla Ballard:Unaffiliated,"Jordan """"Cancer"""" Scott / Jennifer Tepool:Unaffiliated""",Kanye West / Michelle Tidball:Unaffiliated,Don Blankenship / William Mohr:American Constitution,Howie Hawkins / Angela Nicole Walker:Green,Write-in,John W. Hickenlooper:Democratic,Cory Gardner:Republican,Daniel Doyle:Approval Voting,"Stephan """"Seku"""" Evans:Unity""",Raymon Anthony Doane:Libertarian,Lauren Boebert:Republican,Diane E. Mitsch Bush:Democratic,John Ryan Keil:Libertarian,Critter Milton:Unity,Mayling Simpson:Democratic,Joyce Rankin:Republican,Karl Hanlon:Democratic,Bob Rankin:Republican,Perry Will:Republican,Colin Wilhelm:Democratic,Jefferson J. Cheney:Republican,John Martin:Republican,Beatriz Soto:Democratic,Brian Bark:Unaffiliated,Leslie Robinson:Democratic,Mike Samson:Republican,Yes,No,Yes,No,Yes,No,Yes,No,Yes,No,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against,Yes/For,No/Against
//1,,VBM-0001,1,VBM-0001+10003,All,17,3,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,0,1,1,0,1,0,1,0,1,0,0,1,0,1,0,1,0,1,0,1,,,,,,,1,0,,
//2,,VBM-0001,2,VBM-0001+10005,All,9,4,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,1,0,1,0,1,0,1,0,1,1,0,0,0,1,0,1,0,0,1,1,0,1,0,1,0,1,0,1,0,1,0,1,0,,,,,1,0,,

// modified cvr2 and renamed as cvr

class Garfield20Cvrs(val filename: String, showHeaders: Boolean = false): CorlaCvrsIF {

    override val electionName = "Garfield2020"
    override val versionName = "unknown"
    override val schema: CvrSchema
    val nvotesMap: Map<Int, Int>

    val cvrNumberIdx: Int
    val batchIdIdx: Int
    val recordIdIdx: Int
    val imprintedIdIdx: Int
    val ballotTypeIdx: Int
    val precinctIdx: Int

    val ballotStyles = BallotStyles()
    val records: Iterator<CSVRecord>
    var lineno = 0
    val cvrs = mutableListOf<CvrRow>()

    init {
        val parser = if (filename.endsWith(".zip")) {
            val zipReader = ZipReader(filename)
            // by convention, the file inside is the filename with zip replaced by csv
            val lastPart = filename.substringAfterLast("/")
            val innerFilename = lastPart.replace(".zip", ".csv")
            val inputStream = zipReader.inputStream(innerFilename)
            val reader: Reader = InputStreamReader(inputStream, "UTF-8")
            CSVParser.parse(reader, CSVFormat.DEFAULT)
        } else {
            CSVParser.parse(File(filename), Charset.forName("UTF-8"), CSVFormat.DEFAULT)
        }

        records = parser.iterator()

        /* 1) no first line
        val electionLine = records.next()
        if (showHeaders) showLine("electionName", electionLine)
        lineno++
        electionName = electionLine.get(0).replace("[^ -~]".toRegex(), "")
        versionName = electionLine.get(1).trim() */

        try {
            val contestLine = records.next()
            if (showLines) showLine("contestLine", contestLine)
            lineno++

            // 2) the header for the first n columns, then the choice/candidate name for that column
            val headerChoiceLine = records.next()
            if (showLines) showLine("choice/candidate", headerChoiceLine)
            lineno++

            schema = makeCvrSchema(filename, contestLine, headerChoiceLine, headerChoiceLine)
            // println(CvrSchema.showColumns())
            // println()
            // println(CvrSchema.showContests())

            // 3) match on header name
            // colno,          header name, firstRow
            //    0,            RowNumber, 1
            //    1,            Tabulator,
            //    2,                BoxID, VBM-0001
            //    3,          BoxPosition, 1
            //    4,             BallotID, VBM-0001+10003
            //    5,                   CG, All
            //    6,           PrecinctID, 17
            //    7,        BallotStyleID, 3

            cvrNumberIdx = schema.headerMap["RowNumber".lowercase()]!!
            batchIdIdx = schema.headerMap["BoxID".lowercase()]!!
            recordIdIdx = schema.headerMap["BoxPosition".lowercase()]!!
            imprintedIdIdx = schema.headerMap["BallotID".lowercase()]!!
            ballotTypeIdx = schema.headerMap["BallotStyleID".lowercase()]!!
            precinctIdx = schema.headerMap["PrecinctID".lowercase()]!!

            nvotesMap = schema.contests.associate { it.contestIdx to it.voteForN }

            read()
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    fun read(showFirst: Int? = null, showAfter: Int? = null) {

        while (records.hasNext()) {
            val line = records.next()
            if (line.isEmpty()) break
            lineno++

            // 3) use header name matching
            val cvr = CvrRow(
                cvrNumber = line.get(cvrNumberIdx).toInt(),
                tabulatorNum = 1,
                batchId = line.get(batchIdIdx),
                recordId = line.get(recordIdIdx).toInt(),
                imprintedId = line.get(imprintedIdIdx),
                ballotType = line.get(ballotTypeIdx),
                precinctPortion = line.get(precinctIdx),
            ).addVotes(schema, line, lineno)

            if (cvr.contestVotes.isNotEmpty()) {
                cvrs.add(cvr)
                ballotStyles.add(cvr)
            }

            if (showFirst != null && lineno < showFirst) println(cvr.show())
            if (showAfter != null && lineno >= showAfter) println(cvr.show())
        }
    }

    fun showLine(what: String, line: CSVRecord) {
        println(what)
        val elems: List<String> = line.toList()
        elems.forEachIndexed { idx, it ->
            if (it.isNotEmpty()) println("  ${nfn(idx, 3)}: $it")
        }
    }

    override fun redactedGroups() = emptyList<RedactedGroup>()
    override fun groupWithLines() = null
    override fun cardStyles() = ballotStyles.cardStyles()
    override fun cvrs() = cvrs
    override fun nrows() = lineno
    override fun redactedCvrs() = emptyList<CvrRow>()
    override fun ngroups() = 0
}
