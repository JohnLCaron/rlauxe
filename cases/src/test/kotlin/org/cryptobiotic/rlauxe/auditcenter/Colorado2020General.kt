package org.cryptobiotic.rlauxe.auditcenter

import org.cryptobiotic.rlauxe.auditcenter
import org.cryptobiotic.rlauxe.corla.CanonicalContest
import org.cryptobiotic.rlauxe.corla.ColoradoInput
import org.cryptobiotic.rlauxe.corla.readGeneralCanonicalList

class Colorado2020General: ColoradoInput(
    generalCanonicalFile = "$general2020/canonicalTitleCase.csv",
    contestRoundFile = "$general2020/round_1/contest.csv",
    tabulateCountyFile = "$general2020/tabulate_county.csv",
    mvrComparisonFile = "$general2020/round_3/contestComparison.csv"
) {
    // canonical contests and choices
    override fun canonicalContests() = canonicalContests
    private val canonicalContests: Map<String, CanonicalContest> by lazy {
        val result: MutableMap<String, CanonicalContest> =
            readGeneralCanonicalList(generalCanonicalFile).associateBy { it.contestName }.toMutableMap()

        // add these missing contests:
        val extras = listOf(
            CanonicalContest("Adams County Ballot Issue 1A", listOf("Yes/For", "No/Against",)).addCounties(listOf("Adams"))
        )
        extras.forEach { result[it.contestName] = it }

        // compare canonical contest counties and CountyTabulateCsv
        //  countyTabulateCsv doesnt have 'Gunnison' from canonical
        //  countyTabulateCsv doesnt have 'San Juan' from canonical

        // remove these contests: TODO some? are in cvrs; see TestContestNames
        //countyTabulate missing auditcenter 'Gunnison County Commissioner - District 1'
        //countyTabulate missing auditcenter 'Gunnison County Commissioner - District 2'
        //countyTabulate missing auditcenter 'Gunnison County Court Judge - Burgemeister'
        //countyTabulate missing auditcenter 'Town of Marble - Board of Trustees'
        //countyTabulate missing auditcenter 'Town of Marble Ballot Issue 2A'
        //countyTabulate missing auditcenter 'San Juan County Commissioner - District 1'
        //countyTabulate missing auditcenter 'San Juan County Commissioner - District 2'
        //countyTabulate missing auditcenter 'San Juan County Court Judge - Edwards'
        //
        //result.remove("Gunnison County Commissioner - District 1")
        //result.remove("Gunnison County Commissioner - District 2")
        //result.remove("Gunnison County Court Judge - Burgemeister")
        //result.remove("Town of Marble - Board of Trustees")
        //result.remove("Town of Marble Ballot Issue 2A")
        //result.remove("San Juan County Commissioner - District 1")
        //result.remove("San Juan County Commissioner - District 2")
        //result.remove("San Juan County Court Judge - Edwards")

        // these are from checkCountyTabulateHasCanonical() and checkContestRoundHasCanonical()
        result.remove("Gunnison County Commissioner - District 1")
        result.remove("Gunnison County Commissioner - District 2")
        result.remove("Gunnison County Court Judge - Burgemeister") // ?? check, also signal when not present in file
        result.remove("Town of Marble - Board of Trustees")
        result.remove("Town of Marble Ballot Issue 2A")
        result.remove("San Juan County Commissioner - District 1")
        result.remove("San Juan County Commissioner - District 2")
        result.remove("San Juan County Court Judge - Edwards")

        result.toSortedMap()
    }

    // county specific ??
    fun contestNameCleanup(name: String): String {
        return when (name) {
            "Representative to the 117th United States Congress-District 2" -> "Representative to the 117th United States Congress - District 2"
            "Representative to the 117th United States Congress-District 4" -> "Representative to the 117th United States Congress - District 4"
            "District Attorney-20th Judicial District" -> "District Attorney - 20th Judicial District"
            "Boulder County Court" -> "Boulder County Court Judge - Martin"
            "City of Louisville City Council Ward 3 (1-Year Term)" -> "City of Louisville City Council Ward 3"
            "Town of Superior - Trustee" -> "Town of Superior Trustee"
            "City Of Boulder Ballot Issue 2B" -> "City of Boulder Ballot Issue 2B"
            "Amendment B (Constitutional)" -> "Amendment B (CONSTITUTIONAL)"
            "Amendment C (Constitutional)" -> "Amendment C (CONSTITUTIONAL)"
            "Amendment 76 (Constitutional)" -> "Amendment 76 (CONSTITUTIONAL)"
            "Amendment 77 (Constitutional)" -> "Amendment 77 (CONSTITUTIONAL)"
            "Proposition EE (Statutory)" -> "Proposition EE (STATUTORY)"
            "Proposition 113 (Statutory)" -> "Proposition 113 (STATUTORY)"
            "Proposition 114 (Statutory)" -> "Proposition 114 (STATUTORY)"
            "Proposition 115 (Statutory)" -> "Proposition 115 (STATUTORY)"
            "Proposition 116 (Statutory)" -> "Proposition 116 (STATUTORY)"
            "Proposition 117 (Statutory)" -> "Proposition 117 (STATUTORY)"
            "Proposition 118 (Statutory)" -> "Proposition 118 (STATUTORY)"
            else -> name
        }
    }

    override fun contestNameCleanup(county: String, name: String): String {
        when (name) {
            "Colorado Supreme Court Justice - Hart" -> return "Justice of the Colorado Supreme Court - Hart"
            "Colorado Supreme Court Justice - Samour" -> return "Justice of the Colorado Supreme Court - Samour"
            "Supreme Court Hart" -> return "Justice of the Colorado Supreme Court - Hart"
            "Supreme Court Samour" -> return "Justice of the Colorado Supreme Court - Samour"
        }

        return when (county) {
            "Adams" -> when (name) {
                "Adams County Court - Flaum" -> "Adams County Court Judge - Flaum"
                "Adams County Court - Kirby" -> "Adams County Court Judge - Kirby"
                "ISSUE 1A" -> "Adams County Ballot Issue 1A"
                "ISSUE 1B" -> "Adams County Ballot Issue 1B"
                "Question 1C" -> "Adams County Ballot Issue 1C"
                else -> name
            }
            "Alamosa" -> when (name) {
                "Alamosa County Revenue Protection - 1A" -> "Alamosa County Ballot Question 1A"
                "Alamosa County Ambulance District Revenue Protection - 6A" -> "Alamosa County Ambulance District Ballot Question 6A"
                else -> name
            }
            "Archuleta" -> when (name) {
                "Ballot Issue 7A" -> "Los Pinos Fire Protection District Ballot Issue 7A"
                "Ballot Issue 6A" -> "Aspen Springs Metropolitan District Ballot Issue 6A"
                else -> name
            }
            "Arapahoe" -> when (name) {
                "Arapahoe County Court - Contiguglia" -> "Arapahoe County Court Judge - Contiguglia"
                "Arapahoe County Court - Ollada" -> "Arapahoe County Court Judge - Ollada"
                "Arapahoe County Court - Williford" -> "Arapahoe County Court Judge - Williford"
                "Ballot Question 2A" -> "City of Englewood Ballot Question 2A"
                "Ballot Issue 2B" -> "City of Englewood Ballot Issue 2B"
                "Referred Ballot Question No. 3A" -> "City of Littleton Ballot Question 3A"
                "Initiated Ballot Question No. 300" -> "City of Littleton Ballot Question 300"
                "Ballot Issue 4C" -> "Arapahoe County School District #6 Littleton Public Schools Ballot Issue 4C"
                "Ballot Issue 5A" -> "Byers School District 32J Ballot Issue 5A"
                "Ballot Issue 4A" -> "Cherry Creek School District No. 5 Ballot Issue 4A"
                "Ballot Issue 4B" -> "Cherry Creek School District No. 5 Ballot Issue 4B"
                "Ballot Issue 5B" -> "Strasburg School District 31J Ballot Issue 5B"
                "Ballot Issue 6A" -> "Sundance Hills Metro District Ballot Issue 6A"
                "Ballot Issue 6B" -> "Sundance Hills Metro District Ballot Issue 6B"
                else -> name
            }
            "Bent" -> when (name) {
                "County Court Judge" -> "Bent County Court Judge - Vigil"
                else -> name
            }
            "Boulder" -> when (name) {
                "Boulder County Court" -> "Boulder County Court Judge - Martin" // could remove Judge
                "City of Louisville City Council Ward 3 (1-Year Term)" -> "City of Louisville City Council Ward 3" // could remove after (
                else -> name
            }

            "Chaffee" -> when (name) {
                "Sales Tax On Retail Marijuana" -> "Town of Buena Vista Ballot Issue 2A" // could be switched
                "Allow Retail Marijuana Stores" -> "Town of Buena Vista Ballot Question 2B"
                else -> name
            }
            "Clear Creek" -> when (name) {
                "County Court Judge" -> "Clear Creek County Court Judge - Jones"
                else -> name
            }
            "Conejos" -> when (name) {
                "Conejos County Commissoiner District 1" -> "Conejos County Commissioner - District 1"
                "Conejos County Court Judge" -> "Conejos County Court Judge - Cortez"
                else -> name
            }
            "Crowley" -> when (name) {
                "Crowley County Court" -> "Crowley County Court Judge - Medina"
                else -> name
            }
            "Delta" -> when (name) {
                "Delta County Ballot Issue 1A - Public Safety Improvements Sales Tax (Back the Badge)" -> "Delta County Ballot Issue 1A"
                "Town of Cedaredge Ballot Issue 2A - Establishment of retail and medical marijuana stores" -> "Town of Cedaredge Ballot Issue 2A"
                "Town of Cedaredge Ballot Issue 2B - Retail marijuana sales tax" -> "Town of Cedaredge Ballot Issue 2B"
                "Town of Paonia Ballot Issue 2C - Establishment of marijuana stores" -> "Town of Paonia Ballot Issue 2C"
                "Town of Paonia Ballot Issue 2D - Levy of occupational tax on marijuana sales" -> "Town of Paonia Ballot Issue 2D"
                else -> name
            }
            "Denver" -> when (name) {
                "Court of Appeals Tow" -> "Colorado Court of Appeals Judge - Tow"
                "Court of Appeals Welling" -> "Colorado Court of Appeals Judge - Welling"
                "District Judge - 2nd Judicial District Baumann" -> "District Court Judge - 2nd Judicial District - Baumann"
                "District Judge - 2nd Judicial District Egelhoff" -> "District Court Judge - 2nd Judicial District - Egelhoff"
                "District Judge - 2nd Judicial District Elliff" -> "District Court Judge - 2nd Judicial District - Elliff"
                "District Judge - 2nd Judicial District Jones" -> "District Court Judge - 2nd Judicial District - Jones"
                "District Judge - 2nd Judicial District Vallejos" -> "District Court Judge - 2nd Judicial District - Vallejos"
                "District Judge - 2nd Judicial District Leith" -> "District Court Judge - 2nd Judicial District/Probate - Leith"
                "County Judge - Denver Faragher" -> "Denver County Court Judge - Faragher"
                "County Judge - Denver Pallares" -> "Denver County Court Judge - Pallares"
                "County Judge - Denver Rodarte" -> "Denver County Court Judge - Rodarte"
                "County Judge - Denver Rudolph" -> "Denver County Court Judge - Rudolph"
                "County Judge - Denver Schwartz" -> "Denver County Court Judge - Schwartz"
                "County Judge - Denver Simonet" -> "Denver County Court Judge - Simonet"
                "County Judge - Denver Spahn" -> "Denver County Court Judge - Spahn"
                "Amendment B" -> "Amendment B (CONSTITUTIONAL)"
                "Amendment C" -> "Amendment C (CONSTITUTIONAL)"
                "Amendment 76" -> "Amendment 76 (CONSTITUTIONAL)"
                "Amendment 77" -> "Amendment 77 (CONSTITUTIONAL)"
                "Proposition EE" -> "Proposition EE (STATUTORY)"
                "Proposition 113" -> "Proposition 113 (STATUTORY)"
                "Proposition 114" -> "Proposition 114 (STATUTORY)"
                "Proposition 115" -> "Proposition 115 (STATUTORY)"
                "Proposition 116" -> "Proposition 116 (STATUTORY)"
                "Proposition 117" -> "Proposition 117 (STATUTORY)"
                "Proposition 118" -> "Proposition 118 (STATUTORY)"
                "Ballot Measure 2A" -> "City and County of Denver Ballot Measure 2A"
                "Ballot Measure 2B" -> "City and County of Denver Ballot Measure 2B"
                "Ballot Measure 2C" -> "City and County of Denver Ballot Measure 2C"
                "Ballot Measure 2D" -> "City and County of Denver Ballot Measure 2D"
                "Ballot Measure 2E" -> "City and County of Denver Ballot Measure 2E"
                "Ballot Measure 2F" -> "City and County of Denver Ballot Measure 2F"
                "Ballot Measure 2G" -> "City and County of Denver Ballot Measure 2G"
                "Ballot Measure 2H" -> "City and County of Denver Ballot Measure 2H"
                "Ballot Measure 2I" -> "City and County of Denver Ballot Measure 2I"
                "Ballot Measure 2J" -> "City and County of Denver Ballot Measure 2J"
                "Ballot Measure 4A" -> "Denver Public Schools (School District No. 1) Ballot Measure 4A"
                "Ballot Measure 4B" -> "Denver Public Schools (School District No. 1) Ballot Measure 4B"
                else -> name
            }
            "Douglas" -> when (name) {
                "Representative to the United States Congress - District 4" -> "Representative to the 117th United States Congress - District 4"
                "Representative to the United States Congress - District 6" -> "Representative to the 117th United States Congress - District 6"
                "Regent University of Colorado - Congressional District 6" -> "Regent of the University of Colorado - Congressional District 6"
                "Castle Rock Councilmember District 1" -> "Town of Castle Rock Councilmember - District 1"
                "Castle Rock Councilmember District 2" -> "Town of Castle Rock Councilmember - District 2"
                "Castle Rock Councilmember District 4" -> "Town of Castle Rock Councilmember - District 4"
                "Castle Rock Councilmember District 6" -> "Town of Castle Rock Councilmember - District 6"
                "City of Littleton Initiated Ballot Question 300" -> "City of Littleton Ballot Question 300"
                "City of Littleton Referred Ballot Question 3A" -> "City of Littleton Ballot Question 3A"
                else -> name
            }
            "Eagle" -> when (name) {
                "Candidates for Town Council" -> "Town of Avon Town Council"
                "Eagle County Court" -> "Eagle County Court Judge - Olguin-Fresquez"
                "Ballot Issue 1A - Sustaining Existing Levels of County Revenue from Future State Imposed Reductions in Residential Assessed V..." -> "Eagle County Ballot Issue 1A"
                "Ballot Issue 2E - Sustaining Existing Levels of Town of Avon Revenue from Future State Imposed Reductions in Residential Asse..." -> "Town of Avon Ballot Issue 2E"
                "Ballot Question 2F - Avon Home Rule Charter Amendment Regarding Council Compensation." -> "Town of Avon Ballot Question 2F"
                "Ballot Issue 2A" -> "Town of Eagle Ballot Issue 2A"
                "Ballot Issue 2B" -> "Town of Eagle Ballot Issue 2B"
                "Town of Eagle Proposed Downtown Development Authority Ballot Question 2C" -> "Town of Eagle Ballot Question 2C"
                "Town of Gypsum Ballot Issue No. 2D" -> "Town of Gypsum Ballot Issue 2D"
                "Ballot Issue 2G - Sustaining Existing Levels of Town Revenue from Future State Imposed Reductions in Residential Assessed Val..." -> "Town of Vail Ballot Issue 2G"
                "Eagle County School District Ballot Issue 5B:" -> "Eagle County School District Re50J Ballot Issue 5B"
                else -> name
            }
            "El Paso" -> when (name) {
                "El Paso County Court - Findorff" -> "El Paso County Court Judge - Findorff"
                "El Paso County Court - Gerhart" -> "El Paso County Court Judge - Gerhart"
                else -> name
            }
            "Fremont" -> when (name) {
                "Fremont County Board of County Commissioners - District 1" -> "Fremont County Commissioners - District 1"
                "Fremont County Board of County Commissioners - District 3" -> "Fremont County Commissioners - District 3"
                "FREMONT COUNTY - ISSUE 1A - EXTENSION OF 1% SHERIFF'S TAX" -> "Fremont County Ballot Issue 1A"
                "City of Ca�on City - Question 2A - Black Hills Energy Franchise" -> "City of Canon City Ballot Question 2A"
                "DEER MOUNTAIN FIRE PROTECTION DISTRICT - ISSUE 6A - MILL LEVY INCREASE" -> "Deer Mountain Fire Protection District Ballot Issue 6A"
                else -> name
            }
            "Gilpin" -> when (name) {
                "Gilpin County Issue 1A" -> "Gilpin County Ballot Issue 1A"
                "Gilpin County Issue 1B" -> "Gilpin County Ballot Issue 1B"
                "City of Black Hawk Question 2A" -> "City of Black Hawk Ballot Question 2A"
                "City of Central Question 2B" -> "City of Central Ballot Question 2B"
                "Gilpin County RE-1 School District Issue 4A" -> "Gilpin County RE-1 School District Ballot Issue 4A,"
                "Gilpin County Library District Issue 6A" -> "Gilpin County Library District Ballot Issue 6A"
                else -> name
            }
            "Grand" -> when (name) {
                else -> name
            }
            "Jackson" -> when (name) {
                "Jackson County Commissioner Dist 2" -> "Jackson County Commissioner - District 2"
                "Jackson County Commissioner Dist 3" -> "Jackson County Commissioner - District 3"
                "District Court Judge - 8th Judicial District - Villaseñor" -> "District Court Judge - 8th Judicial District - Villasenor"
                else -> name
            }
            "Jefferson" -> when (name) {
                "Jefferson County Court - Burback" -> "Jefferson County Court Judge - Burback"
                "Jefferson County Court - Carpenter" -> "Jefferson County Court Judge - Carpenter"
                "Jefferson County Court - Moore" -> "Jefferson County Court Judge - Moore"
                "Jefferson County Court - Sargent" -> "Jefferson County Court Judge - Sargent"
                "Amendment B - SCR20-001 Property Tax" -> "Amendment B (CONSTITUTIONAL)"
                "Amendment C - HCR20-1001 Bingo Raffles" -> "Amendment C (CONSTITUTIONAL)"
                "Amendment 76 - Citizenship Qualification of Electors" -> "Amendment 76 (CONSTITUTIONAL)"
                "Amendment 77 - Gaming Limits" -> "Amendment 77 (CONSTITUTIONAL)"
                "Proposition EE - HB20-1427 Cigarette Tobacco and Nicotine Products Tax" -> "Proposition EE (STATUTORY)"
                "Proposition 113 - National Popular Vote" -> "Proposition 113 (STATUTORY)"
                "Proposition 114 - Restoration of Grey Wolves" -> "Proposition 114 (STATUTORY)"
                "Proposition 115 - Prohibition on Late-Term Abortions" -> "Proposition 115 (STATUTORY)"
                "Proposition 116 - State Income Tax Rate Reduction" -> "Proposition 116 (STATUTORY)"
                "Proposition 117 - Voter Approval Requirement for Fee-Based Enterprises" -> "Proposition 117 (STATUTORY)"
                "Proposition 118 - Paid Family and Medical Leave Insurance Program" -> "Proposition 118 (STATUTORY)"
                "City of Littleton Referred Ballot Question 3A" -> "City of Littleton Ballot Question 3A"
                "City of Littleton Initiated Ballot Question 300" -> "City of Littleton Ballot Question 300"
                "Lookout Mounatin Water District Ballot Issue 6B" -> "Lookout Mountain Water District Ballot Issue 6B"
                else -> name
            }
            "Kiowa" -> when (name) {
                "2A TOWN OF EADS TABOR QUESTION" -> "Town of Eads Ballot Question 2A,"
                else -> name
            }
            "Lake" -> when (name) {
                "County Court Judge - Lake - Shamis" -> "Lake County Court Judge - Shamis"
                else -> name
            }
            "La Plata" -> when (name) {
                "Durango School District 9-R" -> "Durango School District 9-R Ballot Issue 4A"
                else -> name
            }
            "Larimer" -> when (name) {
                "County Commissioner - District 2" -> "Larimer County Commissioner - District 2"
                "County Commissioner - District 3" -> "Larimer County Commissioner - District 3"
                "County Court Judge - Larimer - Berenato" -> "Larimer County Court Judge - Berenato"
                "County Court Judge - Larimer - Ecton" -> "Larimer County Court Judge - Ecton"
                "County Court Judge - Larimer - Lehman" -> "Larimer County Court Judge - Lehman"
                else -> name
            }
            "Logan" -> when (name) {
                "County Court Judge - Logan - Brammer" -> "Logan County Court Judge - Brammer"
                "Frenchman Groundwater Management District Referred Ballot Issue 7A" -> "Frenchman Groundwater Management District Ballot Issue 7A"
                else -> name
            }
            "Lincoln" -> when (name) {
                "Amendment B" -> "Amendment B (CONSTITUTIONAL)"
                "Amendment C" -> "Amendment C (CONSTITUTIONAL)"
                "Amendment 76" -> "Amendment 76 (CONSTITUTIONAL)"
                "Amendment 77" -> "Amendment 77 (CONSTITUTIONAL)"
                "Proposition EE" -> "Proposition EE (STATUTORY)"
                "Proposition 113" -> "Proposition 113 (STATUTORY)"
                "Proposition 114" -> "Proposition 114 (STATUTORY)"
                "Proposition 115" -> "Proposition 115 (STATUTORY)"
                "Proposition 116" -> "Proposition 116 (STATUTORY)"
                "Proposition 117" -> "Proposition 117 (STATUTORY)"
                "Proposition 118" -> "Proposition 118 (STATUTORY)"
                else -> name
            }
            "Mesa" -> {
                if (name.startsWith("BALLOT ISSUE 6A")) return "Redlands 360 Metropolitan District No. 1 Ballot Issue ${name.substring(13)}"
                if (name.startsWith("BALLOT ISSUE 6B")) return "Redlands 360 Metropolitan District No. 9 Ballot Issue ${name.substring(13)}"
                when (name) {
                    "Mesa County Court - Grattan" -> "Mesa County Court Judge - Grattan"
                    "Mesa County Court - Henderson" -> "Mesa County Court Judge - Henderson"
                    "Ballot Question 6AA Formation of District Redlands 360 Metropolitan District No. 1" -> "Redlands 360 Metropolitan District No. 1 Ballot Question 6AA"
                    "BALLOT ISSUE 6AB" -> "Redlands 360 Metropolitan District No. 1 Ballot Issue 6AB"
                    "BALLOT ISSUE 6AC" -> "unknown" // ditto
                    "BALLOT ISSUE 6AD" -> "unknown"
                    "BALLOT ISSUE 6AE" -> "unknown"
                    "BALLOT ISSUE 6AF" -> "unknown"
                    "BALLOT ISSUE 6AG" -> "unknown"
                    "BALLOT ISSUE 6AH" -> "unknown"
                    "BALLOT ISSUE 6AI" -> "unknown"
                    "BALLOT ISSUE 6AJ" -> "unknown"
                    "BALLOT ISSUE 6AK" -> "unknown"
                    "BALLOT ISSUE 6AL" -> "unknown"
                    "BALLOT ISSUE 6AM" -> "unknown"
                    "BALLOT ISSUE 6AN" -> "unknown"
                    "BALLOT ISSUE 6AO" -> "unknown"
                    "BALLOT ISSUE 6AP" -> "unknown"
                    "BALLOT ISSUE 6AQ" -> "unknown"
                    "BALLOT QUESTION 6BA" -> "Redlands 360 Metropolitan District No. 9 Ballot Question 6BA"
                    "BALLOT ISSUE 6BB" -> "Redlands 360 Metropolitan District No. 9 Ballot Issue 6BB"
                    "BALLOT ISSUE 6BC" -> "unknown" // ditto
                    "BALLOT ISSUE 6BD" -> "unknown"
                    "BALLOT ISSUE 6BE" -> "unknown"
                    "BALLOT ISSUE 6BF" -> "unknown"
                    "BALLOT ISSUE 6BG" -> "unknown"
                    "BALLOT ISSUE 6BH" -> "unknown"
                    "BALLOT ISSUE 6BI" -> "unknown"
                    "BALLOT ISSUE 6BJ" -> "unknown"
                    "BALLOT ISSUE 6BK" -> "unknown"
                    "BALLOT ISSUE 6BL" -> "unknown"
                    "BALLOT ISSUE 6BM" -> "unknown"
                    "BALLOT ISSUE 6BN" -> "unknown"
                    "BALLOT ISSUE 6BO" -> "unknown"
                    "BALLOT ISSUE 6BP" -> "unknown"
                    "BALLOT ISSUE 6BQ" -> "unknown"
                    else -> name
                }
            }
            "Mineral" -> when (name) {
                "Mineral County Court" -> "Mineral County Court Judge - Acheson"
                else -> name
            }
            "Moffat" -> when (name) {
                "Ciyt of Craig Ballot Question 2D" -> "City of Craig Ballot Question 2D"
                else -> name
            }
            "Montrose" -> when (name) {
                "County Commissioner - District 1" -> "Montrose County Commissioner - District 1"
                "County Commissioner - District 3" -> "Montrose County Commissioner - District 3"
                "County Surveyor" -> "Montrose County Surveyor"
                else -> name
            }
            "Morgan" -> when (name) {
                "Brush RE-2J School District BALLOT ISSUE" -> "Brush RE-2J School District Ballot Issue 3D"
                else -> name
            }
            "Otero" -> when (name) {
                "Ballot Question 2A" -> "City of La Junta Ballot Question 2A"
                "General Obligation Bonds" -> "Rocky Ford School District R-2 Ballot Question 4A" // probably
                else -> name
            }
            "Ouray" -> when (name) {
                "Ouray County Court Judge" -> "Ouray County Court Judge - Martin"
                else -> name
            }
            "Park" -> when (name) {
                "Amendment B" -> "Amendment B (CONSTITUTIONAL)"
                "Amendment C" -> "Amendment C (CONSTITUTIONAL)"
                "Amendment 76" -> "Amendment 76 (CONSTITUTIONAL)"
                "Amendment 77" -> "Amendment 77 (CONSTITUTIONAL)"
                "Proposition EE" -> "Proposition EE (STATUTORY)"
                "Proposition 113" -> "Proposition 113 (STATUTORY)"
                "Proposition 114" -> "Proposition 114 (STATUTORY)"
                "Proposition 115" -> "Proposition 115 (STATUTORY)"
                "Proposition 116" -> "Proposition 116 (STATUTORY)"
                "Proposition 117" -> "Proposition 117 (STATUTORY)"
                "Proposition 118" -> "Proposition 118 (STATUTORY)"
                else -> name
            }
            "Phillips" -> when (name) {
                "Mayor, Town of Holyoke" -> "City of Holyoke Mayor,"
                "City Council Member, Town of Holyoke" -> "City of Holyoke Council Member"
                "Phillips County Court - Killin" -> "Phillips County Court Judge - Killin"
                "Frenchman Groundwater Management District Referred Ballot Issue 7A" -> "Frenchman Groundwater Management District Ballot Issue 7A"
                else -> name
            }
            "Pitkin" -> when (name) {
                "Mayor" -> "Town of Snowmass Village Mayor"
                "Town Council" -> "Town of Snowmass Village Town Council"
                "City of Aspen - Extension of Existing 0.3% Sales Tax for Educational Purposes Ballot Issue 2B:" -> "City of Aspen Ballot Issue 2B"
                else -> name
            }
            "Pueblo" -> when (name) {
                "Extension of the One-Half Cent Sales Tax for Economic Development Question 2A" -> "City of Pueblo Ballot Issue 2A"
                "Pueblo County School District 70 Issue 4A" -> "Pueblo County School District 70 Ballot Issue 4A"
                else -> name
            }
            "Rio Blanco" -> when (name) {
                "Term limits for Assessor Ballot Issue 1A" -> "Rio Blanco County Ballot Issue 1A"
                "Term limits for Clerk and Recorder Ballot Issue 1B" -> "Rio Blanco County Ballot Issue 1B"
                "Term limits for County Commissioners Ballot Issue 1C" -> "Rio Blanco County Ballot Issue 1C"
                "Term limits for Coroner Ballot Issue 1D" -> "Rio Blanco County Ballot Issue 1D"
                "Term limits for Sheriff Ballot Issue 1E" -> "Rio Blanco County Ballot Issue 1E"
                "Term limits for Treasurer Ballot Issue 1F" -> "Rio Blanco County Ballot Issue 1F"
                "South Routt School District Ballot Issue 5A" -> "RE3 South Routt School District Ballot Issue 5A"
                else -> name
            }
            "Routt" -> when (name) {
                "Eagle County School District Ballot Issue 5B" -> "Eagle County School District Re50J Ballot Issue 5B"
                "South Routt School District Ballot Issue 5A" -> "RE3 South Routt School District Ballot Issue 5A"
                else -> name
            }
            "San Miguel" -> when (name) {
                "Amendment B" -> "Amendment B (CONSTITUTIONAL)"
                "Amendment C" -> "Amendment C (CONSTITUTIONAL)"
                "Amendment 76" -> "Amendment 76 (CONSTITUTIONAL)"
                "Amendment 77" -> "Amendment 77 (CONSTITUTIONAL)"
                "Proposition EE" -> "Proposition EE (STATUTORY)"
                "Proposition 113" -> "Proposition 113 (STATUTORY)"
                "Proposition 114" -> "Proposition 114 (STATUTORY)"
                "Proposition 115" -> "Proposition 115 (STATUTORY)"
                "Proposition 116" -> "Proposition 116 (STATUTORY)"
                "Proposition 117" -> "Proposition 117 (STATUTORY)"
                "Proposition 118" -> "Proposition 118 (STATUTORY)"
                else -> name
            }
            "Sedgwick" -> when (name) {
                "Sedgwick County Court" -> "Sedgwick County Court Judge - Dolezal"
                "Amendment B" -> "Amendment B (CONSTITUTIONAL)"
                "Amendment C" -> "Amendment C (CONSTITUTIONAL)"
                "Amendment 76" -> "Amendment 76 (CONSTITUTIONAL)"
                "Amendment 77" -> "Amendment 77 (CONSTITUTIONAL)"
                "Proposition EE" -> "Proposition EE (STATUTORY)"
                "Proposition 113" -> "Proposition 113 (STATUTORY)"
                "Proposition 114" -> "Proposition 114 (STATUTORY)"
                "Proposition 115" -> "Proposition 115 (STATUTORY)"
                "Proposition 116" -> "Proposition 116 (STATUTORY)"
                "Proposition 117" -> "Proposition 117 (STATUTORY)"
                "Proposition 118" -> "Proposition 118 (STATUTORY)"
                else -> name
            }
            "Summit" -> when (name) {
                "Summit County Court - Casias" -> "Summit County Court Judge - Casias"
                else -> name
            }
            "Teller" -> when (name) {
                "City Councilmember" -> "City of Woodland Park Councilmember"
                "Northeast Teller County Fire Protection District 7A" -> "Northeast Teller County Fire Protection District Ballot Issue 7A"
                else -> name
            }
            "Washington" -> when (name) {
                "Brush RE-2J School District BALLOT ISSUE" -> "Brush RE-2J School District Ballot Issue 3D"
                else -> name
            }
            "Weld" -> when (name) {
                else -> name
            }
            "Yuma" -> when (name) {
                "County Court Judge - Yuma" -> "Yuma County Court Judge - Jones"
                "BALLOT ISSUE 5A:  GENERAL OBLIGATION BONDS" -> "Holyoke School District RE-1J Ballot Issue 5A"
                else -> name
            }
            else -> name
        }
    }
    override fun candidateNameCleanup(county: String, name: String): String {
        when (name) {
            "Colorado Supreme Court Justice - Hart" -> return "Justice of the Colorado Supreme Court - Hart"
            "Colorado Supreme Court Justice - Samour" -> return "Justice of the Colorado Supreme Court - Samour"
            "Supreme Court Hart" -> return "Justice of the Colorado Supreme Court - Hart"
            "Supreme Court Samour" -> return "Justice of the Colorado Supreme Court - Samour"
        }

        return when (county) {
            "Adams" -> when (name) {
                "Martín Mendez" -> "Martin Mendez"
                else -> name
            }
            "Arapahoe" -> when (name) {
                "Lisa Escárcega" -> "Lisa Escarcega"
                else -> name
            }
            "Cheyenne" -> when (name) {
                "Todd Cella / Timothy Byran Cella" -> "Todd Cella / Timothy Bryan Cella"
                "Danny Skelley" -> "Danny Skelly"
                else -> name
            }
            "Denver" -> when (name) {
                "Lisa Escárcega" -> "Lisa Escarcega"
                else -> name
            }
            "Douglas" -> when (name) {
                "Princess Khadijah Maryam Jacob-Fambro / Khadijah Maryam Jaco" -> "Princess Khadijah Maryam Jacob-Fambro / Khadijah Maryam Jacob Sr."
                // "Jonathan" -> "Jonathan (JB) Bollefer"
                else -> name
            }
            "Elbert" -> when (name) {
                // "Theresa" -> "Theresa (Terry) Howard"
                else -> name
            }
            "Fremont" -> when (name) {
                // "Stephen" -> "Stephen (Steve) Harrison"
                else -> name
            }
            "Grand" -> when (name) {
                // "Mary" -> "Mary (Cathy) Tindle"
                else -> name
            }
            "Jefferson" -> when (name) {
                "Lisa Escárcega" -> "Lisa Escarcega"
                else -> name
            }
            "San Miguel" -> when (name) {
                "C.\"Kieffer\" Parrino" -> "Chris \"Kieffer\" Parrino"
                else -> name
            }
            "Sedgwick" -> when (name) {
                "Todd Cella / Bryan Cella" -> "Todd Cella / Timothy Bryan Cella"
                else -> name
            }
            "Teller" -> when (name) {
                "Kasey Wells" -> "Kasey Wells / Rachel Wells"
                "Todd Cella" -> "Todd Cella / Timothy Bryan Cella"
                "Tom Hoefling" -> "Tom Hoefling / Andy Prior"
                else -> name
            }
            else -> name
        }
    }

    fun candidateNameCleanup(candName: String): String {
        return when (candName) {
            "Jordan 'Cancer' Scott / Jennifer Tepool" -> "Jordan \"Cancer\" Scott / Jennifer Tepool"
            "Roque 'Rocky' De La Fuente / Darcy G. Richardson" -> "Roque \"Rocky\" De La Fuente / Darcy G. Richardson"
            "Jo Jorgensen / Jeremy 'Spike' Cohen" -> "Jo Jorgensen / Jeremy \"Spike\" Cohen"
            "Stephan 'Seku' Evans" -> "Stephan \"Seku\" Evans"
            "Andrew J. O'Connor" -> "Andrew J. OConnor"
            "James T Crowder" -> "James T. Crowder"
            "James E. 'Jed' Gilman" -> "James E. \"Jed\" Gilman"
            "YES" -> "Yes"
            "NO" -> "No"
            "YES/FOR" -> "Yes/For"
            "NO/AGAINST" -> "No/Against"
            else -> candName
        }
    }

    companion object {
        private val general2020 = "$auditcenter/2020/general"
    }
}
