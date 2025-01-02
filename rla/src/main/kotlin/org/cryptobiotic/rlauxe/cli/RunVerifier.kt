package org.cryptobiotic.rlauxe.cli

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.rlauxe.util.Publisher
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.verifier.Verifier
import kotlin.system.exitProcess

/** Run election record verification CLI. */
class RunVerifier {

    companion object {
//         private val logger = KotlinLogging.logger("RunVerifier")

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunVerifier")
            val inputDir by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Directory containing input election record"
            ).required()
            val nthreads by parser.option(
                ArgType.Int,
                shortName = "nthreads",
                description = "Number of parallel threads to use"
            ).default(11)
            val showTime by parser.option(
                ArgType.Boolean,
                shortName = "time",
                description = "Show timing"
            ).default(false)
            val noexit by parser.option(
                ArgType.Boolean,
                shortName = "noexit",
                description = "Dont call System.exit"
            ).default(false)

            parser.parse(args)
            try {
                val retval = runVerifier(inputDir, nthreads, showTime)
                if (!noexit && retval != 0) exitProcess(retval)
            } catch (t: Throwable) {
                if (!noexit) exitProcess(-1)
            }
        }

        fun runVerifier(inputDir: String, nthreads: Int, showTime: Boolean = false): Int {
            val publisher = Publisher(inputDir)
            val verifier = Verifier(publisher)
            val allOk = verifier.verify()
            return if (allOk) 0 else 1
        }
    }
}
