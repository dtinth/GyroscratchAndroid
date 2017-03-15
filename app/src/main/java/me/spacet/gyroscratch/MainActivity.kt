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
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import org.jetbrains.anko.find

class MainActivity : AppCompatActivity() {
    private var textView: TextView? = null
    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null
    private var rootView: View? = null
    private var rotationMode: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val button = find<Button>(R.id.bluetoothConnectButton)

        textView = find<TextView>(R.id.textView)
        rootView = textView!!.rootView

        button.setOnClickListener {
            if (inputPort != null) {
                return@setOnClickListener
            }

            val permission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)

            when (permission) {
                PackageManager.PERMISSION_GRANTED -> scan()
                else -> ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 0)
            }
        }

        val sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        var rotationHP = 1.0
        var lastTimestamp: Long = 0
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                val rotationSpeed = event.values[2] * 180 / Math.PI
                when {
                    rotationSpeed > 10 -> {
                        rotationMode = -1
                        rotationHP = 1.0
                    }

                    rotationSpeed < -10 -> {
                        rotationMode = 1
                        rotationHP = 1.0
                    }

                    Math.abs(rotationSpeed) < 3 && rotationHP < 0.9 -> {
                        rotationMode = 0
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 0) {
            scan()
        }
    }

    fun scan() {
        textView!!.text = "Gonna scan now!"
        val midiManager = applicationContext.getSystemService(Context.MIDI_SERVICE) as MidiManager
        val scanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
        val list = ArrayList<ScanFilter>()
        list.add(ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700"))
                .build())
        val settings = ScanSettings.Builder()
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
        scanner.startScan(list, settings, object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (inputPort != null) return

                result?.device?.let {
                    textView!!.text = "Found ${it.name}"

                    if (it.name == "gyroscratch") {
                        textView!!.text = "Connecting to ${it.address}"
                        midiManager.openBluetoothDevice(it, fun(device) = take(device), Handler(Looper.getMainLooper()))
                    }
                }
            }
        })
    }
    fun take(device: MidiDevice) {
        textView!!.text = "Connected to ${device.info}"
        midiDevice = device
        inputPort = device.openInputPort(0)
    }
    private var activeNote: Byte = 0
    fun reconcile() {
        val mode = rotationMode
        rootView!!.setBackgroundColor(
                if (mode == 0) { Color.BLACK } else if (mode == 1) { Color.BLUE } else { Color.RED })
        val port = inputPort
        if (port != null) {
            val note: Byte = if (mode == 1) { 48 } else if (mode == -1) { 47 } else { 0 }
            if (note != activeNote) {
                if (activeNote > 0) {
                    port.send(byteArrayOf(0x80.toByte(), activeNote, 127), 0, 3)
                }
                activeNote = note
                if (activeNote > 0) {
                    port.send(byteArrayOf(0x90.toByte(), activeNote, 127), 0, 3)
                }
            }
        }
    }
}
