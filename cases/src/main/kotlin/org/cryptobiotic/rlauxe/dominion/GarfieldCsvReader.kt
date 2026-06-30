package org.cryptobiotic.rlauxe.dominion

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.boulder.isEmpty
import org.cryptobiotic.rlauxe.util.ZipReader
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset

// this reads csv files from a "Dominion CVR export files", for Garfield County, Colorado 2020 General election.
// presumable some earlier version ??

private val logger = KotlinLogging.logger("DominionCvrExportReader")

private val d3f = "%3d"
private val showHeader = false
private val showLines = false

private val showDontMatch = false
private val showBallotStyles = false
private val showRedactedGroups = false

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

class GarfieldCsvReader(val filename: String, showHeaders: Boolean = false) {
    val ballotStyles = BallotStyles()
    val records: Iterator<CSVRecord>
    var lineno = 0
    // val electionName: String
    // val versionName: String
    val schema: Schema
    val nvotesMap: Map<Int, Int>

    val cvrNumberIdx: Int
    val batchIdIdx: Int
    val recordIdIdx: Int
    val imprintedIdIdx: Int
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
            CSVParser.parse(reader, CSVFormat.DEFAULT)
        } else {
            CSVParser.parse(File(filename), Charset.forName("UTF-8"), CSVFormat.DEFAULT)
        }

        records = parser.iterator()
        lineno++

        /* 1) no first line
        val electionLine = records.next()
        if (showHeaders) showLine("electionName", electionLine)
        lineno++
        electionName = electionLine.get(0).replace("[^ -~]".toRegex(), "")
        versionName = electionLine.get(1).trim() */


        try {
            val contestLine = records.next()
            lineno++

            // 2) the header for the first n columns, then the choice/candidate name for that column
            val headerChoiceLine = records.next()
            if (showLines) showLine("choice/candidate", headerChoiceLine)
            lineno++

            schema = makeSchema(contestLine, headerChoiceLine, headerChoiceLine)
            // println(schema.showColumns())
            // println()
            // println(schema.showContests())

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

            cvrNumberIdx = schema.columns.find { it.header == "RowNumber"}!!.colno
            batchIdIdx = schema.columns.find { it.header == "BoxID"}!!.colno
            recordIdIdx = schema.columns.find { it.header == "BoxPosition"}!!.colno
            imprintedIdIdx = schema.columns.find { it.header == "BallotID"}!!.colno
            ballotTypeIdx = schema.columns.find { it.header == "BallotStyleID"}!!.colno

            nvotesMap = schema.contests.associate { it.contestIdx to it.voteForN }
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    fun read(showFirst: Int? = null, showAfter: Int? = null): DominionCvrCsvSummary {

        var cvrCount = 0
        while (records.hasNext()) {
            val line = records.next()
            if (line.isEmpty()) break

            // 3) use header name matching
            val cvr = CastVoteRecord(
                cvrNumber = line.get(cvrNumberIdx).toInt(),
                tabulatorNum = -1,
                batchId = line.get(batchIdIdx),
                recordId = line.get(recordIdIdx).toInt(),
                imprintedId = line.get(imprintedIdIdx),
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

        val ballotTypes = ballotStyles.ballotTypes.values.sortedBy { it.countCards }.reversed()
        return DominionCvrCsvSummary(
            "Garfield", "unknown", filename, schema, cvrs,
            ballotStyles.redactedGroups.toSortedMap().values.toList(),
            ballotTypes
        )
    }

    fun showLine(what: String, line: CSVRecord) {
        println(what)
        val elems: List<String> = line.toList()
        elems.forEachIndexed { idx, it ->
            if (it.isNotEmpty()) println("  ${d3f.format(idx)}: $it")
        }
    }
}
