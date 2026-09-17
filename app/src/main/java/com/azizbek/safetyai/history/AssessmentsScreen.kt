package com.azizbek.safetyai.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azizbek.safetyai.assessment.TaskType
import com.azizbek.safetyai.assessment.label
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.domain.Assessment
import com.azizbek.safetyai.domain.AssessmentStatus
import com.azizbek.safetyai.domain.label
import com.azizbek.safetyai.ui.components.AssessmentCard

/** The real, Room-backed "Assessments" tab: search, filter, sort over every saved assessment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentsScreen(
    repository: AssessmentRepository,
    onOpenAssessment: (String) -> Unit,
    onStartAssessment: () -> Unit,
    viewModel: AssessmentsViewModel = viewModel(factory = AssessmentsViewModel.factory(repository))
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val taskTypeFilter by viewModel.taskTypeFilter.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val newestFirst by viewModel.sortNewestFirst.collectAsState()
    var pendingDelete by remember { mutableStateOf<Assessment?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Assessments", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.searchQuery.value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by name, site, or worker reference") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = MaterialTheme.colorScheme.onBackground
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskTypeFilterChip(selected = taskTypeFilter, onSelected = { viewModel.taskTypeFilter.value = it })
                StatusFilterChip(selected = statusFilter, onSelected = { viewModel.statusFilter.value = it })
                FilterChip(
                    selected = false,
                    onClick = { viewModel.sortNewestFirst.value = !newestFirst },
                    label = { Text(if (newestFirst) "Newest first" else "Oldest first") }
                )
            }
        }

        when (val state = uiState) {
            is AssessmentsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is AssessmentsUiState.Empty -> EmptyAssessments(onStartAssessment)
            is AssessmentsUiState.SearchNoResults -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No assessments match your search or filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is AssessmentsUiState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            is AssessmentsUiState.Populated -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items, key = { it.id }) { assessment ->
                    AssessmentCard(
                        assessment = assessment,
                        onClick = { onOpenAssessment(assessment.id) },
                        onDelete = { pendingDelete = assessment }
                    )
                }
            }
        }
    }

    pendingDelete?.let { assessment ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this assessment?") },
            text = { Text("\"${assessment.name}\" and its saved video will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(assessment); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        )
    }
}

@Composable
private fun EmptyAssessments(onStartAssessment: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier)
        Text("No assessments yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Record or upload a task video to begin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        TextButton(onClick = onStartAssessment, modifier = Modifier.padding(top = 12.dp)) {
            Text("Start assessment", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskTypeFilterChip(selected: TaskType?, onSelected: (TaskType?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected != null, onClick = { expanded = true }, label = { Text(selected?.label() ?: "Task type") })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All task types") }, onClick = { onSelected(null); expanded = false })
            TaskType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(type.label()) }, onClick = { onSelected(type); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusFilterChip(selected: AssessmentStatus?, onSelected: (AssessmentStatus?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected != null, onClick = { expanded = true }, label = { Text(selected?.label() ?: "Status") })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All statuses") }, onClick = { onSelected(null); expanded = false })
            AssessmentStatus.entries.forEach { status ->
                DropdownMenuItem(text = { Text(status.label()) }, onClick = { onSelected(status); expanded = false })
            }
        }
    }
}
