package work.slhaf.partner.framework.agent.log

import com.alibaba.fastjson2.JSONObject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import work.slhaf.partner.framework.agent.factory.context.AgentContext
import java.io.BufferedWriter
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream

object TraceRecorder {

    private const val ACTIVE_FILE_NAME = "active.jsonl"
    private const val HISTORICAL_DIR_NAME = "historical"
    private const val ARCHIVED_DIR_NAME = "archived"
    private const val MAX_ACTIVE_SIZE_BYTES = 16L * 1024 * 1024
    private const val MAX_ACTIVE_RECORDS = 2000
    private const val MAX_HISTORICAL_SIZE_BYTES = 256L * 1024 * 1024
    private const val MAX_ARCHIVED_SIZE_BYTES = 1024L * 1024 * 1024
    private const val ARCHIVED_RETENTION_DAYS = 14L

    private val log = LoggerFactory.getLogger(TraceRecorder::class.java)
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val historyNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<TraceEvent>(Channel.UNLIMITED)
    private val writerStates = linkedMapOf<Path, WriterState>()
    private val closed = AtomicBoolean(false)
    private val writerJob: Job

    init {
        AgentContext.addPostShutdownHook("trace-recorder-close") { close() }
        writerJob = scope.launch {
            try {
                for (event in channel) {
                    handleEvent(event)
                }
            } catch (e: Exception) {
                log.error("TraceRecorder writer loop failed", e)
            } finally {
                closeAllWriters()
            }
        }
    }

    fun record(event: TraceEvent) {
        if (closed.get()) {
            log.warn("TraceRecorder is closed, skip event for path: {}", event.path)
            return
        }
        val result = channel.trySend(event)
        if (result.isFailure) {
            log.error("Failed to enqueue trace event for path: {}", event.path, result.exceptionOrNull())
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        channel.close()
        runBlocking {
            writerJob.join()
        }
        scope.cancel()
    }

    private fun handleEvent(event: TraceEvent) {
        val basePath = event.path.normalize().toAbsolutePath()
        runCatching {
            val state = writerStates.getOrPut(basePath) { openWriterState(basePath) }
            writeEvent(state, event)
            if (shouldRotate(state)) {
                rotateActiveFile(state)
            }
        }.onFailure {
            log.error("Failed to persist trace event for path: {}", basePath, it)
        }
    }

    private fun writeEvent(state: WriterState, event: TraceEvent) {
        val json = JSONObject(event.payload)
        json["timestamp"] = event.timestamp
        val line = json.toJSONString()
        state.writer.write(line)
        state.writer.newLine()
        state.writer.flush()
        state.recordCount += 1
        state.byteCount += line.toByteArray(StandardCharsets.UTF_8).size + 1L
    }

    private fun shouldRotate(state: WriterState): Boolean {
        return state.byteCount >= MAX_ACTIVE_SIZE_BYTES || state.recordCount >= MAX_ACTIVE_RECORDS
    }

    private fun openWriterState(basePath: Path): WriterState {
        ensureDirectories(basePath)
        val activeFile = activeFile(basePath)
        if (Files.exists(activeFile)) {
            val existingSize = Files.size(activeFile)
            val existingCount = countLines(activeFile)
            if (existingSize >= MAX_ACTIVE_SIZE_BYTES || existingCount >= MAX_ACTIVE_RECORDS) {
                rotateExistingActiveFile(basePath, activeFile)
            }
        }
        if (Files.notExists(activeFile)) {
            Files.createFile(activeFile)
        }
        val writer = Files.newBufferedWriter(
            activeFile,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
        return WriterState(
            basePath = basePath,
            activeFile = activeFile,
            writer = writer,
            recordCount = countLines(activeFile),
            byteCount = Files.size(activeFile)
        )
    }

    private fun rotateExistingActiveFile(basePath: Path, activeFile: Path) {
        if (Files.notExists(activeFile) || Files.size(activeFile) == 0L) {
            return
        }
        val historicalFile = nextHistoricalFile(basePath)
        Files.move(activeFile, historicalFile, StandardCopyOption.REPLACE_EXISTING)
        archiveHistoricalFiles(basePath)
        cleanupArchivedFiles(basePath)
    }

    private fun rotateActiveFile(state: WriterState) {
        state.writer.flush()
        state.writer.close()
        val historicalFile = nextHistoricalFile(state.basePath)
        Files.move(state.activeFile, historicalFile, StandardCopyOption.REPLACE_EXISTING)
        Files.createFile(state.activeFile)
        state.writer = Files.newBufferedWriter(
            state.activeFile,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
        state.recordCount = 0
        state.byteCount = 0L
        archiveHistoricalFiles(state.basePath)
        cleanupArchivedFiles(state.basePath)
    }

    private fun archiveHistoricalFiles(basePath: Path) {
        val historicalDir = historicalDir(basePath)
        if (Files.notExists(historicalDir)) {
            return
        }
        val files = listRegularFiles(historicalDir)
        if (files.isEmpty()) {
            return
        }

        val today = LocalDate.now(zoneId)
        val totalSize = files.sumOf { Files.size(it) }
        val hasNonToday = files.any { !fileDate(it).isEqual(today) }
        if (totalSize < MAX_HISTORICAL_SIZE_BYTES && !hasNonToday) {
            return
        }

        val candidates = if (totalSize >= MAX_HISTORICAL_SIZE_BYTES) {
            files
        } else {
            files.filter { !fileDate(it).isEqual(today) }
        }

        candidates.forEach { file ->
            runCatching {
                val archiveFile = archivedDir(basePath).resolve("${file.fileName}.gz")
                Files.newInputStream(file).use { input ->
                    Files.newOutputStream(
                        archiveFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                    ).use { output ->
                        gzip(input, output)
                    }
                }
                Files.setLastModifiedTime(archiveFile, Files.getLastModifiedTime(file))
                Files.deleteIfExists(file)
            }.onFailure {
                log.error("Failed to archive historical trace file: {}", file, it)
            }
        }
    }

    private fun cleanupArchivedFiles(basePath: Path) {
        val archivedDir = archivedDir(basePath)
        if (Files.notExists(archivedDir)) {
            return
        }
        val files = listRegularFiles(archivedDir).toMutableList()
        if (files.isEmpty()) {
            return
        }

        val today = LocalDate.now(zoneId)
        val retainedFiles = mutableListOf<Path>()
        files.forEach { file ->
            val expired = fileDate(file).plusDays(ARCHIVED_RETENTION_DAYS).isBefore(today)
            if (expired) {
                deleteArchivedFile(file)
            } else {
                retainedFiles.add(file)
            }
        }

        var totalSize = retainedFiles.sumOf { Files.size(it) }
        val iterator = retainedFiles.iterator()
        while (totalSize > MAX_ARCHIVED_SIZE_BYTES && iterator.hasNext()) {
            val file = iterator.next()
            val fileSize = Files.size(file)
            if (deleteArchivedFile(file)) {
                totalSize -= fileSize
                iterator.remove()
            }
        }
    }

    private fun deleteArchivedFile(file: Path): Boolean {
        return runCatching {
            Files.deleteIfExists(file)
        }.onFailure {
            log.error("Failed to delete archived trace file: {}", file, it)
        }.getOrDefault(false)
    }

    private fun ensureDirectories(basePath: Path) {
        Files.createDirectories(basePath)
        Files.createDirectories(historicalDir(basePath))
        Files.createDirectories(archivedDir(basePath))
    }

    private fun closeAllWriters() {
        writerStates.values.forEach { state ->
            runCatching {
                state.writer.flush()
                state.writer.close()
            }.onFailure {
                log.error("Failed to close trace writer for path: {}", state.basePath, it)
            }
        }
        writerStates.clear()
    }

    private fun activeFile(basePath: Path): Path = basePath.resolve(ACTIVE_FILE_NAME)

    private fun historicalDir(basePath: Path): Path = basePath.resolve(HISTORICAL_DIR_NAME)

    private fun archivedDir(basePath: Path): Path = basePath.resolve(ARCHIVED_DIR_NAME)

    private fun nextHistoricalFile(basePath: Path): Path {
        val baseName = historyNameFormatter.format(Instant.now().atZone(zoneId))
        var candidate = historicalDir(basePath).resolve("$baseName.jsonl")
        var index = 1
        while (Files.exists(candidate)) {
            candidate = historicalDir(basePath).resolve("$baseName-$index.jsonl")
            index += 1
        }
        return candidate
    }

    private fun fileDate(path: Path): LocalDate {
        return Files.getLastModifiedTime(path).toInstant().atZone(zoneId).toLocalDate()
    }

    private fun countLines(path: Path): Int {
        if (Files.notExists(path)) {
            return 0
        }
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
            var count = 0
            while (reader.readLine() != null) {
                count += 1
            }
            return count
        }
    }

    private fun gzip(input: java.io.InputStream, output: OutputStream) {
        GZIPOutputStream(output).use { gzipOutput ->
            input.copyTo(gzipOutput)
        }
    }

    private fun listRegularFiles(dir: Path): List<Path> {
        val files = mutableListOf<Path>()
        Files.list(dir).use { stream ->
            stream.forEach { path ->
                if (Files.isRegularFile(path)) {
                    files.add(path)
                }
            }
        }
        return files.sortedWith(compareBy<Path> {
            Files.getLastModifiedTime(it).toMillis()
        }.thenBy { it.fileName.toString() })
    }

    private data class WriterState(
        val basePath: Path,
        val activeFile: Path,
        var writer: BufferedWriter,
        var recordCount: Int,
        var byteCount: Long
    )
}

data class TraceEvent @JvmOverloads constructor(
    val path: Path,
    val payload: JSONObject,
    val timestamp: Long = System.currentTimeMillis()
)
