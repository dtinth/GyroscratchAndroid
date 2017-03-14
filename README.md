Gyroscratch
===========

[An BMS turntable built using real, ceramic dishes.](https://twitter.com/bemusegame/status/841693301707223041)

1. Download and install Android Studio

2. Install Kotlin plugin

3. Clone this code and build it into your Android.
    Make sure your Android supports MIDI over Bluetooth LE and has a Gyroscope sensor.

4. Set up your computer to be a MIDI Peripheral, with the name "gyroscratch". It's hardcoded because I am not experienced in Android UI development and I built it in one night. Contributions welcome.

5. Run the app and click the button. It should connect.

6. Now you get a MIDI device that sends the Note On/Note Off events according to the scratch.

