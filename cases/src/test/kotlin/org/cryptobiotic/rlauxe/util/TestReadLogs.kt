package org.cryptobiotic.rlauxe.util

import java.io.BufferedReader
import java.io.File
import kotlin.test.Test

class TestReadLogs {
    val levels = setOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR")

    @Test
    fun testReadLogs() {
        val logsFile = "/home/stormy/datadrive/rla/cases/corla/corla2026/primary/logs.log"
        val reader: BufferedReader = File(logsFile).bufferedReader()
        reader.readLine() // skip header line

        while (true) {
            val line = reader.readLine()
            if (line == null) break

            var lastBean: LogBean? = null
            val logsBeans = mutableListOf<LogBean>()
            while (true) {
                val line = reader.readLine()
                if (line == null) break

                if (isContinuation(line)) {
                    if (lastBean != null) lastBean.addContinuation(line)
                } else {
                    lastBean = LogBean(line)
                    logsBeans.add(lastBean)
                }
            }
        }
        reader.close()
    }


    // is this a msg continuation line ??
    fun isContinuation(line: String): Boolean {
        val tokens = line.split(" ".toRegex()).filter{ it.isNotEmpty() }
        if (tokens.size < 2) return true
        val level = tokens[1].trim()
        return !levels.contains(level)
    }

    class LogBean(logLine: String) {
        var date: String = ""
        var level: String = ""
        var loggerName: String = ""
        var msg: String = ""

        init {
            // 2026-08-26T09:12:39 WARN  CountyPoolsSimCvrs: makeCardPoolsFromCountyStyles has (contestNc-sum) 6 > 5
            val tokens = logLine.split(" ".toRegex()).filter{ it.isNotEmpty() }
            if (tokens.size > 0) date = tokens[0]
            if (tokens.size > 1) level = tokens[1]
            if (tokens.size > 2) loggerName = tokens[2]
            if (tokens.size > 3) {
                val remaining = logLine.indexOf(loggerName) + loggerName.length + 1
                msg = logLine.substring(remaining).trim()
            }
        }

        fun addContinuation(contMsg: String) {
            msg += "\n"
            msg += contMsg
        }

        fun show() = buildString {
            appendLine("date = $date")
            appendLine("level = $level")
            appendLine("loggerName = $loggerName")
            appendLine("msg = $msg")
        }
    }

}