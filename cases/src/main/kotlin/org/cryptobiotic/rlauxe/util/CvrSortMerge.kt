package org.cryptobiotic.rlauxe.util

import com.github.michaelbull.result.unwrap
import org.cryptobiotic.rlauxe.core.CvrUnderAudit
import org.cryptobiotic.rlauxe.persist.csv.CvrsCsvWriter
import org.cryptobiotic.rlauxe.persist.csv.IteratorCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.csv.readCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.csv.writeCvrsCsvFile
import org.cryptobiotic.rlauxe.persist.json.Publisher
import org.cryptobiotic.rlauxe.persist.json.readAuditConfigJsonFile
import org.cryptobiotic.rlauxe.persist.json.validateOutputDir
import java.nio.file.*
import java.nio.file.spi.FileSystemProvider

// candidate for removal

private val maxChunk = 100000

fun sortMergeCvrs(
    auditDir: String,
    cvrFile: String?,
) {
    // out of memory sort by sampleNum()
    sortCvrs(auditDir, cvrFile, "$auditDir/sortChunks")
    mergeCvrs(auditDir, "$auditDir/sortChunks")
    // TODO zip sortedCvs.csv directory to sortedCvs.zip
}

// out of memory sorting
fun sortCvrs(
    auditDir: String,
    cvrFile: String?, // may be xipped or not; if null use files in "$auditDir/cvrs/"
    workingDirectory: String,
) {
    val stopwatch = Stopwatch()
    val publisher = Publisher(auditDir)
    val auditConfig = readAuditConfigJsonFile(publisher.auditConfigFile()).unwrap()
    validateOutputDir(Path.of(workingDirectory), ErrorMessages("sortCvrs"))

    val prng = Prng(auditConfig.seed)
    val cvrSorter = CvrSorter(workingDirectory, prng, maxChunk)

    //// the reading and sorted chunks
    if (cvrFile != null) {
        if (cvrFile.endsWith(".zip")) {
            val provider: FileSystemProvider = ZipReader(cvrFile).fileSystemProvider
            val fileSystem: FileSystem = ZipReader(cvrFile).fileSystem
            fileSystem.rootDirectories.forEach { root: Path ->
                readDirectory(Indent(0), provider, root, cvrSorter)
            }
        } else {
            val cvrIter = IteratorCvrsCsvFile(cvrFile)
            while (cvrIter.hasNext()) {
                cvrSorter.add(cvrIter.next())
            }
        }
    } else {
        val cvrDir = "$auditDir/cvrs/"
        val fileSystem: FileSystem = FileSystems.getDefault()
        val provider : FileSystemProvider = fileSystem.provider()
        Files.newDirectoryStream(Path.of(cvrDir)).use { stream ->
            for (path in stream) {
                readDirectory(Indent(0), provider, path, cvrSorter)
            }
        }
    }
    cvrSorter.writeSortedChunk()
    println("writeSortedChunk took $stopwatch")
}

fun mergeCvrs(
    auditDir: String,
    workingDirectory: String,
) {
    val stopwatch = Stopwatch()

    //// the merging of the sorted chunks, and writing the completely sorted file
    val writer = CvrsCsvWriter("$auditDir/sortedCvrs.csv")

    val paths = mutableListOf<String>()
    Files.newDirectoryStream(Path.of(workingDirectory)).use { stream ->
        for (path in stream) {
            paths.add(path.toString())
        }
    }
    val merger = CvrMerger(paths, writer)
    merger.merge()
    println("mergeSortedChunk took $stopwatch")
}

fun readDirectory(indent: Indent, provider: FileSystemProvider, dirPath: Path, cvrSorter: CvrSorter): Int {
    val paths = mutableListOf<Path>()
    Files.newDirectoryStream(dirPath).use { stream ->
        for (path in stream) {
            paths.add(path)
        }
    }
    paths.sort()
    var count = 0
    paths.forEach { path ->
        if (Files.isDirectory(path)) {
            println("$indent ${path.fileName}")
            val ncvrs = readDirectory(indent.incr(), provider, path, cvrSorter)
            println("$indent ${path.fileName} has $ncvrs cvrs")
            count += ncvrs
        } else {
            val input = provider.newInputStream(path, StandardOpenOption.READ)
            val cvrsUA = readCvrsCsvFile(input)
            println("$indent ${path.fileName} has ${cvrsUA.size} cvrs")
            cvrSorter.add(cvrsUA)
            count += cvrsUA.size
        }
    }
    return count
}

class CvrSorter(val workingDirectory: String, val prng: Prng, val max: Int) {
    var index = 0
    var count = 0
    val cvrs = mutableListOf<CvrUnderAudit>()
    var countChunks = 0

    fun add(rawCvrs: List<CvrUnderAudit>) {
        rawCvrs.forEach {
            cvrs.add(CvrUnderAudit(it.cvr, index, prng.next()))
            index++
            count++
        }

        if (count > max) {
            writeSortedChunk()
        }
    }

    fun add(rawCvr: CvrUnderAudit) {
        cvrs.add(CvrUnderAudit(rawCvr.cvr, index, prng.next()))
        index++
        count++

        if (count > max) {
            writeSortedChunk()
        }
    }

    fun writeSortedChunk() {
        val sortedCvrs = cvrs.sortedBy { it.sampleNum }
        val filename = "$workingDirectory/sorted-cvrs-part-${countChunks}.csv"
        writeCvrsCsvFile(sortedCvrs, filename)
        println("write $filename")
        cvrs.clear()
        count = 0
        countChunks++
    }
}

class CvrMerger(chunkFilenames: List<String>, val writer: CvrsCsvWriter) {
    val nextUps = chunkFilenames.map { NextUp(IteratorCvrsCsvFile(it)) }
    val cvrs = mutableListOf<CvrUnderAudit>()
    var total = 0

    fun merge() {
        var moar = true
        while (moar) {
            val nextUppers = nextUps.map { it.sampleNumber }
            val nextUp = nextUps.minBy { it.sampleNumber }
            cvrs.add ( nextUp.currentCvr!! )
            nextUp.pop()
            if (cvrs.size >= maxChunk) writeMergedCvrs()
            moar = nextUps.any { it.currentCvr != null }
        }
        writeMergedCvrs()
        writer.close()
    }

    fun writeMergedCvrs() {
        writer.write(cvrs)
        total += cvrs.size
        println("write ${cvrs.size} total = $total")
        cvrs.clear()
    }

    class NextUp(val nextIter: Iterator<CvrUnderAudit>) {
        var currentCvr: CvrUnderAudit? = null
        var sampleNumber = Long.MAX_VALUE

        init {
            pop()
        }

        fun pop() {
            if (nextIter.hasNext()) {
                currentCvr = nextIter.next()
                sampleNumber = currentCvr!!.sampleNum
            } else {
                currentCvr = null
                sampleNumber = Long.MAX_VALUE
            }
        }
    }

}

class TreeReaderZip(val cvrZipFile: String) {
    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    var count = 0

    fun process(cvrs: List<CvrUnderAudit>) {
        cvrs.forEach { cvr ->
            cvr.votes.forEach { (key, value) ->
                haveSampleSize[key] = haveSampleSize[key]?.plus(1) ?: 1
            }
        }
        count += cvrs.size
    }

    fun processCvrs() {
        val provider: FileSystemProvider = ZipReader(cvrZipFile).fileSystemProvider
        val fileSystem: FileSystem = ZipReader(cvrZipFile).fileSystem
        fileSystem.rootDirectories.forEach { root: Path ->
            readDirectory(Indent(0), provider, root)
        }
        println("count = $count")
        haveSampleSize.toSortedMap().forEach {
            println("${it.key} : ${it.value}")
        }
    }

    fun readDirectory(indent: Indent, provider: FileSystemProvider, dirPath: Path): Int {
        val paths = mutableListOf<Path>()
        Files.newDirectoryStream(dirPath).use { stream ->
            for (path in stream) {
                paths.add(path)
            }
        }
        paths.sort()
        var count = 0
        paths.forEach { path ->
            if (Files.isDirectory(path)) {
                println("$indent ${path.fileName}")
                val ncvrs = readDirectory(indent.incr(), provider, path)
                println("$indent ${path.fileName} has $ncvrs cvrs")
                count += ncvrs
            } else {
                val input = provider.newInputStream(path, StandardOpenOption.READ)
                val cvrsUA = readCvrsCsvFile(input)
                println("$indent ${path.fileName} has ${cvrsUA.size} cvrs")
                process(cvrsUA)
                count += cvrsUA.size
            }
        }
        return count
    }
}

class TreeReader(val cvrDir: String) {
    val haveSampleSize = mutableMapOf<Int, Int>() // contestId -> nmvrs in sample
    var count = 0

    fun process(cvrs: List<CvrUnderAudit>) {
        cvrs.forEach { cvr ->
            cvr.votes.forEach { (key, value) ->
                haveSampleSize[key] = haveSampleSize[key]?.plus(1) ?: 1
            }
        }
        count += cvrs.size
    }

    fun processCvrs() {
        readDirectory(Indent(0), Path.of(cvrDir))
        println("count = $count")
        haveSampleSize.toSortedMap().forEach {
            println("${it.key} : ${it.value}")
        }
    }

    fun readDirectory(indent: Indent, dirPath: Path): Int {
        val paths = mutableListOf<Path>()
        Files.newDirectoryStream(dirPath).use { stream ->
            for (path in stream) {
                paths.add(path)
            }
        }
        paths.sort()
        var count = 0
        paths.forEach { path ->
            if (Files.isDirectory(path)) {
                println("$indent ${path.fileName}")
                val ncvrs = readDirectory(indent.incr(), path)
                println("$indent ${path.fileName} has $ncvrs cvrs")
                count += ncvrs
            } else {
                val cvrsUA = readCvrsCsvFile(path.toString())
                println("$indent ${path.fileName} has ${cvrsUA.size} cvrs")
                process(cvrsUA)
                count += cvrsUA.size
            }
        }
        return count
    }
}

class TreeReaderTour(val topDir: String, val silent: Boolean = true, val visitor: (Path) -> Unit) {
    var count = 0

    // depth first tour of all files in the directory tree
    fun tourFiles() {
        readDirectory(Indent(0), Path.of(topDir))
        if (!silent) println("count = $count")
    }

    fun readDirectory(indent: Indent, dirPath: Path): Int {
        val paths = mutableListOf<Path>()
        Files.newDirectoryStream(dirPath).use { stream ->
            for (path in stream) {
                paths.add(path)
            }
        }
        paths.sort()
        var count = 0
        paths.forEach { path ->
            if (Files.isDirectory(path)) {
                if (!silent) println("$indent ${path.fileName}")
                val nfiles = readDirectory(indent.incr(), path)
                if (!silent) println("$indent ${path.fileName} has $nfiles files")
                count += nfiles
            } else {
                visitor(path)
                count++
            }
        }
        return count
    }
}

class PrecinctReader(cvrDir: String): Iterator<CvrUnderAudit> {
    var count = 0
    var precinct: PrecinctIterator? = null
    var county: CountyIterator? = null

    val paths: Iterator<Path>
    init {
        val pass = mutableListOf<Path>()
        Files.newDirectoryStream(Path.of(cvrDir)).use { stream ->
            for (path in stream) {
                if (Files.isDirectory(path)) {
                    pass.add(path)
                }
            }
        }
        paths = pass.iterator()
    }

    override fun hasNext(): Boolean {
        if (precinct == null) precinct = getNextPrecinctIterator()
        if (precinct == null) return false
        while (!precinct!!.hasNext()) {
            precinct = getNextPrecinctIterator()
            if (precinct == null) return false
        }
        return true
    }

    override fun next(): CvrUnderAudit {
        count++
        return precinct!!.next()
    }

    fun getNextPrecinctIterator() : PrecinctIterator? {
        if (county == null) county = getNextCountyIterator()
        if (county == null) return null
        while (!county!!.hasNext()) {
            county = getNextCountyIterator()
            if (county == null) return null
        }
        return county!!.next()
    }

    fun getNextCountyIterator() : CountyIterator? {
        if (paths.hasNext()) {
            return CountyIterator(paths.next())
        }
        return null
    }

    // has only files
    inner class CountyIterator(dirPath: Path) : Iterator<PrecinctIterator> {
        val pathIterator: Iterator<Path>

        init {
            val paths = mutableListOf<Path>()
            Files.newDirectoryStream(dirPath).use { stream ->
                for (path in stream) paths.add(path)
            }
            pathIterator = paths.iterator()
        }
        override fun hasNext(): Boolean {
            return pathIterator.hasNext()
        }
        override fun next(): PrecinctIterator {
            if (!pathIterator.hasNext())
                println("why")
            return PrecinctIterator(pathIterator.next())
        }
    }

    inner class PrecinctIterator(val path: Path) : Iterator<CvrUnderAudit> {
        val cvrsUA: List<CvrUnderAudit>
        val cvrsIterator: Iterator<CvrUnderAudit>
        init {
            cvrsUA = readCvrsCsvFile(path.toString())
            cvrsIterator = cvrsUA.iterator()
        }
        override fun hasNext(): Boolean {
            return cvrsIterator.hasNext()
        }
        override fun next(): CvrUnderAudit {
            if (!cvrsIterator.hasNext())
                println("why")
            return cvrsIterator.next()
        }
    }

}

