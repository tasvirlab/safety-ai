package com.azizbek.safetyai.analysis

data class RiskTimeSummary(
    val level: RiskLevel,
    val frames: Int,
    val durationMs: Double,
    val percentOfVideo: Double
)

data class CaseStudySummary(
    val totalFrames: Int,
    val scoredFrames: Int,
    val unscoredFrames: Int,
    val videoDurationMs: Double,
    val scoredDurationMs: Double,
    val unscoredDurationMs: Double,
    val minimumScore: Int,
    val minimumFrameIndex: Int,
    val minimumTimestampMs: Double,
    val maximumScore: Int,
    val maximumFrameIndex: Int,
    val maximumTimestampMs: Double,
    val timeWeightedMeanScore: Double,
    val riskTimes: List<RiskTimeSummary>
)

/** Uses video time, including unscored gaps, as the denominator. */
fun SafetyAnalysisResult.caseStudySummary(): CaseStudySummary? {
    val timeline = frameRebaResults ?: return null
    if (timeline.isEmpty() || timeline.size != frames.size) return null
    if (timeline.indices.any { index ->
            timeline[index].frameIndex != index ||
                timeline[index].durationUs < 0L ||
                (index > 0 && timeline[index].timestampUs < timeline[index - 1].timestampUs)
        }) return null

    val scored = timeline.filter { it.valid && it.finalScore != null && it.riskLevel != null }
    if (scored.isEmpty()) return null
    val minimum = scored.minByOrNull { it.finalScore!! }!!
    val maximum = scored.maxByOrNull { it.finalScore!! }!!
    val videoUs = timeline.sumOf { it.durationUs }.toDouble()
    val scoredUs = scored.sumOf { it.durationUs }.toDouble()
    val weightedScore = scored.sumOf { it.finalScore!!.toDouble() * it.durationUs.toDouble() }

    return CaseStudySummary(
        totalFrames = timeline.size,
        scoredFrames = scored.size,
        unscoredFrames = timeline.size - scored.size,
        videoDurationMs = videoUs / 1_000.0,
        scoredDurationMs = scoredUs / 1_000.0,
        unscoredDurationMs = (videoUs - scoredUs) / 1_000.0,
        minimumScore = minimum.finalScore!!,
        minimumFrameIndex = minimum.frameIndex,
        minimumTimestampMs = minimum.timestampUs / 1_000.0,
        maximumScore = maximum.finalScore!!,
        maximumFrameIndex = maximum.frameIndex,
        maximumTimestampMs = maximum.timestampUs / 1_000.0,
        timeWeightedMeanScore = if (scoredUs > 0.0) weightedScore / scoredUs else Double.NaN,
        riskTimes = RiskLevel.values().map { level ->
            val matching = scored.filter { it.riskLevel == level }
            val durationUs = matching.sumOf { it.durationUs }.toDouble()
            RiskTimeSummary(
                level = level,
                frames = matching.size,
                durationMs = durationUs / 1_000.0,
                percentOfVideo = if (videoUs > 0.0) durationUs * 100.0 / videoUs else 0.0
            )
        }
    )
}
