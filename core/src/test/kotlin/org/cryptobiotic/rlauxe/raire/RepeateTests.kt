package org.cryptobiotic.rlauxe.raire

import kotlin.test.Test

class RepeateTests {

    // @Test
    fun repeatTest() {
        repeat(1111) {
            val test = TestSimulateOneAuditRaire()
            test.testPoolMargins()
        }
    }
}