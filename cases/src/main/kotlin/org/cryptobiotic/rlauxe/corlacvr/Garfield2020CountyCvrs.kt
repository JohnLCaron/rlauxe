package org.cryptobiotic.rlauxe.corlacvr

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.cryptobiotic.rlauxe.corlaInput.munge
import org.cryptobiotic.rlauxe.util.ZipReader
import org.cryptobiotic.rlauxe.util.nfn
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset

// this reads csv files for Garfield County, Colorado 2020 General election.
// apparently this is from Clear Ballot, not Dominion.
// See TestGarfield2020

private val logger = KotlinLogging.logger("GarfieldCsvReader")

private val showLines = false

// original
// RowNumber,BoxID,BoxPosition,BallotID,PrecinctID,BallotStyleID,PrecinctStyleName,ScanComputerName,Status,Remade, Choice_18_1:Presidential Electors:Vote For 1:Write-in:Non-Partisan,Choice_19_30:Presidential Electors:Vote For 1:Donald J. Trump / Michael R. Pence:Republican,Choice_20_23:Presidential Electors:Vote For 1:Joseph R. Biden / Kamala D. Harris:Democratic,Choice_21_5:Presidential Electors:Vote For 1:Bill Hammons / Eric Bodenstab:Unity,Choice_22_22:Presidential Electors:Vote For 1:Blake Huber / Frank Atwood:Approval Voting,"Choice_23_27:Presidential Electors:Vote For 1:Jo Jorgensen / Jeremy ""Spike"" Cohen:Libertarian",Choice_24_7:Presidential Electors:Vote For 1:Brian Carroll / Amar Patel:American Solidarity,Choice_25_31:Presidential Electors:Vote For 1:Mark Charles / Adrian Wallace:Unaffiliated,Choice_26_9:Presidential Electors:Vote For 1:Phil Collins / Billy Joe Parker:Prohibition,"Choice_27_10:Presidential Electors:Vote For 1:Roque ""Rocky"" De La Fuente / Darcy G. Richardson:Alliance",Choice_28_11:Presidential Electors:Vote For 1:Dario Hunter / Dawn Neptune Adams:Progressive,Choice_29_31:Presidential Electors:Vote For 1:Princess Khadijah Maryam Jacob-Fambro/Khadijah Maryam Jacob :Unaffiliated,Choice_30_12:Presidential Electors:Vote For 1:Alyson Kennedy / Malcolm Jarrett:Socialist Workers,Choice_31_13:Presidential Electors:Vote For 1:Joseph Kishore / Norissa Santa Cruz:Socialist Equality,Choice_32_14:Presidential Electors:Vote For 1:Kyle Kenley Kopitke / Nathan Re Vo Sorenson:Independent American,Choice_33_15:Presidential Electors:Vote For 1:Gloria La Riva / Sunil Freeman:Socialism and Liberation,Choice_34_31:Presidential Electors:Vote For 1:Joe McHugh / Elizabeth Storm:Unaffiliated,Choice_92_31:Presidential Electors:Vote For 1:Brock Pierce / Karla Ballard:Unaffiliated,"Choice_93_31:Presidential Electors:Vote For 1:Jordan ""Cancer"" Scott / Jennifer Tepool:Unaffiliated",Choice_94_31:Presidential Electors:Vote For 1:Kanye West / Michelle Tidball:Unaffiliated,Choice_99_20:Presidential Electors:Vote For 1:Don Blankenship / William Mohr:American Constitution,Choice_100_25:Presidential Electors:Vote For 1:Howie Hawkins / Angela Nicole Walker:Green,Choice_17_1:United States Senator:Vote For 1:Write-in:Non-Partisan,Choice_38_23:United States Senator:Vote For 1:John W. Hickenlooper:Democratic,Choice_39_30:United States Senator:Vote For 1:Cory Gardner:Republican,Choice_40_22:United States Senator:Vote For 1:Daniel Doyle:Approval Voting,"Choice_41_5:United States Senator:Vote For 1:Stephan ""Seku"" Evans:Unity",Choice_42_27:United States Senator:Vote For 1:Raymon Anthony Doane:Libertarian,Choice_43_30:Representative to the 117th US Congress Dist 3:Vote For 1:Lauren Boebert:Republican,Choice_44_23:Representative to the 117th US Congress Dist 3:Vote For 1:Diane E. Mitsch Bush:Democratic,Choice_45_27:Representative to the 117th US Congress Dist 3:Vote For 1:John Ryan Keil:Libertarian,Choice_46_5:Representative to the 117th US Congress Dist 3:Vote For 1:Critter Milton:Unity,Choice_47_23:Board of Education District 3:Vote For 1:Mayling Simpson:Democratic,Choice_48_30:Board of Education District 3:Vote For 1:Joyce Rankin:Republican,Choice_49_23:State Senate District 8:Vote For 1:Karl Hanlon:Democratic,Choice_50_30:State Senate District 8:Vote For 1:Bob Rankin:Republican,Choice_51_30:State Representative - District 57:Vote For 1:Perry Will:Republican,Choice_52_23:State Representative - District 57:Vote For 1:Colin Wilhelm:Democratic,Choice_91_30:District Attorney - 9th Judicial District:Vote For 1:Jefferson J. Cheney:Republican,Choice_11_30:County Commissioner District 2:Vote For 1:John Martin:Republican,Choice_12_23:County Commissioner District 2:Vote For 1:Beatriz Soto:Democratic,Choice_16_31:County Commissioner District 2:Vote For 1:Brian Bark:Unaffiliated,Choice_13_23:County Commissioner District 3:Vote For 1:Leslie Robinson:Democratic,Choice_14_30:County Commissioner District 3:Vote For 1:Mike Samson:Republican,Choice_53_1:Colorado Supreme Court- Hart:Vote For 1:Yes:Non-Partisan,Choice_54_1:Colorado Supreme Court- Hart:Vote For 1:No:Non-Partisan,Choice_55_1:Colorado Supreme Court-Samour:Vote For 1:Yes:Non-Partisan,Choice_56_1:Colorado Supreme Court-Samour:Vote For 1:No:Non-Partisan,Choice_57_1:Court of Appeals-Tow:Vote For 1:Yes:Non-Partisan,Choice_58_1:Court of Appeals-Tow:Vote For 1:No:Non-Partisan,Choice_63_1:Court of Appeals-Welling:Vote For 1:Yes:Non-Partisan,Choice_64_1:Court of Appeals-Welling:Vote For 1:No:Non-Partisan,Choice_67_1:9th Judicial Judge-Lynch:Vote For 1:Yes:Non-Partisan,Choice_68_1:9th Judicial Judge-Lynch:Vote For 1:No:Non-Partisan,Choice_79_1:Amendment B - Local District Funding:Vote For 1:Yes/For:Non-Partisan,Choice_80_1:Amendment B - Local District Funding:Vote For 1:No/Against:Non-Partisan,Choice_77_1:Amendment C - Gaming:Vote For 1:Yes/For:Non-Partisan,Choice_78_1:Amendment C - Gaming:Vote For 1:No/Against:Non-Partisan,Choice_81_1:Amendment 76 - Citizen to Vote:Vote For 1:Yes/For:Non-Partisan,Choice_82_1:Amendment 76 - Citizen to Vote:Vote For 1:No/Against:Non-Partisan,Choice_95_1:Amendment 77  -Towns and Gaming:Vote For 1:Yes/For:Non-Partisan,Choice_96_1:Amendment 77  -Towns and Gaming:Vote For 1:No/Against:Non-Partisan,Choice_75_1:Proposition EE - Taxes on Vaping:Vote For 1:Yes/For:Non-Partisan,Choice_76_1:Proposition EE - Taxes on Vaping:Vote For 1:No/Against:Non-Partisan,Choice_71_1:Proposition 113- Popular Vote:Vote For 1:Yes/For:Non-Partisan,Choice_72_1:Proposition 113- Popular Vote:Vote For 1:No/Against:Non-Partisan,Choice_83_1:Proposition 114 - Gray Wolf:Vote For 1:Yes/For:Non-Partisan,Choice_84_1:Proposition 114 - Gray Wolf:Vote For 1:No/Against:Non-Partisan,Choice_85_1:Proposition 115 - Late term abortion:Vote For 1:Yes/For:Non-Partisan,Choice_86_1:Proposition 115 - Late term abortion:Vote For 1:No/Against:Non-Partisan,Choice_97_1:Proposition 116-State Income Tax Rate Reduction:Vote For 1:Yes/For:Non-Partisan,Choice_98_1:Proposition 116-State Income Tax Rate Reduction:Vote For 1:No/Against:Non-Partisan,Choice_87_1:Proposition 117 - New Enterprise:Vote For 1:Yes/For:Non-Partisan,Choice_88_1:Proposition 117 - New Enterprise:Vote For 1:No/Against:Non-Partisan,Choice_89_1:Proposition 118 - Paid FMLA:Vote For 1:Yes/For:Non-Partisan,Choice_90_1:Proposition 118 - Paid FMLA:Vote For 1:No/Against:Non-Partisan,Choice_3_1:Issue 2A Glenwood Springs:Vote For 1:Yes/For:Non-Partisan,Choice_4_1:Issue 2A Glenwood Springs:Vote For 1:No/Against:Non-Partisan,Choice_5_1:Issue 5B Eagle School District RE50J:Vote For 1:Yes/For:Non-Partisan,Choice_6_1:Issue 5B Eagle School District RE50J:Vote For 1:No/Against:Non-Partisan,Choice_7_1:Issue 6A Glenwood Springs Rural Fire:Vote For 1:Yes/For:Non-Partisan,Choice_8_1:Issue 6A Glenwood Springs Rural Fire:Vote For 1:No/Against:Non-Partisan,Choice_9_1:Issue 7A Colorado River Water Consveration Dist:Vote For 1:Yes/For:Non-Partisan,Choice_10_1:Issue 7A Colorado River Water Consveration Dist:Vote For 1:No/Against:Non-Partisan,Choice_1_1:Issue 7B Carbondale & Rural Fire Protection:Vote For 1:Yes/For:Non-Partisan,Choice_2_1:Issue 7B Carbondale & Rural Fire Protection:Vote For 1:No/Against:Non-Partisan
// 1,VBM-0001,1,VBM-0001+10003,17,3,3085723017,ScanStation01,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,0,1,1,0,1,0,1,0,1,0,0,1,0,1,0,1,0,1,0,1,,,,,,,1,0,,
// 2,VBM-0001,2,VBM-0001+10005,9,4,3085723009 [PREC9IN],ScanStation01,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,1,0,1,0,1,0,1,0,1,1,0,0,0,1,0,1,0,0,1,1,0,1,0,1,0,1,0,1,0,1,0,1,0,,,,,1,0,,

class Garfield2020CountyCvrs(override val inputSource: String, showHeaders: Boolean = false,
                             val redaction: Redaction = Redaction()): CorlaCountyCvrsIF {

    override val electionName = "Garfield2020"
    override val versionName = "unknown"
    override val schema: CvrSchema

    val cvrNumberIdx: Int
    val batchIdIdx: Int
    val recordIdIdx: Int
    val imprintedIdIdx: Int
    val ballotTypeIdx: Int
    val precinctIdx: Int
    val precinctNameIdx: Int

    val headerLine: CSVRecord
    val ballotStyles = BallotStyles()
    val records: Iterator<CSVRecord>
    var lineno = 0
    val cvrs = mutableListOf<CvrRow>()
    var countBlankPrecincts = 0

    init {
        val parser = if (inputSource.endsWith(".zip")) {
            val zipReader = ZipReader(inputSource)
            // by convention, the file inside is the filename with zip replaced by csv
            val lastPart = inputSource.substringAfterLast("/")
            val innerFilename = lastPart.replace(".zip", ".csv")
            val inputStream = zipReader.inputStream(innerFilename)
            val reader: Reader = InputStreamReader(inputStream, "UTF-8")
            CSVParser.parse(reader, CSVFormat.DEFAULT)
        } else {
            CSVParser.parse(File(inputSource), Charset.forName("UTF-8"), CSVFormat.DEFAULT)
        }

        records = parser.iterator()

        try {
            headerLine = records.next()
            if (showLines) showLine("headerLine", headerLine)
            lineno++

            schema = makeSchemaFromClearCountyHeader(inputSource, headerLine)
            // println(CvrSchema.showColumns())
            // println()
            // println(CvrSchema.showContests())

            // RowNumber,BoxID,BoxPosition,BallotID,PrecinctID,BallotStyleID,PrecinctStyleName,ScanComputerName,Status,Remade,
            //    Choice_18_1:Presidential Electors:Vote For 1:Write-in:Non-Partisan,Choice_19_30:Presidential Electors:Vote For 1:Donald J. Trump / Michael R. Pence:Republican,Choice_20_23:Presidential Electors:Vote For 1:Joseph R. Biden / Kamala D. Harris:Democratic,Choice_21_5:Presidential Electors:Vote For 1:Bill Hammons / Eric Bodenstab:Unity,Choice_22_22:Presidential Electors:Vote For 1:Blake Huber / Frank Atwood:Approval Voting,"Choice_23_27:Presidential Electors:Vote For 1:Jo Jorgensen / Jeremy ""Spike"" Cohen:Libertarian",Choice_24_7:Presidential Electors:Vote For 1:Brian Carroll / Amar Patel:American Solidarity,Choice_25_31:Presidential Electors:Vote For 1:Mark Charles / Adrian Wallace:Unaffiliated,Choice_26_9:Presidential Electors:Vote For 1:Phil Collins / Billy Joe Parker:Prohibition,"Choice_27_10:Presidential Electors:Vote For 1:Roque ""Rocky"" De La Fuente / Darcy G. Richardson:Alliance",Choice_28_11:Presidential Electors:Vote For 1:Dario Hunter / Dawn Neptune Adams:Progressive,Choice_29_31:Presidential Electors:Vote For 1:Princess Khadijah Maryam Jacob-Fambro/Khadijah Maryam Jacob :Unaffiliated,Choice_30_12:Presidential Electors:Vote For 1:Alyson Kennedy / Malcolm Jarrett:Socialist Workers,Choice_31_13:Presidential Electors:Vote For 1:Joseph Kishore / Norissa Santa Cruz:Socialist Equality,Choice_32_14:Presidential Electors:Vote For 1:Kyle Kenley Kopitke / Nathan Re Vo Sorenson:Independent American,Choice_33_15:Presidential Electors:Vote For 1:Gloria La Riva / Sunil Freeman:Socialism and Liberation,Choice_34_31:Presidential Electors:Vote For 1:Joe McHugh / Elizabeth Storm:Unaffiliated,Choice_92_31:Presidential Electors:Vote For 1:Brock Pierce / Karla Ballard:Unaffiliated,"Choice_93_31:Presidential Electors:Vote For 1:Jordan ""Cancer"" Scott / Jennifer Tepool:Unaffiliated",Choice_94_31:Presidential Electors:Vote For 1:Kanye West / Michelle Tidball:Unaffiliated,Choice_99_20:Presidential Electors:Vote For 1:Don Blankenship / William Mohr:American Constitution,Choice_100_25:Presidential Electors:Vote For 1:Howie Hawkins / Angela Nicole Walker:Green,Choice_17_1:United States Senator:Vote For 1:Write-in:Non-Partisan,Choice_38_23:United States Senator:Vote For 1:John W. Hickenlooper:Democratic,Choice_39_30:United States Senator:Vote For 1:Cory Gardner:Republican,Choice_40_22:United States Senator:Vote For 1:Daniel Doyle:Approval Voting,"Choice_41_5:United States Senator:Vote For 1:Stephan ""Seku"" Evans:Unity",Choice_42_27:United States Senator:Vote For 1:Raymon Anthony Doane:Libertarian,Choice_43_30:Representative to the 117th US Congress Dist 3:Vote For 1:Lauren Boebert:Republican,Choice_44_23:Representative to the 117th US Congress Dist 3:Vote For 1:Diane E. Mitsch Bush:Democratic,Choice_45_27:Representative to the 117th US Congress Dist 3:Vote For 1:John Ryan Keil:Libertarian,Choice_46_5:Representative to the 117th US Congress Dist 3:Vote For 1:Critter Milton:Unity,Choice_47_23:Board of Education District 3:Vote For 1:Mayling Simpson:Democratic,Choice_48_30:Board of Education District 3:Vote For 1:Joyce Rankin:Republican,Choice_49_23:State Senate District 8:Vote For 1:Karl Hanlon:Democratic,Choice_50_30:State Senate District 8:Vote For 1:Bob Rankin:Republican,Choice_51_30:State Representative - District 57:Vote For 1:Perry Will:Republican,Choice_52_23:State Representative - District 57:Vote For 1:Colin Wilhelm:Democratic,Choice_91_30:District Attorney - 9th Judicial District:Vote For 1:Jefferson J. Cheney:Republican,Choice_11_30:County Commissioner District 2:Vote For 1:John Martin:Republican,Choice_12_23:County Commissioner District 2:Vote For 1:Beatriz Soto:Democratic,Choice_16_31:County Commissioner District 2:Vote For 1:Brian Bark:Unaffiliated,Choice_13_23:County Commissioner District 3:Vote For 1:Leslie Robinson:Democratic,Choice_14_30:County Commissioner District 3:Vote For 1:Mike Samson:Republican,Choice_53_1:Colorado Supreme Court- Hart:Vote For 1:Yes:Non-Partisan,Choice_54_1:Colorado Supreme Court- Hart:Vote For 1:No:Non-Partisan,Choice_55_1:Colorado Supreme Court-Samour:Vote For 1:Yes:Non-Partisan,Choice_56_1:Colorado Supreme Court-Samour:Vote For 1:No:Non-Partisan,Choice_57_1:Court of Appeals-Tow:Vote For 1:Yes:Non-Partisan,Choice_58_1:Court of Appeals-Tow:Vote For 1:No:Non-Partisan,Choice_63_1:Court of Appeals-Welling:Vote For 1:Yes:Non-Partisan,Choice_64_1:Court of Appeals-Welling:Vote For 1:No:Non-Partisan,Choice_67_1:9th Judicial Judge-Lynch:Vote For 1:Yes:Non-Partisan,Choice_68_1:9th Judicial Judge-Lynch:Vote For 1:No:Non-Partisan,Choice_79_1:Amendment B - Local District Funding:Vote For 1:Yes/For:Non-Partisan,Choice_80_1:Amendment B - Local District Funding:Vote For 1:No/Against:Non-Partisan,Choice_77_1:Amendment C - Gaming:Vote For 1:Yes/For:Non-Partisan,Choice_78_1:Amendment C - Gaming:Vote For 1:No/Against:Non-Partisan,Choice_81_1:Amendment 76 - Citizen to Vote:Vote For 1:Yes/For:Non-Partisan,Choice_82_1:Amendment 76 - Citizen to Vote:Vote For 1:No/Against:Non-Partisan,Choice_95_1:Amendment 77  -Towns and Gaming:Vote For 1:Yes/For:Non-Partisan,Choice_96_1:Amendment 77  -Towns and Gaming:Vote For 1:No/Against:Non-Partisan,Choice_75_1:Proposition EE - Taxes on Vaping:Vote For 1:Yes/For:Non-Partisan,Choice_76_1:Proposition EE - Taxes on Vaping:Vote For 1:No/Against:Non-Partisan,Choice_71_1:Proposition 113- Popular Vote:Vote For 1:Yes/For:Non-Partisan,Choice_72_1:Proposition 113- Popular Vote:Vote For 1:No/Against:Non-Partisan,Choice_83_1:Proposition 114 - Gray Wolf:Vote For 1:Yes/For:Non-Partisan,Choice_84_1:Proposition 114 - Gray Wolf:Vote For 1:No/Against:Non-Partisan,Choice_85_1:Proposition 115 - Late term abortion:Vote For 1:Yes/For:Non-Partisan,Choice_86_1:Proposition 115 - Late term abortion:Vote For 1:No/Against:Non-Partisan,Choice_97_1:Proposition 116-State Income Tax Rate Reduction:Vote For 1:Yes/For:Non-Partisan,Choice_98_1:Proposition 116-State Income Tax Rate Reduction:Vote For 1:No/Against:Non-Partisan,Choice_87_1:Proposition 117 - New Enterprise:Vote For 1:Yes/For:Non-Partisan,Choice_88_1:Proposition 117 - New Enterprise:Vote For 1:No/Against:Non-Partisan,Choice_89_1:Proposition 118 - Paid FMLA:Vote For 1:Yes/For:Non-Partisan,Choice_90_1:Proposition 118 - Paid FMLA:Vote For 1:No/Against:Non-Partisan,Choice_3_1:Issue 2A Glenwood Springs:Vote For 1:Yes/For:Non-Partisan,Choice_4_1:Issue 2A Glenwood Springs:Vote For 1:No/Against:Non-Partisan,Choice_5_1:Issue 5B Eagle School District RE50J:Vote For 1:Yes/For:Non-Partisan,Choice_6_1:Issue 5B Eagle School District RE50J:Vote For 1:No/Against:Non-Partisan,Choice_7_1:Issue 6A Glenwood Springs Rural Fire:Vote For 1:Yes/For:Non-Partisan,Choice_8_1:Issue 6A Glenwood Springs Rural Fire:Vote For 1:No/Against:Non-Partisan,Choice_9_1:Issue 7A Colorado River Water Consveration Dist:Vote For 1:Yes/For:Non-Partisan,Choice_10_1:Issue 7A Colorado River Water Consveration Dist:Vote For 1:No/Against:Non-Partisan,Choice_1_1:Issue 7B Carbondale & Rural Fire Protection:Vote For 1:Yes/For:Non-Partisan,Choice_2_1:Issue 7B Carbondale & Rural Fire Protection:Vote For 1:No/Against:Non-Partisan

            // RowNumber,BoxID,BoxPosition,BallotID,PrecinctID,BallotStyleID,PrecinctStyleName,ScanComputerName,Status,Remade,Choice_18_1: ...
            cvrNumberIdx = schema.headers.indexOf("RowNumber".lowercase())
            batchIdIdx = schema.headers.indexOf("BoxID".lowercase())
            recordIdIdx = schema.headers.indexOf("BoxPosition".lowercase())
            imprintedIdIdx = schema.headers.indexOf("BallotID".lowercase())
            precinctIdx = schema.headers.indexOf("PrecinctID".lowercase())
            ballotTypeIdx = schema.headers.indexOf("BallotStyleID".lowercase())
            precinctNameIdx = schema.headers.indexOf("PrecinctStyleName".lowercase())

            read()
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    // Choice_18_1:Presidential Electors:Vote For 1:Write-in:Non-Partisan,
    // Choice_19_30:Presidential Electors:Vote For 1:Donald J. Trump / Michael R. Pence:Republican,
    // Choice_20_23:Presidential Electors:Vote For 1:Joseph R. Biden / Kamala D. Harris:Democratic,
    // Choice_21_5:Presidential Electors:Vote For 1:Bill Hammons / Eric Bodenstab:Unity,
    // Choice_22_22:Presidential Electors:Vote For 1:Blake Huber / Frank Atwood:Approval Voting,
    // "Choice_23_27:Presidential Electors:Vote For 1:Jo Jorgensen / Jeremy ""Spike"" Cohen:Libertarian",
    // Choice_24_7:Presidential Electors:Vote For 1:Brian Carroll / Amar Patel:American Solidarity,
    // Choice_25_31:Presidential Electors:Vote For 1:Mark Charles / Adrian Wallace:Unaffiliated,
    // Choice_26_9:Presidential Electors:Vote For 1:Phil Collins / Billy Joe Parker:Prohibition,
    // "Choice_27_10:Presidential Electors:Vote For 1:Roque ""Rocky"" De La Fuente / Darcy G. Richardson:Alliance",
    // Choice_28_11:Presidential Electors:Vote For 1:Dario Hunter / Dawn Neptune Adams:Progressive,
    // Choice_29_31:Presidential Electors:Vote For 1:Princess Khadijah Maryam Jacob-Fambro/Khadijah Maryam Jacob :Unaffiliated,
    // Choice_30_12:Presidential Electors:Vote For 1:Alyson Kennedy / Malcolm Jarrett:Socialist Workers,
    // Choice_31_13:Presidential Electors:Vote For 1:Joseph Kishore / Norissa Santa Cruz:Socialist Equality,
    // Choice_32_14:Presidential Electors:Vote For 1:Kyle Kenley Kopitke / Nathan Re Vo Sorenson:Independent American,
    // Choice_33_15:Presidential Electors:Vote For 1:Gloria La Riva / Sunil Freeman:Socialism and Liberation,
    // Choice_34_31:Presidential Electors:Vote For 1:Joe McHugh / Elizabeth Storm:Unaffiliated,
    // Choice_92_31:Presidential Electors:Vote For 1:Brock Pierce / Karla Ballard:Unaffiliated,
    // "Choice_93_31:Presidential Electors:Vote For 1:Jordan ""Cancer"" Scott / Jennifer Tepool:Unaffiliated",
    // Choice_94_31:Presidential Electors:Vote For 1:Kanye West / Michelle Tidball:Unaffiliated,
    // Choice_99_20:Presidential Electors:Vote For 1:Don Blankenship / William Mohr:American Constitution,
    // Choice_100_25:Presidential Electors:Vote For 1:Howie Hawkins / Angela Nicole Walker:Green,
    // Choice_17_1:United States Senator:Vote For 1:Write-in:Non-Partisan,
    // Choice_38_23:United States Senator:Vote For 1:John W. Hickenlooper:Democratic,
    // Choice_39_30:United States Senator:Vote For 1:Cory Gardner:Republican,
    // Choice_40_22:United States Senator:Vote For 1:Daniel Doyle:Approval Voting,
    // "Choice_41_5:United States Senator:Vote For 1:Stephan ""Seku"" Evans:Unity",
    // Choice_42_27:United States Senator:Vote For 1:Raymon Anthony Doane:Libertarian,
    // Choice_43_30:Representative to the 117th US Congress Dist 3:Vote For 1:Lauren Boebert:Republican,
    // Choice_44_23:Representative to the 117th US Congress Dist 3:Vote For 1:Diane E. Mitsch Bush:Democratic,
    // Choice_45_27:Representative to the 117th US Congress Dist 3:Vote For 1:John Ryan Keil:Libertarian,
    // Choice_46_5:Representative to the 117th US Congress Dist 3:Vote For 1:Critter Milton:Unity,
    // Choice_47_23:Board of Education District 3:Vote For 1:Mayling Simpson:Democratic,
    // Choice_48_30:Board of Education District 3:Vote For 1:Joyce Rankin:Republican,
    // Choice_49_23:State Senate District 8:Vote For 1:Karl Hanlon:Democratic,
    // Choice_50_30:State Senate District 8:Vote For 1:Bob Rankin:Republican,
    // Choice_51_30:State Representative - District 57:Vote For 1:Perry Will:Republican,
    // Choice_52_23:State Representative - District 57:Vote For 1:Colin Wilhelm:Democratic,
    // Choice_91_30:District Attorney - 9th Judicial District:Vote For 1:Jefferson J. Cheney:Republican,
    // Choice_11_30:County Commissioner District 2:Vote For 1:John Martin:Republican,
    // Choice_12_23:County Commissioner District 2:Vote For 1:Beatriz Soto:Democratic,
    // Choice_16_31:County Commissioner District 2:Vote For 1:Brian Bark:Unaffiliated,
    // Choice_13_23:County Commissioner District 3:Vote For 1:Leslie Robinson:Democratic,
    // Choice_14_30: County Commissioner District 3:Vote For 1:Mike Samson:Republican,
    // Choice_53_1:Colorado Supreme Court- Hart:Vote For 1:Yes:Non-Partisan,
    // Choice_54_1: Colorado Supreme Court- Hart:Vote For 1:No:Non-Partisan,
    // Choice_55_1: Colorado Supreme Court-Samour:Vote For 1:Yes:Non-Partisan,
    // Choice_56_1:Colorado Supreme Court-Samour:Vote For 1:No:Non-Partisan,Choice_57_1:Court of Appeals-Tow:Vote For 1:Yes:Non-Partisan,Choice_58_1:Court of Appeals-Tow:Vote For 1:No:Non-Partisan,Choice_63_1:Court of Appeals-Welling:Vote For 1:Yes:Non-Partisan,Choice_64_1:Court of Appeals-Welling:Vote For 1:No:Non-Partisan,Choice_67_1:9th Judicial Judge-Lynch:Vote For 1:Yes:Non-Partisan,Choice_68_1:9th Judicial Judge-Lynch:Vote For 1:No:Non-Partisan,Choice_79_1:Amendment B - Local District Funding:Vote For 1:Yes/For:Non-Partisan,Choice_80_1:Amendment B - Local District Funding:Vote For 1:No/Against:Non-Partisan,Choice_77_1:Amendment C - Gaming:Vote For 1:Yes/For:Non-Partisan,Choice_78_1:Amendment C - Gaming:Vote For 1:No/Against:Non-Partisan,Choice_81_1:Amendment 76 - Citizen to Vote:Vote For 1:Yes/For:Non-Partisan,Choice_82_1:Amendment 76 - Citizen to Vote:Vote For 1:No/Against:Non-Partisan,Choice_95_1:Amendment 77  -Towns and Gaming:Vote For 1:Yes/For:Non-Partisan,Choice_96_1:Amendment 77  -Towns and Gaming:Vote For 1:No/Against:Non-Partisan,Choice_75_1:Proposition EE - Taxes on Vaping:Vote For 1:Yes/For:Non-Partisan,Choice_76_1:Proposition EE - Taxes on Vaping:Vote For 1:No/Against:Non-Partisan,Choice_71_1:Proposition 113- Popular Vote:Vote For 1:Yes/For:Non-Partisan,Choice_72_1:Proposition 113- Popular Vote:Vote For 1:No/Against:Non-Partisan,Choice_83_1:Proposition 114 - Gray Wolf:Vote For 1:Yes/For:Non-Partisan,Choice_84_1:Proposition 114 - Gray Wolf:Vote For 1:No/Against:Non-Partisan,Choice_85_1:Proposition 115 - Late term abortion:Vote For 1:Yes/For:Non-Partisan,Choice_86_1:Proposition 115 - Late term abortion:Vote For 1:No/Against:Non-Partisan,Choice_97_1:Proposition 116-State Income Tax Rate Reduction:Vote For 1:Yes/For:Non-Partisan,Choice_98_1:Proposition 116-State Income Tax Rate Reduction:Vote For 1:No/Against:Non-Partisan,Choice_87_1:Proposition 117 - New Enterprise:Vote For 1:Yes/For:Non-Partisan,Choice_88_1:Proposition 117 - New Enterprise:Vote For 1:No/Against:Non-Partisan,Choice_89_1:Proposition 118 - Paid FMLA:Vote For 1:Yes/For:Non-Partisan,Choice_90_1:Proposition 118 - Paid FMLA:Vote For 1:No/Against:Non-Partisan,Choice_3_1:Issue 2A Glenwood Springs:Vote For 1:Yes/For:Non-Partisan,Choice_4_1:Issue 2A Glenwood Springs:Vote For 1:No/Against:Non-Partisan,Choice_5_1:Issue 5B Eagle School District RE50J:Vote For 1:Yes/For:Non-Partisan,Choice_6_1:Issue 5B Eagle School District RE50J:Vote For 1:No/Against:Non-Partisan,Choice_7_1:Issue 6A Glenwood Springs Rural Fire:Vote For 1:Yes/For:Non-Partisan,Choice_8_1:Issue 6A Glenwood Springs Rural Fire:Vote For 1:No/Against:Non-Partisan,Choice_9_1:Issue 7A Colorado River Water Consveration Dist:Vote For 1:Yes/For:Non-Partisan,Choice_10_1:Issue 7A Colorado River Water Consveration Dist:Vote For 1:No/Against:Non-Partisan,Choice_1_1:Issue 7B Carbondale & Rural Fire Protection:Vote For 1:Yes/For:Non-Partisan,Choice_2_1:Issue 7B Carbondale & Rural Fire Protection:Vote For 1:No/Against:Non-Partisan
    fun makeSchemaFromClearCountyHeader(inputSource: String, headerLine: CSVRecord): CvrSchema {
        val nheaders = headerLine.toList().indexOfFirst { it.startsWith("Choice") || it.startsWith("\"Choice") }
        val headers = headerLine.values().take(nheaders).map { it.lowercase() }

        var currContestName = ""
        var currContestIdx = 0
        var currContestStart = nheaders
        var currContestVoteFor = 0

        val ccontests = mutableListOf<SchemaContestInfo>()
        val columns = mutableListOf<SchemaColumnInfo>()

        for (colidx in nheaders until headerLine.size()) {
            val clean = removeParen(headerLine.get(colidx)).trim()
            val parts = clean.split(":")
            if (parts.size < 5)
                print("")
            // starter:contest:votefor:choice:party (5)
            val contestName = parts.get(1).trim()
            val voteFor = parseVoteFor(parts.get(2))
            val choice = parts.get(3).trim()
            val party = parts.get(4).trim()
            if (currContestName.isEmpty()) currContestName = contestName

            // running through munge to try to detect minor typos in the contest names
            if (munge(contestName) != munge(currContestName)) {
                // save the old contest
                ccontests.add( SchemaContestInfo(currContestIdx, currContestName, currContestStart, colidx-currContestStart, voteFor, false) )
                // start the new contest
                currContestName = contestName
                currContestStart = colidx
                currContestVoteFor = voteFor
                currContestIdx++
            }

            val colInfo = SchemaColumnInfo(currContestIdx, colidx, contestName, choice, party)
            columns.add(colInfo)
        }
        // save the last contest
        ccontests.add( SchemaContestInfo(currContestIdx, currContestName, currContestStart, headerLine.size()-currContestStart, currContestVoteFor, false) )

        val voteForNs = ccontests.associate { it.contestIdx to it.voteForN }
        return CvrSchema(inputSource, headers, columns, ccontests, voteForNs)
    }

    fun removeParen(s: String): String {
        if (s.isEmpty()) return s
        if (s.first() == '"' && s.last() == '"') return s.substring(1, s.length-1)
        return s.trim()
    }

    fun parseVoteFor(votefor: String) : Int {
        val tokens = votefor.split("Vote For ")
        require (tokens.size == 2) { "unexpected contest votefor= $votefor" }
        val ncand = tokens[1].trim().toInt()
        return ncand
    }

    override fun headers(skipHeaders:List<CvrHeader>): List<String> {
        return listOf(unskipped(skipHeaders, headerLine.toList()))
    }

    override fun rowHeaderCols(row: CvrRow, redact: List<CvrHeader>): String {
        return unskipped(redact, row.headerCols)
    }

    //// only implementing "skip precincts"
    private fun unskipped(skipHeaders:List<CvrHeader>, headers: List<String>): String {
        val skipPrecinct = skipHeaders.any { it == CvrHeader.precinctportion }
        val skipIndex = if (skipPrecinct) listOf(precinctIdx, precinctNameIdx) else emptyList()
        val notskipped = mutableListOf<String>()
        headers.forEachIndexed { idx, head ->
            if (!skipIndex.contains(idx)) notskipped.add(head)
        }
        return notskipped.joinToString(",")
    }

    override fun redaction() = redaction
    override fun cardStyleMap() = ballotStyles.cardStyleMap
    override fun cardStyles() = ballotStyles.cardStyles()
    override fun ballotStyles() = ballotStyles
    override fun cvrs() = cvrs
    override fun nrows() = lineno
    override fun unredactedCvrs() = cvrs.size
    override fun hasBallotType() = ballotTypeIdx != null
    override fun countBlankPrecincts() = countBlankPrecincts
    override fun ballotStyleUnique() = ballotStyles.ballotStylesUnique

    fun read(showFirst: Int? = null, showAfter: Int? = null) {

        while (records.hasNext()) {
            val line = records.next()
            if (line.isEmpty()) break
            lineno++

            if (!redaction.isRedaction(line, this)) {
                // 3) use header name matching
                val cvr = CvrRow(
                    line.values().take(schema.nheaders),
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
    }

    fun showLine(what: String, line: CSVRecord) {
        println(what)
        val elems: List<String> = line.toList()
        elems.forEachIndexed { idx, it ->
            if (it.isNotEmpty()) println("  ${nfn(idx, 3)}: $it")
        }
    }

    override fun getBallotType(line: CSVRecord): String {
        return line.get(ballotTypeIdx)
    }

    override fun parseHeader(line: CSVRecord, defCvrNumber: Int): CvrRow {
        return CvrRow(
            line.values().take(schema.nheaders),
            cvrNumber = line.get(cvrNumberIdx).toInt(),
            tabulatorNum = 1,
            batchId = line.get(batchIdIdx),
            recordId = line.get(recordIdIdx).toInt(),
            imprintedId = line.get(imprintedIdIdx),
            ballotType = line.get(ballotTypeIdx),
            precinctPortion = line.get(precinctIdx),
        )
    }


}
