package io.github.shreyasskdev.tiledeck.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.shreyasskdev.tiledeck.R
import io.github.shreyasskdev.tiledeck.data.AttendancePrefs
import io.github.shreyasskdev.tiledeck.data.SubjectAttendance
import io.github.shreyasskdev.tiledeck.data.TimetableDayResult
import io.github.shreyasskdev.tiledeck.data.TimetableResult
import io.github.shreyasskdev.tiledeck.data.TimetableSubjectResult
import io.github.shreyasskdev.tiledeck.ui.MainActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val TIMETABLE_WIDGET_UPDATE_KEY = longPreferencesKey("timetable_widget_update_timestamp")

// LBSCEK standard period start times (hour index 1–6 → hour, minute)
private val PERIOD_START_TIMES = mapOf(
    1 to Pair(9, 30),
    2 to Pair(10, 30),
    3 to Pair(11, 30),
    4 to Pair(13, 30),
    5 to Pair(14, 30),
    6 to Pair(15, 30)
)

// LBSCEK standard period end times
private val PERIOD_END_TIMES = mapOf(
    1 to Pair(10, 20),
    2 to Pair(11, 20),
    3 to Pair(12, 20),
    4 to Pair(14, 20),
    5 to Pair(15, 20),
    6 to Pair(16, 20)
)

// ─────────────────────────────────────────────────────────────────────────────
//  Responsive layouts
//
//  1 row tall  → "stretch" layout  (day pill + 6 pills in one row)
//  2 rows tall → "bloom" layout    (progress ring + 2×3 grid with status icons)
//
//  Glance picks the largest size below that fits the real widget, so anything
//  shorter than BLOOM_SIZE.height renders the stretch layout.
// ─────────────────────────────────────────────────────────────────────────────

private val STRETCH_SIZE = DpSize(250.dp, 40.dp)
private val BLOOM_SIZE = DpSize(250.dp, 96.dp)
private val TALL_SIZE = DpSize(250.dp, 104.dp)
private val XL_SIZE = DpSize(250.dp, 114.dp)

private const val SHAPE_DP = 58
private const val ICON_DP = 14
private const val STRETCH_ICON_DP = 9

internal enum class PeriodState { Free, Upcoming, Current, Next, Present, Absent, Unmarked }

internal data class PillSpec(
    val label: String,
    val time: String,
    val state: PeriodState,
)

class TimetableWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(STRETCH_SIZE, BLOOM_SIZE, TALL_SIZE, XL_SIZE))

    override val previewSizeMode: PreviewSizeMode
        get() = SizeMode.Responsive(setOf(STRETCH_SIZE, BLOOM_SIZE, TALL_SIZE, XL_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext

        provideContent {
            val glanceState = currentState<Preferences>()
            val updateTime = glanceState[TIMETABLE_WIDGET_UPDATE_KEY] ?: 0L

            val prefs = remember(updateTime) { AttendancePrefs(appContext) }
            val timetable = prefs.getLastTimetable()
            val subjectNameOverrides = prefs.getSubjectNames()
            val useCustomNames = prefs.getUseCustomNames()
            val attendanceResult = prefs.getLastResult()
            val attendanceSubjects = attendanceResult?.subjects.orEmpty()
            val overallPercent = attendanceResult?.overallPercent
            val hasCredentials = prefs.hasCredentials()

            GlanceTheme {
                WidgetContent(hasCredentials, timetable, subjectNameOverrides, useCustomNames, attendanceSubjects, overallPercent)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        hasCredentials: Boolean,
        timetable: TimetableResult?,
        subjectNameOverrides: Map<String, String>,
        useCustomNames: Boolean,
        attendanceSubjects: List<SubjectAttendance>,
        overallPercent: Double?
    ) {
        val context = LocalContext.current
        val stretched = LocalSize.current.height < BLOOM_SIZE.height
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
        val today = LocalDate.now()
        val todayKey = when (today.dayOfWeek.value) {
            1 -> "M"
            2 -> "T"
            3 -> "W"
            4 -> "Th"
            5 -> "F"
            else -> null // Weekend
        }

        val todaySchedule = todayKey?.let { key ->
            timetable?.timetable?.find { it.day == key }
        }

        val dayLabel = today.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).uppercase()
        val dateLabel = today.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).uppercase()
        val headerText = "$dayLabel • $dateLabel"

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .cornerRadius(if (stretched) 33.dp else 24.dp)
                .padding(if (stretched) 3.dp else 8.dp)
        ) {
            if (!hasCredentials || timetable == null) {
                CenteredMessage(stretched, null, "Tap to set up TileDeck", openApp)
            } else if (todayKey == null) {
                CenteredMessage(stretched, headerText, "No classes today", openApp)
            } else if (todaySchedule == null || todaySchedule.subjects.isEmpty()) {
                CenteredMessage(stretched, headerText, "Timetable unavailable", openApp)
            } else {
                val pills = buildPills(todaySchedule, subjectNameOverrides, useCustomNames, attendanceSubjects)
                val branch = branchBadgeLabel(timetable)
                if (stretched) {
                    StretchLayout(pills, openApp)
                } else {
                    BloomLayout(dayLabel, branch, pills, openApp, overallPercent)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Data → pill specs
    // ─────────────────────────────────────────────────────────────────────

    private fun buildPills(
        schedule: TimetableDayResult,
        overrides: Map<String, String>,
        useCustomNames: Boolean,
        attendanceSubjects: List<SubjectAttendance>
    ): List<PillSpec> {
        val now = LocalTime.now()
        val nowHour = now.hour
        val nowMinute = now.minute

        // Always exactly 6 periods (hour 7 is intentionally excluded), even if
        // the API omits an hour.
        val base = (1..6).map { hour ->
            val subject = schedule.subjects.firstOrNull { it.hour == hour }
                ?: TimetableSubjectResult(hour, "")
            val shortName = resolveShortName(subject.subject, overrides, useCustomNames, attendanceSubjects)
            val freeKey = normalizeKey(shortName)
            val isFree = shortName.isBlank() || freeKey == "FREE" || freeKey.startsWith("FREEPERIOD")
            val started = hasStartedPeriod(hour, nowHour, nowMinute)
            val current = isCurrentPeriod(hour, nowHour, nowMinute)
            val status = subject.attendanceStatus

            val state = when {
                isFree -> PeriodState.Free
                current -> PeriodState.Current
                started && status.equals("present", ignoreCase = true) -> PeriodState.Present
                started && status?.contains("absent", ignoreCase = true) == true -> PeriodState.Absent
                started -> PeriodState.Unmarked
                else -> PeriodState.Upcoming
            }

            val timeStr = PERIOD_START_TIMES[hour]?.let { (h, m) ->
                "%d:%02d".format(if (h > 12) h - 12 else h, m)
            } ?: ""

            PillSpec(if (isFree) "FREE" else shortName, timeStr, state)
        }

        // Between periods / lunch nothing is "current", so the highlight would vanish.
        // Mark the next upcoming class as NEXT instead.
        if (base.none { it.state == PeriodState.Current }) {
            val nextIdx = base.indexOfFirst { it.state == PeriodState.Upcoming }
            if (nextIdx >= 0) {
                return base.toMutableList().also { it[nextIdx] = it[nextIdx].copy(state = PeriodState.Next) }
            }
        }
        return base
    }

    /** Derives the badge from course identity instead of user-specific settings. */
    private fun branchBadgeLabel(timetable: TimetableResult): String {
        val courseCode = timetable.timetable
            .asSequence()
            .flatMap { it.subjects.asSequence() }
            .map { it.subject.substringBefore(" - ").trim().uppercase() }
            .firstOrNull { it.matches(Regex("PC[A-Z]{2}T[0-9]{3}")) }
            ?: return "BRANCH"

        val match = Regex("^PC([A-Z]{2})T([0-9])").find(courseCode) ?: return "BRANCH"
        val branchCode = match.groupValues[1]
        val semester = match.groupValues[2]
        val branchName = when (branchCode) {
            "EC" -> "ECE"
            "CS" -> "CSE"
            "EE" -> "EEE"
            else -> branchCode
        }
        return "S$semester $branchName"
    }

    /**
     * Resolves the display name for a period subject.
     *
     * Priority:
     * 1. User-defined override keyed by COURSE CODE (e.g., "PBITT504" -> "WAD").
     * 2. Fallback: match the raw string against the attendance subjects (by code
     *    in text, or by name) and use that subject's override.
     * 3. Cleaned subject name parsed from the raw API string.
     */
    private fun resolveShortName(
        rawSubject: String,
        overrides: Map<String, String>,
        useCustomNames: Boolean,
        attendanceSubjects: List<SubjectAttendance>
    ): String {
        // Only apply overrides when the "use custom names" toggle is on,
        // matching AttendanceWidget.labelFor().
        if (useCustomNames) {
            // 1. Direct match on the course code parsed from the raw string.
            extractCourseCode(rawSubject)?.let { code ->
                findOverride(overrides, code)?.let { return it }
            }

            // 2. Fallback: the raw string may have no (or an oddly formatted) code,
            //    so match it against the attendance subjects by code-in-text or name,
            //    then use that subject's override.
            val normRaw = normalizeKey(rawSubject)
            val normParsed = normalizeKey(parseSubjectName(rawSubject))
            val match = attendanceSubjects.firstOrNull { s ->
                val normCode = normalizeKey(s.code)
                val normName = normalizeKey(s.name)
                (normCode.isNotEmpty() && normRaw.contains(normCode)) ||
                        (normParsed.length >= 4 && normName.isNotEmpty() &&
                                (normName == normParsed ||
                                        normName.contains(normParsed) ||
                                        normParsed.contains(normName)))
            }
            match?.let { findOverride(overrides, it.code) }?.let { return it }

            Log.d("TimetableWidget", "No override match for raw='$rawSubject' (overrides keys=${overrides.keys})")
        }

        return parseSubjectName(rawSubject)
    }

    /** Uppercases and strips everything but letters/digits; treats "&" as "AND". */
    private fun normalizeKey(s: String): String =
        s.uppercase().replace("&", "AND").filter { it.isLetterOrDigit() }

    /** Finds an override by exact code, then by whitespace/case-insensitive code. */
    private fun findOverride(overrides: Map<String, String>, code: String): String? {
        overrides[code]?.takeIf { it.isNotBlank() }?.let { return it }
        val norm = normalizeKey(code)
        return overrides.entries
            .firstOrNull { normalizeKey(it.key) == norm && it.value.isNotBlank() }
            ?.value
    }

    /**
     * Extracts the course code prefix from a raw Etlab subject string.
     *
     * Example: "PCITT501 - MACHINE LEARNING [ Theory ]RICHU NORMAN" → "PCITT501"
     *          "CLOUD COMPUTING" → null (no code prefix)
     */
    private fun extractCourseCode(raw: String): String? {
        val dashIdx = raw.indexOf(" - ")
        if (dashIdx <= 0) return null
        val potentialCode = raw.substring(0, dashIdx).trim()
        return if (potentialCode.length <= 14 && potentialCode.matches(Regex("[A-Za-z0-9 ]+"))) {
            potentialCode
        } else null
    }

    /**
     * Parses Etlab's verbose subject strings into a clean display name.
     *
     *   "PCITT501 - MACHINE LEARNING [ Theory ]RICHU NORMAN" → "MACHINE LEARNING"
     *   "CLOUD COMPUTING"                                    → "CLOUD COMPUTING"
     */
    private fun parseSubjectName(raw: String): String {
        var name = raw.trim()

        // Strip everything from "[" onwards (type tag + faculty name)
        val bracketIdx = name.indexOf('[')
        if (bracketIdx > 0) {
            name = name.substring(0, bracketIdx).trim()
        }

        // Strip leading course code: "PCITT501 - MACHINE LEARNING" → "MACHINE LEARNING"
        val dashIdx = name.indexOf(" - ")
        if (dashIdx > 0) {
            val potentialCode = name.substring(0, dashIdx).trim()
            if (potentialCode.length <= 14 && potentialCode.matches(Regex("[A-Za-z0-9 ]+"))) {
                name = name.substring(dashIdx + 3).trim()
            }
        }

        return name
    }

    private fun isCurrentPeriod(hour: Int, nowHour: Int, nowMinute: Int): Boolean {
        val start = PERIOD_START_TIMES[hour] ?: return false
        val end = PERIOD_END_TIMES[hour] ?: return false
        val nowTotal = nowHour * 60 + nowMinute
        val startTotal = start.first * 60 + start.second
        val endTotal = end.first * 60 + end.second
        return nowTotal in startTotal..endTotal
    }

    private fun hasStartedPeriod(hour: Int, nowHour: Int, nowMinute: Int): Boolean {
        val start = PERIOD_START_TIMES[hour] ?: return false
        val nowTotal = nowHour * 60 + nowMinute
        val startTotal = start.first * 60 + start.second
        return nowTotal >= startTotal
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared UI helpers
// ─────────────────────────────────────────────────────────────────────────────

private class PillColors(val bg: ColorProvider, val fg: ColorProvider)

@Composable
private fun pillColors(state: PeriodState): PillColors = when (state) {
    // Brightest tile: inverted onPrimaryContainer fill, no border.
    PeriodState.Current, PeriodState.Next -> PillColors(
        GlanceTheme.colors.onPrimaryContainer, GlanceTheme.colors.primaryContainer
    )
    PeriodState.Present -> PillColors(
        GlanceTheme.colors.tertiaryContainer, GlanceTheme.colors.onTertiaryContainer
    )
    PeriodState.Absent -> PillColors(
        GlanceTheme.colors.errorContainer, GlanceTheme.colors.onErrorContainer
    )
    PeriodState.Upcoming -> PillColors(
        GlanceTheme.colors.secondaryContainer, GlanceTheme.colors.onSecondaryContainer
    )
    // Free periods: quieter surface tone so they read as "nothing here".
    PeriodState.Free -> PillColors(
        GlanceTheme.colors.surfaceVariant, GlanceTheme.colors.onSurfaceVariant
    )
    PeriodState.Unmarked -> PillColors(
        GlanceTheme.colors.secondaryContainer, GlanceTheme.colors.onSecondaryContainer
    )
}

/**
 * Background with per-corner radii. Glance's cornerRadius() is uniform, so when a
 * [shapeRes] drawable is given it is used (tinted) instead, like AttendanceWidget does.
 */
private fun GlanceModifier.pillBackground(
    color: ColorProvider,
    shapeRes: Int?,
    corner: Dp
): GlanceModifier =
    if (shapeRes != null) {
        background(imageProvider = ImageProvider(shapeRes), colorFilter = ColorFilter.tint(color))
    } else {
        background(color).cornerRadius(corner)
    }

private fun PeriodState.isHighlighted(): Boolean =
    this == PeriodState.Current || this == PeriodState.Next

/** Filled pill; per-corner radii come from [shapeRes] when given. */
@Composable
private fun PillShell(
    state: PeriodState,
    corner: Dp,
    modifier: GlanceModifier,
    shapeRes: Int? = null,
    content: @Composable () -> Unit
) {
    val colors = pillColors(state)
    Box(
        modifier = modifier.pillBackground(colors.bg, shapeRes, corner),
        contentAlignment = Alignment.Center
    ) { content() }
}

private fun compact(label: String, max: Int): String =
    if (label.length > max) label.take(max - 1).trimEnd() + "…" else label

@Composable
private fun CenteredMessage(stretched: Boolean, header: String?, message: String, openApp: Action) {
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(openApp),
        contentAlignment = Alignment.Center
    ) {
        if (stretched || header == null) {
            Text(
                text = if (header != null) "$header  •  $message" else message,
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp),
                maxLines = 1
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    header,
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    message,
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 11.sp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Stretch layout (1 row)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StretchLayout(pills: List<PillSpec>, openApp: Action) {
    // NOTE: Glance rows/columns support at most 10 children; extras are silently
    // dropped. Gaps are therefore padding on wrapper Boxes, never Spacers.
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pills.forEachIndexed { index, spec ->
            val shapeRes = when (index) {
                0 -> R.drawable.bg_tile_outer_left
                pills.size - 1 -> R.drawable.bg_tile_outer_right
                else -> R.drawable.bg_tile_inner
            }
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .padding(2.dp)
            ) {
                StretchPill(spec, GlanceModifier.fillMaxSize().clickable(openApp), shapeRes)
            }
        }
    }
}

@Composable
private fun StretchPill(spec: PillSpec, modifier: GlanceModifier, shapeRes: Int) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val colors = pillColors(spec.state)

    val bgArgb = colors.bg.getColor(context).toArgb()
    val fgArgb = colors.fg.getColor(context).toArgb()
    val iconPx = (STRETCH_ICON_DP * density).toInt()

    val icon: Bitmap? = remember(spec.state, iconPx, bgArgb, fgArgb) {
        when (spec.state) {
            PeriodState.Present -> statusIconBitmap(IconKind.Check, iconPx, fgArgb, bgArgb)
            PeriodState.Absent -> statusIconBitmap(IconKind.Cross, iconPx, fgArgb, bgArgb)
            PeriodState.Upcoming -> statusIconBitmap(IconKind.Dashed, iconPx, fgArgb, bgArgb)
            PeriodState.Unmarked -> statusIconBitmap(IconKind.Pending, iconPx, fgArgb, bgArgb)
            else -> null
        }
    }

    PillShell(
        state = spec.state,
        corner = 19.dp,
        modifier = modifier,
        shapeRes = shapeRes
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                compact(spec.label, 5),
                style = TextStyle(
                    color = colors.fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Row(
                modifier = GlanceModifier.height(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (spec.state.isHighlighted()) {
                    Box(
                        modifier = GlanceModifier
                            .background(GlanceTheme.colors.primaryContainer)
                            .cornerRadius(6.dp)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (spec.state == PeriodState.Next) "NEXT" else "NOW",
                            style = TextStyle(
                                color = GlanceTheme.colors.onPrimaryContainer,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    if (icon != null) {
                        Image(
                            provider = ImageProvider(icon),
                            contentDescription = null,
                            modifier = GlanceModifier.size(STRETCH_ICON_DP.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(2.dp))
                    }
                    Text(
                        spec.time,
                        style = TextStyle(
                            color = colors.fg,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bloom layout (2 rows)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BloomLayout(
    day: String,
    branch: String,
    pills: List<PillSpec>,
    openApp: Action,
    overallPercent: Double?
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val roundedFont = FontFamily("google_sans_flex_rounded")

    // Taller widget -> more content and a bigger shape, so the card never looks empty.
    val height = LocalSize.current.height
    val showOverall = overallPercent != null && height >= TALL_SIZE.height
    val shapeDp = when {
        showOverall && height >= XL_SIZE.height -> 54
        showOverall -> 44
        else -> 50
    }

    val got = pills.count { it.state == PeriodState.Present }
    val total = pills.count { it.state != PeriodState.Free }
    val allDone = total > 0 && got == total

    // Same palette as the attendance summary card: onPrimaryContainer card,
    // primaryContainer content on top. A sunburst replaces the cookie once all are attended.
    val shapeArgb = GlanceTheme.colors.primaryContainer.getColor(context).toArgb()
    val shapeTextColor = GlanceTheme.colors.onPrimaryContainer
    val shapePx = (shapeDp * density).toInt()

    val shapeBitmap = remember(shapePx, shapeArgb, allDone) {
        rasterizeMaterialShape(
            shape = if (allDone) MaterialShapes.Sunny else MaterialShapes.Cookie9Sided,
            widthPx = shapePx,
            heightPx = shapePx,
            colorArgb = shapeArgb
        )
    }

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Full-height card. Weighted spacers push the day pill to the top and the
        // overall pill to the bottom, so it fills the widget at any height.
        Column(
            modifier = GlanceModifier
                .width(76.dp)
                .fillMaxHeight()
                .background(
                    imageProvider = ImageProvider(R.drawable.bg_grid_card_left),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                )
                .padding(horizontal = 3.dp, vertical = 5.dp)
                .clickable(openApp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(100.dp)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$day • ${branch.substringBefore(" ")}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontSize = 9.sp,
                        fontFamily = roundedFont,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Box(
                modifier = GlanceModifier.size(shapeDp.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(shapeBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$got",
                            style = TextStyle(
                                color = shapeTextColor,
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            "/$total",
                            style = TextStyle(
                                color = shapeTextColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                    Text(
                        "GOT",
                        style = TextStyle(
                            color = shapeTextColor,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Overall attendance (same 75% threshold as the big widget), tinted by status.
            if (showOverall && overallPercent != null) {
                val good = overallPercent >= 75.0
                Box(
                    modifier = GlanceModifier
                        .background(
                            if (good) GlanceTheme.colors.tertiaryContainer
                            else GlanceTheme.colors.errorContainer
                        )
                        .cornerRadius(100.dp)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "%.0f%% overall".format(overallPercent),
                        style = TextStyle(
                            color = if (good) GlanceTheme.colors.onTertiaryContainer
                            else GlanceTheme.colors.onErrorContainer,
                            fontSize = 8.sp,
                            fontFamily = roundedFont,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.width(7.dp))

        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            val rows = pills.chunked(3)
            rows.forEachIndexed { rowIndex, rowPills ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .padding(bottom = if (rowIndex < rows.size - 1) 7.dp else 0.dp)
                ) {
                    rowPills.forEachIndexed { colIndex, spec ->
                        // Same logic as AttendanceWidget: only tiles touching the widget's
                        // right edge get the large outer radius, the rest are tight.
                        val isRight = colIndex == rowPills.size - 1
                        val isTop = rowIndex == 0
                        val isBottom = rowIndex == rows.size - 1
                        val shapeRes = when {
                            isRight && isTop -> R.drawable.bg_grid_top_right
                            isRight && isBottom -> R.drawable.bg_grid_bottom_right
                            else -> R.drawable.bg_grid_inner
                        }
                        BloomPill(spec, GlanceModifier.defaultWeight().fillMaxHeight().clickable(openApp), shapeRes)
                        if (colIndex < rowPills.size - 1) {
                            Spacer(modifier = GlanceModifier.width(7.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BloomPill(spec: PillSpec, modifier: GlanceModifier, shapeRes: Int) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val colors = pillColors(spec.state)

    val bgArgb = colors.bg.getColor(context).toArgb()
    val fgArgb = colors.fg.getColor(context).toArgb()
    val iconPx = (ICON_DP * density).toInt()

    val icon: Bitmap? = remember(spec.state, iconPx, bgArgb, fgArgb) {
        when (spec.state) {
            PeriodState.Present -> statusIconBitmap(IconKind.Check, iconPx, fgArgb, bgArgb)
            PeriodState.Absent -> statusIconBitmap(IconKind.Cross, iconPx, fgArgb, bgArgb)
            PeriodState.Upcoming -> statusIconBitmap(IconKind.Dashed, iconPx, fgArgb, bgArgb)
            PeriodState.Unmarked -> statusIconBitmap(IconKind.Pending, iconPx, fgArgb, bgArgb)
            else -> null
        }
    }

    PillShell(
        state = spec.state,
        corner = 22.dp,
        modifier = modifier,
        shapeRes = shapeRes
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = GlanceModifier.height(ICON_DP.dp),
                contentAlignment = Alignment.Center
            ) {
                if (spec.state.isHighlighted()) {
                    Box(
                        modifier = GlanceModifier
                            .background(GlanceTheme.colors.primaryContainer)
                            .cornerRadius(8.dp)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (spec.state == PeriodState.Next) "NEXT" else "NOW",
                            style = TextStyle(
                                color = GlanceTheme.colors.onPrimaryContainer,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else if (icon != null) {
                    Image(
                        provider = ImageProvider(icon),
                        contentDescription = null,
                        modifier = GlanceModifier.size(ICON_DP.dp)
                    )
                }
            }

            Text(
                compact(spec.label, 8),
                style = TextStyle(
                    color = colors.fg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                spec.time,
                style = TextStyle(
                    color = colors.fg,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic
                ),
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Rasterized bits (Glance can't draw arcs / icons / dashes natively)
// ─────────────────────────────────────────────────────────────────────────────

private enum class IconKind { Check, Cross, Dashed, Pending }

/**
 * Small status glyph: ✓ / ✕ in a filled circle, or a dashed empty circle.
 * [circleArgb] is the circle/outline color, [glyphArgb] the mark drawn on top.
 */
private fun statusIconBitmap(kind: IconKind, sizePx: Int, circleArgb: Int, glyphArgb: Int): Bitmap {
    val size = sizePx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val s = size.toFloat()

    when (kind) {
        IconKind.Dashed -> {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = circleArgb
                style = Paint.Style.STROKE
                strokeWidth = s * 0.1f
                pathEffect = DashPathEffect(floatArrayOf(s * 0.14f, s * 0.12f), 0f)
            }
            val r = s / 2f - paint.strokeWidth
            canvas.drawCircle(s / 2f, s / 2f, r, paint)
        }

        IconKind.Pending -> {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = circleArgb
                style = Paint.Style.STROKE
                strokeWidth = s * 0.1f
            }
            canvas.drawCircle(s / 2f, s / 2f, s / 2f - ring.strokeWidth, ring)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = circleArgb
                style = Paint.Style.FILL
            }
            canvas.drawCircle(s / 2f, s / 2f, s * 0.1f, dot)
        }

        IconKind.Check, IconKind.Cross -> {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = circleArgb
                style = Paint.Style.FILL
            }
            canvas.drawCircle(s / 2f, s / 2f, s / 2f, fill)

            val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = glyphArgb
                style = Paint.Style.STROKE
                strokeWidth = s * 0.13f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            if (kind == IconKind.Check) {
                path.moveTo(s * 0.28f, s * 0.52f)
                path.lineTo(s * 0.44f, s * 0.68f)
                path.lineTo(s * 0.72f, s * 0.36f)
            } else {
                path.moveTo(s * 0.34f, s * 0.34f)
                path.lineTo(s * 0.66f, s * 0.66f)
                path.moveTo(s * 0.66f, s * 0.34f)
                path.lineTo(s * 0.34f, s * 0.66f)
            }
            canvas.drawPath(path, glyph)
        }
    }
    return bitmap
}