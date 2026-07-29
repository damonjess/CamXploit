# Implementation Plan - Enable Picture-in-Picture (PiP) for Stream Viewer

Enable PiP mode for `StreamViewerActivity` to allow users to monitor camera streams while performing other tasks on their device.

## Proposed Changes

### [Manifest]

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/AndroidManifest.xml)
- Add `android:supportsPictureInPicture="true"` to `StreamViewerActivity`.
- Update `android:configChanges` to include `smallestScreenSize`, `screenLayout`, and `screenSize` to handle PiP transitions correctly.

### [UI Layout]

#### [MODIFY] [activity_stream_viewer.xml](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/res/layout/activity_stream_viewer.xml)
- Add a new button for manual PiP entry in the controls row.

### [Activity Logic]

#### [MODIFY] [StreamViewerActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/StreamViewerActivity.kt)
- Implement `onUserLeaveHint` to automatically enter PiP mode when the user navigates away (e.g., Home button).
- Implement manual PiP trigger via the new button.
- Override `onPictureInPictureModeChanged` to hide the header, stream info, and controls when in PiP mode, leaving only the video stream visible.

## Verification Plan

### Automated Tests
- Run `gradle assembleDebug` to ensure manifest and code changes are valid.

### Manual Verification
1. Launch a camera stream in the app.
2. Tap the new PiP button to enter PiP mode manually.
3. While the stream is playing, press the Home button to verify auto-PiP via `onUserLeaveHint`.
4. Ensure UI elements (header, info, controls) are hidden in PiP mode and reappear when returning to full screen.
5. Verify that the stream continues to play smoothly in the PiP window.
