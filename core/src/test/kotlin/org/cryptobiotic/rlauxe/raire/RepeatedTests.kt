package org.cryptobiotic.rlauxe.raire

class RepeatedTests {

    // @Test
    fun repeatTest() {
        repeat(1111) {
            val test = TestSimulateOneAuditRaire()
            test.testPoolMargins()
        }
    }
}