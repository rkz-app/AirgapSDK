package app.rkz.airgapsdk.player

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Entry-point composable  (drop this into your NavHost or Activity)
// ---------------------------------------------------------------------------

/**
 * @param viewModel  Provide via viewModel() / hiltViewModel() / remember { QRPlayerViewModel(data) }
 * @param title      Screen title shown in the top-app-bar
 * @param onClose    Called when the user taps the back/cancel button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRPlayerScreen(
    viewModel: QRPlayerViewModel,
    title: String,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()

    // Playback timer — mirrors the iOS Timer.scheduledTimer logic
    var frameRate by remember { mutableFloatStateOf(2f) }

    LaunchedEffect(state, frameRate) {
        if (state == QRPlayerState.Playing) {
            val intervalMs = (1000f / frameRate).toLong()
            while (true) {
                delay(intervalMs)
                viewModel.nextFrame()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ----------------------------------------------------------------
            // Display area
            // ----------------------------------------------------------------
            QRDisplayView(
                state = state,
                images = images,
                currentIndex = currentIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // ----------------------------------------------------------------
            // Config area (chunk-size slider)
            // ----------------------------------------------------------------
            val showConfig = state is QRPlayerState.Initial ||
                    state is QRPlayerState.BuildingQR ||
                    state is QRPlayerState.QRError

            if (showConfig) {
                var chunkSize by remember { mutableDoubleStateOf(460.0) }
                QRConfigView(
                    chunkSize = chunkSize,
                    enabled = state !is QRPlayerState.BuildingQR,
                    onChunkChanged = {
                        chunkSize = it
                        viewModel.chunkSize = it.toInt()
                    }
                )
            }

            // ----------------------------------------------------------------
            // Controls (playback)
            // ----------------------------------------------------------------
            if (state is QRPlayerState.Paused || state is QRPlayerState.Playing) {
                QRControlsView(
                    playButtonState = viewModel.playButtonState,
                    currentIndex = currentIndex,
                    totalFrames = images.size,
                    frameRate = frameRate,
                    isPlaying = state == QRPlayerState.Playing,
                    canGoBack = state == QRPlayerState.Paused && currentIndex > 0,
                    canGoForward = state == QRPlayerState.Paused && currentIndex < images.size - 1,
                    onPlay = {
                        if (viewModel.isPlaying) viewModel.pause() else viewModel.play()
                    },
                    onBack = { viewModel.backward() },
                    onForward = { viewModel.forward() },
                    onSpeedChanged = { frameRate = it }
                )
            }

            // ----------------------------------------------------------------
            // Build / Rebuild button
            // ----------------------------------------------------------------
            val buildEnabled = state !is QRPlayerState.BuildingQR
            val buildLabel = when (state) {
                is QRPlayerState.Paused, is QRPlayerState.Playing -> "Rebuild"
                is QRPlayerState.BuildingQR -> "Building…"
                else -> "Build QR Codes"
            }

            Button(
                onClick = {
                    if (state is QRPlayerState.Paused || state is QRPlayerState.Playing) {
                        viewModel.resetToInitial()
                    } else {
                        viewModel.assemble()
                    }
                },
                enabled = buildEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buildLabel)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Display view
// ---------------------------------------------------------------------------

@Composable
private fun QRDisplayView(
    state: QRPlayerState,
    images: List<Bitmap>,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is QRPlayerState.BuildingQR -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Generating QR codes…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is QRPlayerState.QRError -> {
                Text(
                    text = "Error:\n${state.message}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            is QRPlayerState.Paused, is QRPlayerState.Playing -> {
                if (images.isNotEmpty()) {
                    Image(
                        bitmap = images[currentIndex].asImageBitmap(),
                        contentDescription = "QR Code frame ${currentIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            else -> {
                Text(
                    text = "Configure settings and build QR codes",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Config view  (chunk-size slider)
// ---------------------------------------------------------------------------

@Composable
private fun QRConfigView(
    chunkSize: Double,
    enabled: Boolean,
    onChunkChanged: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Chunk size: ${chunkSize.toInt()} bytes",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(if (enabled) 1f else 0.5f)
        )
        Slider(
            value = chunkSize.toFloat(),
            onValueChange = { onChunkChanged(it.toDouble()) },
            valueRange = 16f..1920f,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// Controls view  (playback buttons + speed slider)
// ---------------------------------------------------------------------------

@Composable
private fun QRControlsView(
    playButtonState: PlayButtonState,
    currentIndex: Int,
    totalFrames: Int,
    frameRate: Float,
    isPlaying: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSpeedChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Frame counter
        Text(
            text = "Frame ${currentIndex + 1} / $totalFrames",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Playback buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Back
                IconButton(onClick = onBack, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous frame")
                }

                // Play / Pause / Replay
                IconButton(onClick = onPlay) {
                    val icon = when (playButtonState) {
                        PlayButtonState.PLAY -> Icons.Outlined.PlayArrow
                        PlayButtonState.PAUSE -> Icons.Outlined.Pause
                        PlayButtonState.REPLAY -> Icons.Outlined.Refresh
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = playButtonState.name,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Forward
                IconButton(onClick = onForward, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next frame")
                }
            }

            // Speed slider
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Speed",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.wrapContentWidth()
                )

                // Snap to 0.5 steps, range 1–4  (mirrors iOS speedChanged logic)
                Slider(
                    value = frameRate,
                    onValueChange = { raw ->
                        val step = 0.5f
                        val snapped = (Math.round(raw / step) * step).coerceIn(1f, 4f)
                        onSpeedChanged(snapped)
                    },
                    valueRange = 1f..4f,
                    enabled = !isPlaying,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "$frameRate fps",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.wrapContentWidth()
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Convenience extension used inside QRConfigView
// ---------------------------------------------------------------------------
private fun Modifier.alpha(alpha: Float): Modifier = this.then(
    Modifier.graphicsLayer { this.alpha = alpha }
)
