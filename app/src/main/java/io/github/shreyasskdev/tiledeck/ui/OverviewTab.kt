package io.github.shreyasskdev.tiledeck.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shreyasskdev.tiledeck.data.AttendanceResult
import io.github.shreyasskdev.tiledeck.data.SubjectAttendance

private const val SAFE_THRESHOLD = 75.0
private const val COMFY_THRESHOLD = 85.0

@Composable
internal fun OverviewTab(
    result: AttendanceResult?,
    updatedText: String,
    loading: Boolean,
    nameOverrides: Map<String, String>,
    useCustomNames: Boolean,
    onRefresh: () -> Unit,
) {
    val subjects = result?.subjects.orEmpty()
    val hasSubjects = result != null && subjects.isNotEmpty()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 108.dp + navBarBottom),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            HeroAttendanceCard(
                result = result,
                updatedText = updatedText,
                loading = loading,
                onRefresh = onRefresh,
            )
        }

        if (hasSubjects) {
            item {
                StatsRow(
                    subjects = subjects.size,
                    safe = subjects.count { it.percent >= SAFE_THRESHOLD },
                    atRisk = subjects.count { it.percent < SAFE_THRESHOLD },
                )
            }

            item {
                Section("Subject breakdown") {
                    TileColumn(subjects.size) { index, position ->
                        val subject = subjects[index]
                        val override = nameOverrides[subject.code]
                        val label = when {
                            useCustomNames && !override.isNullOrBlank() -> override
                            subject.name.isNotBlank() -> subject.name
                            else -> subject.code
                        }
                        SubjectCard(subject, label, groupShape(position))
                    }
                }
            }
        } else {
            item { EmptyState() }
        }
    }
}

// ───────────────────────────── Hero ─────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroAttendanceCard(
    result: AttendanceResult?,
    updatedText: String,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val overall = result?.overallPercent ?: 0.0
    val isGood = overall >= SAFE_THRESHOLD

    val containerBg = when {
        result == null -> MaterialTheme.colorScheme.surfaceContainerHigh
        isGood -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val contentFg = when {
        result == null -> MaterialTheme.colorScheme.onSurface
        isGood -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onError
    }
    val contentSubtle = contentFg.copy(alpha = 0.72f)

    val progress by animateFloatAsState(
        targetValue = (overall / 100.0).toFloat().coerceIn(0f, 1f),
        label = "OverallProgress",
    )

    AppCard(containerColor = containerBg, contentPadding = 24.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .semantics {
                        contentDescription = "Overall Attendance %.1f percent".format(overall)
                    },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = contentFg,
                    trackColor = contentFg.copy(alpha = 0.22f),
                    strokeWidth = 10.dp,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (result == null) "--" else "%.1f".format(overall),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentFg,
                    )
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentFg,
                        modifier = Modifier.padding(bottom = 4.dp, start = 1.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Surface(shape = CircleShape, color = contentFg.copy(alpha = 0.18f)) {
                    Text(
                        text = when {
                            result == null -> "NOT SET UP"
                            isGood -> "ON TRACK"
                            else -> "LOW ATTENDANCE"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentFg,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Overall Attendance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentFg,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = updatedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentSubtle,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Always enabled so the button never takes Material's disabled palette
        // (which overrides our custom colors with onSurface @ 12%/38% and makes
        // the whole button vanish against the solid primary card). The click
        // is guarded instead — taps while loading are simply ignored.
        Button(
            onClick = { if (!loading) onRefresh() },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = contentFg,
                contentColor = containerBg,
                // Redundant safety net — if some ancestor ever sets enabled=false,
                // keep the visual identical to the enabled state rather than
                // snapping to the default disabled palette.
                disabledContainerColor = contentFg,
                disabledContentColor = containerBg,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (loading) {
                // Material 3 Expressive morphing-shape loading indicator.
                // Cycles through a sequence of RoundedPolygon shapes while
                // visible. Recommended over CircularProgressIndicator for
                // indeterminate waits under ~5 seconds.
                LoadingIndicator(
                    modifier = Modifier.size(20.dp),
                    color = containerBg,
                )
                Spacer(Modifier.size(10.dp))
            }
            Text(
                text = if (loading) "Refreshing…" else "Refresh attendance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ───────────────────────────── Stats ─────────────────────────────

private val STATS_OUTER_CORNER = 40.dp
private val STATS_INNER_CORNER = 8.dp
private val STATS_GAP = 6.dp
private val STATS_OUTER_EDGE_PADDING = 16.dp

@Composable
private fun StatsRow(subjects: Int, safe: Int, atRisk: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(STATS_GAP),
    ) {
        StatCard(
            value = subjects.toString(),
            label = "Subjects",
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(
                topStart = STATS_OUTER_CORNER,
                bottomStart = STATS_OUTER_CORNER,
                topEnd = STATS_INNER_CORNER,
                bottomEnd = STATS_INNER_CORNER,
            ),
            extraStartPadding = STATS_OUTER_EDGE_PADDING,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = safe.toString(),
            label = "Safe",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(STATS_INNER_CORNER),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = atRisk.toString(),
            label = "At risk",
            container = if (atRisk > 0) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            content = if (atRisk > 0) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(
                topEnd = STATS_OUTER_CORNER,
                bottomEnd = STATS_OUTER_CORNER,
                topStart = STATS_INNER_CORNER,
                bottomStart = STATS_INNER_CORNER,
            ),
            extraEndPadding = STATS_OUTER_EDGE_PADDING,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    container: Color,
    content: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    extraStartPadding: Dp = 0.dp,
    extraEndPadding: Dp = 0.dp,
) {
    AppCard(
        modifier = modifier,
        containerColor = container,
        contentPadding = 16.dp,
        shape = shape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = extraStartPadding, end = extraEndPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = content,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ───────────────────────────── Subjects ─────────────────────────────

@Composable
private fun SubjectCard(subject: SubjectAttendance, displayLabel: String, shape: Shape) {
    val isGood = subject.percent >= COMFY_THRESHOLD
    val isFair = subject.percent >= SAFE_THRESHOLD

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

    AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, shape = shape) {
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            Surface(shape = CircleShape, color = chipBg) {
                Text(
                    text = "%.0f%%".format(subject.percent),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = chipFg,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = { (subject.percent / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .semantics { contentDescription = "${subject.percent.toInt()} percent attendance" },
            color = if (isFair) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "${subject.present} of ${subject.total} hours attended",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ───────────────────────────── Empty ─────────────────────────────

@Composable
private fun EmptyState() {
    AppCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = 28.dp,
    ) {
        Text(
            text = "No attendance data yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Open the Settings tab and save your Etlab credentials to fetch your attendance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}