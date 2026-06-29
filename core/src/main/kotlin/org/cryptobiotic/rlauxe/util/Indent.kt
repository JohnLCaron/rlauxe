package org.cryptobiotic.rlauxe.util

class Indent(val level: Int, val nspaces: Int = 2) {
    private val indent = makeBlanks(level * nspaces)

    override fun toString(): String {
        return indent
    }

    fun incr() = Indent(level+1, nspaces)
    fun decr() = Indent(level-1, nspaces)

    private fun makeBlanks(len: Int) : String {
        val blanks = StringBuilder(len)
        for (i in 0 until len) {
            blanks.append(" ")
        }
        return blanks.toString()
    }
}