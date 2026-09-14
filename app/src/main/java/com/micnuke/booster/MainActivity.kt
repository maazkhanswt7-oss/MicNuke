package com.micnuke.booster

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var gainSlider: SeekBar
    private lateinit var gainText: TextView
    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView

    private var micThread: Thread? = null
    private var isRunning = false
    private var gain = 8.0f // Default HOLY MOLY level

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gainSlider = findViewById(R.id.gainSlider)
        gainText = findViewById(R.id.gainText)
        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)

        // Setup slider: 1x to 20x gain
        gainSlider.max = 190
        gainSlider.progress = 70 // 8x default
        updateGainText(8.0f)

        gainSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                gain = 1.0f + (progress / 10f)
                updateGainText(gain)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        toggleButton.setOnClickListener {
            if (isRunning) stopMic() else startMic()
        }

        // Ask permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 123)
        }
    }

    private fun updateGainText(g: Float) {
        gainText.text = "Gain: ${"%.1f".format(g)}x"
    }

    private fun startMic() {
        isRunning = true
        toggleButton.text = "STOP"
        statusText.text = "🔥 NUKE ACTIVE 🔥"

        micThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            val sampleRate = 48000
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4

            val recorder = AudioRecord(MediaRecorder.AudioSource.CAMCORDER,
                sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize)

            val player = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            recorder.startRecording()
            player.play()

            val buffer = ShortArray(bufferSize)

            while (isRunning) {
                val read = recorder.read(buffer, 0, bufferSize)
                if (read > 0) {
                    for (i in 0 until read) {
                        var sample = buffer[i].toFloat() * gain

                        // Soft limiter
                        val absVal = abs(sample)
                        if (absVal > 28000f) {
                            val excess = absVal - 28000f
                            sample = if (sample > 0) 28000f + excess/3 else -28000f - excess/3
                        }

                        buffer[i] = sample.coerceIn(-32768f, 32767f).toInt().toShort()
                    }
                    player.write(buffer, 0, read)
                }
            }

            recorder.stop()
            recorder.release()
            player.stop()
            player.release()
        }.apply { start() }
    }

    private fun stopMic() {
        isRunning = false
        micThread?.join(500)
        toggleButton.text = "START"
        statusText.text = "Ready"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMic()
    }
}
