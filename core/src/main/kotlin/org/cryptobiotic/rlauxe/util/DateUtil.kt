package org.cryptobiotic.rlauxe.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun today(): String {
    val today = LocalDate.now()
    // Custom pattern (Example: "Friday, July 03, 2026")
    val customFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy", Locale.getDefault())
    return today.format(customFormatter)

    // Built-in localized format (Example: "Jul 3, 2026")
    //val localizedFormatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
    //return today.format(localizedFormatter)
}