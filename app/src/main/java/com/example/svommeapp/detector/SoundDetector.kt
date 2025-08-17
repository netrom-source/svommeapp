package com.example.svommeapp.detector

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Simple audio level detector that triggers when the RMS level measured in dB
 * exceeds a given threshold.
 */
class SoundDetector(
    private val onLevel: (Double) -> Unit
) {
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var record: AudioRecord? = null
    private var job: Job? = null

    fun start() {
        if (record != null) return
        record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        record?.startRecording()
        job = CoroutineScope(Dispatchers.Default).launch {
            val buffer = ShortArray(bufferSize)
            while (true) {
                val read = record?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    val rms = sqrt(buffer.take(read).map { it.toDouble() * it.toDouble() }.average())
                    val db = 20 * log10(rms.coerceAtLeast(1.0))
                    onLevel(db)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        record?.stop()
        record?.release()
        record = null
    }
}
