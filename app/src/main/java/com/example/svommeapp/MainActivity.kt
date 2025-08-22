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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Delete
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.svommeapp.detector.MotionDetector
import com.example.svommeapp.detector.SoundDetector
import com.example.svommeapp.ui.theme.SvommeTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
            SvommeTheme {
                if (!permissions.allPermissionsGranted) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Kamera- og mikrofontilladelser er påkrævet")
                    }
                } else {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val uri = vm.activationSoundUri.value
        if (vm.playSoundOnActivation.value && uri != null) {
            try {
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.setDataSource(this, Uri.parse(uri))
                player.setOnCompletionListener { it.release() }
                player.prepare()
                player.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val laps by vm.laps.collectAsState()
        val distance by vm.distanceMeters.collectAsState()
        val lapTimes by vm.lapTimestamps.collectAsState()
        val cameraEnabled by vm.cameraEnabled.collectAsState()
        val soundEnabled by vm.soundEnabled.collectAsState()
        val roi by vm.roi.collectAsState()
        val cameraFacing by vm.cameraFacing.collectAsState()
        val sensitivity by vm.sensitivity.collectAsState()
        val audioThreshold by vm.audioThresholdDb.collectAsState()
        val laneLength by vm.laneLengthMeters.collectAsState()
        val minInterval by vm.minIntervalMs.collectAsState()
        val turnsPerLap by vm.turnsPerLap.collectAsState()
        val counting by vm.counting.collectAsState()
        val motionLevel by vm.motionLevel.collectAsState()
        val soundLevel by vm.soundLevelDb.collectAsState()
        val debugOverlay by vm.debugOverlay.collectAsState()
        val debugLog by vm.debugLog.collectAsState()
        val previewMinimized by vm.previewMinimized.collectAsState()
        val activationSoundUri by vm.activationSoundUri.collectAsState()
        val playSoundOnActivation by vm.playSoundOnActivation.collectAsState()

        DisposableEffect(counting) {
            if (counting) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val executor = remember { Executors.newSingleThreadExecutor() }
        val previewView = remember { PreviewView(context) }
        var showSettings by remember { mutableStateOf(false) }
        var showReset by remember { mutableStateOf(false) }
        var showSessionIntervals by remember { mutableStateOf(false) }
        var showHistory by remember { mutableStateOf(false) }
        var motionFlash by remember { mutableStateOf(false) }
        var soundFlash by remember { mutableStateOf(false) }
        var hasFront by remember { mutableStateOf(false) }
        var hasBack by remember { mutableStateOf(false) }
        val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                vm.setActivationSoundUri(it.toString())
            }
        }
        val contentScroll = rememberScrollState()

        LaunchedEffect(motionLevel) {
            if (motionLevel > sensitivity) {
                motionFlash = true
                delay(200)
                motionFlash = false
            }
        }

        LaunchedEffect(soundLevel) {
            if (soundLevel > audioThreshold) {
                soundFlash = true
                delay(200)
                soundFlash = false
            }
        }

        LaunchedEffect(Unit) {
            val provider = ProcessCameraProvider.getInstance(context)
            provider.addListener({
                val p = provider.get()
                hasFront = p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                hasBack = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                if (!hasBack && hasFront) {
                    vm.setCameraFacing(CameraFacing.FRONT)
                }
            }, ContextCompat.getMainExecutor(context))
        }

        LaunchedEffect(cameraEnabled, roi, sensitivity, cameraFacing, previewMinimized) {
            if (cameraEnabled) {
                startCamera(
                    previewView,
                    executor,
                    roi,
                    sensitivity,
                    lifecycleOwner,
                    cameraFacing,
                    !previewMinimized
                )
            } else {
                stopCamera()
            }
        }

        LaunchedEffect(soundEnabled, audioThreshold) {
            if (soundEnabled) startSound(audioThreshold) else stopSound()
        }

        Scaffold(topBar = {
            TopAppBar(title = { Text("Fars Svøm-o-meter") }, actions = {
                IconButton(onClick = { showHistory = true }) {
                    Icon(Icons.Default.History, contentDescription = "Historik")
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Indstillinger")
                }
            })
        }, bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                LastIntervalsPanel(lapTimes = lapTimes) { showSessionIntervals = true }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.toggleCounting() },
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (counting) Color.Red else Color(0xFF388E3C),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            if (counting) "Stop" else "Start",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { showReset = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp),
                        enabled = true
                    ) {
                        Text(
                            "Nulstil",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AnimatedVisibility(
                    visible = !previewMinimized,
                    enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(250)),
                    exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(250)),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
                        RoiOverlay(roi = roi, highlight = motionFlash, onChange = { vm.updateRoi(it) })
                        if (soundFlash) {
                            Box(Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.2f)))
                        }
                        if (debugOverlay) {
                            Column(Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.5f)).padding(4.dp)) {
                                Text("Bevægelse: ${"%.2f".format(motionLevel)} / ${"%.2f".format(sensitivity)}", color = Color.White, fontSize = 12.sp)
                                Text("Lyd: ${"%.1f".format(soundLevel)} / $audioThreshold dB", color = Color.White, fontSize = 12.sp)
                            }
                            Column(
                                Modifier.align(Alignment.BottomStart)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(4.dp)
                                    .height(100.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                debugLog.forEach { Text(it, color = Color.White, fontSize = 10.sp) }
                            }
                        }
                        Row(
                            Modifier.align(Alignment.TopEnd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    vm.setCameraFacing(if (cameraFacing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK)
                                },
                                enabled = hasFront && hasBack,
                                modifier = Modifier.size(96.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Cameraswitch, contentDescription = "Skift kamera")
                                    Text("Skift", fontSize = 14.sp)
                                }
                            }
                            Button(
                                onClick = { vm.setPreviewMinimized(true) },
                                modifier = Modifier.size(96.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Minimér kamera")
                                    Text("Minimér", fontSize = 14.sp)
                                }
                            }
                        }
                        if (!hasFront || !hasBack) {
                            val msg = if (!hasBack) "Bagkamera mangler" else "Frontkamera mangler"
                            Text(
                                msg,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(4.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                if (previewMinimized) {
                    Box(Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { vm.setPreviewMinimized(false) },
                            modifier = Modifier.align(Alignment.TopEnd).size(96.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Vis kamera")
                                Text("Vis", fontSize = 14.sp)
                            }
                        }
                    }
                }
                val contentModifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .alpha(if (counting) 1f else 0.3f)
                    .verticalScroll(contentScroll)
                Column(
                    if (previewMinimized) contentModifier.weight(1f) else contentModifier,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text("$laps", fontSize = if (previewMinimized) 192.sp else 96.sp, fontWeight = FontWeight.Bold)
                    Text("Omgange", fontSize = if (previewMinimized) 40.sp else 20.sp)
                    Text("${distance} m", fontSize = if (previewMinimized) 96.sp else 48.sp, fontWeight = FontWeight.Bold)
                    Text("Afstand", fontSize = if (previewMinimized) 40.sp else 20.sp)
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
                debugOverlay = debugOverlay,
                onDebugOverlayChange = vm::setDebugOverlay,
                playSoundOnActivation = playSoundOnActivation,
                onPlaySoundOnActivationChange = vm::setPlaySoundOnActivation,
                soundUri = activationSoundUri,
                onPickSound = { soundPicker.launch(arrayOf("audio/mpeg")) },
                onDismiss = { showSettings = false }
            )
        }

        if (showSessionIntervals) {
            SessionIntervalsDialog(
                lapTimes = lapTimes,
                onClear = {
                    vm.clearLapHistory()
                    showSessionIntervals = false
                },
                onDismiss = { showSessionIntervals = false }
            )
        }

        if (showHistory) {
            HistoryDialog(vm = vm, onDismiss = { showHistory = false })
        }

        if (showReset) {
            AlertDialog(
                onDismissRequest = { showReset = false },
                title = { Text("Nulstil alt?") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.reset()
                        showReset = false
                    }) { Text("Ja") }
                },
                dismissButton = {
                    TextButton(onClick = { showReset = false }) { Text("Nej") }
                }
            )
        }
    }

    private fun startCamera(
        previewView: PreviewView,
        executor: Executor,
        roi: RectF,
        sensitivity: Float,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        facing: CameraFacing,
        showPreview: Boolean
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            val useCases = mutableListOf<androidx.camera.core.UseCase>()
            if (showPreview) {
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                useCases += preview
            }
            val analysis = ImageAnalysis.Builder().build()
            motionDetector = MotionDetector(roi) { level ->
                vm.reportMotion(level)
                if (level > sensitivity) {
                    vm.onTurnDetected("camera")
                }
            }
            analysis.setAnalyzer(executor, motionDetector!!)
            useCases += analysis
            provider.unbindAll()
            val selector = if (facing == CameraFacing.FRONT) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        motionDetector = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun startSound(threshold: Int) {
        soundDetector = SoundDetector { db ->
            vm.reportSound(db)
            if (db > threshold) {
                vm.onTurnDetected("audio")
            }
        }
        soundDetector?.start()
    }

    private fun stopSound() {
        soundDetector?.stop()
        soundDetector = null
    }
}

@Composable
private fun LastIntervalsPanel(lapTimes: List<Long>, onClick: () -> Unit) {
    val intervals = lapTimes.zipWithNext { a, b -> b - a }
    val last = intervals.takeLast(3).asReversed()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Seneste 3 intervaller",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
            repeat(3) { idx ->
                val value = last.getOrNull(idx)
                Text(
                    text = value?.let {
                        String.format(Locale.getDefault(), "%.1f s", it / 1000f)
                    } ?: "-",
                    fontSize = 32.sp
                )
            }
        }
    }
}

@Composable
private fun RoiOverlay(roi: RectF, highlight: Boolean, onChange: (RectF) -> Unit) {
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
                    .border(
                        BorderStroke(2.dp, if (highlight) Color.Red else MaterialTheme.colors.primary)
                    )
                    .pointerInput(parentSize) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            var newWidth = rect.width() * zoom
                            var newHeight = rect.height() * zoom
                            newWidth = newWidth.coerceIn(0.05f, 1f)
                            newHeight = newHeight.coerceIn(0.05f, 1f)
                            val marginX = with(density) { 2.dp.toPx() } / parentSize.width
                            val marginY = with(density) { 2.dp.toPx() } / parentSize.height
                            var left = rect.left + pan.x / parentSize.width
                            var top = rect.top + pan.y / parentSize.height
                            left = left.coerceIn(marginX, 1f - marginX - newWidth)
                            top = top.coerceIn(marginY, 1f - marginY - newHeight)
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
    debugOverlay: Boolean,
    onDebugOverlayChange: (Boolean) -> Unit,
    playSoundOnActivation: Boolean,
    onPlaySoundOnActivationChange: (Boolean) -> Unit,
    soundUri: String?,
    onPickSound: () -> Unit,
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
                Row(
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kamera-tælling", Modifier.weight(1f), fontSize = 20.sp)
                    Switch(cameraEnabled, onCameraEnabledChange, modifier = Modifier.size(72.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lyd-tælling", Modifier.weight(1f), fontSize = 20.sp)
                    Switch(soundEnabled, onSoundEnabledChange, modifier = Modifier.size(72.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("Kamera følsomhed: ${"%.2f".format(sensitivity)}", fontSize = 18.sp)
                Slider(
                    value = sensitivity,
                    onValueChange = onSensitivityChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.height(72.dp)
                )
                Text("Lydtærskel (dB): $audioThreshold", fontSize = 18.sp)
                Slider(
                    value = audioThreshold.toFloat(),
                    onValueChange = { onAudioThresholdChange(it.toInt()) },
                    valueRange = 50f..120f,
                    modifier = Modifier.height(72.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("Banelængde (m)")
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
                    Text("2 vendinger = 1 omgang", Modifier.weight(1f))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fejlretningslag", Modifier.weight(1f))
                    Switch(debugOverlay, onDebugOverlayChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktiveringslyd", Modifier.weight(1f))
                    Switch(playSoundOnActivation, onPlaySoundOnActivationChange)
                }
                Button(onClick = onPickSound, enabled = playSoundOnActivation) {
                    Text(if (soundUri == null) "Vælg MP3" else "Skift MP3")
                }
                soundUri?.let { Text(it, fontSize = 12.sp) }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Luk")
                }
            }
        }
    }
}

@Composable
private fun SessionIntervalsDialog(
    lapTimes: List<Long>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Slet historik?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onClear()
                }) { Text("Ja") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Nej") }
            }
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colors.background) {
            val intervals = lapTimes.zipWithNext { a, b -> b - a }
            val totalTime = if (lapTimes.size < 2) 0L else lapTimes.last() - lapTimes.first()
            val avg = if (intervals.isEmpty()) 0.0 else intervals.average()
            val fastest = intervals.minOrNull() ?: 0L
            val slowest = intervals.maxOrNull() ?: 0L
            Column(
                Modifier.padding(16.dp).width(300.dp).heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Sessionens intervaller", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Omgange: ${intervals.size}")
                Text("Samlet tid: " + String.format(Locale.getDefault(), "%.1f s", totalTime / 1000f))
                Text("Gennemsnit: " + String.format(Locale.getDefault(), "%.1f s", avg / 1000))
                Text("Hurtigste: " + String.format(Locale.getDefault(), "%.1f s", fastest / 1000f))
                Text("Langsomste: " + String.format(Locale.getDefault(), "%.1f s", slowest / 1000f))
                Spacer(Modifier.height(8.dp))
                intervals.forEachIndexed { idx, it ->
                    Text(
                        "#${idx + 1}: " + String.format(Locale.getDefault(), "%.1f s", it / 1000f),
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { confirmDelete = true }) { Text("Slet historik") }
                    Button(onClick = onDismiss) { Text("Luk") }
                }
            }
        }
    }
}

@Composable
private fun HistoryDialog(vm: LapCounterViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colors.background) {
            val sessions by vm.history.collectAsState(initial = emptyList())
            val laneLength by vm.laneLengthMeters.collectAsState()
            val turnsPerLap by vm.turnsPerLap.collectAsState()
            val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }
            var sessionToDelete by remember { mutableStateOf<Long?>(null) }
            var confirmDeleteAll by remember { mutableStateOf(false) }
            val totalLaps = sessions.sumOf { it.laps.size }
            val totalDistance = if (turnsPerLap == 0) 0 else totalLaps * laneLength / turnsPerLap
            val totalTime = sessions.sumOf { swl ->
                val end = swl.session.endedAt ?: swl.laps.lastOrNull()?.timestamp ?: swl.session.startedAt
                end - swl.session.startedAt
            }
            Column(
                Modifier.padding(16.dp).width(300.dp).heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Historik", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Samlede omgange: $totalLaps")
                Text("Samlet distance: ${totalDistance} m")
                Text("Samlet tid: " + String.format(Locale.getDefault(), "%.1f s", totalTime / 1000f))
                Spacer(Modifier.height(8.dp))
                sessions.forEach { swl ->
                    val count = swl.laps.size
                    val total = if (count > 0) swl.laps.last().timestamp - swl.laps.first().timestamp else 0L
                    val start = Instant.ofEpochMilli(swl.session.startedAt).atZone(ZoneId.systemDefault()).format(formatter)
                    val end = swl.session.endedAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(formatter)
                    } ?: "-"
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$start - $end : $count omgange, ${total/1000}s",
                            Modifier.weight(1f)
                        )
                        IconButton(onClick = { sessionToDelete = swl.session.id }) {
                            Icon(Icons.Default.Delete, contentDescription = "Slet")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val csvFormatter = DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ssXXX")
                        .withZone(ZoneId.of("Europe/Copenhagen"))
                    val text = buildString {
                        append("session_id,started_at,started_at_unix_ms,ended_at,ended_at_unix_ms,lap_timestamp,lap_timestamp_unix_ms,duration_ms,source\n")
                        sessions.forEach { s ->
                            val started = csvFormatter.format(Instant.ofEpochMilli(s.session.startedAt))
                            val ended = s.session.endedAt?.let { csvFormatter.format(Instant.ofEpochMilli(it)) } ?: ""
                            s.laps.forEach { l ->
                                val lapTs = csvFormatter.format(Instant.ofEpochMilli(l.timestamp))
                                append("${s.session.id},$started,${s.session.startedAt},$ended,${s.session.endedAt ?: ""},$lapTs,${l.timestamp},${l.durationMs},${l.source}\n")
                            }
                        }
                    }
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    dir.mkdirs()
                    val file = java.io.File(dir, "svomme_history.csv")
                    file.writeText(text)
                }) { Text("Eksportér CSV") }
                Spacer(Modifier.height(4.dp))
                Button(onClick = { confirmDeleteAll = true }) { Text("Slet alt") }
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Luk") }
            }

            if (sessionToDelete != null) {
                AlertDialog(
                    onDismissRequest = { sessionToDelete = null },
                    title = { Text("Slet session?") },
                    text = { Text("Er du sikker på, at du vil slette denne session?") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.deleteSession(sessionToDelete!!)
                            sessionToDelete = null
                        }) { Text("Slet") }
                    },
                    dismissButton = {
                        TextButton(onClick = { sessionToDelete = null }) { Text("Annuller") }
                    }
                )
            }

            if (confirmDeleteAll) {
                AlertDialog(
                    onDismissRequest = { confirmDeleteAll = false },
                    title = { Text("Slet al historik?") },
                    text = { Text("Dette kan ikke fortrydes.") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.deleteAllHistory()
                            confirmDeleteAll = false
                        }) { Text("Slet alt") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDeleteAll = false }) { Text("Annuller") }
                    }
                )
            }
        }
    }
}
