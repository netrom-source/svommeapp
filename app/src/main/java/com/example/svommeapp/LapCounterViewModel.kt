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

    private val _roi = MutableStateFlow(
        RectF(
            prefs.getFloat("roiX", 0.3f),
            prefs.getFloat("roiY", 0.3f),
            prefs.getFloat("roiX", 0.3f) + prefs.getFloat("roiW", 0.4f),
            prefs.getFloat("roiY", 0.3f) + prefs.getFloat("roiH", 0.4f)
        )
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

    private var lastTriggerTime: Long = 0

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

    fun updateRoi(rect: RectF) {
        _roi.value = rect
        prefs.edit()
            .putFloat("roiX", rect.left)
            .putFloat("roiY", rect.top)
            .putFloat("roiW", rect.width())
            .putFloat("roiH", rect.height())
            .apply()
    }

    fun onTurnDetected(timestamp: Long = System.currentTimeMillis()) {
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

    fun reset() {
        viewModelScope.launch {
            _laps.emit(0)
            _distanceMeters.emit(0)
            _lastLapTimes.emit(emptyList())
            lastTriggerTime = 0
        }
    }
}
