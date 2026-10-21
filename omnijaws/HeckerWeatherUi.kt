// SPDX-License-Identifier: Apache-2.0
package org.omnirom.omnijaws.hecker

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.omnirom.omnijaws.R

/** Native Material components for the existing weather screens and callbacks. */
@Composable
fun HeckerWeatherTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeckerWeatherScaffold(
    title: String,
    onBackClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.hecker_weather_back))
                    }
                },
            )
        },
        content = content,
    )
}

class WeatherPreferenceScope {
    internal val entries = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) { entries.add(content) }
}

@Composable
fun PreferenceGroup(title: String? = null, content: WeatherPreferenceScope.() -> Unit) {
    val group = WeatherPreferenceScope().apply(content)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
            Column {
                group.entries.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    entry()
                }
            }
        }
    }
}

@Composable
fun ClickablePreference(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = if (summary != null) ({ Text(summary) }) else null,
            leadingContent = if (icon != null) ({ Icon(icon, contentDescription = null) }) else null,
        )
    }
}

@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    icon: ImageVector? = null,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        headlineContent = { Text(title) },
        supportingContent = if (summary != null) ({ Text(summary) }) else null,
        leadingContent = if (icon != null) ({ Icon(icon, contentDescription = null) }) else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
fun ListPreference(
    title: String,
    options: List<Pair<String, String>>,
    value: String,
    onValueChange: (String) -> Unit,
    summary: String? = null,
) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    ClickablePreference(title = title, summary = summary, onClick = { open = true })
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                            .selectable(selected = option.first == value, role = Role.RadioButton,
                                onClick = { open = false; onValueChange(option.first) })
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = option.first == value, onClick = null,
                            modifier = Modifier.size(24.dp))
                        Text(option.second)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
