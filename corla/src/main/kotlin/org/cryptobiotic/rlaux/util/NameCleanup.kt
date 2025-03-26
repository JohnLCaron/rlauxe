package org.cryptobiotic.rlaux.util

fun nameCleanup(name: String): String {
    var working = name
    if (working.contains("''")) working = working.replace("''", "'")
    if (working.contains("\"")) working = working.replace("\"", "'")
    if (working.contains(" -")) working = working.replace(" -", "")
    if (working.contains("County Court Judge Cheyenne")) working = working.replace("County Court Judge Cheyenne", "Cheyenne County Court Judge")
    if (working.contains("County Court Judge Denver")) working = working.replace("County Court Judge Denver", "Denver County Court Judge")
    if (working.contains("County Court Judge Jefferson")) working = working.replace("County Court Judge Jefferson", "Jefferson County Court Judge")
    if (working.contains("County Court Judge Gunnison")) working = working.replace("County Court Judge Gunnison", "Gunnison County Court Judge")
    if (working.contains("County Court Judge Routt")) working = working.replace("County Court Judge Routt", "Routt County Court Judge")
    if (working.contains("Jefferson County Court- ")) working = working.replace("Jefferson County Court- ", "Jefferson County Court ")
    if (working.contains("Jefferson County Court-")) working = working.replace("Jefferson County Court-", "Jefferson County Court ")
    if (working.equals("Cheyenne County Court Judge")) working = "Cheyenne County Court Judge Eiring"
    if (working.equals("Gunnison County Court Judge")) working = "Gunnison County Court Judge Burgemeister"
    if (working.equals("Mesa County Court Judge Grattan III")) working = "Mesa County Court Judge Grattan"
    if (working.equals("Routt County Court Judge")) working = "Routt County Court Judge Wilson"
    if (working.equals("Seth Ryan")) working = "Anna Cooling"
    return working.trim()
}