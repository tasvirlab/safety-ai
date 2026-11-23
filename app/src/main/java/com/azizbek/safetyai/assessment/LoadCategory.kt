package com.azizbek.safetyai.assessment

/** Representative inputs preserve the frozen REBA load thresholds; they are not measured weights. */
fun loadCategoryLabel(loadKg: Double): String = when {
    loadKg < 5.0 -> "No load / <5 kg"
    loadKg <= 10.0 -> "5–10 kg"
    else -> ">10 kg"
}

fun loadDisplay(loadKg: Double, isCategory: Boolean): String =
    if (isCategory) loadCategoryLabel(loadKg)
    else if (loadKg == loadKg.toLong().toDouble()) "${loadKg.toLong()} kg"
    else "$loadKg kg"
