package com.example.svommeapp

import android.app.Application
import android.content.Context
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel holding all lap counting logic and settings.
 * Settings are persisted in SharedPreferences so the app remembers the
 * configuration and ROI between sessions.
 */
class LapCounterViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Lap data
    private val _laps = MutableStateFlow(0)
    val laps: StateFlow<Int> = _laps

    private val _lastLapTimes = MutableStateFlow<List<Long>>(emptyList())
    val lastLapTimes: StateFlow<List<Long>> = _lastLapTimes

    private val _distanceMeters = MutableStateFlow(0)
    val distanceMeters: StateFlow<Int> = _distanceMeters

    // Settings flows
    private val _cameraEnabled = MutableStateFlow(prefs.getBoolean("cameraEnabled", false))
    val cameraEnabled: StateFlow<Boolean> = _cameraEnabled

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean("soundEnabled", false))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled

    private val _laneLengthMeters = MutableStateFlow(prefs.getInt("laneLength", 25))
    val laneLengthMeters: StateFlow<Int> = _laneLengthMeters

    private val _sensitivity = MutableStateFlow(prefs.getFloat("sensitivity", 0.5f))
    val sensitivity: StateFlow<Float> = _sensitivity

    private val _audioThresholdDb = MutableStateFlow(prefs.getInt("audioThreshold", 80))
    val audioThresholdDb: StateFlow<Int> = _audioThresholdDb

    // ROI is stored separately for front and back cameras
    private fun loadRoi(prefix: String): RectF = RectF(
        prefs.getFloat("roi_${prefix}_x", 0.3f),
        prefs.getFloat("roi_${prefix}_y", 0.3f),
        prefs.getFloat("roi_${prefix}_x", 0.3f) + prefs.getFloat("roi_${prefix}_w", 0.4f),
        prefs.getFloat("roi_${prefix}_y", 0.3f) + prefs.getFloat("roi_${prefix}_h", 0.4f)
    )

    private val _roiBack = MutableStateFlow(loadRoi("back"))
    private val _roiFront = MutableStateFlow(loadRoi("front"))

    private val _cameraFacing = MutableStateFlow(
        CameraFacing.valueOf(prefs.getString("cameraFacing", CameraFacing.BACK.name)!!)
    )
    val cameraFacing: StateFlow<CameraFacing> = _cameraFacing

    private val _roi = MutableStateFlow(
        if (_cameraFacing.value == CameraFacing.BACK) _roiBack.value else _roiFront.value
    )
    val roi: StateFlow<RectF> = _roi

    private val _minIntervalMs = MutableStateFlow(prefs.getLong("minInterval", 2000))
    val minIntervalMs: StateFlow<Long> = _minIntervalMs

    private val _turnsPerLap = MutableStateFlow(prefs.getInt("turnsPerLap", 2))
    val turnsPerLap: StateFlow<Int> = _turnsPerLap

    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("themeMode", ThemeMode.AUTO.name)!!)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    // Debug and runtime state
    private val _counting = MutableStateFlow(true)
    val counting: StateFlow<Boolean> = _counting

    private val _motionLevel = MutableStateFlow(0f)
    val motionLevel: StateFlow<Float> = _motionLevel

    private val _soundLevelDb = MutableStateFlow(0.0)
    val soundLevelDb: StateFlow<Double> = _soundLevelDb

    private val _debugLog = MutableStateFlow<List<String>>(emptyList())
    val debugLog: StateFlow<List<String>> = _debugLog

    private val _debugOverlay = MutableStateFlow(false)
    val debugOverlay: StateFlow<Boolean> = _debugOverlay

    private var lastTriggerTime: Long = 0

    private val _previewMinimized = MutableStateFlow(prefs.getBoolean("previewMinimized", false))
    val previewMinimized: StateFlow<Boolean> = _previewMinimized

    fun setCameraEnabled(enabled: Boolean) {
        _cameraEnabled.value = enabled
        prefs.edit().putBoolean("cameraEnabled", enabled).apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs.edit().putBoolean("soundEnabled", enabled).apply()
    }

    fun updateLaneLength(meters: Int) {
        _laneLengthMeters.value = meters
        prefs.edit().putInt("laneLength", meters).apply()
    }

    fun updateSensitivity(value: Float) {
        _sensitivity.value = value
        prefs.edit().putFloat("sensitivity", value).apply()
    }

    fun updateAudioThreshold(db: Int) {
        _audioThresholdDb.value = db
        prefs.edit().putInt("audioThreshold", db).apply()
    }

    fun updateMinInterval(ms: Long) {
        _minIntervalMs.value = ms
        prefs.edit().putLong("minInterval", ms).apply()
    }

    fun updateTurnsPerLap(value: Int) {
        _turnsPerLap.value = value
        prefs.edit().putInt("turnsPerLap", value).apply()
    }

    fun updateThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("themeMode", mode.name).apply()
    }

    fun setCounting(active: Boolean) {
        _counting.value = active
    }

    fun toggleCounting() { _counting.value = !_counting.value }

    fun setDebugOverlay(enabled: Boolean) {
        _debugOverlay.value = enabled
    }

    fun updateRoi(rect: RectF) {
        if (_cameraFacing.value == CameraFacing.BACK) {
            _roiBack.value = rect
            saveRoi("back", rect)
        } else {
            _roiFront.value = rect
            saveRoi("front", rect)
        }
        _roi.value = rect
    }

    private fun saveRoi(prefix: String, rect: RectF) {
        prefs.edit()
            .putFloat("roi_${prefix}_x", rect.left)
            .putFloat("roi_${prefix}_y", rect.top)
            .putFloat("roi_${prefix}_w", rect.width())
            .putFloat("roi_${prefix}_h", rect.height())
            .apply()
    }

    fun setCameraFacing(facing: CameraFacing) {
        _cameraFacing.value = facing
        prefs.edit().putString("cameraFacing", facing.name).apply()
        _roi.value = if (facing == CameraFacing.BACK) _roiBack.value else _roiFront.value
    }

    fun setPreviewMinimized(minimized: Boolean) {
        _previewMinimized.value = minimized
        prefs.edit().putBoolean("previewMinimized", minimized).apply()
    }

    fun onTurnDetected(timestamp: Long = System.currentTimeMillis()) {
        if (!_counting.value) return
        if (timestamp - lastTriggerTime < _minIntervalMs.value) return
        lastTriggerTime = timestamp
        viewModelScope.launch {
            val newLaps = _laps.value + 1
            _laps.emit(newLaps)
            _distanceMeters.emit(newLaps * _laneLengthMeters.value / _turnsPerLap.value)
            val times = (_lastLapTimes.value + timestamp).takeLast(3)
            _lastLapTimes.emit(times)
        }
    }

    fun reportMotion(level: Float) {
        viewModelScope.launch {
            _motionLevel.emit(level)
            addLog("camera", level)
        }
    }

    fun reportSound(db: Double) {
        viewModelScope.launch {
            _soundLevelDb.emit(db)
            addLog("sound", db.toFloat())
        }
    }

    private fun addLog(source: String, value: Float) {
        val entry = "${System.currentTimeMillis()} $source ${"%.2f".format(value)}"
        _debugLog.value = (_debugLog.value + entry).takeLast(50)
    }

    fun reset() {
        viewModelScope.launch {
            _laps.emit(0)
            _distanceMeters.emit(0)
            _lastLapTimes.emit(emptyList())
            lastTriggerTime = 0
        }
    }
}
