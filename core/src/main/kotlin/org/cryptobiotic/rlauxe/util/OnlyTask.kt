package org.cryptobiotic.rlauxe.util

data class OnlyTask(val contestId: Int, val taskName: String) {
    companion object {
        fun parse(taskName: String?): OnlyTask? {
            if (taskName == null) return null
            val tokens = taskName.split("-")
            return OnlyTask(tokens[0].toInt(), taskName)
        }
    }
}