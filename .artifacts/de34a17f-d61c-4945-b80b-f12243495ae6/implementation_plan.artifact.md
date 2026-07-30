# Fix Console Error in Storm Tab

The Storm console is showing a `TypeError` because the Python bridge (Chaquopy) cannot find a `write(String)` method on the output stream used to capture logs in `StormViewModel`.

## User Review Required

> [!IMPORTANT]
> This fix involves refactoring the `TerminalOutputStream` class to be a standalone file so it can be shared between `MainActivity` and `StormViewModel`. This ensures consistent behavior for capturing Python output across the app.

## Proposed Changes

### [Core Utilities]

#### [NEW] [TerminalOutputStream.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/TerminalOutputStream.kt)
- Create a standalone `TerminalOutputStream` class that implements `OutputStream` and includes the required `write(String)` method for Python compatibility.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/MainActivity.kt)
- Remove the local definition of `TerminalOutputStream`.

#### [MODIFY] [StormViewModel.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/StormViewModel.kt)
- Replace the anonymous `OutputStream` with an instance of `TerminalOutputStream` to fix the `TypeError`.

## Verification Plan

### Automated Tests
- Run `gradle build` to ensure the refactoring didn't break anything and the new class is correctly linked.

### Manual Verification
- Deploy the app to the device.
- Open the **STORM** tab and initiate a scan.
- Verify that the console no longer shows a `TypeError` and correctly displays the output from the Python scripts.
