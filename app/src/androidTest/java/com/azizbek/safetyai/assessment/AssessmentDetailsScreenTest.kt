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
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azizbek.safetyai.ui.theme.SafetyAITheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the wizard's step 1 ([JobDetailsScreen]) and step 2 ([LoadCouplingScreen]) — split from one combined form so each screen gets its own coverage. */
@RunWith(AndroidJUnit4::class)
class AssessmentDetailsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Wires a real [AssessmentFormState] driven by the screen's own callbacks, like the ViewModel does. */
    private fun setJobDetailsContent(
        initialState: AssessmentFormState = AssessmentFormState(source = AssessmentSource.CAMERA),
        darkTheme: Boolean = false,
        onBack: () -> Unit = {},
        onContinue: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            SafetyAITheme(darkTheme = darkTheme) {
                var state by remember { mutableStateOf(initialState) }
                fun update(transform: (AssessmentFormState) -> AssessmentFormState) {
                    state = transform(state).copy(formEdited = true)
                }
                JobDetailsScreen(
                    state = state,
                    onProjectOrSiteChange = { update { s -> s.copy(projectOrSite = it) } },
                    onWorkerReferenceChange = { update { s -> s.copy(workerReference = it) } },
                    onTaskTypeSelected = { update { s -> s.copy(taskType = it, taskTypeTouched = true) } },
                    onCustomTaskDescriptionChange = {
                        update { s -> s.copy(customTaskDescription = it, customTaskTouched = true) }
                    },
                    onBack = onBack,
                    onContinue = onContinue
                )
            }
        }
    }

    private fun setLoadCouplingContent(
        initialState: AssessmentFormState = AssessmentFormState(source = AssessmentSource.CAMERA, taskType = TaskType.LIFTING),
        darkTheme: Boolean = false,
        onBack: () -> Unit = {},
        onContinue: (AssessmentDraft) -> Unit = {}
    ) {
        composeTestRule.setContent {
            SafetyAITheme(darkTheme = darkTheme) {
                var state by remember { mutableStateOf(initialState) }
                fun update(transform: (AssessmentFormState) -> AssessmentFormState) {
                    state = transform(state).copy(formEdited = true)
                }
                LoadCouplingScreen(
                    state = state,
                    onLoadTextChange = { update { s -> s.withLoadText(it) } },
                    onLoadCategorySelected = { update { s -> s.withLoadCategory(it) } },
                    onSuddenForceChange = { update { s -> s.copy(suddenForce = it) } },
                    onCouplingSelected = { update { s -> s.copy(coupling = it, couplingTouched = true) } },
                    onBack = onBack,
                    onContinue = onContinue
                )
            }
        }
    }

    @Test
    fun jobDetails_titleAndStep_areCorrect() {
        setJobDetailsContent()
        composeTestRule.onNodeWithText("Task details").assertIsDisplayed()
        composeTestRule.onNodeWithText("Step 1 of 5").assertIsDisplayed()
    }

    @Test
    fun jobDetails_continueButton_isInitiallyDisabled() {
        setJobDetailsContent()
        composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun jobDetails_otherTaskField_isOnlyVisibleWhenOtherSelected() {
        setJobDetailsContent()
        composeTestRule.onNodeWithText("Describe the task").assertDoesNotExist()
        composeTestRule.onNodeWithText("Select a task").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Other construction task").performClick()
        composeTestRule.onNodeWithText("Describe the task").assertIsDisplayed()
    }

    @Test
    fun jobDetails_darkTheme_rendersWithoutCrashing() {
        setJobDetailsContent(darkTheme = true)
        composeTestRule.onNodeWithText("Task details").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work area").assertIsDisplayed()
    }

    @Test
    fun jobDetails_backWithoutEdits_returnsImmediately() {
        var backInvoked = false
        setJobDetailsContent(onBack = { backInvoked = true })
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backInvoked)
        composeTestRule.onNodeWithText("Discard this assessment?").assertDoesNotExist()
    }

    @Test
    fun jobDetails_backAfterEdit_showsDiscardDialog_keepEditingDismissesWithoutNavigating() {
        var backInvoked = false
        setJobDetailsContent(onBack = { backInvoked = true })
        composeTestRule.onNodeWithText("Worker code").performTextInput("W-1")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Discard this assessment?").assertExists()
        composeTestRule.onNodeWithText("Keep editing").performClick()
        composeTestRule.onNodeWithText("Discard this assessment?").assertDoesNotExist()
        assertFalse(backInvoked)
    }

    @Test
    fun jobDetails_backAfterEdit_discardInvokesOnBack() {
        var backInvoked = false
        setJobDetailsContent(onBack = { backInvoked = true })
        composeTestRule.onNodeWithText("Worker code").performTextInput("W-1")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Discard this assessment?").assertExists()
        composeTestRule.onNodeWithText("Discard").performClick()
        assertTrue(backInvoked)
    }

    @Test
    fun loadCoupling_titleAndStep_areCorrect() {
        setLoadCouplingContent()
        composeTestRule.onNodeWithText("Load & coupling").assertIsDisplayed()
        composeTestRule.onNodeWithText("Step 2 of 5").assertIsDisplayed()
    }

    @Test
    fun loadCoupling_continueButton_isInitiallyDisabled() {
        setLoadCouplingContent()
        composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun loadCoupling_backInvokesCallback_withNoDialog() {
        var backInvoked = false
        setLoadCouplingContent(onBack = { backInvoked = true })
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backInvoked)
    }

    @Test
    fun loadCoupling_fillingLoadAndCoupling_enablesContinueAndProducesDraft() {
        var producedDraft: AssessmentDraft? = null
        setLoadCouplingContent(onContinue = { producedDraft = it })
        composeTestRule.onNodeWithText("5–10 kg").performClick()
        composeTestRule.onNodeWithText("Good").performClick()
        composeTestRule.onNodeWithText("Next").performClick()
        assertTrue(producedDraft != null)
        assertTrue(producedDraft?.taskType == TaskType.LIFTING)
        assertTrue(producedDraft?.loadIsCategory == true)
    }
}
