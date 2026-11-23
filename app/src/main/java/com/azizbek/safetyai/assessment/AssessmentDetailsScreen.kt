package com.azizbek.safetyai.assessment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
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
        topBar = { WizardTopBar(title = "Task details", step = 1, onBackClick = ::requestBack) },
        bottomBar = {
            WizardBottomBar(
                label = "Next",
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
                text = "Choose the construction task shown in the video. This does not change the pose model or validate task-specific scoring.",
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
                    placeholder = "e.g., Installing formwork",
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
                placeholder = "e.g., Building A, Level 2",
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
                supportingText = "Use an anonymous code. Videos and task details are stored on this device.",
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
        onLoadCategorySelected = viewModel::onLoadCategorySelected,
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
    onLoadCategorySelected: (String) -> Unit = onLoadTextChange,
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
                label = "Next",
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
            TaskImageCard(state)

            Text(
                text = "Load, force and coupling affect the final REBA score. Enter values observed during this task.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LoadCategorySelector(
                loadKg = state.loadKg,
                onSelected = onLoadCategorySelected
            )
            state.loadError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            SuddenForceToggle(checked = state.suddenForce, onCheckedChange = onSuddenForceChange)

            CouplingSelector(
                selected = state.coupling,
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
    onLoadCategorySelected: (String) -> Unit = onLoadTextChange,
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
                    placeholder = "e.g., Installing formwork",
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

        LoadCategorySelector(state.loadKg, onLoadCategorySelected)
        val savedLoadKg = state.loadKg
        if (!state.loadIsCategory && savedLoadKg != null) {
            Text("Previously saved exact load: ${loadDisplay(savedLoadKg, false)}. Select a category to change it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SuddenForceToggle(checked = state.suddenForce, onCheckedChange = onSuddenForceChange)

        CouplingSelector(
            selected = state.coupling,
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

@Composable
private fun TaskImageCard(state: AssessmentFormState) {
    val type = state.taskType ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(
            painter = painterResource(type.constructionImage(state.otherImageIndex)),
            contentDescription = "Illustration of ${type.label()}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(16.dp))
        )
        Text(type.label(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(type.constructionDescription(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (type == TaskType.OTHER) {
            Text("Illustrative image", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskTypeDropdown(
    selected: TaskType?,
    onSelected: (TaskType) -> Unit,
    errorText: String?
) {
    var expanded by remember { mutableStateOf(false) }
    val options = if (selected != null && selected !in constructionTaskChoices) {
        listOf(selected) + constructionTaskChoices
    } else constructionTaskChoices
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Construction task", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                .clickable(role = Role.Button) { expanded = !expanded },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selected?.label() ?: "Select a task", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Collapse tasks" else "Expand tasks")
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { type ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
                            .selectable(selected = selected == type, role = Role.RadioButton, onClick = {
                                onSelected(type)
                                expanded = false
                            }),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected == type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected == type) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(type.label(), style = MaterialTheme.typography.titleSmall)
                                Text(type.constructionDescription(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = selected == type, onClick = null)
                        }
                    }
                }
            }
        }
        if (errorText != null) Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Text("Task selection describes the recording; it does not select a separate model.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadCategorySelector(loadKg: Double?, onSelected: (String) -> Unit) {
    val choices = listOf(
        "No load / <5 kg" to "0",
        "5–10 kg" to "5",
        ">10 kg" to "11"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Load / force", style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEachIndexed { index, (label, preset) ->
                val selected = loadKg != null && when (index) {
                    0 -> loadKg < 5.0
                    1 -> loadKg in 5.0..10.0
                    else -> loadKg > 10.0
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
                        .selectable(selected = selected, onClick = { onSelected(preset) }, role = Role.RadioButton),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Text("Select the load range; sudden force is selected separately.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    errorText: String?,
    onSelected: (CouplingQuality) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Coupling / grip", style = MaterialTheme.typography.titleSmall)
        Text("How securely can the worker hold or control the load?",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CouplingQuality.GOOD, CouplingQuality.FAIR, CouplingQuality.POOR).forEach { option ->
                    CouplingOptionCard(option, option == selected, { onSelected(option) }, Modifier.weight(1f))
                }
            }
        }
        selected?.let {
            Text(it.description(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (errorText != null) {
            Text(errorText, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CouplingOptionCard(
    option: CouplingQuality,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 52.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp,
            if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline)
    ) {
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Text(option.label(), style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, maxLines = 2,
                color = MaterialTheme.colorScheme.onBackground)
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
