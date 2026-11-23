package com.azizbek.safetyai.data.result

import android.content.Context
import com.azizbek.safetyai.analysis.ANALYSIS_SCHEMA_VERSION
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
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

            if (target.exists()) {
                require(target.delete()) {
                    "Could not replace the existing analysis result."
                }
            }

            if (!temporary.renameTo(target)) {
                temporary.copyTo(
                    target = target,
                    overwrite = true
                )

                require(temporary.delete()) {
                    "Could not remove the temporary result file."
                }
            }

            require(target.isFile && target.length() > 0L) {
                "Analysis result was not written."
            }

            target
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

        val result = ObjectInputStream(
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
        val file = resultFile(assessmentId)
        !file.exists() || file.delete()
    }

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
