package com.azizbek.safetyai.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A form that would be valid but for the one field under test. */
private fun validState(source: AssessmentSource = AssessmentSource.CAMERA) = AssessmentFormState(
    source = source,
    name = "Floor-to-waist lifting",
    nameTouched = true,
    taskType = TaskType.LIFTING,
    taskTypeTouched = true,
    loadKgText = "12.5",
    loadTouched = true,
    coupling = CouplingQuality.GOOD,
    couplingTouched = true
)

class AssessmentFormStateTest {

    @Test
    fun `required assessment name - blank name is invalid`() {
        val state = validState().copy(name = "", nameTouched = true)
        assertFalse(state.isNameValid)
        assertFalse(state.isValid)
        assertNotNull(state.nameError)
        assertNull(state.toDraftOrNull())
    }

    @Test
    fun `whitespace-only name is invalid`() {
        val state = validState().copy(name = "   ", nameTouched = true)
        assertFalse(state.isNameValid)
        assertNotNull(state.nameError)
    }

    @Test
    fun `name error is hidden until the field is touched`() {
        val state = validState().copy(name = "", nameTouched = false)
        assertNull(state.nameError)
    }

    @Test
    fun `required task type - null task type is invalid`() {
        val state = validState().copy(taskType = null, taskTypeTouched = true)
        assertFalse(state.isValid)
        assertNotNull(state.taskTypeError)
        assertNull(state.toDraftOrNull())
    }

    @Test
    fun `other task without custom text is invalid`() {
        val state = validState().copy(
            taskType = TaskType.OTHER,
            taskTypeTouched = true,
            customTaskDescription = "",
            customTaskTouched = true
        )
        assertFalse(state.isCustomTaskValid)
        assertFalse(state.isValid)
        assertNotNull(state.customTaskError)
    }

    @Test
    fun `other task with custom text is valid`() {
        val state = validState().copy(
            taskType = TaskType.OTHER,
            customTaskDescription = "Pallet jack maneuvering",
            customTaskTouched = true
        )
        assertTrue(state.isCustomTaskValid)
        assertNull(state.customTaskError)
        assertTrue(state.isValid)
    }

    @Test
    fun `negative load is invalid`() {
        val state = validState().copy(loadKgText = "-5", loadTouched = true)
        assertFalse(state.isLoadValid)
        assertFalse(state.isValid)
        assertNotNull(state.loadError)
    }

    @Test
    fun `invalid numeric load is invalid`() {
        val state = validState().copy(loadKgText = "not-a-number", loadTouched = true)
        assertFalse(state.isLoadValid)
        assertNotNull(state.loadError)
        assertNull(state.loadKg)
    }

    @Test
    fun `blank load is invalid`() {
        val state = validState().copy(loadKgText = "", loadTouched = true)
        assertFalse(state.isLoadValid)
        assertNotNull(state.loadError)
    }

    @Test
    fun `valid zero load is valid`() {
        val state = validState().copy(loadKgText = "0", loadTouched = true)
        assertTrue(state.isLoadValid)
        assertNull(state.loadError)
        assertTrue(state.isZeroLoad)
        assertEquals(0.0, state.loadKg)
    }

    @Test
    fun `zero load does not make the form valid on its own`() {
        // A zero load is a legitimate value, not an excuse to skip the rest
        // of validation.
        val state = validState().copy(
            loadKgText = "0",
            loadTouched = true,
            coupling = null,
            couplingTouched = true
        )
        assertTrue(state.isLoadValid)
        assertFalse(state.isValid)
    }

    @Test
    fun `coupling requirement - null coupling invalid even when everything else is valid`() {
        val state = validState().copy(coupling = null, couplingTouched = true)
        assertFalse(state.isCouplingValid)
        assertFalse(state.isValid)
        assertNotNull(state.couplingError)
        assertNull(state.toDraftOrNull())
    }

    @Test
    fun `not-applicable coupling is a valid explicit choice`() {
        val state = validState().copy(
            loadKgText = "0",
            loadTouched = true,
            coupling = CouplingQuality.NOT_APPLICABLE
        )
        assertTrue(state.isValid)
        assertEquals(CouplingQuality.NOT_APPLICABLE, state.toDraftOrNull()?.coupling)
    }

    @Test
    fun `valid form construction into AssessmentDraft`() {
        val state = validState().copy(
            name = "  Floor-to-waist lifting  ",
            projectOrSite = "  Tower A — Level 3  ",
            workerReference = "  Worker 04  ",
            suddenForce = true
        )
        val draft = state.toDraftOrNull()
        assertNotNull(draft)
        checkNotNull(draft)
        assertEquals("Floor-to-waist lifting", draft.name)
        assertEquals("Tower A — Level 3", draft.projectOrSite)
        assertEquals("Worker 04", draft.workerReference)
        assertEquals(TaskType.LIFTING, draft.taskType)
        assertNull(draft.customTaskDescription)
        assertEquals(12.5, draft.loadKg, 0.0)
        assertTrue(draft.suddenForce)
        assertEquals(CouplingQuality.GOOD, draft.coupling)
    }

    @Test
    fun `blank optional fields become null in the draft`() {
        val state = validState().copy(projectOrSite = "   ", workerReference = "")
        val draft = state.toDraftOrNull()
        assertNotNull(draft)
        checkNotNull(draft)
        assertNull(draft.projectOrSite)
        assertNull(draft.workerReference)
    }

    @Test
    fun `source is preserved into the draft`() {
        val cameraDraft = validState(source = AssessmentSource.CAMERA).toDraftOrNull()
        val uploadDraft = validState(source = AssessmentSource.VIDEO_UPLOAD).toDraftOrNull()
        assertEquals(AssessmentSource.CAMERA, cameraDraft?.source)
        assertEquals(AssessmentSource.VIDEO_UPLOAD, uploadDraft?.source)
    }

    @Test
    fun `custom task description is only kept in the draft when Other is selected`() {
        val state = validState().copy(customTaskDescription = "stale leftover text")
        val draft = state.toDraftOrNull()
        assertNotNull(draft)
        assertNull(draft?.customTaskDescription)
    }

    @Test
    fun `decimal load accepts a dot separator`() {
        val state = validState().copy(loadKgText = "12.5", loadTouched = true)
        assertTrue(state.isLoadValid)
        assertEquals(12.5, state.loadKg)
    }

    @Test
    fun `decimal load accepts a comma separator`() {
        val state = validState().copy(loadKgText = "12,5", loadTouched = true)
        assertTrue(state.isLoadValid)
        assertEquals(12.5, state.loadKg)
    }

    @Test
    fun `positive load combined with Not Applicable coupling is invalid`() {
        val state = validState().copy(
            loadKgText = "10",
            loadTouched = true,
            coupling = CouplingQuality.NOT_APPLICABLE
        )
        assertFalse(state.isCouplingValid)
        assertFalse(state.isValid)
        assertNotNull(state.couplingError)
        assertNull(state.toDraftOrNull())
    }

    @Test
    fun `withLoadText clears Not Applicable coupling once load leaves zero`() {
        val zeroLoadState = validState().copy(
            loadKgText = "0",
            loadTouched = true,
            coupling = CouplingQuality.NOT_APPLICABLE
        )
        val updated = zeroLoadState.withLoadText("5")
        assertNull(updated.coupling)
        assertEquals(5.0, updated.loadKg)
    }

    @Test
    fun `withLoadText keeps Not Applicable coupling while load stays zero`() {
        val zeroLoadState = validState().copy(
            loadKgText = "0",
            loadTouched = true,
            coupling = CouplingQuality.NOT_APPLICABLE
        )
        val updated = zeroLoadState.withLoadText("0.0")
        assertEquals(CouplingQuality.NOT_APPLICABLE, updated.coupling)
    }
}
