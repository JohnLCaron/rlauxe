package org.cryptobiotic.rlauxe.corla

fun contestNameCleanup(name: String): String {
    var working = name
    if (working.contains(" -")) working = working.replace(" -", "")
    if (working.contains("County Court Judge Cheyenne")) working = working.replace("County Court Judge Cheyenne", "Cheyenne County Court Judge")
    if (working.contains("County Court Judge Denver")) working = working.replace("County Court Judge Denver", "Denver County Court Judge")
    if (working.contains("County Court Judge Jefferson")) working = working.replace("County Court Judge Jefferson", "Jefferson County Court Judge")
    if (working.contains("County Court Judge Gunnison")) working = working.replace("County Court Judge Gunnison", "Gunnison County Court Judge")
    if (working.contains("County Court Judge Routt")) working = working.replace("County Court Judge Routt", "Routt County Court Judge")
    if (working.contains("Jefferson County Court- ")) working = working.replace("Jefferson County Court- ", "Jefferson County Court ")
    if (working.contains("Jefferson County Court-")) working = working.replace("Jefferson County Court-", "Jefferson County Court ")
    if (working.equals("BRUSH RURAL FIRE PROTECTION DISTRICT BALLOT ISSUE 7A")) working = "Brush Rural Fire Protection District Ballot Issue 7A"
    if (working.equals("Cheyenne County Court Judge")) working = "Cheyenne County Court Judge Eiring"
    if (working.equals("Gunnison County Court Judge")) working = "Gunnison County Court Judge Burgemeister"
    if (working.equals("Mesa County Court Judge Grattan III")) working = "Mesa County Court Judge Grattan"
    if (working.equals("Routt County Court Judge")) working = "Routt County Court Judge Wilson"
    if (working.equals("City of Aurora Question 3A")) working = "City of Aurora Ballot Question 3A"
    if (working.equals("Byers School District No. 32J Ballot Issue 5C")) working = "Byers School District 32J Ballot Issue 5C"
    if (working.equals("Holyoke School District RE-1J Ballot Issue 5K Bonds")) working = "Holyoke School District RE-1J Ballot Issue 5K"
    if (working.equals("Montrose School District RE-1J Ballot Issue 5A")) working = "Montrose County School District RE-1J Ballot Issue 5A"
    if (working.equals("Norwood School District R-2J Issue 5B")) working = "Norwood School District R-2J Ballot Issue 5B"
    if (working.equals("Weld County School District RE-8 Ballot Issue 5G Override")) working = "Weld County School District RE-8 Ballot Issue 5G"
    if (working.equals("Weld County School District RE-8 Ballot Issue 5H Bonds")) working = "Weld County School District RE-8 Ballot Issue 5H"
    if (working.equals("Weld County School District RE-10J Ballot Issue 5D Bonds")) working = "Weld County School District RE-10J Ballot Issue 5D"
    if (working.equals("Weld County School District RE-3J Ballot Issue 5F Override")) working = "Weld County School District RE-3J Ballot Issue 5F"
    if (working.equals("Weld County School District No. RE-9 Ballot Issue 4C Bonds")) working = "Weld County School District No. RE-9 Ballot Issue 4C"
    if (working.equals("Weld County School District No. RE-7 Ballot Issue 4B Bonds")) working = "Weld County School District No. RE-7 Ballot Issue 4B"
    if (working.equals("Weld County School District No. RE-7 Ballot Issue 4A Mill Levy Override")) working = "Weld County School District No. RE-7 Ballot Issue 4A"
    return working.trim()
}

// Weld County School District No. RE-7 Ballot Issue 4A Mill Levy Override,opportunistic_benefits,in_progress,1,182397,2729,"""Yes/For""",731,0.03000000,0,0,0,0,0,0,0,1.03905000,0,1819,1819
//Weld County School District No. RE-7 Ballot Issue 4B Bonds,opportunistic_benefits,in_progress,1,182397,2729,"""Yes/For""",720,0.03000000,0,0,0,0,0,0,0,1.03905000,0,1847,1847

fun candidateNameCleanup(name: String): String {
    var working = name
    if (working.contains("''")) working = working.replace("''", "'")
    if (working.contains("\"")) working = working.replace("\"", "'")
    if (working.equals("Seth Ryan")) working = "Anna Cooling"
    return working.trim()
}

fun mutatisMutandi(choiceName: String): String {
    return when (choiceName) {
        "Randall Terry / Stephen E. Broden" -> "Randall Terry / Stephen E Broden"
        "Claudia De la Cruz / Karina García" -> "Claudia De la Cruz / Karina Garcia"
        "Colorado Supreme Court Justice Márquez" -> "Colorado Supreme Court Justice Marquez"
        "Colorado Court of Appeals Judge Román" -> "Colorado Court of Appeals Judge Roman"
        "Daniel Campaña" -> "Daniel Campana"
        "Yes/For" -> "Yes"
        "No/Against" -> "No"
        "Yes" -> "Yes/For"
        "No" -> "No/Against"
        else -> {
            if (choiceName.contains("Judge ")) choiceName.replace("Judge ", "")
            else {
                // println("HEY $choiceName")
                choiceName
            }
        }
    }
}