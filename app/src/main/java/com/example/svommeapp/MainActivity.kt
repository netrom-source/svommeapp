package com.example.svommeapp

import android.Manifest
import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.svommeapp.detector.MotionDetector
import com.example.svommeapp.detector.SoundDetector
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val vm: LapCounterViewModel by viewModels()
    private var motionDetector: MotionDetector? = null
    private var soundDetector: SoundDetector? = null

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val permissions = rememberMultiplePermissionsState(
                listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
            LaunchedEffect(Unit) { permissions.launchMultiplePermissionRequest() }
            if (!permissions.allPermissionsGranted) {
                Text("Camera and microphone permissions are required")
            } else {
                MainScreen()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val laps by vm.laps.collectAsState()
        val distance by vm.distanceMeters.collectAsState()
        val lapTimes by vm.lastLapTimes.collectAsState()
        var cameraEnabled by remember { mutableStateOf(false) }
        var soundEnabled by remember { mutableStateOf(false) }
        val executor = remember { Executors.newSingleThreadExecutor() }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Laps: $laps", fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text("Distance: ${distance}m (${distance/1000f}km)", fontSize = 24.sp)
            Text("Last intervals:", fontWeight = FontWeight.Bold)
            lapTimes.takeLast(3).zipWithNext { a, b -> b - a }.forEach {
                Text("${it/1000f}s")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Camera")
                Switch(cameraEnabled, {
                    cameraEnabled = it
                    if (it) startCamera(executor) else stopCamera()
                })
            }
            if (!cameraEnabled) {
                Text("Kamera-tælling er slået fra")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Microphone")
                Switch(soundEnabled, {
                    soundEnabled = it
                    if (it) startSound() else stopSound()
                })
            }
            Spacer(Modifier.height(8.dp))
            Text("Lane length (m)")
            var lane by remember { mutableStateOf(vm.laneLengthMeters.toString()) }
            TextField(lane, {
                lane = it
                vm.laneLengthMeters = it.toIntOrNull() ?: vm.laneLengthMeters
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }

    private fun startCamera(executor: java.util.concurrent.Executor) {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val analysis = ImageAnalysis.Builder().build()
            val roi = Rect(0, 0, 100, 100) // Placeholder ROI
            motionDetector = MotionDetector(roi, vm.sensitivity) {
                vm.onTurnDetected()
            }
            analysis.setAnalyzer(executor, motionDetector!!)
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        motionDetector = null
    }

    private fun startSound() {
        soundDetector = SoundDetector(vm.audioThresholdDb) {
            vm.onTurnDetected()
        }
        soundDetector?.start()
    }

    private fun stopSound() {
        soundDetector?.stop()
        soundDetector = null
    }
}
