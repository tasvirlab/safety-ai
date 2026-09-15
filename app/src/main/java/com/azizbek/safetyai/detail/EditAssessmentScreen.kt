package com.azizbek.safetyai.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azizbek.safetyai.assessment.AssessmentForm
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.ui.components.SafetyPrimaryButton

@Composable
fun EditAssessmentScreen(
    repository: AssessmentRepository,
    assessmentId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditAssessmentViewModel = viewModel(factory = EditAssessmentViewModel.factory(repository, assessmentId))
) {
    val state by viewModel.formState.collectAsState()

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(modifier = Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Text("Edit assessment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SafetyPrimaryButton(
                        text = "Save changes",
                        onClick = { viewModel.save(onSaved) },
                        enabled = state?.isValid == true,
                        modifier = Modifier.fillMaxWidth().padding(20.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        val formState = state
        if (formState == null) {
            Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            AssessmentForm(
                modifier = Modifier.padding(innerPadding),
                state = formState,
                onNameChange = viewModel::onNameChange,
                onProjectOrSiteChange = viewModel::onProjectOrSiteChange,
                onWorkerReferenceChange = viewModel::onWorkerReferenceChange,
                onTaskTypeSelected = viewModel::onTaskTypeSelected,
                onCustomTaskDescriptionChange = viewModel::onCustomTaskDescriptionChange,
                onLoadTextChange = viewModel::onLoadTextChange,
                onSuddenForceChange = viewModel::onSuddenForceChange,
                onCouplingSelected = viewModel::onCouplingSelected
            )
        }
    }
}
