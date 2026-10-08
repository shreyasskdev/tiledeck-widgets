package io.github.shreyasskdev.tiledeck.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
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
import io.github.shreyasskdev.tiledeck.data.UpdateChecker
import io.github.shreyasskdev.tiledeck.data.UpdateStatus
import io.github.shreyasskdev.tiledeck.ui.theme.AttendanceTheme
import io.github.shreyasskdev.tiledeck.widget.ATTENDANCE_WIDGET_UPDATE_KEY
import io.github.shreyasskdev.tiledeck.widget.AttendanceWidget
import io.github.shreyasskdev.tiledeck.widget.TOTAL_WIDGET_UPDATE_KEY
import io.github.shreyasskdev.tiledeck.widget.TotalPercentageWidget
import io.github.shreyasskdev.tiledeck.widget.TIMETABLE_WIDGET_UPDATE_KEY
import io.github.shreyasskdev.tiledeck.widget.TimetableWidget
import io.github.shreyasskdev.tiledeck.widget.enqueueWidgetRefresh
import io.github.shreyasskdev.tiledeck.work.AttendanceWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UI_PREFS = "attendance_ui_prefs"
private const val KEY_USE_SHORTHAND = "use_shorthand"
private const val TAG = "AttendanceUI"
private const val TAB_COUNT = 3

internal enum class SaveState { Idle, Saving, Saved }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.enableEdgeToEdge(window)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            AttendanceTheme {
                val darkTheme = isSystemInDarkTheme()
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    controller.isAppearanceLightNavigationBars = !darkTheme
                }

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
//  Reduced motion
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

internal fun motionSpecMillis(reducedMotion: Boolean, normal: Int): Int =
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

        val timetableIds = attendanceManager.getGlanceIds(TimetableWidget::class.java)
        timetableIds.forEach { glanceId ->
            runCatching {
                updateAppWidgetState(appContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[TIMETABLE_WIDGET_UPDATE_KEY] = now
                    }
                }
                TimetableWidget().update(appContext, glanceId)
            }
        }

        AttendanceWidget().updateAll(appContext)
        TotalPercentageWidget().updateAll(appContext)
        TimetableWidget().updateAll(appContext)
    } catch (e: Exception) {
        Log.e(TAG, "refreshAttendanceWidgets failed", e)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Root screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AttendanceExpressiveApp() {
    val context = LocalContext.current
    val prefs = remember { AttendancePrefs(context) }
    val uiPrefs = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    val reducedMotion = rememberReducedMotion()
    val tabScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { TAB_COUNT })
    val selectedTab: Int = pagerState.currentPage

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

    var updateStatus by remember { mutableStateOf<UpdateStatus?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<ApkDownloader.Progress?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var lastCheckedAt by remember { mutableStateOf<Long?>(null) }

    // Native toast for status messages (success / error / credentials).
    // The effect clears `status` after showing it so it doesn't re-fire.
    StatusToastEffect(
        status = status,
        onStatusShown = { status = null },
    )

    val topAppBarScrollBehavior: TopAppBarScrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            state = rememberTopAppBarState(),
        )

    val onCheckForUpdates: () -> Unit = {
        AppScope.scope.launch {
            updateStatus = null
            val checked = UpdateChecker.check(context.applicationContext, force = true)
            updateStatus = checked
            lastCheckedAt = System.currentTimeMillis()
            if (checked is UpdateStatus.Available) {
                showUpdateDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val checked = UpdateChecker.check(context.applicationContext, force = true)
        updateStatus = checked
        lastCheckedAt = System.currentTimeMillis()
        if (checked is UpdateStatus.Available) {
            showUpdateDialog = true
        }
    }

    BackHandler(enabled = showAbout) { showAbout = false }

    val onFetchAttendance: () -> Unit = {
        if (username.isNotBlank() && password.isNotBlank()) {
            loading = true
            status = null
            val appContext = context.applicationContext
            AppScope.scope.launch {
                try {
                    val repo = EtlabRepository()
                    val fetchResult = repo.fetchAttendance(username.trim(), password)
                    val fetched: AttendanceResult = fetchResult.attendance

                    prefs.saveCredentials(username.trim(), password)
                    prefs.saveLastResult(fetched)
                    fetchResult.timetable?.let { prefs.saveLastTimetable(it) }

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
                        Text(
                            text = "Version ${available.version} is available.",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        if (!available.releaseNotes.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = available.releaseNotes.take(500),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (downloading) {
                            val p = downloadProgress
                            Spacer(Modifier.height(16.dp))

                            val percent = p?.percent ?: 0
                            val totalUnknown = p == null || p.isTotalUnknown

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Downloading…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (!totalUnknown) {
                                    Text(
                                        text = "$percent%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            if (totalUnknown) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { percent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            val downloadedMb = (p?.bytesDownloaded ?: 0L) / 1024.0 / 1024.0
                            val totalMb = (p?.totalBytes ?: 0L) / 1024.0 / 1024.0
                            val speedMbps = (p?.bytesPerSecond ?: 0L) / 1024.0 / 1024.0

                            val detail = if (!totalUnknown) {
                                "%.1f of %.1f MB  •  %.1f MB/s".format(downloadedMb, totalMb, speedMbps)
                            } else {
                                "%.1f MB  •  %.1f MB/s".format(downloadedMb, speedMbps)
                            }

                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (downloadError != null) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = downloadError!!,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
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
                            downloadProgress = null
                            downloadError = null
                            AppScope.scope.launch {
                                val ok = ApkDownloader.downloadAndInstall(
                                    context = context.applicationContext,
                                    downloadUrl = available.downloadUrl,
                                    onProgress = { downloadProgress = it },
                                )
                                withContext(Dispatchers.Main) {
                                    downloading = false
                                    if (ok) {
                                        showUpdateDialog = false
                                        downloadProgress = null
                                    } else {
                                        downloadError = "Download failed. Check your connection and try again."
                                    }
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
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    LargeTopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = when (selectedTab) {
                                        0 -> "Attendance overview"
                                        1 -> "Custom display names"
                                        else -> "App settings"
                                    },
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = when (selectedTab) {
                                        0 -> result?.let { "Overall: %.1f%% • Tap refresh to update".format(it.overallPercent) } ?: "LBSCEK Etlab Portal"
                                        1 -> "Personalize subject labels & shorthands"
                                        else -> "Account, updates & auto-refresh"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        actions = {
                            Row(
                                modifier = Modifier.padding(end = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (selectedTab == 0) {
                                    CircleIconButton(
                                        onClick = onFetchAttendance,
                                        icon = Icons.Outlined.Refresh,
                                        contentDescription = "Refresh attendance",
                                        loading = loading,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                CircleIconButton(
                                    onClick = { showAbout = true },
                                    icon = Icons.Outlined.Info,
                                    contentDescription = "About & Privacy",
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        scrollBehavior = topAppBarScrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding()),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            when (page) {
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
                                    updateStatus = updateStatus,
                                    lastCheckedAt = lastCheckedAt,
                                    onCheckForUpdates = onCheckForUpdates,
                                    onInstallUpdate = {
                                        if (updateStatus is UpdateStatus.Available) {
                                            downloadError = null
                                            showUpdateDialog = true
                                        }
                                    },
                                )
                            }
                        }
                    }

                    FloatingTabBar(
                        selectedTab = selectedTab,
                        onTabSelected = { index ->
                            tabScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
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
}
