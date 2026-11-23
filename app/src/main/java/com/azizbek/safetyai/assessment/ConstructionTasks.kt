package com.azizbek.safetyai.assessment

import com.azizbek.safetyai.R

/** Old enum members remain readable for assessments saved before the construction UI. */
val constructionTaskChoices = listOf(
    TaskType.MATERIAL_LIFTING,
    TaskType.REBAR_TYING,
    TaskType.ROOFING_WORK,
    TaskType.OVERHEAD_FASTENING,
    TaskType.WALL_PAINTING,
    TaskType.OTHER
)

fun TaskType.constructionDescription(): String = when (this) {
    TaskType.MATERIAL_LIFTING -> "Lift and position bags, blocks, panels or other materials."
    TaskType.REBAR_TYING -> "Tie reinforcement bars while standing, bending or kneeling."
    TaskType.ROOFING_WORK -> "Perform roof installation tasks; only posture is assessed."
    TaskType.OVERHEAD_FASTENING -> "Fasten materials above shoulder height."
    TaskType.WALL_PAINTING -> "Paint a wall using repeated arm movements."
    TaskType.OTHER -> "Describe another construction task before recording."
    else -> "Previously recorded task. Your saved assessment remains available."
}

fun TaskType.constructionImage(otherImageIndex: Int): Int = when (this) {
    TaskType.MATERIAL_LIFTING, TaskType.LIFTING, TaskType.CARRYING,
    TaskType.MATERIAL_HANDLING -> R.drawable.task_material_lifting
    TaskType.REBAR_TYING -> R.drawable.task_rebar_tying
    TaskType.ROOFING_WORK -> R.drawable.task_roofing_work
    TaskType.OVERHEAD_FASTENING, TaskType.OVERHEAD_WORK -> R.drawable.task_overhead_fastening
    TaskType.WALL_PAINTING -> R.drawable.task_wall_painting
    else -> when (otherImageIndex.coerceIn(0, 2)) {
        0 -> R.drawable.other_task_variant_1_masonry
        1 -> R.drawable.other_task_variant_2_pipe_fitting
        else -> R.drawable.other_task_variant_3_concrete_finishing
    }
}
