# Implementation Plan - Fix Scan Hang/Crash during Credential Testing

The app appears to hang or stop producing output during the "Testing Credentials" phase of the camera audit. My investigation revealed a missing constant `IOT_COMMON_PASSWORDS` in the Python backend, which causes a `NameError` crash. Additionally, the progress reporting was too infrequent, making long scans feel like a hang.

## Proposed Changes

### Python Backend

#### [MODIFY] [CamXploit.py](file:///C:/Users/Damon/AndroidStudioProjects/CamXploit/app/src/main/python/CamXploit.py)
- **Add `IOT_COMMON_PASSWORDS`**: Restore the missing list of common IoT passwords used for credential testing.
- **Improve Progress Reporting**:
    - Increase the frequency of progress updates during credential testing (every 2 candidates instead of 5).
    - Add a "Starting..." message to clearly indicate when the credential test begins.
- **Clean up Constants**: Merge and deduplicate the `PORT_SERVICE_MAP` definitions.

## Verification Plan

### Automated Tests
- I'll verify the Python script syntax to ensure all referenced variables (like `IOT_COMMON_PASSWORDS`) are now defined.

### Manual Verification
- The user should run a scan on a target host. The "Testing Credentials" phase should now:
    1. Not crash with a `NameError`.
    2. Show frequent progress updates (e.g., "Progress: Testing 2/60...").
