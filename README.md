# Svømme App

Android app for counting swim laps using camera motion detection and sound detection.

Features:
- Adjustable camera sensitivity and region of interest
- Audio threshold detection
- Configurable lane length and laps-to-distance calculation
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
