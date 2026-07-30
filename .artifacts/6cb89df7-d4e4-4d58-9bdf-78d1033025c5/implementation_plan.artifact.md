# Implementation Plan - Fix Storm Crash and Sentinel UI Confusion

The user reported a persistent crash in the Storm tab when spamming the "VALIDATE" button and confusion over "Missing Headers" in the Sentinel tab.

## Research Findings
- **Log Overload**: The `log()` function launches a new coroutine for every log entry. When the Python backend or rapid validation sends many logs, the Main thread is overwhelmed with state updates and auto-scrolling animations in `LazyColumn`, leading to crashes (likely OOM or ANR).
- **Validation Race Condition**: While `validationJob` is cancelled, the rapid state changes (`Validating` -> `Valid` -> `Validating`) cause UI flickering and potential race conditions in the Compose recomposition loop.
- **UI UX Confusion**: "Missing Headers" in the Sentinel report is interpreted as an app error rather than a security finding.
- **Connection Refused**: In Sentinel, a standard TCP rejection is displayed as a raw error string, which looks like an app failure.

## Proposed Changes

### Storm Module

#### [MODIFY] [StormViewModel.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/StormViewModel.kt)
- **Efficient Logging**:
    - Use `MutableStateFlow.update` for atomic updates.
    - Limit the log list to the last 200 entries to prevent memory bloat.
    - Batch log updates or ensure they don't trigger excessive recompositions.
- **Debounced Validation**: Add a check to prevent launching validation if one was started less than 500ms ago.
- **Process Management**: Ensure `sys.stdout` is only redirected when actually running a storm to avoid side effects during validation.

#### [MODIFY] [StormBreakerScreen.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/StormBreakerScreen.kt)
- Improve the `ConsoleBox` auto-scroll logic to be less aggressive.

### Sentinel Module

#### [MODIFY] [SentinelScreen.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/ui/SentinelScreen.kt)
- **Label Change**: Rename "Missing Headers" to "Analysis: Missing Security Headers" to clarify it's a scan result.
- **Error Formatting**: Clean up the "Connection failed" message to be more user-friendly (e.g., "Service Offline" instead of a full stack trace snippet).

## Verification Plan

### Manual Verification
1.  **Storm Tab**:
    - Rapidly tap "VALIDATE" and "INITIATE STORM".
    - Verify the app remains responsive and does not crash.
    - Verify logs don't fill up indefinitely.
2.  **Sentinel Tab**:
    - Run a scan on a device with a closed port 443.
    - Verify the error message is concise and readable.
    - Verify the "Missing Security Headers" label is clear.
