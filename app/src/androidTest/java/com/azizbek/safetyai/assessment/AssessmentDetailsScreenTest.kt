package com.azizbek.safetyai.assessment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azizbek.safetyai.ui.theme.SafetyAITheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssessmentDetailsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Wires a real [AssessmentFormState] driven by the screen's own callbacks, like the ViewModel does. */
    private fun setContent(
        initialState: AssessmentFormState = AssessmentFormState(source = AssessmentSource.CAMERA),
        darkTheme: Boolean = false,
        onBack: () -> Unit = {},
        onContinue: (AssessmentDraft) -> Unit = {}
    ) {
        composeTestRule.setContent {
            SafetyAITheme(darkTheme = darkTheme) {
                var state by remember { mutableStateOf(initialState) }
                // Mirrors AssessmentFormViewModel.update(): every field change marks the
                // form edited, same as the real ViewModel does.
                fun update(transform: (AssessmentFormState) -> AssessmentFormState) {
                    state = transform(state).copy(formEdited = true)
                }
                AssessmentDetailsScreen(
                    state = state,
                    onNameChange = { update { s -> s.copy(name = it, nameTouched = true) } },
                    onProjectOrSiteChange = { update { s -> s.copy(projectOrSite = it) } },
                    onWorkerReferenceChange = { update { s -> s.copy(workerReference = it) } },
                    onTaskTypeSelected = { update { s -> s.copy(taskType = it, taskTypeTouched = true) } },
                    onCustomTaskDescriptionChange = {
                        update { s -> s.copy(customTaskDescription = it, customTaskTouched = true) }
                    },
                    onLoadTextChange = { update { s -> s.copy(loadKgText = it, loadTouched = true) } },
                    onSuddenForceChange = { update { s -> s.copy(suddenForce = it) } },
                    onCouplingSelected = { update { s -> s.copy(coupling = it, couplingTouched = true) } },
                    onBack = onBack,
                    onContinue = onContinue
                )
            }
        }
    }

    @Test
    fun titleAndSourceLabel_areCorrect() {
        setContent(initialState = AssessmentFormState(source = AssessmentSource.VIDEO_UPLOAD))
        composeTestRule.onNodeWithText("New assessment").assertIsDisplayed()
        composeTestRule.onNodeWithText("Video upload").assertIsDisplayed()
    }

    @Test
    fun continueButton_isInitiallyDisabled() {
        setContent(initialState = AssessmentFormState(source = AssessmentSource.CAMERA))
        composeTestRule.onNodeWithText("Continue to camera").assertIsNotEnabled()
    }

    @Test
    fun nameField_showsErrorOnlyAfterInteraction() {
        setContent()
        composeTestRule.onNodeWithText("Enter an assessment name").assertDoesNotExist()
        composeTestRule.onNodeWithText("Assessment name").performTextInput("Test")
        composeTestRule.onNodeWithText("Assessment name").performTextClearance()
        composeTestRule.onNodeWithText("Enter an assessment name").assertIsDisplayed()
    }

    @Test
    fun otherTaskField_isOnlyVisibleWhenOtherSelected() {
        setContent()
        composeTestRule.onNodeWithText("Describe the task").assertDoesNotExist()
        composeTestRule.onNodeWithText("Task type").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Other").performClick()
        composeTestRule.onNodeWithText("Describe the task").assertIsDisplayed()
    }

    @Test
    fun darkTheme_rendersWithoutCrashing() {
        setContent(darkTheme = true)
        composeTestRule.onNodeWithText("New assessment").assertIsDisplayed()
        composeTestRule.onNodeWithText("Assessment details").assertIsDisplayed()
    }

    @Test
    fun backWithoutEdits_returnsImmediately() {
        var backInvoked = false
        setContent(onBack = { backInvoked = true })
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backInvoked)
        composeTestRule.onNodeWithText("Discard this assessment?").assertDoesNotExist()
    }

    @Test
    fun backAfterEdit_showsDiscardDialog_keepEditingDismissesWithoutNavigating() {
        var backInvoked = false
        setContent(onBack = { backInvoked = true })
        composeTestRule.onNodeWithText("Assessment name").performTextInput("Test")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Discard this assessment?").assertExists()
        composeTestRule.onNodeWithText("Keep editing").performClick()
        composeTestRule.onNodeWithText("Discard this assessment?").assertDoesNotExist()
        assertFalse(backInvoked)
    }

    @Test
    fun backAfterEdit_discardInvokesOnBack() {
        var backInvoked = false
        setContent(onBack = { backInvoked = true })
        composeTestRule.onNodeWithText("Assessment name").performTextInput("Test")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Discard this assessment?").assertExists()
        composeTestRule.onNodeWithText("Discard").performClick()
        assertTrue(backInvoked)
    }
}
