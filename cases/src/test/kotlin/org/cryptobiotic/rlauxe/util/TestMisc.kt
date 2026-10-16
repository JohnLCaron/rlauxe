package org.cryptobiotic.rlauxe.util

import org.cryptobiotic.rlauxe.corlaInput.removeParen
import kotlin.test.Test

class TestMisc {

    @Test
    fun testRemoveParen() {
        val s = "sdkajhsdkjh999 (last one)"
        println("'$s' -> '${removeParen(s)}'")
        val s2 = "trailing blank (last one) "
        println("'$s2' -> '${removeParen(s2)}'")
        val s3 = "Thompson R2-J School District Board of Education Director District A (4 Year Term)"
        println("'$s3' -> '${removeParen(s3)}'")
    }
}