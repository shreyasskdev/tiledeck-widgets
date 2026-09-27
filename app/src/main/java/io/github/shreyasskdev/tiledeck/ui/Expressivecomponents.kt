package io.github.shreyasskdev.tiledeck.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared building blocks for the "grouped, colorful list" pattern used by the
 * Android 16 Settings app and the Google Account app: a run of rows that
 * share one rounded container — big corners at the very top/bottom of the
 * group, tight corners between neighbors — each row led by a flat-color
 * icon badge. Used across Overview, Customize and Settings so the whole app
 * reads as one consistent expressive system rather than a pile of cards.
 */

internal enum class GroupPosition { Single, Top, Middle, Bottom }

internal fun groupPosition(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.Single
    index == 0 -> GroupPosition.Top
    index == count - 1 -> GroupPosition.Bottom
    else -> GroupPosition.Middle
}

private fun groupShape(position: GroupPosition): RoundedCornerShape {
    val large = 26.dp
    val small = 6.dp
    return when (position) {
        GroupPosition.Single -> RoundedCornerShape(large)
        GroupPosition.Top -> RoundedCornerShape(topStart = large, topEnd = large, bottomStart = small, bottomEnd = small)
        GroupPosition.Middle -> RoundedCornerShape(small)
        GroupPosition.Bottom -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = large, bottomEnd = large)
    }
}

/**
 * A small palette of container/on-container token pairs to cycle icon
 * badges through. Pulled from the live [MaterialTheme.colorScheme] (so it
 * follows dynamic color) rather than hardcoded hues.
 */
@Composable
internal fun expressiveBadgePalette(): List<Pair<Color, Color>> {
    val scheme = MaterialTheme.colorScheme
    return listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.errorContainer to scheme.onErrorContainer,
    )
}

/** Rounded-square flat-color badge that leads a [GroupedRow], e.g. holding a small icon or initials. */
@Composable
internal fun IconBadge(
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier.size(40.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/** One row inside a [GroupedList]: badge, title/subtitle, and an optional trailing slot. */
@Composable
internal fun GroupedRow(
    position: GroupPosition,
    title: String,
    badgeColor: Color,
    badgeContent: @Composable () -> Unit,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    rowContentDescription: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = rowContentDescription ?: title }
    } else {
        Modifier
    }

    Surface(
        shape = groupShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(containerColor = badgeColor) { badgeContent() }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

/** Vertical run of [GroupedRow]s, 2dp apart, that together form one rounded settings group. */
@Composable
internal fun GroupedList(
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row -> row() }
    }
}

/** Small section label ("ACCOUNT", "SYNC"...) sitting above a [GroupedList], as in the reference apps. */
@Composable
internal fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, bottom = 2.dp),
    )
}