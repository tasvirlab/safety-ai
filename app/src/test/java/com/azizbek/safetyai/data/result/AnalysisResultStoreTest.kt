package com.azizbek.safetyai.data.result

import com.azizbek.safetyai.analysis.ActivityFindings
import com.azizbek.safetyai.analysis.BodyRegion
import com.azizbek.safetyai.analysis.RebaComponents
import com.azizbek.safetyai.analysis.RiskLevel
import com.azizbek.safetyai.analysis.SafetyAnalysisResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnalysisResultStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun result(id: String, coverage: Float = 0.9f) = SafetyAnalysisResult(
        assessmentId = id, processingStartedAtEpochMs = 0, processingCompletedAtEpochMs = 1,
        inputQualityChecks = emptyList(), frames = emptyList(),
        reba = RebaComponents(1, 1, 1, 1, 1, 1, 1, 2, RiskLevel.LOW),
        bodyRegionFindings = emptyList(),
        activity = ActivityFindings(emptyList(), emptyList(), 0, emptyList(), false),
        overallRiskLevel = RiskLevel.LOW, highestRiskTimestampMs = 0, coveragePercent = coverage,
        recommendations = emptyList(), warnings = emptyList(), processingErrors = emptyList()
    )

    private fun store() = AnalysisResultStore(File(folder.root, "results"))

    @Test fun `round trip`() = runBlocking {
        val s = store(); s.save(result("a"))
        assertEquals("a", s.load("a")?.assessmentId)
    }

    @Test fun `replacement keeps a single readable file`() = runBlocking {
        val s = store(); s.save(result("a", 0.1f)); s.save(result("a", 0.8f))
        assertEquals(0.8f, s.load("a")!!.coveragePercent, 0f)
        assertEquals(1, File(folder.root, "results").listFiles()!!.size)
    }

    @Test fun `failed save leaves previous result intact`() = runBlocking {
        val s = store(); s.save(result("a", 0.1f))
        val target = File(folder.root, "results").listFiles()!!.single()
        // Block the temporary path with a non-empty directory so the new write fails.
        val tmp = File(target.parentFile, target.name + ".tmp").apply { mkdirs(); File(this, "x").writeText("x") }
        runCatching { s.save(result("a", 0.8f)) }
        tmp.deleteRecursively()
        assertEquals(0.1f, s.load("a")!!.coveragePercent, 0f)
    }

    @Test fun `corrupted file fails to load without crashing the store`() = runBlocking {
        val s = store(); s.save(result("a"))
        File(folder.root, "results").listFiles()!!.single().writeText("not a result")
        assertTrue(runCatching { s.load("a") }.isFailure)
        assertTrue(s.delete("a"))
        assertNull(s.load("a"))
    }

    @Test fun `retainOnly removes orphans and leftovers but keeps referenced results`() = runBlocking {
        val s = store(); s.save(result("keep")); s.save(result("orphan"))
        File(File(folder.root, "results"), "leftover.tmp").writeText("x")
        s.retainOnly(setOf("keep"))
        assertNotNull(s.load("keep"))
        assertFalse(s.exists("orphan"))
        assertEquals(1, File(folder.root, "results").listFiles()!!.size)
    }

    @Test fun `repeated cleanup is safe`() = runBlocking {
        val s = store(); s.save(result("a"))
        s.retainOnly(emptySet()); s.retainOnly(emptySet())
        assertTrue(s.delete("a"))
    }
}
