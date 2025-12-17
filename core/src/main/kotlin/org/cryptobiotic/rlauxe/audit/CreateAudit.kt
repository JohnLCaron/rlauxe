package org.cryptobiotic.rlauxe.audit

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rlauxe.core.ContestUnderAudit
import org.cryptobiotic.rlauxe.core.Cvr
import org.cryptobiotic.rlauxe.oneaudit.CardPoolIF
import org.cryptobiotic.rlauxe.persist.Publisher
import org.cryptobiotic.rlauxe.persist.clearDirectory
import org.cryptobiotic.rlauxe.persist.csv.readAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCardsCsvIterator
import org.cryptobiotic.rlauxe.persist.csv.writeAuditableCardCsvFile
import org.cryptobiotic.rlauxe.persist.json.readSamplePrnsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeCardPoolsJsonFile
import org.cryptobiotic.rlauxe.persist.json.writeContestsJsonFile
import org.cryptobiotic.rlauxe.persist.validateOutputDirOfFile
import org.cryptobiotic.rlauxe.util.CloseableIterator
import org.cryptobiotic.rlauxe.util.Closer
import org.cryptobiotic.rlauxe.util.Prng
import org.cryptobiotic.rlauxe.util.SortMerge
import org.cryptobiotic.rlauxe.util.Stopwatch
import org.cryptobiotic.rlauxe.util.createZipFile
import org.cryptobiotic.rlauxe.verify.VerifyResults
import org.cryptobiotic.rlauxe.verify.checkContestsCorrectlyFormed
import org.cryptobiotic.rlauxe.workflow.findSamples
import kotlin.io.path.Path

interface CreateElectionIF {
    fun contestsUA(): List<ContestUnderAudit>
    fun cardPools(): List<CardPoolIF>? // only if OneAudit

    // if you immediately write to disk, you only need one pass through the iterator
    fun cardManifest() : CloseableIterator<AuditableCard>
}

class CreateElection(
    val contestsUA: List<ContestUnderAudit>,
    val cardPools: List<CardPoolIF>,
    val cardManifest: List<AuditableCard>
):  CreateElectionIF {

    override fun contestsUA() = contestsUA
    override fun cardPools() = cardPools
    override fun cardManifest() = Closer( cardManifest.iterator() )
}

private val logger = KotlinLogging.logger("CreateAudit")

class CreateAudit(val name: String, val config: AuditConfig, election: CreateElectionIF, val auditDir: String, clear: Boolean = true) {

    val stopwatch = Stopwatch()

    init {
        if (clear) clearDirectory(Path(auditDir))

        val publisher = Publisher(auditDir)
        writeAuditConfigJsonFile(config, publisher.auditConfigFile())
        logger.info{"CreateAudit writeAuditConfigJsonFile to ${publisher.auditConfigFile()}\n  $config"}

        if (config.isOA) {
            val pools = election.cardPools()!!
            writeCardPoolsJsonFile(pools, publisher.cardPoolsFile())
            logger.info { "CreateAudit write ${pools.size} cardPools, to ${publisher.cardPoolsFile()}" }
        }

        val cards = election.cardManifest()
        val countCvrs = writeAuditableCardCsvFile(cards, publisher.cardManifestFile())
        createZipFile(publisher.cardManifestFile(), delete = true)
        logger.info { "CreateAudit write ${countCvrs} cards to ${publisher.cardManifestFile()}" }

        // this may change the auditStatus to misformed
        val contestsUA = election.contestsUA()

        val results = VerifyResults()
        checkContestsCorrectlyFormed(config, contestsUA, results)
        if (results.hasErrors) {
            logger.warn{ results.toString() }
        } else {
            logger.info{ results.toString() }
        }

        // sf only writes these:
        // contestsUA.filter { it.preAuditStatus == TestH0Status.InProgress }

        // write contests
        writeContestsJsonFile(contestsUA, publisher.contestsFile())
        logger.info{"CreateAudit write ${contestsUA.size} contests to ${publisher.contestsFile()}"}

        // cant write the sorted cards until after seed is generated, after committment to cardManifest
    }
}
