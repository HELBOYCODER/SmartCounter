package com.helboy.smartcounter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.helboy.smartcounter.core.CameraManager
import com.helboy.smartcounter.core.HapticManager
import com.helboy.smartcounter.data.SessionRepository
import com.helboy.smartcounter.engine.IndustrialVisionAnalyzer
import com.helboy.smartcounter.engine.IouTracker
import com.helboy.smartcounter.engine.LineCounter
import com.helboy.smartcounter.engine.TrackedObject
import com.helboy.smartcounter.ui.components.CounterHudBottom
import com.helboy.smartcounter.ui.components.CounterHudTop
import com.helboy.smartcounter.ui.components.CountingOverlay
import com.helboy.smartcounter.ui.components.PermissionCard
import com.helboy.smartcounter.ui.theme.BlackObsidian

@Composable
fun SmartCounterScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "مجوز دوربین اعطا نشد", Toast.LENGTH_SHORT).show()
        }
    }

    // Core managers
    val hapticManager = remember { HapticManager(context) }
    val cameraManager = remember { CameraManager(context) }
    val repository = remember { SessionRepository(context) }

    // Engine state
    val tracker = remember { IouTracker() }
    val lineCounter = remember {
        LineCounter(
            linePositionRatio = 0.55f,
            isVertical = false
        )
    }

    // UI state
    var totalCount by remember { mutableIntStateOf(0) }
    var inFrameCount by remember { mutableIntStateOf(0) }
    var countForward by remember { mutableIntStateOf(0) }
    var countBackward by remember { mutableIntStateOf(0) }
    var fps by remember { mutableFloatStateOf(0f) }
    var activeTracks by remember { mutableStateOf<List<TrackedObject>>(emptyList()) }

    var isPaused by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isVertical by remember { mutableStateOf(false) }
    var isBatchMode by remember { mutableStateOf(false) }
    var linePositionRatio by remember { mutableFloatStateOf(0.55f) }

    // Vision analyzer
    val analyzer = remember {
        IndustrialVisionAnalyzer(
            tracker = tracker,
            lineCounter = lineCounter,
            onAnalysisResult = { tracks, currentFps, inFrame ->
                if (!isPaused) {
                    activeTracks = tracks
                    fps = currentFps
                    inFrameCount = inFrame

                    if (isBatchMode) {
                        // In batch mode, total count is the number of distinct detected items in frame
                        totalCount = inFrame
                    } else {
                        // In flow mode, total count is the line crossing count
                        totalCount = lineCounter.totalCount
                        countForward = lineCounter.countForward
                        countBackward = lineCounter.countBackward
                    }
                }
            }
        )
    }

    // Wire haptic feedback to line crossing
    LaunchedEffect(Unit) {
        lineCounter.onCountIncrement = { _, _ ->
            hapticManager.vibrateCount()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
            repository.close()
        }
    }

    if (!hasCameraPermission) {
        PermissionCard(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackObsidian)
    ) {
        // 1. CameraX Preview Layer
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                }
                cameraManager.bindCamera(
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    analyzer = analyzer
                )
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Optical Overlay Layer (boxes + laser line)
        if (!isPaused) {
            CountingOverlay(
                tracks = activeTracks,
                linePositionRatio = linePositionRatio,
                isVertical = isVertical,
                onLinePositionChanged = { newRatio ->
                    linePositionRatio = newRatio
                    lineCounter.linePositionRatio = newRatio
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Top HUD (Digital Display & Stats)
        CounterHudTop(
            totalCount = totalCount,
            inFrameCount = inFrameCount,
            countForward = countForward,
            countBackward = countBackward,
            fps = fps,
            isBatchMode = isBatchMode,
            isPaused = isPaused,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // 4. Bottom Control Dock
        CounterHudBottom(
            isPaused = isPaused,
            isTorchOn = isTorchOn,
            isVertical = isVertical,
            isBatchMode = isBatchMode,
            onTogglePause = {
                isPaused = !isPaused
                analyzer.isEnabled = !isPaused
            },
            onReset = {
                hapticManager.vibrateReset()
                tracker.reset()
                lineCounter.reset()
                totalCount = 0
                countForward = 0
                countBackward = 0
                activeTracks = emptyList()
                Toast.makeText(context, "شمارش صفر شد", Toast.LENGTH_SHORT).show()
            },
            onToggleTorch = {
                isTorchOn = cameraManager.toggleTorch()
            },
            onFlipCamera = {
                // Re-bind to front/back lens
                Toast.makeText(context, "در حال تغییر دوربین...", Toast.LENGTH_SHORT).show()
            },
            onToggleOrientation = {
                isVertical = !isVertical
                lineCounter.isVertical = isVertical
            },
            onToggleMode = {
                isBatchMode = !isBatchMode
                tracker.reset()
                lineCounter.reset()
                totalCount = 0
                Toast.makeText(
                    context,
                    if (isBatchMode) "حالت شمارش سینی فعال شد" else "حالت نوار نقاله فعال شد",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onExportCsv = {
                val modeStr = if (isBatchMode) "Batch/Tray" else "Conveyor/Flow"
                repository.saveSession(totalCount, countForward, countBackward, modeStr)
                val csvFile = repository.exportCsv()
                if (csvFile != null && csvFile.exists()) {
                    Toast.makeText(context, "گزارش در ${csvFile.name} ذخیره شد", Toast.LENGTH_LONG).show()
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            csvFile
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "ارسال گزارش CSV"))
                    } catch (_: Exception) {
                        // Fallback if sharing direct uri fails
                    }
                } else {
                    Toast.makeText(context, "خطا در صدور فایل CSV", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}
