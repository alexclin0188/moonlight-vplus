package com.alexclin.moonlink.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager

/**
 * Shared Preference access helpers for Compose settings screens.
 */
object Prefs {
    fun of(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
}

// ── CheckBox Preference ─────────────────────────────────────────────

@Composable
fun CheckBoxPreference(
    key: String,
    title: String,
    summary: String = "",
    defaultValue: Boolean = false,
    dependency: String? = null,
    context: Context = LocalContext.current,
) {
    val prefs = remember { Prefs.of(context) }
    var dependencyValue by remember { mutableStateOf(true) }
    var checked by remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }

    // Observe dependency
    if (dependency != null) {
        LaunchedEffect(Unit) {
            dependencyValue = prefs.getBoolean(dependency, false)
        }
        DisposableEffect(dependency) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
                if (k == dependency) dependencyValue = prefs.getBoolean(dependency, false)
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    val enabled = dependency == null || dependencyValue

    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = if (summary.isNotEmpty()) {
            { Text(summary, style = MaterialTheme.typography.bodySmall) }
        } else null,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                checked = !checked
                prefs.edit().putBoolean(key, checked).apply()
            },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ── List Preference ──────────────────────────────────────────────────

@Composable
fun ListPreference(
    key: String,
    title: String,
    summary: String = "",
    entries: List<Pair<String, String>>, // (label, value)
    defaultValue: String = "",
    dependency: String? = null,
    context: Context = LocalContext.current,
) {
    val prefs = remember { Prefs.of(context) }
    var dependencyValue by remember { mutableStateOf(true) }
    var currentValue by remember { mutableStateOf(prefs.getString(key, defaultValue) ?: defaultValue) }
    var showDialog by remember { mutableStateOf(false) }

    if (dependency != null) {
        LaunchedEffect(Unit) {
            dependencyValue = prefs.getBoolean(dependency, false)
        }
        DisposableEffect(dependency) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
                if (k == dependency) dependencyValue = prefs.getBoolean(dependency, false)
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    val enabled = dependency == null || dependencyValue
    val displayLabel = entries.find { it.second == currentValue }?.first ?: currentValue

    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = if (summary.isNotEmpty()) {
            { Text(summary, style = MaterialTheme.typography.bodySmall) }
        } else {
            { Text(displayLabel, style = MaterialTheme.typography.bodySmall) }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    entries.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentValue = value
                                    prefs.edit().putString(key, value).apply()
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = value == currentValue,
                                onClick = null,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

// ── SeekBar Preference ───────────────────────────────────────────────

@Composable
fun SeekBarPreference(
    key: String,
    title: String,
    summary: String = "",
    min: Int = 0,
    max: Int = 100,
    step: Int = 1,
    defaultValue: Int = 0,
    unit: String = "",
    dependency: String? = null,
    context: Context = LocalContext.current,
) {
    val prefs = remember { Prefs.of(context) }
    var dependencyValue by remember { mutableStateOf(true) }
    var sliderValue by remember { mutableFloatStateOf(prefs.getInt(key, defaultValue).toFloat()) }
    var showDialog by remember { mutableStateOf(false) }

    if (dependency != null) {
        LaunchedEffect(Unit) {
            dependencyValue = prefs.getBoolean(dependency, false)
        }
        DisposableEffect(dependency) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
                if (k == dependency) dependencyValue = prefs.getBoolean(dependency, false)
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    val enabled = dependency == null || dependencyValue
    val displayValue = sliderValue.toInt()

    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = {
            Text(
                if (summary.isNotEmpty()) "$summary: $displayValue$unit"
                else "$displayValue$unit",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    Text("$displayValue$unit", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            // Snap to step
                            val snapped = ((it - min) / step * step + min).coerceIn(min.toFloat(), max.toFloat())
                            sliderValue = snapped
                        },
                        onValueChangeFinished = {
                            prefs.edit().putInt(key, sliderValue.toInt()).apply()
                        },
                        valueRange = min.toFloat()..max.toFloat(),
                        steps = ((max - min) / step) - 1,
                        enabled = enabled,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putInt(key, sliderValue.toInt()).apply()
                    showDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

// ── Multi-Select Preference ──────────────────────────────────────────

@Composable
fun MultiSelectPreference(
    key: String,
    title: String,
    summary: String = "",
    items: List<Pair<String, String>>, // (label, value)
    defaultValues: Set<String> = emptySet(),
    dependency: String? = null,
    context: Context = LocalContext.current,
) {
    val prefs = remember { Prefs.of(context) }
    var dependencyValue by remember { mutableStateOf(true) }
    var selectedValues by remember { mutableStateOf(prefs.getStringSet(key, defaultValues) ?: defaultValues) }
    var showDialog by remember { mutableStateOf(false) }

    if (dependency != null) {
        LaunchedEffect(Unit) {
            dependencyValue = prefs.getBoolean(dependency, false)
        }
        DisposableEffect(dependency) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
                if (k == dependency) dependencyValue = prefs.getBoolean(dependency, false)
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    val enabled = dependency == null || dependencyValue
    val enabledCount = items.count { it.second in selectedValues }

    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = if (summary.isNotEmpty()) {
            { Text(if (enabled) "$summary（已选 $enabledCount 项）" else summary, style = MaterialTheme.typography.bodySmall) }
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    items.forEach { (label, value) ->
                        val isChecked = value in selectedValues
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val mutable = selectedValues.toMutableSet()
                                    if (isChecked) mutable.remove(value)
                                    else mutable.add(value)
                                    selectedValues = mutable
                                    prefs.edit().putStringSet(key, mutable).apply()
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("确定")
                }
            },
        )
    }
}

// ── Category Header ──────────────────────────────────────────────────

@Composable
fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

// ── Clickable Preference (button-style) ──────────────────────────────

@Composable
fun ClickablePreference(
    title: String,
    summary: String = "",
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = if (summary.isNotEmpty()) {
            { Text(summary, style = MaterialTheme.typography.bodySmall) }
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
