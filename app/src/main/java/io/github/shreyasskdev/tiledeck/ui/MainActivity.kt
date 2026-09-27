package io.github.shreyasskdev.tiledeck.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.github.shreyasskdev.tiledeck.AppScope
import io.github.shreyasskdev.tiledeck.data.ApkDownloader
import io.github.shreyasskdev.tiledeck.data.AttendancePrefs
import io.github.shreyasskdev.tiledeck.data.AttendanceResult
import io.github.shreyasskdev.tiledeck.data.EtlabRepository
import io.github.shreyasskdev.tiledeck.data.InvalidCredentialsException
import io.github.shreyasskdev.tiledeck.data.SubjectAttendance
import io.github.shreyasskdev.tiledeck.data.UpdateChecker
import io.github.shreyasskdev.tiledeck.data.UpdateStatus
import io.github.shreyasskdev.tiledeck.ui.theme.AttendanceTheme
import io.github.shreyasskdev.tiledeck.widget.ATTENDANCE_WIDGET_UPDATE_KEY
import io.github.shreyasskdev.tiledeck.widget.AttendanceWidget
import io.github.shreyasskdev.tiledeck.widget.TOTAL_WIDGET_UPDATE_KEY
import io.github.shreyasskdev.tiledeck.widget.TotalPercentageWidget
import io.github.shreyasskdev.tiledeck.widget.enqueueWidgetRefresh
import io.github.shreyasskdev.tiledeck.work.AttendanceWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UI_PREFS = "attendance_ui_prefs"
private const val KEY_USE_SHORTHAND = "use_shorthand"
private const val TAG = "AttendanceUI"

private enum class SaveState { Idle, Saving, Saved }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendanceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AttendanceExpressiveApp()
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Reduced motion (honors system Settings > Accessibility > Remove animations)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

private fun motionSpecMillis(reducedMotion: Boolean, normal: Int): Int =
    if (reducedMotion) 0 else normal

// ─────────────────────────────────────────────────────────────────────────────
//  Reliable widget refresh
// ─────────────────────────────────────────────────────────────────────────────

suspend fun refreshAttendanceWidgets(context: Context) {
    try {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()

        val attendanceManager = GlanceAppWidgetManager(appContext)
        val attendanceIds = attendanceManager.getGlanceIds(AttendanceWidget::class.java)
        attendanceIds.forEach { glanceId ->
            runCatching {
                updateAppWidgetState(appContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[ATTENDANCE_WIDGET_UPDATE_KEY] = now
                    }
                }
                AttendanceWidget().update(appContext, glanceId)
            }
        }

        val totalIds = attendanceManager.getGlanceIds(TotalPercentageWidget::class.java)
        totalIds.forEach { glanceId ->
            runCatching {
                updateAppWidgetState(appContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[TOTAL_WIDGET_UPDATE_KEY] = now
                    }
                }
                TotalPercentageWidget().update(appContext, glanceId)
            }
        }

        AttendanceWidget().updateAll(appContext)
        TotalPercentageWidget().updateAll(appContext)
    } catch (e: Exception) {
        Log.e(TAG, "refreshAttendanceWidgets failed", e)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Root screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AttendanceExpressiveApp() {
    val context = LocalContext.current
    val prefs = remember { AttendancePrefs(context) }
    val uiPrefs = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    val reducedMotion = rememberReducedMotion()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }

    var username by remember { mutableStateOf(prefs.getUsername() ?: "") }
    var password by remember { mutableStateOf(prefs.getPassword() ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saveState by remember { mutableStateOf(SaveState.Idle) }

    var result by remember { mutableStateOf(prefs.getLastResult()) }
    var nameOverrides by remember { mutableStateOf(prefs.getSubjectNames()) }
    var useCustomNames by remember { mutableStateOf(prefs.getUseCustomNames()) }
    var refreshInterval by remember { mutableStateOf(prefs.getRefreshIntervalMinutes()) }
    var useShorthand by remember {
        mutableStateOf(uiPrefs.getBoolean(KEY_USE_SHORTHAND, false))
    }

    // ── Auto-update state ─────────────────────────────────────────────────
    var updateStatus by remember { mutableStateOf<UpdateStatus?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    // Auto-check on every launch. Throttling (if any) lives inside
    // UpdateChecker.check(), not here.
    LaunchedEffect(Unit) {
        val checked = UpdateChecker.check(context.applicationContext)
        updateStatus = checked
        if (checked is UpdateStatus.Available) {
            showUpdateDialog = true
        }
    }

    // Back gesture/button closes About instead of exiting when it's open.
    BackHandler(enabled = showAbout) { showAbout = false }

    val onFetchAttendance: () -> Unit = {
        if (username.isNotBlank() && password.isNotBlank()) {
            loading = true
            status = null
            val appContext = context.applicationContext
            AppScope.scope.launch {
                try {
                    val repo = EtlabRepository()
                    val fetched: AttendanceResult =
                        repo.fetchAttendance(username.trim(), password).attendance

                    prefs.saveCredentials(username.trim(), password)
                    prefs.saveLastResult(fetched)

                    refreshAttendanceWidgets(appContext)
                    AttendanceWorker.schedulePeriodic(appContext)

                    withContext(Dispatchers.Main) {
                        result = fetched
                        status = "Attendance fetched successfully! (%.1f%%)".format(fetched.overallPercent)
                    }
                } catch (e: InvalidCredentialsException) {
                    withContext(Dispatchers.Main) { status = "Invalid username or password." }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { status = "Couldn't fetch attendance: ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { loading = false }
                }
            }
        }
    }

    if (showUpdateDialog) {
        val available = updateStatus as? UpdateStatus.Available
        if (available != null) {
            AlertDialog(
                onDismissRequest = { if (!downloading) showUpdateDialog = false },
                title = { Text("Update available") },
                text = {
                    Column {
                        Text("Version ${available.version} is available.")
                        if (!available.releaseNotes.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = available.releaseNotes.take(400),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (downloading) {
                            Spacer(Modifier.height(16.dp))
                            Text("Downloading… $downloadProgress%")
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !downloading,
                        onClick = {
                            if (!canInstallPackages(context)) {
                                openInstallPermissionSettings(context)
                                return@TextButton
                            }
                            downloading = true
                            AppScope.scope.launch {
                                val ok = ApkDownloader.downloadAndInstall(
                                    context = context.applicationContext,
                                    downloadUrl = available.downloadUrl,
                                    onProgress = { downloadProgress = it },
                                )
                                withContext(Dispatchers.Main) {
                                    downloading = false
                                    if (ok) showUpdateDialog = false
                                }
                            }
                        },
                    ) { Text(if (downloading) "Downloading…" else "Update") }
                },
                dismissButton = {
                    TextButton(
                        enabled = !downloading,
                        onClick = { showUpdateDialog = false },
                    ) { Text("Later") }
                },
            )
        }
    }

    AnimatedContent(
        targetState = showAbout,
        transitionSpec = {
            val duration = motionSpecMillis(reducedMotion, 320)
            if (targetState) {
                slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration)) togetherWith
                        fadeOut(tween(motionSpecMillis(reducedMotion, 120)))
            } else {
                fadeIn(tween(duration)) togetherWith
                        slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration))
            }
        },
        label = "AboutScreenTransition",
    ) { aboutOpen ->
        if (aboutOpen) {
            AboutScreen(onBack = { showAbout = false })
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    FloatingTabBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        reducedMotion = reducedMotion,
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "LBSCEK Attendance",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = when (selectedTab) {
                                    0 -> "Attendance overview"
                                    1 -> "Custom display names"
                                    else -> "App settings"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    status?.let { msg ->
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val duration = motionSpecMillis(reducedMotion, 300)
                            if (targetState > initialState) {
                                slideInHorizontally(tween(duration)) { width -> width } + fadeIn(tween(duration)) togetherWith
                                        slideOutHorizontally(tween(duration)) { width -> -width } + fadeOut(tween(duration))
                            } else {
                                slideInHorizontally(tween(duration)) { width -> -width } + fadeIn(tween(duration)) togetherWith
                                        slideOutHorizontally(tween(duration)) { width -> width } + fadeOut(tween(duration))
                            }
                        },
                        label = "TabContentTransition",
                    ) { tabIndex ->
                        when (tabIndex) {
                            0 -> OverviewTab(
                                result = result,
                                updatedText = prefs.getLastUpdatedText(),
                                loading = loading,
                                nameOverrides = nameOverrides,
                                useCustomNames = useCustomNames,
                                onRefresh = onFetchAttendance,
                            )
                            1 -> CustomizationTab(
                                result = result,
                                useCustomNames = useCustomNames,
                                onUseCustomNamesChange = { checked ->
                                    useCustomNames = checked
                                    val appContext = context.applicationContext
                                    AppScope.scope.launch {
                                        prefs.saveUseCustomNames(checked)
                                        refreshAttendanceWidgets(appContext)
                                    }
                                },
                                useShorthand = useShorthand,
                                onUseShorthandChange = { checked ->
                                    useShorthand = checked
                                    uiPrefs.edit().putBoolean(KEY_USE_SHORTHAND, checked).apply()
                                    val currentResult = result ?: return@CustomizationTab
                                    val updated = nameOverrides.toMutableMap()
                                    currentResult.subjects.forEach { subject ->
                                        val original = subject.name.ifBlank { subject.code }
                                        val shorthand = toShorthand(original)
                                        if (checked) {
                                            if (updated[subject.code].isNullOrBlank()) {
                                                updated[subject.code] = shorthand
                                            }
                                        } else {
                                            if (updated[subject.code] == shorthand) {
                                                updated.remove(subject.code)
                                            }
                                        }
                                    }
                                    nameOverrides = updated
                                    val appContext = context.applicationContext
                                    AppScope.scope.launch {
                                        prefs.saveSubjectNames(updated)
                                        refreshAttendanceWidgets(appContext)
                                    }
                                },
                                nameOverrides = nameOverrides,
                                onOverrideChange = { code, value ->
                                    nameOverrides = nameOverrides.toMutableMap().apply { put(code, value) }
                                },
                                saveState = saveState,
                                onSave = {
                                    saveState = SaveState.Saving
                                    val appContext = context.applicationContext
                                    AppScope.scope.launch {
                                        prefs.saveSubjectNames(nameOverrides)
                                        refreshAttendanceWidgets(appContext)
                                        enqueueWidgetRefresh(appContext)
                                        withContext(Dispatchers.Main) { saveState = SaveState.Saved }
                                        delay(1500)
                                        withContext(Dispatchers.Main) { saveState = SaveState.Idle }
                                    }
                                },
                            )
                            else -> SettingsTab(
                                username = username,
                                onUsernameChange = { username = it },
                                password = password,
                                onPasswordChange = { password = it },
                                loading = loading,
                                onLoginSave = onFetchAttendance,
                                refreshInterval = refreshInterval,
                                onIntervalSelected = { minutes ->
                                    refreshInterval = minutes
                                    prefs.saveRefreshIntervalMinutes(minutes)
                                    AttendanceWorker.schedulePeriodic(context.applicationContext, minutes)
                                },
                                onOpenAbout = { showAbout = true },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Floating Pill Navigation Tab Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FloatingTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    reducedMotion: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.clip(RoundedCornerShape(32.dp)),
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabItem(
                    label = "Overview",
                    icon = { PieChartIcon(tint = it) },
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    reducedMotion = reducedMotion,
                )
                TabItem(
                    label = "Customize",
                    icon = { EditIcon(tint = it) },
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    reducedMotion = reducedMotion,
                )
                TabItem(
                    label = "Settings",
                    icon = { SettingsIcon(tint = it) },
                    selected = selectedTab == 2,
                    onClick = { onTabSelected(2) },
                    reducedMotion = reducedMotion,
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: @Composable (tint: Color) -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    reducedMotion: Boolean,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    val duration = motionSpecMillis(reducedMotion, 260)
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 18.dp else 24.dp,
        animationSpec = tween(duration),
        label = "TabItemCornerMorph",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(cornerRadius),
        color = bg,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .semantics { contentDescription = "$label tab${if (selected) ", selected" else ""}" },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon(fg)
            AnimatedVisibility(visible = selected) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 1: Overview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(
    result: AttendanceResult?,
    updatedText: String,
    loading: Boolean,
    nameOverrides: Map<String, String>,
    useCustomNames: Boolean,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroAttendanceCard(
                result = result,
                updatedText = updatedText,
                loading = loading,
                onRefresh = onRefresh,
            )
        }

        if (result != null && result.subjects.isNotEmpty()) {
            item {
                Text(
                    text = "Subject breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            items(result.subjects) { subject ->
                val displayLabel = if (useCustomNames && nameOverrides[subject.code]?.isNotBlank() == true) {
                    nameOverrides[subject.code]!!
                } else if (subject.name.isNotBlank()) {
                    subject.name
                } else {
                    subject.code
                }

                SubjectCardExpressive(
                    subject = subject,
                    displayLabel = displayLabel,
                )
            }
        } else {
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "No attendance data yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Go to the Settings tab and save your Etlab credentials to fetch your attendance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroAttendanceCard(
    result: AttendanceResult?,
    updatedText: String,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val overallPercent = result?.overallPercent ?: 0.0
    val isGood = overallPercent >= 75.0

    val containerBg = if (result == null) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else if (isGood) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    val contentFg = if (result == null) {
        MaterialTheme.colorScheme.onSurface
    } else if (isGood) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    val heroShape = RoundedCornerShape(
        topStart = 36.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 36.dp,
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = heroShape,
        colors = CardDefaults.elevatedCardColors(containerColor = containerBg),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = contentFg.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = if (result == null) "NOT SET UP" else if (isGood) "ON TRACK" else "LOW ATTENDANCE",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentFg,
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !loading,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Refresh attendance" },
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = contentFg,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        RefreshIcon(tint = contentFg)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (result == null) "--" else "%.1f".format(overallPercent),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentFg,
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentFg,
                    modifier = Modifier.padding(bottom = 10.dp, start = 2.dp),
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = updatedText,
                style = MaterialTheme.typography.bodySmall,
                color = contentFg.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun SubjectCardExpressive(
    subject: SubjectAttendance,
    displayLabel: String,
) {
    val progress = (subject.percent / 100.0).toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "ProgressAnim")

    val isGood = subject.percent >= 85.0
    val isFair = subject.percent >= 75.0

    val chipBg = when {
        isGood -> MaterialTheme.colorScheme.tertiaryContainer
        isFair -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val chipFg = when {
        isGood -> MaterialTheme.colorScheme.onTertiaryContainer
        isFair -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subject.code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = chipBg,
                ) {
                    Text(
                        text = "%.0f%%".format(subject.percent),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = chipFg,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .semantics {
                        contentDescription = "${subject.percent.toInt()} percent attendance"
                    },
                color = if (isFair) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "${subject.present} of ${subject.total} hours attended",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 2: Customization
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomizationTab(
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Display preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))

                    ToggleRow(
                        label = "Use my custom names",
                        checked = useCustomNames,
                        onCheckedChange = onUseCustomNamesChange,
                    )

                    Spacer(Modifier.height(8.dp))

                    ToggleRow(
                        label = "Use shorthand auto-generator",
                        checked = useShorthand,
                        enabled = useCustomNames,
                        onCheckedChange = onUseShorthandChange,
                    )
                }
            }
        }

        if (result != null && result.subjects.isNotEmpty()) {
            item {
                Text(
                    text = "Per-subject overrides",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            items(result.subjects) { subject ->
                val original = subject.name.ifBlank { subject.code }
                val override = nameOverrides[subject.code].orEmpty()

                SubjectOverrideCardExpressive(
                    subjectCode = subject.code,
                    originalName = original,
                    overrideValue = override,
                    enabled = useCustomNames,
                    onValueChange = { input -> onOverrideChange(subject.code, input) },
                )
            }

            item {
                Button(
                    onClick = onSave,
                    enabled = useCustomNames && saveState != SaveState.Saving,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
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
        }
    }
}

@Composable
private fun SubjectOverrideCardExpressive(
    subjectCode: String,
    originalName: String,
    overrideValue: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val shorthand = remember(originalName) { toShorthand(originalName) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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
                        style = MaterialTheme.typography.bodySmall,
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            AutoFixIcon(tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                text = shorthand,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = if (enabled) overrideValue else originalName,
                onValueChange = onValueChange,
                enabled = enabled,
                label = { Text("Custom display name") },
                placeholder = { Text(originalName) },
                singleLine = true,
                trailingIcon = {
                    if (overrideValue.isNotBlank() && enabled) {
                        IconButton(
                            onClick = { onValueChange("") },
                            modifier = Modifier.semantics { contentDescription = "Clear custom name" },
                        ) {
                            ClearIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 3: Settings
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsTab(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    loading: Boolean,
    onLoginSave: () -> Unit,
    refreshInterval: Long,
    onIntervalSelected: (Long) -> Unit,
    onOpenAbout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CredentialsCard(
                username = username,
                onUsernameChange = onUsernameChange,
                password = password,
                onPasswordChange = onPasswordChange,
                loading = loading,
                onSave = onLoginSave,
            )
        }

        item {
            BackgroundRefreshCard(
                currentMinutes = refreshInterval,
                onIntervalSelected = onIntervalSelected,
            )
        }

        item {
            AboutNavRow(onClick = onOpenAbout)
        }
    }
}

@Composable
private fun AboutNavRow(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Open About and privacy screen" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "About & privacy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Version, data policy, and how your credentials are handled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChevronIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Toggle row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Credentials card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CredentialsCard(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    loading: Boolean,
    onSave: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Etlab credentials",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Etlab username") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Etlab password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onSave,
                enabled = !loading && username.isNotBlank() && password.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = if (loading) "Checking…" else "Save & fetch attendance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Background refresh card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BackgroundRefreshCard(
    currentMinutes: Long,
    onIntervalSelected: (Long) -> Unit,
) {
    val options = listOf(
        15L to "15m",
        30L to "30m",
        60L to "1h",
        120L to "2h",
        240L to "4h",
        360L to "6h",
        720L to "12h",
        1440L to "24h",
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Background refresh",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Automatically fetches new attendance from Etlab in the background even when the app is closed. Android WorkManager enforces a 15-minute minimum interval.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Update frequency",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.take(4).forEach { (minutes, label) ->
                    val selected = currentMinutes == minutes
                    FilterChip(
                        selected = selected,
                        onClick = { onIntervalSelected(minutes) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.drop(4).forEach { (minutes, label) ->
                    val selected = currentMinutes == minutes
                    FilterChip(
                        selected = selected,
                        onClick = { onIntervalSelected(minutes) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Canvas icon helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PieChartIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        drawArc(color = tint, startAngle = 30f, sweepAngle = 290f, useCenter = true)
        drawArc(color = tint.copy(alpha = 0.5f), startAngle = 330f, sweepAngle = 50f, useCenter = true)
    }
}

@Composable
private fun EditIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        drawRoundRect(color = tint, cornerRadius = CornerRadius(4f, 4f), style = Stroke(width = 3f))
        drawLine(
            color = tint,
            start = Offset(size.width * 0.25f, size.height * 0.75f),
            end = Offset(size.width * 0.75f, size.height * 0.25f),
            strokeWidth = 3f,
        )
    }
}

@Composable
private fun SettingsIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        drawCircle(color = tint, radius = size.minDimension / 2.2f, style = Stroke(width = 3f))
        drawCircle(color = tint, radius = size.minDimension / 5f)
    }
}

@Composable
private fun RefreshIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        drawArc(
            color = tint,
            startAngle = 30f,
            sweepAngle = 290f,
            useCenter = false,
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun ClearIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        drawLine(color = tint, start = Offset(0f, 0f), end = Offset(size.width, size.height), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(size.width, 0f), end = Offset(0f, size.height), strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

@Composable
private fun AutoFixIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(10.dp)) {
        drawCircle(color = tint, radius = size.minDimension / 2f)
    }
}

@Composable
internal fun ChevronIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        drawLine(color = tint, start = Offset(w * 0.3f, h * 0.2f), end = Offset(w * 0.75f, h * 0.5f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.75f, h * 0.5f), end = Offset(w * 0.3f, h * 0.8f), strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

@Composable
internal fun BackArrowIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        drawLine(color = tint, start = Offset(w * 0.7f, h * 0.2f), end = Offset(w * 0.3f, h * 0.5f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.3f, h * 0.5f), end = Offset(w * 0.7f, h * 0.8f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.3f, h * 0.5f), end = Offset(w * 0.9f, h * 0.5f), strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Install permission helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns true if the app is allowed to launch the package installer.
 * On Android 8.0+ the user must grant this manually in Settings.
 */
private fun canInstallPackages(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }
}

/**
 * Sends the user to Settings > Apps > Special access > Install unknown apps
 * so they can flip the toggle for this app.
 */
private fun openInstallPermissionSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shorthand generator
// ─────────────────────────────────────────────────────────────────────────────

private val SHORTHAND_STOPWORDS = setOf(
    "and", "of", "the", "for", "io", "to", "a", "an", "&",
)

private val LAB_REGEX = Regex("(?i)\\b(lab|laboratory)\\b")

internal fun toShorthand(rawName: String): String {
    if (rawName.isBlank()) return ""

    val isLab = LAB_REGEX.containsMatchIn(rawName)
    val cleaned = LAB_REGEX.replace(rawName, " ").trim()

    val words = cleaned
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterIndexed { index, word ->
            index == 0 || word.lowercase() !in SHORTHAND_STOPWORDS
        }

    if (words.isEmpty()) return rawName.uppercase()

    val acronym: String = if (words.size == 1) {
        words.first().take(3).uppercase()
    } else {
        words.mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .take(5)
    }

    return if (isLab) "$acronym LAB" else acronym
}