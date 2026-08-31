package org.cryptobiotic.rlauxe.cvr

import kotlin.test.Test

class TestCvrExportRedaction {
    val show = false

    @Test
    fun testBoulder23() {
        lookForRedactions("src/test/data/Boulder2023/Redacted-2023Coordinated-CVR.csv")
    }

    @Test
    fun testBoulder24() {
        lookForRedactions("src/test/data/Boulder2024/2024-Boulder-County-General-Recount-Redacted-Cast-Vote-Record.csv")
    }

    @Test
    fun testBoulder24redactedOnly() {
        lookForRedactions("src/test/data/Boulder2024/redacted.csv")
    }

    @Test
    fun testBoulder24zip() {
        lookForRedactions("/resources/data/cases/boulder2024/2024-Boulder-County-General-Redacted-Cast-Vote-Record.zip")
    }

    @Test
    fun testBoulder25() {
        lookForRedactions("src/test/data/Boulder2025/Redacted-CVR-PUBLIC.csv")
    }

    @Test
    fun testBoulder26() {
        lookForRedactions("/resources/data/cases/boulder26p/2026P-Redacted-CVR-Public.csv")
    }
}