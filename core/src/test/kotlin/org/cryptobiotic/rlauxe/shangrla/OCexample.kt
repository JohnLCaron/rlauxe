package org.cryptobiotic.rlauxe.shangrla

//     import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
//    import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
//    import java.security.MessageDigest
//    import java.nio.charset.StandardCharsets
//    import kotlin.random.Random

/*
class OCexample {

    fun makeAllAssertions(contests: List<Contest>) = Assertion.makeAllAssertions(contests)

    fun checkAuditParameters(contests: List<Contest>) = Audit.checkAuditParameters(contests)

    fun prepManifest(manifest: DataFrame<String>, audit: Audit, maxCards: Int, cvrListLength: Int) =
        Hart.prepManifest(manifest, audit.maxCards, cvrListLength)

    fun makePhantoms(audit: Audit, contests: List<Contest>, cvrList: List<CVR>, prefix: String) =
        CVR.makePhantoms(audit, contests, cvrList, prefix)

    fun setMarginsFromCvrs(audit: Audit, contests: List<Contest>, cvrList: List<CVR>) =
        Assertion.setMarginsFromCvrs(audit, contests, cvrList)

    fun findSampleSize(audit: Audit, contests: List<Contest>, cvrList: List<CVR>) =
        Audit.findSampleSize(contests, cvrList)

    fun assignSampleNums(cvrList: List<CVR>, prng: MessageDigest) = CVR.assignSampleNums(cvrList, prng)

    fun consistentSampling(cvrList: List<CVR>, contests: List<Contest>) = CVR.consistentSampling(cvrList, contests)

    // * `seed`: the numeric seed for the pseudo-random number generator used to draw sample (for SHA256 PRNG)
    //* `sim_seed`: seed for simulations to estimate sample sizes (for Mersenne Twister PRNG)
    //* `quantile`: quantile of the sample size to use for setting initial sample size
    //* `cvr_file`: filename for CVRs (input)
    //* `manifest_file`: filename for ballot manifest (input)
    //* `use_style`: Boolean. If True, use card style information (inferred from CVRs) to target samples. If False, sample from all cards, regardless of the contest.
    //* `sample_file`: filename for sampled card identifiers (output)
    //* `mvr_file`: filename for manually ascertained votes from sampled cards (input)
    //* `log_file`: filename for audit log (output)
    //* `error_rate_1`: expected rate of 1-vote overstatements. Recommended value $\ge$ 0.001 if there are hand-marked ballots. Larger values increase the initial sample size, but make it more likely that the audit will conclude after a single round even if the audit finds errors
    //* `error_rate_2`: expected rate of 2-vote overstatements. 2-vote overstatements should be extremely rare.
    //     Recommended value: 0. Larger values increase the initial sample size, but make it more likely that the audit will conclude after a single round even if the audit finds errors
    //* `reps`: number of replications to use to estimate sample sizes. If `reps is None`, uses a deterministic method
    //* `quantile`: quantile of sample size to estimate. Not used if `reps is None`
    //* `strata`: a dict describing the strata. Keys are stratum identifiers; values are dicts containing:
    //    + `max_cards`: an upper bound on the number of pieces of paper cast in the contest. This should be derived independently of the voting system. A ballot consists of one or more cards.
    //    + `replacement`: whether to sample from this stratum with replacement.
    //    + `use_style`: True if the sample in that stratum uses card-style information.
    //    + `audit_type` one of Contest.POLLING, Contest.BALLOT_COMPARISON, Contest.BATCH_COMPARISON but only POLLING and BALLOT_COMPARISON are currently implemented.
    //    + `test`: the name of the function to be used to measure risk. Options are `kaplan_markov`,`kaplan_wald`,`kaplan_kolmogorov`,`wald_sprt`,`kaplan_mart`, `alpha_mart`.
    //       Not all risk functions work with every social choice function or every sampling method.
    //    + `estimator`: the estimator to be used by the risk function. Options are [FIX ME!]
    //    + `test_kwargs`: keyword arguments for the risk function
        val audit = Audit.fromDict(
            mapOf(
                "seed" to 12345678901234567890,
                "sim_seed" to 314159265,
                "cvr_file" to "/Users/amanda/Downloads/oc_cvrs.zip", // TODO cant rereate without the data
                "manifest_file" to "data/OC_full_manifest.xlsx",
                "sample_file" to "",
                "mvr_file" to "",
                "log_file" to "data/OC_example_log.json",
                "quantile" to 0.8,
                "error_rate_1" to 0,
                "error_rate_2" to 0,
                "reps" to 100,
                "strata" to mapOf(
                    "stratum_1" to mapOf(
                        "max_cards" to 3094308,
                        "use_style" to false,
                        "replacement" to false,
                        "audit_type" to AuditType.CARD_COMPARISON,
                        "test" to NonnegMean.alphaMart,
                        "estimator" to NonnegMean.optimalComparison,
                        "test_kwargs" to mapOf<String, Any>()
                    )
                )
            )
        )

    fun workflow() {
        // find upper bound on total cards across strata
        audit.maxCards = audit.strata.values().sumBy { it.maxCards }

        val manifest = pd.readExcel("../tests/core/data/Hart_manifest.xlsx")

        val cvrZip = "/Users/amanda/Downloads/oc_cvrs.zip"
        val cvrList = Hart.readCvrsZip(cvrZip, size = 3094308) // all (XML) CVRs that can be read without error

        val votes = tabulateVotes(cvrList)
        val styles = tabulateStyles(cvrList)
        val cards = tabulateCardsContests(cvrList)

        val contests = Contest.fromCvrList(votes, cards, cvrList)

        val voteForTwo = listOf(
            "LAGUNA BEACH UNIFIED SCHOOL DISTRICT\\nGoverning Board Member",
            "HUNTINGTON BEACH UNION HIGH SCHOOL DISTRICT\\nGoverning Board Member",
            "FOUNTAIN VALLEY SCHOOL DISTRICT\\nGoverning Board Member",
            "LA HABRA CITY SCHOOL DISTRICT\\nGoverning Board Member,\\nFull Term",
            "CITY OF BREA\\nMember, City Council",
            "CITY OF CYPRESS\\nMember, City Council",
            "CITY OF FOUNTAIN VALLEY\\nMember, City Council",
            "CITY OF LAGUNA BEACH\\nMember, City Council",
            "CITY OF LAGUNA HILLS\\nMember, City Council",
            "CITY OF MISSION VIEJO\\nMember, City Council,\\nTwo-Year Term",
            "RANCHO SANTA MARGARITA\\nMember, City Council",
            "CITY OF SAN CLEMENTE\\nMember, City Council, Full Term",
            "CAPISTRANO BAY COMMUNITY SERVICES DISTRICT\\nDirector",
            "SILVERADO-MODJESKA RECREATION AND PARK DISTRICT\\nDirector",
            "MIDWAY CITY SANITARY DISTRICT\\nDirector",
            "SURFSIDE COLONY STORM WATER PROTECTION DISTRICT\\nTrustee",
            "EAST ORANGE COUNTY WATER DISTRICT\\nDirector",
            "SANTIAGO GEOLOGIC HAZARD ABATEMENT DISTRICT\\nDirector, Full Term"
        )

        val voteForThree = listOf(
            "SANTA ANA UNIFIED SCHOOL DISTRICT\\nGoverning Board Member",
            "CITY OF ALISO VIEJO\\nMember, City Council",
            "CITY OF HUNTINGTON BEACH\\nMember, City Council",
            "CITY OF IRVINE\\nMember, City Council",
            "CITY OF LAGUNA NIGUEL\\nMember, City Council",
            "CITY OF LAGUNA WOODS\\nMember, City Council",
            "CITY OF LA HABRA\\nMember, City Council",
            "CITY OF LA PALMA\\nMember, City Council",
            "CITY OF TUSTIN\\nMember, City Council",
            "ROSSMOOR COMMUNITY SERVICES DISTRICT\\nDirector, Full Term",
            "SUNSET BEACH SANITARY DISTRICT\\nDirector, Full Term",
            "SANTA MARGARITA WATER DISTRICT\\nDirector",
            "SOUTH COAST WATER DISTRICT\\nDirector",
            "BUENA PARK LIBRARY DISTRICT\\nTrustee"
        )

        voteForTwo.forEach { contest -> contests[contest]?.nWinners = 2 }

        voteForThree.forEach { contest -> contests[contest]?.nWinners = 3 }

        contests["LAGUNA BEACH UNIFIED SCHOOL DISTRICT\\nGoverning Board Member"]?.winner =
            listOf("KELLY OSBORNE", "JAN VICKERS")
        contests["HUNTINGTON BEACH UNION HIGH SCHOOL DISTRICT\\nGoverning Board Member"]?.winner =
            listOf("SUSAN HENRY", "MICHAEL SIMONS")
        contests["FOUNTAIN VALLEY SCHOOL DISTRICT\\nGoverning Board Member"]?.winner =
            listOf("STEVE SCHULTZ", "JEANNE GALINDO")
        contests["LA HABRA CITY SCHOOL DISTRICT\\nGoverning Board Member,\\nFull Term"]?.winner =
            listOf("SUSAN M. PRITCHARD", "OFELIA CORONA HANSON")
// add all others in a similar way


        val sampledCvrIndices: List<Int> = consistentSampling(cvrList, contests)
        val nSampledPhantoms: Int = sampledCvrIndices.filter { it > manifestCards }.size
        println("The sample includes $nSampledPhantoms phantom cards.")

        val cvrDict = listOf(
            mapOf("id" to "1_1", "votes" to mapOf("AvB" to mapOf("Alice" to true))),
            mapOf("id" to "1_2", "votes" to mapOf("AvB" to mapOf("Bob" to true))),
            mapOf("id" to "1_3", "votes" to mapOf("AvB" to mapOf("Alice" to true)))
        )

        val manifestData = mapOf(
            "Container" to listOf("Mail", "Mail"),
            "Tabulator" to listOf(1, 1),
            "Batch Name" to listOf(1, 2),
            "Number of Ballots" to listOf(1, 2)
        )

        val csvWriter = csvWriter
        csvWriter.open("manifest.csv") {
            writeRow(manifestData.keys)
            writeRows(manifestData.values.transpose())
        }

        val manifest = DataFrame.readCsv("manifest.csv")

        val manifestReturn = prepManifest(manifest, contest, manifest.size, manifest.size)
        val sampleIndices = listOf(0, 1)
        val sampledCardIdentifiers = Hart.sampleFromManifest(manifest, sampleIndices)
    }

}

 */
 */