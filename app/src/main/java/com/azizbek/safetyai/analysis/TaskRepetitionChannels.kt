package com.azizbek.safetyai.analysis

import com.azizbek.safetyai.assessment.TaskType

/**
 * Candidate angle channels for the action described by each construction task.
 * These choices are provisional outside lifting and require video annotation
 * before task-specific repetition accuracy can be claimed.
 */
internal object TaskRepetitionChannels {
    fun forTask(task: TaskType?): List<String>? = when (task) {
        TaskType.LIFTING, TaskType.MATERIAL_LIFTING -> listOf(
            "trunk_sagittal_deg"
        )
        TaskType.REBAR_TYING -> listOf(
            "lower_arm_r_deg", "lower_arm_l_deg", "wrist_r_deg", "wrist_l_deg"
        )
        TaskType.ROOFING_WORK -> listOf(
            "upper_arm_r_sagittal_deg", "upper_arm_l_sagittal_deg",
            "lower_arm_r_deg", "lower_arm_l_deg"
        )
        TaskType.OVERHEAD_FASTENING -> listOf(
            "upper_arm_r_sagittal_deg", "upper_arm_l_sagittal_deg"
        )
        TaskType.WALL_PAINTING -> listOf(
            "upper_arm_r_sagittal_deg", "upper_arm_l_sagittal_deg",
            "upper_arm_r_abd_deg", "upper_arm_l_abd_deg"
        )
        // Other and legacy task types retain the previous all-channel selection.
        else -> null
    }
}
