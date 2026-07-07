package org.cryptobiotic.rlauxe.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun datetime(): String {
    val today = LocalDateTime.now()
    // Custom pattern (Example: "Friday, July 03, 2026")
    val customFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy", Locale.getDefault())
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-DD HH:mm:ss")
    return today.format(formatter)

    // Built-in localized format (Example: "Jul 3, 2026")
    //val localizedFormatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
    //return today.format(localizedFormatter)
}