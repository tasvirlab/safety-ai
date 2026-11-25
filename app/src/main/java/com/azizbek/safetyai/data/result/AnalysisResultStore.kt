package com.azizbek.safetyai.data.result

import android.content.Context
import com.azizbek.safetyai.analysis.ANALYSIS_SCHEMA_VERSION
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InvalidClassException
import java.io.ObjectStreamClass
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Stores complete analysis results outside Room.
 *
 * Per-frame 2D/3D joints are too large for an AssessmentEntity row.
 * Files remain inside the app-private files directory and are GZIP-compressed.
 */
class AnalysisResultStore(
    private val resultDirectory: File
) {
    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, RESULT_DIRECTORY)
    )

    suspend fun save(
        result: SafetyAnalysisResult
    ): File = withContext(Dispatchers.IO) {
        writeMutex.withLock { saveLocked(result) }
    }

    private fun saveLocked(
        result: SafetyAnalysisResult
    ): File {
        require(result.assessmentId.isNotBlank()) {
            "Result assessment ID must not be blank."
        }

        require(result.schemaVersion == ANALYSIS_SCHEMA_VERSION) {
            "Unsupported analysis schema version: " +
                result.schemaVersion
        }

        ensureDirectory()

        val target = resultFile(result.assessmentId)
        val temporary = File(
            resultDirectory,
            target.name + TEMPORARY_SUFFIX
        )

        try {
            ObjectOutputStream(
                BufferedOutputStream(
                    GZIPOutputStream(
                        FileOutputStream(temporary)
                    )
                )
            ).use { output ->
                output.writeInt(FILE_MAGIC)
                output.writeInt(FILE_FORMAT_VERSION)
                output.writeObject(result)
            }

            require(temporary.isFile && temporary.length() > 0L) {
                "Analysis result temporary file is empty."
            }

            // rename(2) replaces the target atomically, so the last valid
            // result stays in place until the new one is fully committed.
            if (!temporary.renameTo(target)) {
                error("Could not install the new analysis result.")
            }

            require(target.isFile && target.length() > 0L) {
                "Analysis result was not written."
            }

            return target
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    suspend fun load(
        assessmentId: String
    ): SafetyAnalysisResult? = withContext(Dispatchers.IO) {
        require(assessmentId.isNotBlank()) {
            "Assessment ID must not be blank."
        }

        val source = resultFile(assessmentId)

        if (!source.isFile) {
            return@withContext null
        }

        val result = AllowListObjectInputStream(
            BufferedInputStream(
                GZIPInputStream(
                    FileInputStream(source)
                )
            )
        ).use { input ->
            val magic = input.readInt()
            require(magic == FILE_MAGIC) {
                "Invalid analysis result file."
            }

            val formatVersion = input.readInt()
            require(formatVersion == FILE_FORMAT_VERSION) {
                "Unsupported result file version: $formatVersion"
            }

            input.readObject() as? SafetyAnalysisResult
                ?: error("Stored object is not a SafetyAnalysisResult.")
        }

        require(result.assessmentId == assessmentId) {
            "Stored result belongs to another assessment."
        }

        require(result.schemaVersion in 1..ANALYSIS_SCHEMA_VERSION) {
            "Stored result schema is unsupported: " +
                result.schemaVersion
        }

        result
    }

    suspend fun exists(
        assessmentId: String
    ): Boolean = withContext(Dispatchers.IO) {
        resultFile(assessmentId).isFile
    }

    suspend fun delete(
        assessmentId: String
    ): Boolean = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val file = resultFile(assessmentId)
            File(resultDirectory, file.name + TEMPORARY_SUFFIX).delete()
            !file.exists() || file.delete()
        }
    }

    /** Removes result files (and stale temporaries) whose assessment is not in [assessmentIds]. */
    suspend fun retainOnly(assessmentIds: Set<String>) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val keep = assessmentIds.map { resultFile(it).name }.toSet()
            resultDirectory.listFiles()?.forEach { file ->
                val base = file.name.removeSuffix(TEMPORARY_SUFFIX)
                if (base.endsWith(RESULT_SUFFIX) && base !in keep || file.name.endsWith(TEMPORARY_SUFFIX)) file.delete()
            }
        }
    }

    /** Deserializes only this app's analysis classes plus JDK collections and primitives. */
    private class AllowListObjectInputStream(input: InputStream) : ObjectInputStream(input) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            val name = desc.name.removePrefix("[").removePrefix("L")
            val allowed = name.startsWith("com.azizbek.safetyai.") || name.startsWith("java.lang.") ||
                name.startsWith("java.util.") || name.startsWith("kotlin.") || desc.name.length <= 2
            if (!allowed) throw InvalidClassException(desc.name, "Class is not allowed in analysis results")
            return super.resolveClass(desc)
        }
    }

    private val writeMutex = Mutex()

    private fun ensureDirectory() {
        require(
            resultDirectory.isDirectory ||
                resultDirectory.mkdirs()
        ) {
            "Could not create the analysis result directory."
        }
    }

    private fun resultFile(
        assessmentId: String
    ): File {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(assessmentId.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }

        return File(
            resultDirectory,
            "$digest$RESULT_SUFFIX"
        )
    }

    private companion object {
        const val RESULT_DIRECTORY = "analysis_results"
        const val RESULT_SUFFIX = ".safety-result.gz"
        const val TEMPORARY_SUFFIX = ".tmp"

        const val FILE_MAGIC = 0x53414952
        const val FILE_FORMAT_VERSION = 1
    }
}
