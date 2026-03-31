package org.cryptobiotic.rlauxe.irv

class RepeatedTests {

    // @Test
    fun repeatTest() {
        repeat(1111) {
            val test = TestSimulateOneAuditRaire()
            test.testPoolMargins()
        }
    }
}