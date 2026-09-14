package com.micnuke.booster

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.*
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    // ----- UI references -----
    private lateinit var vuMeter: ProgressBar
    private lateinit var vuText: TextView
    private lateinit var statusText: TextView
    private lateinit var gainText: TextView
    private lateinit var gainSlider: SeekBar
    private lateinit var loudnessText: TextView
    private lateinit var loudnessSlider: SeekBar
    private lateinit var distortionSwitch: Switch
    private lateinit var distortionText: TextView
    private lateinit var distortionSlider: SeekBar
    private lateinit var pitchValText: TextView
    private lateinit var pitchSlider: SeekBar
    private lateinit var ringModSwitch: Switch
    private lateinit var ringModText: TextView
    private lateinit var ringModSlider: SeekBar
    private lateinit var bitcrusherSwitch: Switch
    private lateinit var bitcrushText: TextView
    private lateinit var bitcrushSlider: SeekBar
    private lateinit var echoSwitch: Switch
    private lateinit var echoDelayText: TextView
    private lateinit var echoDelaySlider: SeekBar
    private lateinit var echoFeedbackText: TextView
    private lateinit var echoFeedbackSlider: SeekBar
    private lateinit var flangerSwitch: Switch
    private lateinit var flangerRateText: TextView
    private lateinit var flangerRateSlider: SeekBar
    private lateinit var reverbSpinner: Spinner
    private lateinit var bassBoostSwitch: Switch
    private lateinit var bassBoostText: TextView
    private lateinit var bassBoostSlider: SeekBar
    private lateinit var compressorSwitch: Switch
    private lateinit var noiseGateSwitch: Switch
    private lateinit var toggleButton: Button

    // ----- Audio state -----
    private var micThread: Thread? = null
    @Volatile private var isRunning = false
    private var equalizer: Equalizer? = null

    // ----- Effect params (shared with audio thread) -----
    @Volatile private var gain = 8.0f
    @Volatile private var loudnessMb = 3000
    @Volatile private var distortionOn = false
    @Volatile private var distortionAmount = 0.4f
    @Volatile private var pitchSemi = 0.0f
    @Volatile private var ringModOn = false
    @Volatile private var ringModFreq = 100.0f
    @Volatile private var bitcrushOn = false
    @Volatile private var bitDepth = 16
    @Volatile private var echoOn = false
    @Volatile private var echoDelayMs = 300
    @Volatile private var echoFeedback = 0.4f
    @Volatile private var flangerOn = false
    @Volatile private var flangerRate = 2.0f
    @Volatile private var reverbIdx = 0
    @Volatile private var bassBoostOn = false
    @Volatile private var bassBoostStrength = 500
    @Volatile private var compressorOn = false
    @Volatile private var noiseGateOn = false
    @Volatile private var eqBandLevels = floatArrayOf(0f, 0f, 0f, 0f, 0f)

    private val reverbNames = arrayOf(
        "None", "Small Room", "Medium Room", "Large Room",
        "Medium Hall", "Large Hall", "Plate"
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // bind views
        vuMeter = findViewById(R.id.vuMeter)
        vuText = findViewById(R.id.vuText)
        statusText = findViewById(R.id.statusText)
        gainText = findViewById(R.id.gainText)
        gainSlider = findViewById(R.id.gainSlider)
        loudnessText = findViewById(R.id.loudnessText)
        loudnessSlider = findViewById(R.id.loudnessSlider)
        distortionSwitch = findViewById(R.id.distortionSwitch)
        distortionText = findViewById(R.id.distortionText)
        distortionSlider = findViewById(R.id.distortionSlider)
        pitchValText = findViewById(R.id.pitchValText)
        pitchSlider = findViewById(R.id.pitchSlider)
        ringModSwitch = findViewById(R.id.ringModSwitch)
        ringModText = findViewById(R.id.ringModText)
        ringModSlider = findViewById(R.id.ringModSlider)
        bitcrusherSwitch = findViewById(R.id.bitcrusherSwitch)
        bitcrushText = findViewById(R.id.bitcrushText)
        bitcrushSlider = findViewById(R.id.bitcrushSlider)
        echoSwitch = findViewById(R.id.echoSwitch)
        echoDelayText = findViewById(R.id.echoDelayText)
        echoDelaySlider = findViewById(R.id.echoDelaySlider)
        echoFeedbackText = findViewById(R.id.echoFeedbackText)
        echoFeedbackSlider = findViewById(R.id.echoFeedbackSlider)
        flangerSwitch = findViewById(R.id.flangerSwitch)
        flangerRateText = findViewById(R.id.flangerRateText)
        flangerRateSlider = findViewById(R.id.flangerRateSlider)
        reverbSpinner = findViewById(R.id.reverbSpinner)
        bassBoostSwitch = findViewById(R.id.bassBoostSwitch)
        bassBoostText = findViewById(R.id.bassBoostText)
        bassBoostSlider = findViewById(R.id.bassBoostSlider)
        compressorSwitch = findViewById(R.id.compressorSwitch)
        noiseGateSwitch = findViewById(R.id.noiseGateSwitch)
        toggleButton = findViewById(R.id.toggleButton)

        setupUi()
        setupListeners()

        requestMicPermission()
    }

    private fun setupUi() {
        // Slider defaults
        loudnessSlider.progress = 3000
        updateLoudnessText(3000)
        updateGainText(8.0f)
        distortionSlider.progress = 40
        updateDistortionText(40)
        pitchSlider.progress = 120   // center = 0 semitones
        pitchValText.text = "0"
        ringModSlider.progress = 99
        updateRingModText(100f)
        bitcrushSlider.progress = 12
        updateBitcrushText(12)
        echoDelaySlider.progress = 300
        updateEchoDelayText(300)
        echoFeedbackSlider.progress = 40
        updateEchoFeedbackText(40)
        flangerRateSlider.progress = 20
        updateFlangerRateText(2.0f)
        bassBoostSlider.progress = 500
        updateBassBoostText(500)

        // Reverb spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, reverbNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        reverbSpinner.adapter = adapter
    }


    private fun setupListeners() {
        // Master gain (1x - 50x: 0..490 -> /10 +1)
        gainSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                gain = 1.0f + p / 10f
                updateGainText(gain)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        loudnessSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                loudnessMb = p
                updateLoudnessText(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        distortionSwitch.setOnCheckedChangeListener { _, isChecked -> distortionOn = isChecked }
        distortionSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                distortionAmount = p / 100f
                updateDistortionText(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        pitchSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                pitchSemi = (p - 120) / 10f   // -12 .. +12
                pitchValText.text = formatSigned(pitchSemi) + " st"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        ringModSwitch.setOnCheckedChangeListener { _, isChecked -> ringModOn = isChecked }
        ringModSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                ringModFreq = 10f + p / 10f
                updateRingModText(ringModFreq)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        bitcrusherSwitch.setOnCheckedChangeListener { _, isChecked -> bitcrushOn = isChecked }
        bitcrushSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                bitDepth = maxOf(4, p)
                updateBitcrushText(bitDepth)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        echoSwitch.setOnCheckedChangeListener { _, isChecked -> echoOn = isChecked }
        echoDelaySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                echoDelayMs = maxOf(20, p)
                updateEchoDelayText(echoDelayMs)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        echoFeedbackSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                echoFeedback = p / 100f
                updateEchoFeedbackText(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        flangerSwitch.setOnCheckedChangeListener { _, isChecked -> flangerOn = isChecked }
        flangerRateSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                flangerRate = p / 10f
                updateFlangerRateText(flangerRate)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        reverbSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) { reverbIdx = pos }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        bassBoostSwitch.setOnCheckedChangeListener { _, isChecked -> bassBoostOn = isChecked }
        bassBoostSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                bassBoostStrength = p
                updateBassBoostText(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        compressorSwitch.setOnCheckedChangeListener { _, isChecked -> compressorOn = isChecked }
        noiseGateSwitch.setOnCheckedChangeListener { _, isChecked -> noiseGateOn = isChecked }

        toggleButton.setOnClickListener {
            if (isRunning) stopMic() else startMic()
        }

        // Presets
        findViewById<Button>(R.id.presetNuke).setOnClickListener { applyPreset(0) }
        findViewById<Button>(R.id.presetRobot).setOnClickListener { applyPreset(1) }
        findViewById<Button>(R.id.presetDemon).setOnClickListener { applyPreset(2) }
        findViewById<Button>(R.id.presetChipmunk).setOnClickListener { applyPreset(3) }
        findViewById<Button>(R.id.presetDeep).setOnClickListener { applyPreset(4) }
        findViewById<Button>(R.id.presetHelicopter).setOnClickListener { applyPreset(5) }
    }


    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 123)
        }
    }

    private fun startMic() {
        if (isRunning) return
        isRunning = true
        toggleButton.text = getString(R.string.btn_stop)
        toggleButton.setBackgroundResource(R.drawable.btn_stop)
        statusText.text = getString(R.string.status_active)
        statusText.setTextColor(ContextCompat.getColor(this, R.color.neon_pink))

        micThread = thread(start = true) { audioLoop() }
    }

    private fun stopMic() {
        isRunning = false
        micThread?.join(800)
        micThread = null
        toggleButton.text = getString(R.string.btn_start)
        toggleButton.setBackgroundResource(R.drawable.btn_start)
        statusText.text = getString(R.string.status_ready)
        statusText.setTextColor(ContextCompat.getColor(this, R.color.text_white))
        vuMeter.progress = 0
        vuText.text = "-inf dB"
    }

    override fun onDestroy() {
        stopMic()
        super.onDestroy()
    }


    private fun audioLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        val sampleRate = 48000
        val minBuf = AudioRecord.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(if (minBuf > 0) minBuf * 4 else 4096, 4096)

        var recorder: AudioRecord? = null
        var player: AudioTrack? = null
        var le: LoudnessEnhancer? = null
        var bb: BassBoost? = null
        var eq: Equalizer? = null
        var reverb: PresetReverb? = null
        var ns: NoiseSuppressor? = null

        try {
            recorder = AudioRecord(MediaRecorder.AudioSource.CAMCORDER,
                sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufSize)

            player = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val session = player.audioSessionId

            // Hardware effects attached to output session
            try { le = LoudnessEnhancer(session); le.targetGain = loudnessMb; le.enabled = true } catch (e: Exception) {}
            try {
                bb = BassBoost(0, session)
                bb.strength = if (bassBoostOn) bassBoostStrength.toShort() else 0
                bb.enabled = true
            } catch (e: Exception) {}
            try {
                eq = Equalizer(0, session)
                equalizer = eq
                applyEq(eq)
                eq.enabled = true
            } catch (e: Exception) {}
            try {
                reverb = PresetReverb(0, session)
                applyReverb(reverb)
                reverb.enabled = reverbIdx > 0
            } catch (e: Exception) {}

            // Noise gate on input session
            try {
                if (noiseGateOn) {
                    ns = NoiseSuppressor.create(recorder.audioSessionId)
                    ns?.enabled = true
                }
            } catch (e: Exception) {}

            recorder.startRecording()
            player.play()

            val inBuf = ShortArray(bufSize)
            val outBuf = ShortArray(bufSize)
            val floatBuf = FloatArray(bufSize)

            while (isRunning) {
                val read = recorder.read(inBuf, 0, bufSize)
                if (read > 0) {
                    processEffects(inBuf, floatBuf, outBuf, read)
                    player.write(outBuf, 0, read)
                    updateVu(outBuf, read)
                }
            }

            recorder.stop()
            player.stop()
        } catch (e: Exception) {
            runOnUiThread { statusText.text = "Error: ${e.message ?: "audio failed"}" }
        } finally {
            le?.release(); bb?.release(); eq?.release(); reverb?.release(); ns?.release()
            if (equalizer === eq) equalizer = null
            try { recorder?.release() } catch (e: Exception) {}
            try { player?.release() } catch (e: Exception) {}
            if (isRunning) {
                runOnUiThread {
                    isRunning = false
                    toggleButton.text = getString(R.string.btn_start)
                    toggleButton.setBackgroundResource(R.drawable.btn_start)
                    statusText.text = getString(R.string.status_ready)
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.text_white))
                    vuMeter.progress = 0
                    vuText.text = "-inf dB"
                }
            }
        }
    }

    private fun applyEq(eq: Equalizer) {
        try {
            val bands = eq.numberOfBands
            for (i in 0 until bands) {
                val lvl = if (i < eqBandLevels.size) (eqBandLevels[i] * 100).toInt() else 0
                eq.setBandLevel(i.toShort(), lvl.toShort())
            }
        } catch (e: Exception) {}
    }

    private fun applyReverb(rv: PresetReverb) {
        val preset = when (reverbIdx) {
            1 -> PresetReverb.PRESET_SMALLROOM
            2 -> PresetReverb.PRESET_MEDIUMROOM
            3 -> PresetReverb.PRESET_LARGEROOM
            4 -> PresetReverb.PRESET_MEDIUMHALL
            5 -> PresetReverb.PRESET_LARGEHALL
            6 -> PresetReverb.PRESET_PLATE
            else -> PresetReverb.PRESET_NONE
        }
        rv.preset = preset.toShort()
    }


    // ================= CUSTOM DSP =================
    private val TWO_PI = 6.2831853f

    // pitch shifter state
    private val pitchBuf = FloatArray(8192)
    private var pitchWrite = 0
    private var pitchPhase = -1f
    // ring mod state
    private var ringPhase = 0f
    // flanger state
    private val flangerBuf = FloatArray(2048)
    private var flangerWrite = 0
    private var flangerPhase = 0f
    // echo state (up to 1.5s)
    private val echoDelayBuf = FloatArray(72000)
    private var echoWritePos = 0

    private fun processEffects(inBuf: ShortArray, floatBuf: FloatArray,
                               outBuf: ShortArray, count: Int) {
        for (i in 0 until count) floatBuf[i] = inBuf[i].toFloat() / 32768f

        // 1. gain
        if (gain != 1f) applyGain(floatBuf, count)

        // 2. distortion
        if (distortionOn) applyDistortion(floatBuf, count)

        // 3. pitch shift
        if (pitchSemi != 0f) pitchShift(floatBuf, count)

        // 4. ring modulator
        if (ringModOn) applyRingMod(floatBuf, count)

        // 5. bitcrusher
        if (bitcrushOn) applyBitcrusher(floatBuf, count)

        // 6. flanger
        if (flangerOn) applyFlanger(floatBuf, count)

        // 7. echo
        if (echoOn) applyEcho(floatBuf, count)

        for (i in 0 until count) {
            val v = floatBuf[i] * 32767f
            outBuf[i] = v.coerceIn(-32768f, 32767f).toInt().toShort()
        }
    }

    private fun applyGain(buf: FloatArray, count: Int) {
        for (i in 0 until count) buf[i] *= gain
    }

    private fun applyDistortion(buf: FloatArray, count: Int) {
        val drive = 1f + distortionAmount * 24f
        for (i in 0 until count) {
            buf[i] = tanh(buf[i] * drive)
        }
    }

    private fun pitchShift(buf: FloatArray, count: Int) {
        val ratio = 2f.pow(pitchSemi / 12f)
        for (i in 0 until count) {
            pitchBuf[pitchWrite % pitchBuf.size] = buf[i]
            pitchWrite++
            if (pitchPhase < 0f || pitchPhase > pitchWrite) {
                pitchPhase = (pitchWrite - pitchBuf.size / 2).toFloat()
            }
            val base = pitchPhase.toInt()
            val idx = ((base % pitchBuf.size) + pitchBuf.size) % pitchBuf.size
            val next = (idx + 1) % pitchBuf.size
            val frac = pitchPhase - base
            buf[i] = pitchBuf[idx] * (1f - frac) + pitchBuf[next] * frac
            pitchPhase += ratio
            if (pitchPhase >= pitchWrite - 256) pitchPhase = (pitchWrite - pitchBuf.size / 2).toFloat()
            if (pitchPhase < pitchWrite - pitchBuf.size) pitchPhase = (pitchWrite - pitchBuf.size / 2).toFloat()
        }
    }

    private fun applyRingMod(buf: FloatArray, count: Int) {
        val step = TWO_PI * ringModFreq / 48000f
        for (i in 0 until count) {
            buf[i] *= sin(ringPhase)
            ringPhase += step
            if (ringPhase > TWO_PI) ringPhase -= TWO_PI
        }
    }

    private fun applyBitcrusher(buf: FloatArray, count: Int) {
        val steps = 2f.pow((bitDepth - 1).coerceAtLeast(1).toFloat()).toInt()
        for (i in 0 until count) {
            val q = (buf[i] * steps).roundToInt().coerceIn(-steps, steps)
            buf[i] = q.toFloat() / steps
        }
    }

    private fun applyFlanger(buf: FloatArray, count: Int) {
        val lfoStep = TWO_PI * flangerRate / 48000f
        for (i in 0 until count) {
            flangerBuf[flangerWrite % flangerBuf.size] = buf[i]
            flangerWrite++
            val lfo = 0.5f + 0.5f * sin(flangerPhase)
            val delaySamples = 48 + (240 * lfo).toInt()
            val readIdx = (flangerWrite - delaySamples) % flangerBuf.size
            val r = if (readIdx < 0) readIdx + flangerBuf.size else readIdx
            buf[i] = flangerBuf[r] * 0.7f + buf[i] * 0.3f
            flangerPhase += lfoStep
            if (flangerPhase > TWO_PI) flangerPhase -= TWO_PI
        }
    }

    private fun applyEcho(buf: FloatArray, count: Int) {
        val delaySamples = (echoDelayMs * 48000 / 1000).coerceAtLeast(1)
        for (i in 0 until count) {
            val di = ((echoWritePos - delaySamples) % echoDelayBuf.size + echoDelayBuf.size) % echoDelayBuf.size
            val wet = echoDelayBuf[di]
            val out = buf[i] + wet * echoFeedback
            echoDelayBuf[echoWritePos % echoDelayBuf.size] = out
            echoWritePos++
            buf[i] = out
        }
    }


    private var lastVuUpdate = 0L

    private fun updateVu(outBuf: ShortArray, count: Int) {
        val now = System.currentTimeMillis()
        if (now - lastVuUpdate < 60) return
        lastVuUpdate = now

        var sum = 0.0
        for (i in 0 until count) sum += outBuf[i].toDouble() * outBuf[i].toDouble()
        val rms = if (count > 0) sqrt(sum / count) else 0.0
        val db = 20 * log10(rms / 32768.0 + 1e-10)
        val frac = ((db + 60) / 60.0).coerceIn(0.0, 1.0)

        runOnUiThread {
            vuMeter.progress = (frac * 100).toInt()
            vuText.text = if (db <= -60) "-inf dB" else String.format("%.1f dB", db)
        }
    }

    private fun formatSigned(v: Float): String {
        val r = java.lang.Math.round(v * 10f) / 10f
        return if (r > 0) "+${r}" else "${r}"
    }

    private fun updateGainText(g: Float) { gainText.text = "Gain: ${formatSigned(g)}x" }
    private fun updateLoudnessText(mb: Int) { loudnessText.text = "Loudness: +${mb / 100}.${(mb % 100) / 10} dB" }
    private fun updateDistortionText(p: Int) { distortionText.text = "Amount: ${p}%" }
    private fun updateRingModText(f: Float) { ringModText.text = "Freq: ${Math.round(f)} Hz" }
    private fun updateBitcrushText(b: Int) { bitcrushText.text = "Depth: ${b} bit" }
    private fun updateEchoDelayText(ms: Int) { echoDelayText.text = "Delay: ${ms} ms" }
    private fun updateEchoFeedbackText(p: Int) { echoFeedbackText.text = "Feedback: ${p}%" }
    private fun updateFlangerRateText(r: Float) { flangerRateText.text = "Rate: ${String.format("%.1f", r)} Hz" }
    private fun updateBassBoostText(s: Int) { bassBoostText.text = "Strength: ${s / 10}%" }


    // 0 NUKE, 1 ROBOT, 2 DEMON, 3 CHIP, 4 DEEP, 5 HELI
    private fun applyPreset(idx: Int) {
        when (idx) {
            0 -> { // NUKE: peak everything
                gainSlider.progress = 490; loudnessSlider.progress = 6000
                distortionOn = false; distortionSwitch.isChecked = false
                pitchSemi = 0f; pitchSlider.progress = 120
                ringModOn = false; ringModSwitch.isChecked = false
                bitcrushOn = false; bitcrusherSwitch.isChecked = false
                echoOn = false; echoSwitch.isChecked = false
                flangerOn = false; flangerSwitch.isChecked = false
                reverbIdx = 0; reverbSpinner.setSelection(0)
                bassBoostOn = true; bassBoostSwitch.isChecked = true; bassBoostSlider.progress = 1000
                statusText.text = "⚡ NUKE MODE ⚡"
            }
            1 -> { // ROBOT
                gainSlider.progress = 100
                distortionOn = true; distortionSwitch.isChecked = true; distortionSlider.progress = 30
                pitchSemi = 0f; pitchSlider.progress = 120
                ringModOn = true; ringModSwitch.isChecked = true; ringModSlider.progress = 299
                bitcrushOn = true; bitcrusherSwitch.isChecked = true; bitcrushSlider.progress = 8
                echoOn = false; echoSwitch.isChecked = false
                flangerOn = false; flangerSwitch.isChecked = false
                reverbIdx = 0; reverbSpinner.setSelection(0)
                bassBoostOn = false; bassBoostSwitch.isChecked = false
                statusText.text = "🤖 ROBOT MODE"
            }
            2 -> { // DEMON
                gainSlider.progress = 160
                distortionOn = true; distortionSwitch.isChecked = true; distortionSlider.progress = 60
                pitchSemi = -8f; pitchSlider.progress = 40
                ringModOn = false; ringModSwitch.isChecked = false
                bitcrushOn = false; bitcrusherSwitch.isChecked = false
                echoOn = true; echoSwitch.isChecked = true; echoDelaySlider.progress = 200; echoFeedbackSlider.progress = 30
                flangerOn = false; flangerSwitch.isChecked = false
                reverbIdx = 3; reverbSpinner.setSelection(3)
                bassBoostOn = true; bassBoostSwitch.isChecked = true; bassBoostSlider.progress = 700
                statusText.text = "👹 DEMON MODE"
            }

            3 -> { // CHIPMUNK
                gainSlider.progress = 90
                distortionOn = false; distortionSwitch.isChecked = false
                pitchSemi = 8f; pitchSlider.progress = 200
                ringModOn = false; ringModSwitch.isChecked = false
                bitcrushOn = false; bitcrusherSwitch.isChecked = false
                echoOn = false; echoSwitch.isChecked = false
                flangerOn = false; flangerSwitch.isChecked = false
                reverbIdx = 0; reverbSpinner.setSelection(0)
                bassBoostOn = false; bassBoostSwitch.isChecked = false
                statusText.text = "🐿️ CHIPMUNK MODE"
            }
            4 -> { // DEEP
                gainSlider.progress = 140
                distortionOn = false; distortionSwitch.isChecked = false
                pitchSemi = -5f; pitchSlider.progress = 70
                ringModOn = false; ringModSwitch.isChecked = false
                bitcrushOn = false; bitcrusherSwitch.isChecked = false
                echoOn = false; echoSwitch.isChecked = false
                flangerOn = false; flangerSwitch.isChecked = false
                reverbIdx = 4; reverbSpinner.setSelection(4)
                bassBoostOn = true; bassBoostSwitch.isChecked = true; bassBoostSlider.progress = 900
                statusText.text = "🐻 DEEP MODE"
            }
            5 -> { // HELICOPTER
                gainSlider.progress = 120
                distortionOn = false; distortionSwitch.isChecked = false
                pitchSemi = 0f; pitchSlider.progress = 120
                ringModOn = true; ringModSwitch.isChecked = true; ringModSlider.progress = 149
                bitcrushOn = false; bitcrusherSwitch.isChecked = false
                echoOn = true; echoSwitch.isChecked = true; echoDelaySlider.progress = 250; echoFeedbackSlider.progress = 50
                flangerOn = true; flangerSwitch.isChecked = true; flangerRateSlider.progress = 50
                reverbIdx = 0; reverbSpinner.setSelection(0)
                bassBoostOn = false; bassBoostSwitch.isChecked = false
                statusText.text = "🚁 HELICOPTER MODE"
            }
        }
    }
}

