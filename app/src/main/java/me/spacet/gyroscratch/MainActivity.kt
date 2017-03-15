package me.spacet.gyroscratch

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.midi.MidiDevice
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.support.annotation.ColorInt
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import org.jetbrains.anko.find
import org.jetbrains.anko.onClick
import org.jetbrains.anko.sensorManager

class MainActivity : AppCompatActivity() {

    private enum class RotationMode(val v: Int, @ColorInt val color: Int, val note: Byte = 0) {
        IDLE(0, Color.BLACK),
        CW(1, Color.BLUE, 48),
        CCW(-1, Color.RED, 47)
    }

    private lateinit var textView: TextView
    private var midiDevice: MidiDevice? = null
    private lateinit var rootView: View
    private var inputPort: MidiInputPort? = null
    private var rotationMode: RotationMode = RotationMode.IDLE
        set(value) {
            if (field == value) return

            field = value

            rotationHP = when (value) {
                RotationMode.CCW, RotationMode.CW -> 1.0
                else -> 0.0
            }
        }

    private var lastTimestamp: Long? = null
    private var rotationHP = 1.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rootView = find<View>(android.R.id.content).rootView
        textView = find<TextView>(R.id.textView)

        find<Button>(R.id.bluetoothConnectButton).onClick {
            if (inputPort != null) {
                return@onClick
            }

            val permission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)

            when (permission) {
                PackageManager.PERMISSION_GRANTED -> scan()
                else -> ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 0)
            }
        }

        sensorManager.registerListener(
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        onGyroChanged(event)
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

                    }
                },
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_GAME
        )
    }

    private fun onGyroChanged(event: SensorEvent?) {
        if (event == null) return

        val rotationSpeed = event.values[2] * 180 / Math.PI

        rotationMode = when {
            (rotationSpeed > 10) -> RotationMode.CCW
            (rotationSpeed < -10) -> RotationMode.CW
            (Math.abs(rotationSpeed) < 3) && (rotationHP < 0.9) -> RotationMode.IDLE
            else -> rotationMode
        }

        reconcile()

        lastTimestamp?.let {
            rotationHP *= Math.exp((event.timestamp - it) * -1e-9)
        }

        lastTimestamp = event.timestamp
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 0) {
            scan()
        }
    }

    fun scan() {
        textView.text = "Gonna scan now!"

        val midiManager = applicationContext.getSystemService(Context.MIDI_SERVICE) as MidiManager
        val scanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner

        val filters = listOf(ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700"))
                .build()
        )

        val settings = ScanSettings.Builder()
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()


        scanner.startScan(filters, settings, object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (inputPort != null) return

                result?.device?.let {
                    textView.text = "Found ${it.name}"

                    if (it.name == "gyroscratch") {
                        textView.text = "Connecting to ${it.address}"
                        midiManager.openBluetoothDevice(it, this@MainActivity::take, Handler(Looper.getMainLooper()))
                    }
                }
            }
        })
    }

    fun take(device: MidiDevice) {
        textView.text = "Connected to ${device.info}"
        midiDevice = device
        inputPort = device.openInputPort(0)
    }

    private var activeNote: Byte = 0

    private fun reconcile() {
        val note = rotationMode.note

        if (note == activeNote) {
            return
        }

        rootView.setBackgroundColor(rotationMode.color)


        inputPort?.apply {
            if (activeNote > 0) {
                sendNoteOff(activeNote)
            }

            if (note > 0) {
                sendNoteOn(note)
            }
        }

        activeNote = note
    }

    private fun createNoteEvent(noteOn: Boolean, note: Byte, velocity: Byte) = byteArrayOf((if (noteOn) 0x90 else 0x80).toByte(), note, velocity)

    private fun MidiInputPort.sendNoteEvent(noteOn: Boolean, note: Byte, velocity: Byte = 127) {
        createNoteEvent(noteOn, note, velocity).let {
            send(it, 0, it.size)
        }
    }

    private fun MidiInputPort.sendNoteOff(note: Byte) = sendNoteEvent(noteOn = false, note = note)

    private fun MidiInputPort.sendNoteOn(note: Byte) = sendNoteEvent(noteOn = true, note = note)
}
