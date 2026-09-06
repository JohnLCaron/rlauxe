package org.cryptobiotic.rlauxe.util

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.FileAppender
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// Create the appender: requires LoggerContext, Encoder, and File setting
object Logging {
    val pattern = "%d{yyyy-MM-dd'T'HH:mm:ss} %-5level %logger{36}: %msg%n"

    fun addFileAppender(loggerName: String, filePath: String) {
        val lc = LoggerFactory.getILoggerFactory() as LoggerContext?

        val ple = PatternLayoutEncoder()
        ple.setContext(lc)
        ple.setPattern(pattern)
        ple.start()

        val fileAppender: FileAppender<ILoggingEvent?> = FileAppender()
        fileAppender.setContext(lc)
        fileAppender.setName(loggerName)
        fileAppender.setFile(filePath)
        fileAppender.setEncoder(ple)
        fileAppender.start()

        // Attach to root logger
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger).addAppender(fileAppender)
    }

}
