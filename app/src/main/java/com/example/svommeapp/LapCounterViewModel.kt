package com.example.svommeapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel holding all lap counting logic and settings.
 * The two detector classes report events via [onTurnDetected], which will
 * update lap count and interval information.
 */
class LapCounterViewModel : ViewModel() {
    private val _laps = MutableStateFlow(0)
    val laps: StateFlow<Int> = _laps

    private val _lastLapTimes = MutableStateFlow<List<Long>>(emptyList())
    val lastLapTimes: StateFlow<List<Long>> = _lastLapTimes

    private val _distanceMeters = MutableStateFlow(0)
    val distanceMeters: StateFlow<Int> = _distanceMeters

    // Settings
    var laneLengthMeters: Int = 25
    var sensitivity: Float = 0.5f
    var audioThresholdDb: Int = 80
    var roiX: Float = 0.3f
    var roiY: Float = 0.3f
    var roiW: Float = 0.4f
    var roiH: Float = 0.4f
    var minIntervalMs: Long = 2000
    var turnsPerLap: Int = 2

    private var lastTriggerTime: Long = 0

    fun onTurnDetected(timestamp: Long = System.currentTimeMillis()) {
        if (timestamp - lastTriggerTime < minIntervalMs) return
        lastTriggerTime = timestamp
        viewModelScope.launch {
            val newLaps = _laps.value + 1
            _laps.emit(newLaps)
            _distanceMeters.emit(newLaps * laneLengthMeters / turnsPerLap)
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
