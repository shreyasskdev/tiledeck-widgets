package io.github.shreyasskdev.tiledeck.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.shreyasskdev.tiledeck.data.Developer
import io.github.shreyasskdev.tiledeck.data.DevelopersRepository

private sealed interface DevState {
    data object Loading : DevState
    data object Failed : DevState
    data class Ready(val developers: List<Developer>) : DevState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val palette = expressiveBadgePalette()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var devState by remember { mutableStateOf<DevState>(DevState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var showSheet by remember { mutableStateOf(false) }

    LaunchedEffect(reloadKey) {
        devState = DevState.Loading
        devState = DevelopersRepository.load(force = reloadKey > 0).fold(
            onSuccess = { DevState.Ready(it) },
            onFailure = { DevState.Failed },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "About & privacy",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Tiledeck Widgets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 12.dp, end = 8.dp)) {
                        CircleIconButton(
                            onClick = onBack,
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back to settings",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 12.dp + navBarBottom),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { AboutHero() }

            item { GroupLabel("Made by") }
            item {
                DevelopersCard(
                    state = devState,
                    onClick = {
                        when (devState) {
                            is DevState.Ready -> showSheet = true
                            DevState.Failed -> reloadKey++
                            DevState.Loading -> Unit
                        }
                    },
                )
            }

            item { GroupLabel("App details") }
            item {
                GroupedList(
                    rows = listOf(
                        {
                            GroupedRow(
                                position = GroupPosition.Top,
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

    val ready = devState as? DevState.Ready
    if (showSheet && ready != null) {
        DevelopersSheet(
            developers = ready.developers,
            onDismiss = { showSheet = false },
        )
    }
}

// ───────────────────────────── Hero ─────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutHero() {
    val context = LocalContext.current
    val versionName = remember { appVersionName(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "TD",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Tiledeck Widgets",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Text(
                text = "Version $versionName",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Your widgets, always one glance away.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────── Developers ───────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DevelopersCard(state: DevState, onClick: () -> Unit) {
    val shape = AppCardShape
    val container = MaterialTheme.colorScheme.secondaryContainer
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        onClick = onClick,
        enabled = state !is DevState.Loading,
        shape = shape,
        color = container,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(96.dp), contentAlignment = Alignment.CenterStart) {
                when (state) {
                    DevState.Loading -> LoadingIndicator(modifier = Modifier.size(44.dp))
                    DevState.Failed -> Text("⚠", style = MaterialTheme.typography.headlineMedium)
                    is DevState.Ready -> AvatarStack(state.developers.take(4), ringColor = container)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Developers",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
                Text(
                    text = when (state) {
                        DevState.Loading -> "Loading from GitHub…"
                        DevState.Failed -> "Couldn't load — tap to retry"
                        is DevState.Ready -> {
                            val n = state.developers.size
                            if (n == 1) "1 person built this" else "$n people built this"
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.8f),
                )
            }

            if (state is DevState.Ready) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = onContainer,
                )
            }
        }
    }
}

@Composable
private fun AvatarStack(developers: List<Developer>, ringColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
        developers.forEach { dev ->
            DeveloperAvatar(
                dev = dev,
                size = 44.dp,
                modifier = Modifier.border(2.dp, ringColor, CircleShape),
            )
        }
    }
}

@Composable
private fun DeveloperAvatar(dev: Developer, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = dev.name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        AsyncImage(
            model = dev.avatarUrl,
            contentDescription = "${dev.name}'s profile picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevelopersSheet(developers: List<Developer>, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                Text(
                    text = "Developers",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Tap a profile to open it on GitHub",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(developers, key = { _, dev -> dev.login }) { index, dev ->
                    DeveloperRow(
                        dev = dev,
                        position = groupPosition(index, developers.size),
                        onClick = { uriHandler.openUri(dev.profileUrl) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeveloperRow(dev: Developer, position: GroupPosition, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = groupShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DeveloperAvatar(dev = dev, size = 56.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dev.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${dev.login}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = "${dev.contributions} commits",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// ───────────────────────────── Privacy ─────────────────────────────

@Composable
private fun PrivacyCard() {
    Surface(
        shape = AppCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                LockDotIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "This app fetches your current semester's attendance automatically. " +
                        "Credentials are encrypted at rest on your device and are only ever " +
                        "transmitted to lbscek.etlab.app — never to any third party or " +
                        "analytics service.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LockDotIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.2f, size.height * 0.45f),
            size = Size(size.width * 0.6f, size.height * 0.45f),
            cornerRadius = CornerRadius(3f, 3f),
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.3f, size.height * 0.15f),
            size = Size(size.width * 0.4f, size.height * 0.45f),
            style = Stroke(width = 2.5f),
        )
    }
}

private fun appVersionName(context: Context): String = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0L),
        ).versionName
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
}.getOrNull() ?: "1.0.0"