# Walkthrough: SSL/TLS Security Audit in SENTINEL

I have integrated a new SSL/TLS Security Audit feature into the **SENTINEL** tab. This tool allows you to perform a deep security scan on the encryption configurations of your saved cameras and network devices.

## Changes Made

### Network Security Logic
- **[NEW] [TlsAuditor.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/pentest/TlsAuditor.kt)**: Implemented the core auditing logic that connects to a target host, performs an SSL handshake, and analyzes the certificate, protocol, and cipher suite for vulnerabilities (e.g., POODLE, weak ciphers, expired certificates).

### UI Enhancements
- **[MODIFY] [MainActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/MainActivity.kt)**:
    - Updated the `SentinelTab` with a modern, scrollable layout.
    - Added a horizontal camera selector (`LazyRow`) at the top of the tab for easy device selection.
    - Integrated a new **TLS AUDIT** button alongside the AI Monitor toggle.
    - Implemented a detailed `TlsReportCard` that displays:
        - A visual security grade (**A+** to **F**).
        - The protocol and status (e.g., TLSv1.2, ENCRYPTED).
        - A full list of detected vulnerabilities.
        - Detailed certificate information (Issuer, Expiry, Self-signed status).

## Verification Results

### Automated Tests
- Successfully ran `gradle assembleDebug` to confirm all code compiles and imports are correctly resolved.

### Manual Verification
1.  Navigate to the **SENTINEL** tab.
2.  Select a saved camera from the top list (the card will turn green when selected).
3.  Click the **TLS AUDIT** button.
4.  Wait for the scan to complete. A detailed report card will appear below the controls with the security grade and audit findings.
