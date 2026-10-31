package org.cryptobiotic.rlauxe.corlaInput

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TestStatewideInputs {

    @Test
    fun read2026p() {
        val target = Statewide2026Primary()
        assertEquals(63, target.counties().size)
        assertEquals("Broomfield", target.counties().last())
    }
}