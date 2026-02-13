package org.cryptobiotic.rlauxe.util

import kotlin.test.*

class TestErrorMessages {

    @Test
    fun basics() {
        val errs =  ErrorMessages("test")
        assertEquals("test all OK", errs.toString())
        errs.add("what?")
        assertEquals("test has errors:\n  what?", errs.toString())

        val err = errs.add("add")
        assertEquals(errs, err.component2())

        assertNull(errs.addNull("add"))

        assertTrue(errs.contains("add"))
    }

    @Test
    fun nested() {
        val errs =  ErrorMessages("test")
        errs.add("what?")

        val nested = errs.nested("nest")
        assertEquals("test has errors:\n  what?", errs.toString())

        nested.add("who?")
        assertEquals("nest has errors:\n    who?", nested.toString())

        val expect = """test has errors:
  what?
  nest has errors:
    who?"""
        assertEquals(expect, errs.toString())
        assertTrue(errs.contains("who"))
        assertFalse(errs.contains("when"))
    }

    @Test
    fun merge() {
        val errs1 =  ErrorMessages("errs1")
        errs1.add("what?")
        errs1.add("when?")

        val errs2 =  ErrorMessages("errs2")
        errs2.add("how?")

        val merge = mergeErrorMessages("top", listOf(errs1, errs2))
        val expected = """top has errors:
  errs1 has errors:
    what?
    when?
  errs2 has errors:
    how?"""
        assertEquals(expected, merge.toString())
    }
}