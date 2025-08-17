package com.example.svommeapp

import android.Manifest
import android.graphics.RectF
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.svommeapp.detector.MotionDetector
import com.example.svommeapp.detector.SoundDetector
import com.example.svommeapp.ui.theme.SvommeTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val vm: LapCounterViewModel by viewModels()
    private var motionDetector: MotionDetector? = null
    private var soundDetector: SoundDetector? = null
    private var cameraProvider: ProcessCameraProvider? = null

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val permissions = rememberMultiplePermissionsState(
                listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
            LaunchedEffect(Unit) { permissions.launchMultiplePermissionRequest() }
            val theme by vm.themeMode.collectAsState()
            SvommeTheme(theme) {
                if (!permissions.allPermissionsGranted) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Camera and microphone permissions are required")
                    }
                } else {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val laps by vm.laps.collectAsState()
        val distance by vm.distanceMeters.collectAsState()
        val lapTimes by vm.lastLapTimes.collectAsState()
        val cameraEnabled by vm.cameraEnabled.collectAsState()
        val soundEnabled by vm.soundEnabled.collectAsState()
        val roi by vm.roi.collectAsState()
        val sensitivity by vm.sensitivity.collectAsState()
        val audioThreshold by vm.audioThresholdDb.collectAsState()
        val laneLength by vm.laneLengthMeters.collectAsState()
        val minInterval by vm.minIntervalMs.collectAsState()
        val turnsPerLap by vm.turnsPerLap.collectAsState()
        val themeMode by vm.themeMode.collectAsState()

        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val executor = remember { Executors.newSingleThreadExecutor() }
        val previewView = remember { PreviewView(context) }
        var showSettings by remember { mutableStateOf(false) }

        LaunchedEffect(cameraEnabled, roi, sensitivity) {
            if (cameraEnabled) {
                startCamera(previewView, executor, roi, sensitivity, lifecycleOwner)
            } else {
                stopCamera()
            }
        }

        LaunchedEffect(soundEnabled, audioThreshold) {
            if (soundEnabled) startSound(audioThreshold) else stopSound()
        }

        Scaffold(topBar = {
            TopAppBar(title = { Text("Svømme") }, actions = {
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            })
        }) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
                    RoiOverlay(roi = roi, onChange = { vm.updateRoi(it) })
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("$laps", fontSize = 96.sp, fontWeight = FontWeight.Bold)
                    Text("Omgange", fontSize = 20.sp)
                    Text("${distance} m", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text("Distance", fontSize = 20.sp)
                    Text("Intervaller", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    val intervals = lapTimes.takeLast(3).zipWithNext { a, b -> b - a }
                    intervals.forEach { Text("${it/1000f}s", fontSize = 32.sp) }
                }
            }
        }

        if (showSettings) {
            SettingsDialog(
                cameraEnabled = cameraEnabled,
                onCameraEnabledChange = vm::setCameraEnabled,
                soundEnabled = soundEnabled,
                onSoundEnabledChange = vm::setSoundEnabled,
                sensitivity = sensitivity,
                onSensitivityChange = vm::updateSensitivity,
                audioThreshold = audioThreshold,
                onAudioThresholdChange = vm::updateAudioThreshold,
                laneLength = laneLength,
                onLaneLengthChange = vm::updateLaneLength,
                minInterval = minInterval,
                onMinIntervalChange = vm::updateMinInterval,
                turnsPerLap = turnsPerLap,
                onTurnsPerLapChange = vm::updateTurnsPerLap,
                themeMode = themeMode,
                onThemeModeChange = vm::updateThemeMode,
                onDismiss = { showSettings = false }
            )
        }
    }

    private fun startCamera(
        previewView: PreviewView,
        executor: Executor,
        roi: RectF,
        sensitivity: Float,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder().build()
            motionDetector = MotionDetector(roi, sensitivity) {
                vm.onTurnDetected()
            }
            analysis.setAnalyzer(executor, motionDetector!!)
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        motionDetector = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun startSound(threshold: Int) {
        soundDetector = SoundDetector(threshold) {
            vm.onTurnDetected()
        }
        soundDetector?.start()
    }

    private fun stopSound() {
        soundDetector?.stop()
        soundDetector = null
    }
}

@Composable
private fun RoiOverlay(roi: RectF, onChange: (RectF) -> Unit) {
    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().onSizeChanged { parentSize = it }) {
        if (parentSize != IntSize.Zero) {
            var rect by remember { mutableStateOf(roi) }
            LaunchedEffect(roi) { rect = roi }
            val width = with(density) { (rect.width() * parentSize.width).toDp() }
            val height = with(density) { (rect.height() * parentSize.height).toDp() }
            val offsetX = with(density) { (rect.left * parentSize.width).toDp() }
            val offsetY = with(density) { (rect.top * parentSize.height).toDp() }
            Box(
                Modifier
                    .offset(x = offsetX, y = offsetY)
                    .size(width, height)
                    .border(BorderStroke(2.dp, MaterialTheme.colors.primary))
                    .pointerInput(parentSize) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            var newWidth = rect.width() * zoom
                            var newHeight = rect.height() * zoom
                            newWidth = newWidth.coerceIn(0.05f, 1f)
                            newHeight = newHeight.coerceIn(0.05f, 1f)
                            var left = rect.left + pan.x / parentSize.width
                            var top = rect.top + pan.y / parentSize.height
                            left = left.coerceIn(0f, 1f - newWidth)
                            top = top.coerceIn(0f, 1f - newHeight)
                            rect = RectF(left, top, left + newWidth, top + newHeight)
                            onChange(rect)
                        }
                    }
            ) {
                val handleModifier = Modifier.size(16.dp).background(MaterialTheme.colors.primary)
                Box(handleModifier.align(Alignment.TopStart))
                Box(handleModifier.align(Alignment.TopEnd))
                Box(handleModifier.align(Alignment.BottomStart))
                Box(handleModifier.align(Alignment.BottomEnd))
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    cameraEnabled: Boolean,
    onCameraEnabledChange: (Boolean) -> Unit,
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    audioThreshold: Int,
    onAudioThresholdChange: (Int) -> Unit,
    laneLength: Int,
    onLaneLengthChange: (Int) -> Unit,
    minInterval: Long,
    onMinIntervalChange: (Long) -> Unit,
    turnsPerLap: Int,
    onTurnsPerLapChange: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colors.background) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .width(300.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Camera counting", Modifier.weight(1f))
                    Switch(cameraEnabled, onCameraEnabledChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sound counting", Modifier.weight(1f))
                    Switch(soundEnabled, onSoundEnabledChange)
                }
                Spacer(Modifier.height(8.dp))
                Text("Camera sensitivity: ${"%.2f".format(sensitivity)}")
                Slider(value = sensitivity, onValueChange = onSensitivityChange, valueRange = 0f..1f)
                Text("Audio threshold (dB): $audioThreshold")
                Slider(
                    value = audioThreshold.toFloat(),
                    onValueChange = { onAudioThresholdChange(it.toInt()) },
                    valueRange = 50f..120f
                )
                Spacer(Modifier.height(8.dp))
                Text("Lane length (m)")
                var laneText by remember { mutableStateOf(laneLength.toString()) }
                TextField(
                    laneText,
                    onValueChange = {
                        laneText = it
                        it.toIntOrNull()?.let(onLaneLengthChange)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("2 turns = 1 lap", Modifier.weight(1f))
                    Switch(checked = turnsPerLap == 2, onCheckedChange = {
                        onTurnsPerLapChange(if (it) 2 else 1)
                    })
                }
                Spacer(Modifier.height(8.dp))
                Text("Minimum interval (ms)")
                var intervalText by remember { mutableStateOf(minInterval.toString()) }
                TextField(
                    intervalText,
                    onValueChange = {
                        intervalText = it
                        it.toLongOrNull()?.let(onMinIntervalChange)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))
                Text("Theme")
                ThemeMode.values().forEach { mode ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = themeMode == mode, onClick = { onThemeModeChange(mode) })
                        Text(mode.name.lowercase().replaceFirstChar { c -> c.titlecase() })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}
