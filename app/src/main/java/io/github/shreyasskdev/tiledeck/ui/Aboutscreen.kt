package io.github.shreyasskdev.tiledeck.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Standalone "About & Privacy" destination, split out of the Settings tab.
 * Pushed on top of the tabbed root by [AttendanceExpressiveApp]; back
 * button/gesture returns to whichever tab was active.
 *
 * Hero moment for this screen: the app-identity header card up top with a
 * bold asymmetric shape. Everything below uses the same grouped, colorful
 * list language as Overview/Customize/Settings so the screen doesn't feel
 * bolted on ("max 1-2 hero moments per flow").
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val palette = expressiveBadgePalette()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About & privacy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Back to settings" },
                    ) {
                        BackArrowIcon(tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { AboutHeroHeader() }

            item { GroupLabel("App details") }
            item {
                GroupedList(
                    rows = listOf(
                        {
                            GroupedRow(
                                position = GroupPosition.Top,
                                title = "Developer",
                                subtitle = "Shreyas sk",
                                badgeColor = palette[0].first,
                                badgeContent = {
                                    Text("D", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = palette[0].second)
                                },
                            )
                        },
                        {
                            GroupedRow(
                                position = GroupPosition.Middle,
                                title = "Endpoint",
                                subtitle = "lbscek.etlab.app",
                                badgeColor = palette[1].first,
                                badgeContent = {
                                    Text("E", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = palette[1].second)
                                },
                            )
                        },
                        {
                            GroupedRow(
                                position = GroupPosition.Bottom,
                                title = "Data policy",
                                subtitle = "On-device, encrypted only",
                                badgeColor = palette[2].first,
                                badgeContent = { LockDotIcon(tint = palette[2].second) },
                            )
                        },
                    ),
                )
            }

            item { GroupLabel("Privacy") }
            item { PrivacyCard() }
        }
    }
}

@Composable
private fun AboutHeroHeader() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName
            }
        }.getOrNull() ?: "1.0.0"
    }

    // Mirrors the Overview tab's hero card shape (mirrored corners), so the
    // two screens read as the same expressive system.
    val heroShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 36.dp,
        bottomStart = 36.dp,
        bottomEnd = 20.dp,
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = heroShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.Start) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f),
                modifier = Modifier.size(56.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "LB",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "LBSCEK Attendance",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Version $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun PrivacyCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "This app fetches your current semester's attendance automatically. " +
                        "Credentials are encrypted at rest on your device and are only ever " +
                        "transmitted to lbscek.etlab.app — never to any third party or " +
                        "analytics service.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LockDotIcon(tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(16.dp)) {
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.45f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.45f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.15f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.4f, size.height * 0.45f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
        )
    }
}