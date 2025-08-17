# Svømme App

Android app for counting swim laps using camera motion detection and sound detection.

Features:
- Adjustable camera sensitivity and editable region of interest (ROI)
- Live camera preview with draggable and resizable ROI overlay
- Audio threshold detection
- Configurable lane length, turns-per-lap and debounce interval
- Light/Dark theme with high contrast
- Settings menu for all options with persistence
- Displays lap count, total distance and recent intervals with large text
- Optional debug overlay with live detector values and event log

This project uses Jetpack Compose, CameraX and AudioRecord APIs. It is designed to be
extensible and well documented for future enhancements.

## Building

The repository omits the `gradle-wrapper.jar` binary. To build the project you will
need a local Gradle installation. Run the following to generate the wrapper and list
available tasks:

```bash
gradle wrapper
./gradlew tasks
```

The settings menu can be opened from the top-right settings icon on the main screen.
Here you can enable/disable camera or sound counting, adjust detection thresholds,
change the lane length, theme and ROI.

### Controls & Settings

- **Start/Stop** – the floating button toggles whether detections increment the
  lap counter. Previews and levels continue to update while stopped.
- **Sensitivity** – controls how much average pixel change inside the ROI is
  required to trigger a camera event. Values are 0.0–1.0.
- **dB Threshold** – minimum sound level needed to register a sound trigger.
  Levels are measured using RMS and expressed in decibels.
- **ROI** – drag or pinch the overlay to position and size the region of
  interest used for motion detection.
- **Minimum interval** – debounce time in milliseconds between counted events
  from either detector.
- **Debug overlay** – shows current motion intensity, sound level and a log of
  the last 50 detections to assist with calibration.

### Calibration tips

- Adjust the ROI to cover only the area where motion should be detected.
- Start with a low sensitivity / threshold and increase until false triggers
  disappear.
- Use headphones or a quiet room when calibrating the audio threshold.
- Very noisy environments or low light may require higher debounce intervals.
