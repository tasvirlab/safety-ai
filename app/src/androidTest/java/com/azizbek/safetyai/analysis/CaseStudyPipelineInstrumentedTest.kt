package com.azizbek.safetyai.analysis

import androidx.test.platform.app.InstrumentationRegistry
import com.azizbek.safetyai.assessment.AssessmentSource
import com.azizbek.safetyai.assessment.CouplingQuality
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.data.result.AnalysisResultStore
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.VideoMetadata
import com.azizbek.safetyai.reports.CaseStudyExport
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Runs the complete production pipeline on an explicitly selected long pilot recording. */
class CaseStudyPipelineInstrumentedTest {
    @Test fun longPilotAnalyzesPersistsAndExports() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val fileName = requireNotNull(args.getString("videoFileName")) { "Provide videoFileName" }
        require(File(fileName).name == fileName) { "Provide a filename inside files/videos" }
        val task = TaskType.valueOf(requireNotNull(args.getString("taskType")) { "Provide taskType" })
        val loadKg = requireNotNull(args.getString("loadKg")) { "Provide loadKg" }.toDouble()
        val coupling = CouplingQuality.valueOf(requireNotNull(args.getString("coupling")) { "Provide coupling" })
        val sudden = requireNotNull(args.getString("suddenForce")) { "Provide suddenForce" }.toBooleanStrict()
        val video = File(File(context.filesDir, "videos"), fileName)
        val info = VideoFrameDecoder().inspect(video)
        require(info.durationUs >= 65_000_000L) { "Use a pilot recording of at least 65 seconds" }
        val id = "freeze-pilot-${System.currentTimeMillis()}"
        val store = AnalysisResultStore(context)
        val engine = OnDeviceSafetyAnalysisEngine(context)
        try {
            val outcome = engine.analyze(id, video, AnalysisInputContext(loadKg, sudden, coupling, task)) {}
            val result = (outcome as? AnalysisProgress.Completed)?.result
                ?: error("Pipeline failed: ${(outcome as? AnalysisProgress.Failed)?.reason ?: "no completed result"}")
            assertFalse("Pilot pose geometry was unreliable; re-record with a clear view",
                result.inputQualityChecks.any { !it.passed && it.description == "2D and 3D shoulder geometry is reliable" })
            val provenance = requireNotNull(result.provenance)
            assertEquals(FrozenPipeline.ID, provenance.pipelineId)
            assertEquals(FrozenPipeline.configurationHash(provenance.configuration), provenance.configurationSha256)
            assertEquals(FrozenPipeline.modelHashes(), provenance.modelSha256)
            assertTrue(provenance.sourceVideoSha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(provenance.runtime.getValue("native_scan_sha256").matches(Regex("[0-9a-f]{64}")))
            val timeline = requireNotNull(result.frameRebaResults)
            assertEquals(result.frames.size, timeline.size)
            assertTrue(timeline.any { it.valid })
            timeline.filter { it.valid }.forEach {
                assertEquals(requireNotNull(it.tableCScore) + it.activityScore, it.finalScore)
            }
            store.save(result)
            val loaded = requireNotNull(store.load(id))
            assertEquals(result.provenance, loaded.provenance)
            assertEquals(result.frameRebaResults, loaded.frameRebaResults)
            val assessment = Assessment(id, "Freeze pilot", "Controlled lab", "PILOT", task,
                null, loadKg, sudden, coupling, AssessmentSource.CAMERA,
                VideoMetadata(video.absolutePath, info.durationUs / 1000L, info.widthPx, info.heightPx, video.length()),
                AssessmentStatus.COMPLETED, 0L, 0L)
            val export = CaseStudyExport.generate(context, assessment, loaded)
            val summary = ZipFile(export).use { zip ->
                JSONObject(zip.getInputStream(zip.getEntry("summary.json")).bufferedReader().use { it.readText() })
            }
            assertEquals(FrozenPipeline.ID, summary.getString("pipeline_id"))
            assertEquals(provenance.sourceVideoSha256, summary.getString("source_video_sha256"))
            summary.put("smoke_test_passed", true)
                .put("persistence_round_trip_passed", true)
                .put("export_file", export.name)
            File(context.cacheDir, "freeze_pipeline_smoke_report.json").writeText(summary.toString(2))
        } finally {
            engine.close()
            store.delete(id)
        }
    }
}
