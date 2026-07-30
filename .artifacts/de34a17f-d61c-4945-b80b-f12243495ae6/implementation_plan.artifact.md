# Implement SSL/TLS Security Audit in SENTINEL

The user wants to integrate a new `TlsAuditor` component into the `SENTINEL` tab. This tool will perform a security scan on the SSL/TLS configuration of network devices (like IP cameras) to identify weak protocols, ciphers, and certificate issues.

## User Review Required

> [!IMPORTANT]
> The `SENTINEL` tab currently focuses on AI-based camera monitoring. I will be adding a new section for "Encryption Audit" to keep the UI clean. This allows users to select a saved camera and perform a deep security scan on its encrypted connections.

## Proposed Changes

### [Network Security]

#### [NEW] [TlsAuditor.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/pentest/TlsAuditor.kt)
- Create the SSL/TLS auditing logic (already provided by the user).

### [UI Components]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/MainActivity.kt)
- Import `com.spyboy.camxploit.pentest.TlsAuditor`.
- Update `SentinelTab` to include:
    - A camera selection list (using `LazyRow`).
    - A "TLS AUDIT" button.
    - A `TlsReportCard` to display the scan results (Grade, Protocol, Vulnerabilities).
- Add scrolling to the `SentinelTab` to accommodate the new content.

## Verification Plan

### Automated Tests
- Run `gradle build` to ensure the new package and imports are correctly resolved.

### Manual Verification
- Deploy the app to a device.
- Navigate to the **SENTINEL** tab.
- Select a saved camera from the top list.
- Click the **TLS AUDIT** button.
- Verify that a report card appears showing the encryption grade and any detected vulnerabilities.
