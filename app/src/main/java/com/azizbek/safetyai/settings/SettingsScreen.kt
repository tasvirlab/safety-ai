package com.azizbek.safetyai.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azizbek.safetyai.data.ThemePreferencesRepository
import com.azizbek.safetyai.data.repository.AssessmentRepository
import com.azizbek.safetyai.data.video.VideoFileStore
import java.util.Locale

@Composable
fun SettingsScreen(
    themePreferencesRepository: ThemePreferencesRepository,
    assessmentRepository: AssessmentRepository,
    videoFileStore: VideoFileStore,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(themePreferencesRepository, assessmentRepository, videoFileStore)
    )
) {
    val darkModePreference by viewModel.darkModePreference.collectAsState()
    val assessmentCount by viewModel.assessmentCount.collectAsState()
    val storageBytes by viewModel.storageBytes.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(modifier = Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            SettingsSection(title = "Appearance") {
                Column(Modifier.selectableGroup()) {
                    AppearanceOption("Light", darkModePreference == false) { viewModel.setAppearance(false) }
                    AppearanceOption("Dark", darkModePreference == true) { viewModel.setAppearance(true) }
                    AppearanceOption("Follow system", darkModePreference == null) { viewModel.setAppearance(null) }
                }
            }

            SettingsSection(title = "Data and storage") {
                SettingsRow("Local assessments", "$assessmentCount")
                SettingsRow("Managed video storage", formatSize(storageBytes))
                TextButton(onClick = { viewModel.clearTemporaryFiles() }) {
                    Text("Clear temporary files", color = MaterialTheme.colorScheme.onBackground)
                }
                TextButton(onClick = { showDeleteAllDialog = true }) {
                    Text("Delete all local assessments", color = MaterialTheme.colorScheme.error)
                }
            }

            SettingsSection(title = "Privacy") {
                listOf(
                    "Videos remain on this device",
                    "Anonymous worker references only",
                    "No medical information is collected",
                    "No audio is recorded",
                    "No automatic network upload"
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            SettingsSection(title = "About") {
                SettingsRow("About Safety AI", "Ergonomic task assessment support tool")
                SettingsRow("App version", versionName)
                Text(
                    "Safety AI supports ergonomic task assessment and does not provide a medical diagnosis, and does not diagnose disease, pain, injury, or musculoskeletal disorders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete all local assessments?") },
            text = { Text("This permanently deletes every saved assessment and its video from this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteAllDialog = false; viewModel.deleteAllData {} }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        content()
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun AppearanceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = androidx.compose.ui.semantics.Role.RadioButton)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun formatSize(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
