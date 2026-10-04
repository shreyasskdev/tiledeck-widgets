package io.github.shreyasskdev.tiledeck.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.shreyasskdev.tiledeck.data.AttendanceResult

@Composable
internal fun CustomizationTab(
    result: AttendanceResult?,
    useCustomNames: Boolean,
    onUseCustomNamesChange: (Boolean) -> Unit,
    useShorthand: Boolean,
    onUseShorthandChange: (Boolean) -> Unit,
    nameOverrides: Map<String, String>,
    onOverrideChange: (String, String) -> Unit,
    saveState: SaveState,
    onSave: () -> Unit,
) {
    val palette = expressiveBadgePalette()
    val subjects = result?.subjects.orEmpty()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 108.dp + navBarBottom),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Section("Display") {
                TileColumn(count = 2) { index, position ->
                    if (index == 0) {
                        GroupedRow(
                            position = position,
                            title = "Use my custom names",
                            subtitle = "Show your own labels in the app and widgets",
                            badgeColor = palette[0].first,
                            badgeContent = { EditIcon(tint = palette[0].second) },
                            onClick = { onUseCustomNamesChange(!useCustomNames) },
                            trailing = {
                                Switch(checked = useCustomNames, onCheckedChange = onUseCustomNamesChange)
                            },
                        )
                    } else {
                        GroupedRow(
                            position = position,
                            title = "Shorthand auto-generator",
                            subtitle = "Fill empty names with short forms",
                            badgeColor = palette[2].first,
                            badgeContent = { AutoFixIcon(tint = palette[2].second) },
                            onClick = if (useCustomNames) {
                                { onUseShorthandChange(!useShorthand) }
                            } else null,
                            trailing = {
                                Switch(
                                    checked = useShorthand,
                                    onCheckedChange = onUseShorthandChange,
                                    enabled = useCustomNames,
                                )
                            },
                        )
                    }
                }
            }
        }

        if (subjects.isNotEmpty()) {
            item {
                Section("Per-subject names") {
                    TileColumn(subjects.size) { index, position ->
                        val subject = subjects[index]
                        SubjectOverrideCard(
                            subjectCode = subject.code,
                            originalName = subject.name.ifBlank { subject.code },
                            overrideValue = nameOverrides[subject.code].orEmpty(),
                            enabled = useCustomNames,
                            shape = groupShape(position),
                            onValueChange = { input -> onOverrideChange(subject.code, input) },
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onSave,
                    enabled = useCustomNames && saveState != SaveState.Saving,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(
                        text = when (saveState) {
                            SaveState.Saving -> "Saving…"
                            SaveState.Saved -> "Saved ✓"
                            SaveState.Idle -> "Save name overrides"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else {
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        text = "No subjects yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Fetch your attendance first and your subjects will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectOverrideCard(
    subjectCode: String,
    originalName: String,
    overrideValue: String,
    enabled: Boolean,
    shape: Shape,
    onValueChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val shorthand = remember(originalName) { toShorthand(originalName) }

    AppCard(
        shape = shape,
        containerColor = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = originalName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subjectCode,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (shorthand.isNotBlank() && enabled) {
                Surface(
                    onClick = { onValueChange(shorthand) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.semantics {
                        contentDescription = "Use suggested shorthand $shorthand"
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AutoFixIcon(tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            text = shorthand,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            shape = CircleShape,
            color = if (enabled) MaterialTheme.colorScheme.surfaceBright
            else MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.5f),
            border = BorderStroke(
                width = 1.dp,
                color = if (enabled) MaterialTheme.colorScheme.outlineVariant
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (overrideValue.isEmpty()) {
                    Text(
                        text = if (enabled) "Custom display name"
                        else originalName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                BasicTextField(
                    value = if (enabled) overrideValue else originalName,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}