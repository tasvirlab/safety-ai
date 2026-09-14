package com.azizbek.safetyai.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Drawer content shared by every screen: brand header, primary destinations, dark-mode toggle pinned to the bottom. */
@Composable
fun SafetyDrawerContent(
    destinations: List<SafetyDestination>,
    currentRoute: String?,
    onDestinationClick: (SafetyDestination) -> Unit,
    onClose: () -> Unit,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Safety AI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                NavigationDrawerItem(
                    selected = selected,
                    onClick = { onDestinationClick(destination) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label) },
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.DarkMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Dark mode",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            )
            Switch(checked = isDarkMode, onCheckedChange = onToggleDarkMode)
        }
    }
}
