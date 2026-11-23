package com.azizbek.safetyai.assessment

/**
 * Raw New Assessment form state plus its validation — deliberately plain
 * Kotlin (no Compose, no Android) so it's cheap to unit test and easy to
 * persist via [androidx.lifecycle.SavedStateHandle] (implements
 * [java.io.Serializable] for exactly that). `*Touched` flags gate inline
 * errors so nothing shows red before the user has interacted with a field.
 */
data class AssessmentFormState(
    val source: AssessmentSource,
    val name: String = "",
    val nameTouched: Boolean = false,
    val projectOrSite: String = "",
    val workerReference: String = "",
    val taskType: TaskType? = null,
    val otherImageIndex: Int = kotlin.random.Random.nextInt(3),
    val taskTypeTouched: Boolean = false,
    val customTaskDescription: String = "",
    val customTaskTouched: Boolean = false,
    val loadKgText: String = "",
    val loadIsCategory: Boolean = false,
    val loadTouched: Boolean = false,
    val suddenForce: Boolean = false,
    val coupling: CouplingQuality? = null,
    val couplingTouched: Boolean = false,
    /** True the moment any field changes — drives the discard-confirmation on Back. */
    val formEdited: Boolean = false
) : java.io.Serializable {

    private val trimmedName: String get() = name.trim()
    val isNameValid: Boolean get() = trimmedName.isNotEmpty() && trimmedName.length <= MAX_NAME_LENGTH
    val nameError: String?
        get() = when {
            !nameTouched -> null
            trimmedName.isEmpty() -> "Enter an assessment name"
            trimmedName.length > MAX_NAME_LENGTH -> "Keep the name under $MAX_NAME_LENGTH characters"
            else -> null
        }

    val isOtherTaskType: Boolean get() = taskType == TaskType.OTHER
    val taskTypeError: String?
        get() = if (taskTypeTouched && taskType == null) "Select a task type" else null

    private val trimmedCustomTask: String get() = customTaskDescription.trim()
    val isCustomTaskValid: Boolean get() = !isOtherTaskType || trimmedCustomTask.isNotEmpty()
    val customTaskError: String?
        get() = when {
            !isOtherTaskType -> null
            !customTaskTouched -> null
            trimmedCustomTask.isEmpty() -> "Describe the task"
            else -> null
        }

    /** Parsed kg value, or null when the text isn't a valid number. Accepts "," or "." as the decimal separator. */
    val loadKg: Double? get() = loadKgText.trim().replace(',', '.').toDoubleOrNull()
    val isLoadValid: Boolean get() = loadKg?.let { it.isFinite() && it >= 0.0 } == true
    val loadError: String?
        get() = when {
            !loadTouched -> null
            loadKgText.trim().isEmpty() -> "Enter the load handled"
            loadKg == null -> "Enter a valid number"
            loadKg!! < 0.0 -> "Load can't be negative"
            else -> null
        }

    /** Whether the "Not applicable" coupling option should be offered. */
    val isZeroLoad: Boolean get() = loadKg == 0.0

    // NOT_APPLICABLE is only a valid choice at zero load — the ViewModel clears
    // it as soon as the load changes away from zero, but this is checked here
    // too so a directly-constructed state can never be considered valid with
    // a positive load paired to "Not applicable".
    val isCouplingValid: Boolean
        get() = coupling != null && (coupling != CouplingQuality.NOT_APPLICABLE || isZeroLoad)
    val couplingError: String?
        get() = when {
            coupling == CouplingQuality.NOT_APPLICABLE && !isZeroLoad -> "Not applicable requires a zero load"
            !couplingTouched -> null
            coupling == null -> "Select a coupling quality"
            else -> null
        }

    /** Step 1 (Job details) is complete once name + task type are set — load/coupling belong to step 2. */
    val isStep1Valid: Boolean
        get() = isNameValid && taskType != null && isCustomTaskValid

    val isValid: Boolean
        get() = isStep1Valid && isLoadValid && isCouplingValid

    /** Applies a new load-text value, clearing a "Not applicable" coupling choice that a nonzero load would make invalid. */
    fun withLoadText(value: String): AssessmentFormState {
        val next = copy(loadKgText = value, loadTouched = true, loadIsCategory = false)
        return if (next.coupling == CouplingQuality.NOT_APPLICABLE && !next.isZeroLoad) {
            next.copy(coupling = null)
        } else {
            next
        }
    }

    fun withLoadCategory(value: String): AssessmentFormState =
        withLoadText(value).copy(loadIsCategory = true)

    /** The typed, immutable draft — only non-null when the whole form validates. */
    fun toDraftOrNull(): AssessmentDraft? {
        if (!isValid) return null
        val type = taskType ?: return null
        val load = loadKg ?: return null
        val couplingValue = coupling ?: return null
        return AssessmentDraft(
            source = source,
            name = trimmedName,
            projectOrSite = projectOrSite.trim().ifEmpty { null },
            workerReference = workerReference.trim().ifEmpty { null },
            taskType = type,
            customTaskDescription = if (type == TaskType.OTHER) trimmedCustomTask else null,
            loadKg = load,
            suddenForce = suddenForce,
            coupling = couplingValue,
            loadIsCategory = loadIsCategory
        )
    }

    companion object {
        const val MAX_NAME_LENGTH = 80
    }
}
