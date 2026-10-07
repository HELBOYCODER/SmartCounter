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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.helboy.smartcounter.core.CameraManager
import com.helboy.smartcounter.core.HapticManager
import com.helboy.smartcounter.data.SessionRepository
import com.helboy.smartcounter.engine.IouTracker
import com.helboy.smartcounter.engine.LineCounter
import com.helboy.smartcounter.engine.OnnxYoloAnalyzer
import com.helboy.smartcounter.engine.TrackedObject
import com.helboy.smartcounter.engine.UnifiedVisionAnalyzer
import com.helboy.smartcounter.ui.components.CounterHudBottom
import com.helboy.smartcounter.ui.components.CounterHudTop
import com.helboy.smartcounter.ui.components.CountingOverlay
import com.helboy.smartcounter.ui.components.PermissionCard
import com.helboy.smartcounter.ui.theme.AmberAccent
import com.helboy.smartcounter.ui.theme.BlackObsidian
import com.helboy.smartcounter.ui.theme.CyanNeon
import com.helboy.smartcounter.ui.theme.EmeraldCyber
import com.helboy.smartcounter.ui.theme.SurfaceGlass
import com.helboy.smartcounter.ui.theme.TextPrimary
import com.helboy.smartcounter.ui.theme.TextSecondary

enum class SensitivityLevel(val conf: Float, val cvSens: Float, val label: String) {
    LOW(0.36f, 0.35f, "کم"),
    NORMAL(0.30f, 0.45f, "استاندارد"),
    HIGH(0.26f, 0.55f, "دقیق"),
    ULTRA(0.20f, 0.65f, "حداکثر")
}

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

    // Strict Target Settings: Default is strict Food Container mode with YOLO AI (Zero false positives on walls/doors)
    var activePreset by remember { mutableStateOf(OnnxYoloAnalyzer.DetectionPreset.FOOD_CONTAINER) }
    var sensitivityLevel by remember { mutableStateOf(SensitivityLevel.HIGH) }
    var engineMode by remember { mutableStateOf(UnifiedVisionAnalyzer.EngineMode.YOLO_AI) }

    // UI state
    var totalCount by remember { mutableIntStateOf(0) }
    var inFrameCount by remember { mutableIntStateOf(0) }
    var countForward by remember { mutableIntStateOf(0) }
    var countBackward by remember { mutableIntStateOf(0) }
    var fps by remember { mutableFloatStateOf(0f) }
    var activeEngineName by remember { mutableStateOf("YOLOv8 AI") }
    var activeTracks by remember { mutableStateOf<List<TrackedObject>>(emptyList()) }

    var isPaused by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isVertical by remember { mutableStateOf(false) }
    var isBatchMode by remember { mutableStateOf(true) } // Batch Mode for food container arrays/trays
    var linePositionRatio by remember { mutableFloatStateOf(0.55f) }

    // Unified AI & CV Vision Analyzer
    val analyzer = remember {
        UnifiedVisionAnalyzer(
            context = context,
            tracker = tracker,
            lineCounter = lineCounter,
            onAnalysisResult = { tracks, currentFps, inFrame, engineName ->
                if (!isPaused) {
                    activeTracks = tracks
                    fps = currentFps
                    inFrameCount = inFrame
                    activeEngineName = engineName

                    if (isBatchMode) {
                        // In batch mode: count is the active target tracks in frame
                        totalCount = tracks.size
                    } else {
                        // In conveyor flow mode: count is line crossing
                        totalCount = lineCounter.totalCount
                        countForward = lineCounter.countForward
                        countBackward = lineCounter.countBackward
                    }
                }
            }
        ).apply {
            initialize()
            yoloAnalyzer.confThreshold = sensitivityLevel.conf
            yoloAnalyzer.targetMode = activePreset
            cvAnalyzer.sensitivity = sensitivityLevel.cvSens
            this.engineMode = engineMode
        }
    }

    // Apply sensitivity changes dynamically
    LaunchedEffect(sensitivityLevel, activePreset, engineMode) {
        analyzer.yoloAnalyzer.confThreshold = sensitivityLevel.conf
        analyzer.yoloAnalyzer.targetMode = activePreset
        analyzer.cvAnalyzer.sensitivity = sensitivityLevel.cvSens
        analyzer.engineMode = engineMode
    }

    // Wire haptic feedback when new items are detected or cross
    LaunchedEffect(Unit) {
        lineCounter.onCountIncrement = { _, _ ->
            hapticManager.vibrateCount()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.release()
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

        // 2. Optical Overlay Layer (Bounding Boxes + Laser Line)
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

        // 3. Central Target Status Pill
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlass)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (activeTracks.isNotEmpty()) EmeraldCyber else AmberAccent)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (activeTracks.isNotEmpty()) {
                        "✅ ${activeTracks.size} ظرف غذا در کادر شناسایی شد"
                    } else {
                        "🔍 دوربین را رو به ظروف غذا بگیرید..."
                    },
                    fontSize = 11.sp,
                    color = if (activeTracks.isNotEmpty()) EmeraldCyber else TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 4. Top HUD (Big Display, Mode, Sensitivity, Badges)
        CounterHudTop(
            totalCount = totalCount,
            inFrameCount = inFrameCount,
            countForward = countForward,
            countBackward = countBackward,
            fps = fps,
            isBatchMode = isBatchMode,
            isPaused = isPaused,
            activeEngine = activeEngineName,
            preset = activePreset,
            sensitivityLabel = sensitivityLevel.label,
            onCycleSensitivity = {
                val levels = SensitivityLevel.values()
                val nextIdx = (sensitivityLevel.ordinal + 1) % levels.size
                sensitivityLevel = levels[nextIdx]
                Toast.makeText(context, "حساسیت: ${sensitivityLevel.label}", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // 5. Bottom Control Dock (Presets, Mode, Action Buttons)
        CounterHudBottom(
            isPaused = isPaused,
            isTorchOn = isTorchOn,
            isVertical = isVertical,
            isBatchMode = isBatchMode,
            preset = activePreset,
            engineMode = engineMode,
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
                cameraManager.flipCamera(
                    lifecycleOwner = lifecycleOwner,
                    previewView = PreviewView(context),
                    analyzer = analyzer
                )
                Toast.makeText(context, "تغییر دوربین", Toast.LENGTH_SHORT).show()
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
                    if (isBatchMode) "حالت سینی (شمارش کل کادر)" else "حالت نوار نقاله (خط عبور)",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onSelectPreset = { newPreset ->
                activePreset = newPreset
                tracker.reset()
                lineCounter.reset()
                totalCount = 0
                val name = when (newPreset) {
                    OnnxYoloAnalyzer.DetectionPreset.FOOD_CONTAINER -> "قفل هدف: فقط ظروف غذا"
                    OnnxYoloAnalyzer.DetectionPreset.SPOOL_CIRCULAR -> "قفل هدف: قرقره و اجسام مدور"
                    OnnxYoloAnalyzer.DetectionPreset.ALL_OBJECTS -> "قفل هدف: کالاهای عمومی"
                }
                Toast.makeText(context, name, Toast.LENGTH_SHORT).show()
            },
            onCycleEngineMode = {
                val modes = UnifiedVisionAnalyzer.EngineMode.values()
                val nextIdx = (engineMode.ordinal + 1) % modes.size
                engineMode = modes[nextIdx]
                val modeDesc = when (engineMode) {
                    UnifiedVisionAnalyzer.EngineMode.YOLO_AI -> "هوش مصنوعی YOLOv8 (فقط ظروف)"
                    UnifiedVisionAnalyzer.EngineMode.HYBRID -> "هیبرید هوشمند"
                    UnifiedVisionAnalyzer.EngineMode.FAST_CV -> "پردازش سریع ۶۰ فریم"
                }
                Toast.makeText(context, modeDesc, Toast.LENGTH_SHORT).show()
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
