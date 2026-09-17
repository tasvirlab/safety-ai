package com.azizbek.safetyai.assessment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azizbek.safetyai.ui.components.SafetyPrimaryButton
import com.azizbek.safetyai.ui.components.StepProgressBar

private const val TOTAL_WIZARD_STEPS = 5

/** Connects [AssessmentFormViewModel] to the stateless [JobDetailsScreen] — step 1 of 5. */
@Composable
fun JobDetailsRoute(
    viewModel: AssessmentFormViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    JobDetailsScreen(
        state = state,
        onProjectOrSiteChange = viewModel::onProjectOrSiteChange,
        onWorkerReferenceChange = viewModel::onWorkerReferenceChange,
        onTaskTypeSelected = viewModel::onTaskTypeSelected,
        onCustomTaskDescriptionChange = viewModel::onCustomTaskDescriptionChange,
        onBack = onBack,
        onContinue = onContinue
    )
}

/**
 * Step 1 of 5 — task context. Stateless: [state] in, typed callbacks out.
 * Stops at [onContinue] — no camera, file picker, or REBA logic lives here.
 */
@Composable
fun JobDetailsScreen(
    state: AssessmentFormState,
    onProjectOrSiteChange: (String) -> Unit,
    onWorkerReferenceChange: (String) -> Unit,
    onTaskTypeSelected: (TaskType) -> Unit,
    onCustomTaskDescriptionChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var showDiscardDialog by remember { mutableStateOf(false) }

    fun requestBack() {
        if (state.formEdited) showDiscardDialog = true else onBack()
    }

    // Disabled while the dialog is up so a second back-press can't stack a
    // duplicate dialog — the dialog's own onDismissRequest handles back then.
    BackHandler(enabled = !showDiscardDialog) { requestBack() }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { WizardTopBar(title = "Job details", step = 1, onBackClick = ::requestBack) },
        bottomBar = {
            WizardBottomBar(
                label = "Continue",
                enabled = state.isStep1Valid,
                onContinueClick = onContinue
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Sets the posture model and the reference technique used for scoring.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TaskTypeDropdown(
                selected = state.taskType,
                onSelected = onTaskTypeSelected,
                errorText = state.taskTypeError
            )
            if (state.isOtherTaskType) {
                AssessmentTextField(
                    label = "Describe the task",
                    placeholder = "e.g., Pallet jack maneuvering",
                    value = state.customTaskDescription,
                    onValueChange = onCustomTaskDescriptionChange,
                    errorText = state.customTaskError,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )
            }

            AssessmentTextField(
                label = "Work area",
                placeholder = "e.g., Warehouse Zone C",
                value = state.projectOrSite,
                onValueChange = onProjectOrSiteChange,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            AssessmentTextField(
                label = "Worker code",
                placeholder = "e.g., W-A3",
                value = state.workerReference,
                onValueChange = onWorkerReferenceChange,
                supportingText = "Anonymised identifier — no personal data is stored.",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                )
            )
        }
    }

    if (showDiscardDialog) {
        DiscardAssessmentDialog(
            onKeepEditing = { showDiscardDialog = false },
            onDiscard = {
                showDiscardDialog = false
                onBack()
            }
        )
    }
}

/** Connects [AssessmentFormViewModel] to the stateless [LoadCouplingScreen] — step 2 of 5. */
@Composable
fun LoadCouplingRoute(
    viewModel: AssessmentFormViewModel,
    onBack: () -> Unit,
    onContinue: (AssessmentDraft) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoadCouplingScreen(
        state = state,
        onLoadTextChange = viewModel::onLoadTextChange,
        onSuddenForceChange = viewModel::onSuddenForceChange,
        onCouplingSelected = viewModel::onCouplingSelected,
        onBack = onBack,
        onContinue = onContinue
    )
}

/**
 * Step 2 of 5 — the modifiers that adjust the final score but can't be read
 * from video. Stateless: [state] in, typed callbacks out.
 */
@Composable
fun LoadCouplingScreen(
    state: AssessmentFormState,
    onLoadTextChange: (String) -> Unit,
    onSuddenForceChange: (Boolean) -> Unit,
    onCouplingSelected: (CouplingQuality) -> Unit,
    onBack: () -> Unit,
    onContinue: (AssessmentDraft) -> Unit
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { WizardTopBar(title = "Load & coupling", step = 2, onBackClick = onBack) },
        bottomBar = {
            WizardBottomBar(
                label = "Continue",
                enabled = state.isValid,
                onContinueClick = { state.toDraftOrNull()?.let(onContinue) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "These modifiers adjust the final score. They can't be read from video.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AssessmentTextField(
                label = "Load handled",
                placeholder = "0",
                value = state.loadKgText,
                onValueChange = onLoadTextChange,
                errorText = state.loadError,
                supportingText = if (state.loadError == null) {
                    "Enter the approximate load handled during the task."
                } else {
                    null
                },
                suffix = "kg",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
            )

            SuddenForceToggle(checked = state.suddenForce, onCheckedChange = onSuddenForceChange)

            CouplingSelector(
                selected = state.coupling,
                showNotApplicable = state.isZeroLoad,
                errorText = state.couplingError,
                onSelected = onCouplingSelected
            )
        }
    }
}

@Composable
private fun WizardTopBar(title: String, step: Int, onBackClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            Row(modifier = Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Step $step of $TOTAL_WIZARD_STEPS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            StepProgressBar(
                currentStep = step,
                totalSteps = TOTAL_WIZARD_STEPS,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun WizardBottomBar(label: String, enabled: Boolean, onContinueClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SafetyPrimaryButton(
                text = label,
                onClick = onContinueClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }
}

/** Internal (not private): reused as-is by [com.azizbek.safetyai.detail.EditAssessmentScreen] for editing a saved assessment's metadata in one screen. */
@Composable
internal fun AssessmentForm(
    modifier: Modifier = Modifier,
    state: AssessmentFormState,
    onNameChange: (String) -> Unit,
    onProjectOrSiteChange: (String) -> Unit,
    onWorkerReferenceChange: (String) -> Unit,
    onTaskTypeSelected: (TaskType) -> Unit,
    onCustomTaskDescriptionChange: (String) -> Unit,
    onLoadTextChange: (String) -> Unit,
    onSuddenForceChange: (Boolean) -> Unit,
    onCouplingSelected: (CouplingQuality) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Assessment details",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Add task context to organize this ergonomic assessment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AssessmentTextField(
            label = "Assessment name",
            placeholder = "e.g., Floor-to-waist lifting",
            value = state.name,
            onValueChange = onNameChange,
            errorText = state.nameError,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            )
        )

        AssessmentTextField(
            label = "Project or site",
            placeholder = "e.g., Tower A — Level 3",
            value = state.projectOrSite,
            onValueChange = onProjectOrSiteChange,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            )
        )

        AssessmentTextField(
            label = "Worker reference",
            placeholder = "e.g., Worker 04",
            value = state.workerReference,
            onValueChange = onWorkerReferenceChange,
            supportingText = "Use an anonymous reference. Do not enter a worker’s name or medical information.",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            )
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TaskTypeDropdown(
                selected = state.taskType,
                onSelected = onTaskTypeSelected,
                errorText = state.taskTypeError
            )
            if (state.isOtherTaskType) {
                AssessmentTextField(
                    label = "Describe the task",
                    placeholder = "e.g., Pallet jack maneuvering",
                    value = state.customTaskDescription,
                    onValueChange = onCustomTaskDescriptionChange,
                    errorText = state.customTaskError,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )
            }
        }

        AssessmentTextField(
            label = "Load handled",
            placeholder = "0",
            value = state.loadKgText,
            onValueChange = onLoadTextChange,
            errorText = state.loadError,
            supportingText = if (state.loadError == null) {
                "Enter the approximate load handled during the task."
            } else {
                null
            },
            suffix = "kg",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
        )

        SuddenForceToggle(checked = state.suddenForce, onCheckedChange = onSuddenForceChange)

        CouplingSelector(
            selected = state.coupling,
            showNotApplicable = state.isZeroLoad,
            errorText = state.couplingError,
            onSelected = onCouplingSelected
        )
    }
}

@Composable
private fun AssessmentTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    supportingText: String? = null,
    suffix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val message = errorText ?: supportingText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        suffix = suffix?.let { { Text(it) } },
        isError = errorText != null,
        supportingText = message?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.onBackground,
            cursorColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskTypeDropdown(
    selected: TaskType?,
    onSelected: (TaskType) -> Unit,
    errorText: String?
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Task type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected?.label() ?: "",
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Select a task type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = errorText != null,
                supportingText = (errorText ?: "Sets the posture model and the reference technique clip.").let { { Text(it) } },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = MaterialTheme.colorScheme.onBackground
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TaskType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label()) },
                        onClick = {
                            onSelected(type)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuddenForceToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sudden or rapidly applied force",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Enable when force is applied suddenly, with impact, or builds rapidly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // onCheckedChange is null: the whole row above already toggles —
            // wiring the switch too would double-toggle on a direct tap.
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun CouplingSelector(
    selected: CouplingQuality?,
    showNotApplicable: Boolean,
    errorText: String?,
    onSelected: (CouplingQuality) -> Unit
) {
    val options = remember(showNotApplicable) {
        buildList {
            add(CouplingQuality.GOOD)
            add(CouplingQuality.FAIR)
            add(CouplingQuality.POOR)
            add(CouplingQuality.UNACCEPTABLE)
            if (showNotApplicable) add(CouplingQuality.NOT_APPLICABLE)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Grip and coupling",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "How securely can the worker hold or control the load?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                CouplingOptionCard(
                    option = option,
                    selected = option == selected,
                    onClick = { onSelected(option) }
                )
            }
        }
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun CouplingOptionCard(option: CouplingQuality, selected: Boolean, onClick: () -> Unit) {
    // Charcoal, not yellow — a yellow border reads too weakly against a white/light card.
    val borderColor = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                // Charcoal selected dot — a yellow one reads too weakly against white.
                colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onBackground)
            )
            Column {
                Text(
                    text = option.label(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = option.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiscardAssessmentDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Discard this assessment?") },
        text = { Text("The details you entered will not be saved.") },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text("Discard", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text("Keep editing", color = MaterialTheme.colorScheme.onBackground) }
        }
    )
}
