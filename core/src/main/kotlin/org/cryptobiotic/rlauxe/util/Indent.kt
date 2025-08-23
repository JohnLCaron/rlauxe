package org.cryptobiotic.rlauxe.util

private const val nspaces : Int = 2
class Indent(val level: Int) {
    private val indent = makeBlanks(level * nspaces)

    override fun toString(): String {
        return indent
    }

    fun incr() = Indent(level+1)
    fun decr() = Indent(level-1)

    private fun makeBlanks(len: Int) : String {
        val blanks = StringBuilder(len)
        for (i in 0 until len) {
            blanks.append(" ")
        }
        return blanks.toString()
    }
}