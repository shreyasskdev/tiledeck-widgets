@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.shreyasskdev.tiledeck.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.shreyasskdev.tiledeck.BuildConfig
import io.github.shreyasskdev.tiledeck.data.UpdateStatus

private val PASSWORD_SHAPES = listOf(
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Sunny,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Gem,
    MaterialShapes.Cookie9Sided,
)

private val REFRESH_OPTIONS = listOf(
    60L to "1h",
    120L to "2h",
    240L to "4h",
    720L to "12h",
    1440L to "24h",
)

private const val DEFAULT_REFRESH_MINUTES = 60L

@Composable
internal fun SettingsTab(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    loading: Boolean,
    onLoginSave: () -> Unit,
    refreshInterval: Long,
    onIntervalSelected: (Long) -> Unit,
    onOpenAbout: () -> Unit,
    updateStatus: UpdateStatus?,
    lastCheckedAt: Long?,
    onCheckForUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val palette = expressiveBadgePalette()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 108.dp + navBarBottom),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Section("Account") {
                CredentialsCard(
                    username = username,
                    onUsernameChange = onUsernameChange,
                    password = password,
                    onPasswordChange = onPasswordChange,
                    loading = loading,
                    onSave = onLoginSave,
                )
            }
        }

        item {
            Section("Sync") {
                BackgroundRefreshCard(
                    currentMinutes = refreshInterval,
                    onIntervalSelected = onIntervalSelected,
                )
            }
        }

        item {
            Section("App") {
                TileColumn(count = 2) { index, position ->
                    if (index == 0) {
                        VersionCard(
                            status = updateStatus,
                            lastCheckedAt = lastCheckedAt,
                            onCheckForUpdates = onCheckForUpdates,
                            onInstallUpdate = onInstallUpdate,
                            shape = groupShape(position),
                        )
                    } else {
                        GroupedRow(
                            position = position,
                            title = "About & privacy",
                            subtitle = "Developers, data policy and how credentials are handled",
                            badgeColor = palette[1].first,
                            badgeContent = {
                                Text(
                                    text = "i",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette[1].second,
                                )
                            },
                            onClick = onOpenAbout,
                            rowContentDescription = "Open About and privacy screen",
                            trailing = { ChevronIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Credentials ─────────────────────────────

@Composable
private fun CredentialsCard(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    loading: Boolean,
    onSave: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text = "Etlab credentials",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Encrypted and stored only on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        ExpressiveField(
            value = username,
            onValueChange = onUsernameChange,
            placeholder = "Etlab username",
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        )

        Spacer(Modifier.height(12.dp))

        PasswordShapeField(
            value = password,
            onValueChange = onPasswordChange,
            onDone = { focusManager.clearFocus() },
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSave,
            enabled = !loading && username.isNotBlank() && password.isNotBlank(),
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = if (loading) "Checking…" else "Save & fetch attendance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ExpressiveField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceBright,
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@Composable
private fun PasswordShapeField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(value.length, showPassword) {
        withFrameNanos { }
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceBright,
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Etlab password",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (showPassword) MaterialTheme.colorScheme.onSurface
                        else Color.Transparent,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(
                        if (showPassword) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (!showPassword && value.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.align(Alignment.CenterStart),
                                ) {
                                    repeat(value.length) { index ->
                                        PasswordShapeGlyph(index)
                                    }
                                }
                            }
                            innerTextField()
                        }
                    },
                )
            }

            IconButton(
                onClick = { showPassword = !showPassword },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = if (showPassword) Icons.Outlined.VisibilityOff
                    else Icons.Outlined.Visibility,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PasswordShapeGlyph(index: Int) {
    val polygon = PASSWORD_SHAPES[index % PASSWORD_SHAPES.size]
    val tint = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(polygon.toShape())
            .background(tint),
    )
}

// ───────────────────────────── Background refresh ─────────────────────────────

@Composable
private fun BackgroundRefreshCard(
    currentMinutes: Long,
    onIntervalSelected: (Long) -> Unit,
) {
    val displayedMinutes = if (REFRESH_OPTIONS.any { it.first == currentMinutes }) {
        currentMinutes
    } else {
        DEFAULT_REFRESH_MINUTES
    }

    // ToggleButtonDefaults.colors() — the correct API for styling ToggleButtons.
    // The previous ButtonGroupDefaults.connectedButtonGroupColors() does not exist.
    val groupColors = ToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        checkedContainerColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
    )

    AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text = "Background refresh",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "New attendance is fetched automatically, even when the app is closed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                ButtonGroupDefaults.ConnectedSpaceBetween
            ),
        ) {
            REFRESH_OPTIONS.forEachIndexed { index, (minutes, label) ->
                val checked = minutes == displayedMinutes

                ToggleButton(
                    checked = checked,
                    onCheckedChange = { onIntervalSelected(minutes) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        REFRESH_OPTIONS.lastIndex ->
                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = groupColors,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Android enforces a 15-minute minimum interval.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ───────────────────────────── Version ─────────────────────────────

@Composable
private fun VersionCard(
    status: UpdateStatus?,
    lastCheckedAt: Long?,
    onCheckForUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    shape: Shape,
) {
    val currentVersion = remember {
        runCatching { BuildConfig.VERSION_NAME }.getOrDefault("unknown")
    }

    val headline = when (status) {
        null -> "Checking for updates…"
        is UpdateStatus.UpToDate -> "You're on the latest version"
        is UpdateStatus.Available -> "Update available — v${status.version}"
        is UpdateStatus.Error -> "Couldn't check for updates"
    }
    val chipText = when (status) {
        null -> "CHECKING"
        is UpdateStatus.UpToDate -> "UP TO DATE"
        is UpdateStatus.Available -> "UPDATE"
        is UpdateStatus.Error -> "OFFLINE"
    }
    val chipBg = when (status) {
        null -> MaterialTheme.colorScheme.surfaceContainerHighest
        is UpdateStatus.UpToDate -> MaterialTheme.colorScheme.tertiaryContainer
        is UpdateStatus.Available -> MaterialTheme.colorScheme.primaryContainer
        is UpdateStatus.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val chipFg = when (status) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant
        is UpdateStatus.UpToDate -> MaterialTheme.colorScheme.onTertiaryContainer
        is UpdateStatus.Available -> MaterialTheme.colorScheme.onPrimaryContainer
        is UpdateStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
    }
    val supportingText = when (status) {
        null -> "Fetching the latest release info from GitHub."
        is UpdateStatus.UpToDate -> lastCheckedAt?.let { "Last checked ${relativeTime(it)}" }
            ?: "Auto-checks once per hour."
        is UpdateStatus.Available ->
            status.releaseNotes?.takeIf { it.isNotBlank() }?.take(180) ?: "Tap to download and install."
        is UpdateStatus.Error -> status.message
    }

    AppCard(shape = shape, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "App version",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Surface(shape = CircleShape, color = chipBg) {
                Text(
                    text = chipText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = chipFg,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "v$currentVersion",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        val available = status as? UpdateStatus.Available
        if (available != null) {
            Button(
                onClick = onInstallUpdate,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = "Get v${available.version}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        FilledTonalButton(
            onClick = onCheckForUpdates,
            enabled = status != null,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (status == null) {
                LoadingIndicator(modifier = Modifier.size(28.dp))
            } else {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (status == null) "Checking…" else "Check for updates",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun relativeTime(epochMs: Long): String {
    val minutes = (System.currentTimeMillis() - epochMs) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}