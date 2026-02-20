package app.rkz.airgapsdk

import android.Manifest
import android.content.Context
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

// ---------------------------------------------------------------------------
// Colour palette  (mirrors iOS Palette enum)
// ---------------------------------------------------------------------------

private object Palette {
    val Blue  = Color(0x00, 0x7A, 0xFF)
    val Green = Color(0x34, 0xC7, 0x59)
    val Red   = Color(0xFF, 0x3B, 0x30)
    val Gray  = Color(0x99, 0x99, 0x99)
}

// ---------------------------------------------------------------------------
// Entry-point composable
// ---------------------------------------------------------------------------

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    viewModel: QRScannerViewModel,
    onDataReceived: (ByteArray) -> Unit,
    onClose: () -> Unit
) {
    val state         by viewModel.state.collectAsStateWithLifecycle()
    val scannedChunks by viewModel.scannedChunks.collectAsStateWithLifecycle()
    val totalChunks   by viewModel.totalChunks.collectAsStateWithLifecycle()
    val progress      by viewModel.progress.collectAsStateWithLifecycle()

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Whether the camera preview should be running
    val isCameraActive = state is ScannerState.Scanning || state is ScannerState.Processing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Code Scanner") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ------------------------------------------------------------------
            // Camera preview (full-screen, behind the overlay)
            // ------------------------------------------------------------------
            if (isCameraActive && cameraPermission.status.isGranted) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onQRCodeDetected = { viewModel.processQRCode(it) }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }

            // ------------------------------------------------------------------
            // Bottom overlay panel  (mirrors overlayContainer / UIVisualEffectView)
            // ------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.88f))
                    .padding(20.dp)
            ) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "scanner_state"
                ) { targetState ->
                    when (targetState) {
                        is ScannerState.Idle -> IdleStatePanel(
                            onStart = {
                                if (cameraPermission.status.isGranted) {
                                    viewModel.startScanning()
                                } else {
                                    cameraPermission.launchPermissionRequest()
                                }
                            }
                        )

                        is ScannerState.Scanning -> ScanningStatePanel(
                            scannedChunks = scannedChunks,
                            totalChunks   = totalChunks,
                            progress      = progress,
                            onCancel      = { viewModel.reset() }
                        )

                        is ScannerState.Processing -> ProcessingStatePanel()

                        is ScannerState.Success -> SuccessStatePanel(
                            byteCount  = targetState.data.size,
                            onUseData  = { onDataReceived(targetState.data) },
                            onScanAgain = {
                                viewModel.reset()
                                viewModel.startScanning()
                            }
                        )

                        is ScannerState.Error -> ErrorStatePanel(
                            message    = targetState.message,
                            onTryAgain = {
                                viewModel.reset()
                                viewModel.startScanning()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Camera preview  (CameraX + ML Kit QR scanning)
// ---------------------------------------------------------------------------

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onQRCodeDetected: (String) -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor       = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val scanner = BarcodeScanning.getClient()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                barcodes
                                    .firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                                    ?.rawValue
                                    ?.let { onQRCodeDetected(it) }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

// ---------------------------------------------------------------------------
// Idle state panel
// ---------------------------------------------------------------------------

@Composable
private fun IdleStatePanel(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = null,
            tint   = Palette.Blue,
            modifier = Modifier.size(70.dp)
        )
        Text("QR Code Scanner", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text      = "Position QR codes within the camera view",
            fontSize  = 15.sp,
            color     = Palette.Gray,
            textAlign = TextAlign.Center
        )
        FilledButton(text = "Start Scanning", color = Palette.Blue, onClick = onStart)
    }
}

// ---------------------------------------------------------------------------
// Scanning state panel
// ---------------------------------------------------------------------------

@Composable
private fun ScanningStatePanel(
    scannedChunks: Set<Int>,
    totalChunks: Int,
    progress: Double,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (totalChunks == 0) {
            // Waiting sub-panel
            CircularProgressIndicator(color = Palette.Blue)
            Text("Waiting for QR code...", fontSize = 16.sp, color = Palette.Gray)
        } else {
            // Progress sub-panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Scanning", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text     = "${scannedChunks.size} / $totalChunks",
                    fontSize = 15.sp,
                    color    = Palette.Gray
                )
            }
            LinearProgressIndicator(
                progress       = { progress.toFloat() },
                modifier       = Modifier.fillMaxWidth().height(6.dp),
                color          = Palette.Blue,
                trackColor     = Palette.Gray.copy(alpha = 0.3f)
            )
        }

        FilledButton(
            text    = "Cancel",
            color   = Color(0xFFBBBBBB),
            onClick = onCancel
        )
    }
}

// ---------------------------------------------------------------------------
// Processing state panel
// ---------------------------------------------------------------------------

@Composable
private fun ProcessingStatePanel() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(color = Palette.Blue)
        Text("Processing data...", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Decoding received chunks", fontSize = 15.sp, color = Palette.Gray)
    }
}

// ---------------------------------------------------------------------------
// Success state panel
// ---------------------------------------------------------------------------

@Composable
private fun SuccessStatePanel(
    byteCount: Int,
    onUseData: () -> Unit,
    onScanAgain: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint     = Palette.Green,
            modifier = Modifier.size(70.dp)
        )
        Text("Success!", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Received $byteCount bytes", fontSize = 15.sp, color = Palette.Gray)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledButton(
                text     = "Use Data",
                color    = Palette.Green,
                onClick  = onUseData,
                modifier = Modifier.weight(1f)
            )
            FilledButton(
                text     = "Scan Again",
                color    = Palette.Blue,
                onClick  = onScanAgain,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Error state panel
// ---------------------------------------------------------------------------

@Composable
private fun ErrorStatePanel(message: String, onTryAgain: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint     = Palette.Red,
            modifier = Modifier.size(70.dp)
        )
        Text("Error", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(text = message, fontSize = 15.sp, color = Palette.Gray, textAlign = TextAlign.Center)
        FilledButton(text = "Try Again", color = Palette.Blue, onClick = onTryAgain)
    }
}

// ---------------------------------------------------------------------------
// Reusable filled button  (mirrors iOS FilledButton)
// ---------------------------------------------------------------------------

@Composable
private fun FilledButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick  = onClick,
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = color),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}