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
