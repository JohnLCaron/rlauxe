package org.cryptobiotic.rlauxe.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required

object CreateCaseData {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = ArgParser("CreateCaseData")
        val output by parser.option(
            ArgType.String,
            shortName = "topdir",
            description = "output directory"
        ).required()
        val case by parser.option(
            ArgType.String,
            shortName = "case",
            description = "belgium boulder corla sf"
        ).required()

        try {
            parser.parse(args)
            println("CreateCaseData for $case topdir = $output")
            when (case) {
                "belgium" -> createBelgiumTestCase(output)
            }
        } catch (t: Throwable) {
            println(t.message)
        }
    }

    fun createBelgiumTestCase(output: String) {
        org.cryptobiotic.rlauxe.belgium.createAllBelgiumElections(output)
    }
}

/*
java -classpath cases/build/libs/cases-0.9.5.3-uber.jar org.cryptobiotic.rlauxe.cli.CreateCaseData \
    -case belgium -topdir "/home/stormy/datadrive/rla/cases/belgium/belgium24"
 */
