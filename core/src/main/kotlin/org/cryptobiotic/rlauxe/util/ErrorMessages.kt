package org.cryptobiotic.rlauxe.util

import com.github.michaelbull.result.Err

class ErrorMessages(val id: String, private val level: Int = 1) {
    private val messages = ArrayList<String>()
    private val nested = ArrayList<ErrorMessages>()
    private val indent = Indent(level)

    fun add(mess: String) : Err<ErrorMessages> {
        messages.add(mess)
        return Err(this)
    }

    fun addNull(mess: String) : Any? {
        messages.add(mess)
        return null
    }

    fun addNested(errs: ErrorMessages) {
        nested.add(errs)
    }

    fun nested(id: String): ErrorMessages {
        val mess = ErrorMessages(id, level + 1)
        nested.add(mess)
        return mess
    }

    fun incr(): ErrorMessages {
        val result = ErrorMessages(id, level + 1)
        messages.forEach { result.add(it) }
        nested.forEach { result.addNested(it.incr()) }
        return result
    }

    override fun toString(): String {
        if (!hasErrors()) {
            return "$id all OK"
        }
        return buildString {
            append("$id has errors:")
            messages.forEach { append("\n$indent$it") }
            nested.forEach {
                if (it.hasErrors()) { append("\n$indent$it") }
            }
        }
    }

    fun hasErrors(): Boolean {
        if (messages.isNotEmpty()) {
            return true
        }
        if (nested.isEmpty()) {
            return false
        }
        return nested.map { it.hasErrors()}.reduce {a, b -> a or b }
    }

    fun contains(subs: String): Boolean {
        var result = false
        messages.forEach { if (it.contains(subs)) result = true}
        if (result) return true
        nested.forEach { if (it.contains(subs)) result = true }
        return result
    }
}

fun mergeErrorMessages(top: String, errss: List<ErrorMessages>) : ErrorMessages {
    val result = ErrorMessages(top)
    errss.forEach { result.addNested(it.incr()) }
    return result
}

private const val nspaces : Int = 2
class Indent(val level: Int) {
    private val indent = makeBlanks(level * nspaces)

    override fun toString(): String {
        return indent
    }

    fun incr() = Indent(level+1)

    private fun makeBlanks(len: Int) : String {
        val blanks = StringBuilder(len)
        for (i in 0 until len) {
            blanks.append(" ")
        }
        return blanks.toString()
    }
}

fun Double.sigfig(minSigfigs: Int = 4): String {
    val df = "%.${minSigfigs}G".format(this)
    return if (df.startsWith("0.")) df.substring(1) else df
}
