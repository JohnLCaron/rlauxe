package org.cryptobiotic.rlauxe.corlacvr

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required

// ---------------------------------------------------------------------------
// CLI entry point
// ---------------------------------------------------------------------------

/* (my notes)
Clikt: The undisputed industry standard for Kotlin CLI development.
    It features a highly extensible design, supports subcommands natively, offers great type safety via property delegates, and fully supports Kotlin Multiplatform (KMP).
Mordant: Built by the same author as Clikt, this library is specifically designed for styling text, handling ANSI escape codes, colors,
    and formatting terminal output nicely in KMP projects.
Kotter: An excellent choice if you want to build a fully interactive, dynamic terminal user interface (TUI) with support for rendering complex live UI components.
 */
enum class Mode { check, redact }
const val MIN_BALLOTS_DEFAULT = 10
const val VERSION = "0.3"

object AnonymizeCvrsCli {

    @JvmStatic
    fun main(args: Array<String>) {

        val parser = ArgParser("AnonymizeCvr")
        val mode by parser.option(
            ArgType.Choice<Mode> { it.name.lowercase() },
            shortName = "check",
            description = "Check mode: report whether redaction is needed without writing output."
        ).default(Mode.check)

        val input by parser.option(
            ArgType.String,
            shortName = "input",
            description = "Path to the input CVR file (CSV format)."
        ).required()

        val output by parser.option(
            ArgType.String,
            shortName = "output",
            description = "Path for the redacted output CVR file. Required in redact mode; not needed in --check mode."
        )

        val min_ballots by parser.option(
            ArgType.Int,
            shortName = "min-ballots",
            description = "Minimum ballots required per style or precinct."
        ).default(MIN_BALLOTS_DEFAULT)

        val redact_on_precinct by parser.option(
            ArgType.Boolean,
            shortName = "redact-on-precinct",
            description = """Treat precincts with fewer than --min-ballots ballots as rare 
            and aggregate them, instead of simply blanking the PrecinctPortion column.
        """.trimIndent()
        ).default(false)

        val no_contest_balancing by parser.option(
            ArgType.Boolean,
            shortName = "no-contest-balancing",
            description = """ Do not do contest balancing. Redact only the rare-style ballots 
            without borrowing from common styles to satisfy minimums or balance near-unanimous contests.
        """.trimIndent()
        ).default(false)

        val stylecol by parser.option(
            ArgType.Int,
            shortName = "stylecol",
            description = "Column index (0-based) of the named_style field, if present."
        )

        val redacted_list_filename by parser.option(
            ArgType.String,
            shortName = "redacted-list",
            description = """Write the ImprintedId of every redacted ballot to FILENAME,
            one per line.  Useful for identifying ballot images that also need redaction.
            """.trimIndent()
        )

        val version by parser.option(
            ArgType.Boolean,
            shortName = "version",
            description = "Show version."
        )

        val helps by parser.option(
            ArgType.Boolean,
            shortName = "description",
            description = """ Anonymize Cast Vote Records per Colorado C.R.S. 24-72-205.5. 
            Ballot styles with fewer than --min-ballots ballots are aggregated to protect voter privacy.
        """.trimIndent()
        )

        try {
            parser.parse(args)

            if (helps != null) {
                println(
                    """Anonymize Cast Vote Records per Colorado C.R.S. 24-72-205.5. 
                    Ballot styles with fewer than --min-ballots ballots are aggregated to protect voter privacy.
                """
                )
                return
            }

            if (version != null) {
                println("version = $VERSION")
                return
            }

            println("AnonymizeCvr ${mode} for $input")
            if (mode == Mode.redact) println(" output to $output")
            print(" min_ballots=$min_ballots,")
            print(" no_contest_balancing=$no_contest_balancing,")
            print(" redact_on_precinct=$redact_on_precinct,")
            if (stylecol != null) print(" stylecol=$stylecol,")
            if (redacted_list_filename != null) print(" redacted_list_filename=$redacted_list_filename,")
            print(" version=$VERSION")
            println()
            println()

            if (mode == Mode.redact && output == null) {
                println(" you must set an output file when redacting")
                return
            }

            execute(mode, input, min_ballots, output, redacted_list_filename, redact_on_precinct, stylecol, no_contest_balancing)

        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun execute(
        mode: Mode, input: String, min_ballots: Int, output: String?, redacted_list_filename: String?,
        redact_on_precinct: Boolean, stylecol: Int?, no_contest_balancing: Boolean
    ) {
        val anon = Anonymize(input, min_ballots, output, redact_on_precinct, stylecol, no_contest_balancing, redacted_list_filename)

        when (mode) {
            Mode.check -> anon.execute_check()
            Mode.redact -> anon.execute_redact()
        }
    }
}


