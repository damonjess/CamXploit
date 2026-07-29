# Implementation Plan - Update RobustLanScanner

This plan outlines the steps to replace the current `RobustLanScanner.kt` with a new, two-phase scanning implementation provided by the user.

## User Review Required

> [!NOTE]
> The new `RobustLanScanner` implements a two-phase scan:
> 1. **Phase 1 (Fast Discovery)**: Scans only 5 high-probability camera ports (80, 8080, 554, 8000, 443) on all hosts in the subnet.
> 2. **Phase 2 (Deep Scan)**: Scans a broader list of ports only on hosts that responded in Phase 1 or are present in the ARP table.
> This should significantly improve scanning speed while maintaining depth for relevant targets.

## Proposed Changes

### [Component Name] Networking / Scanning

#### [MODIFY] [RobustLanScanner.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/RobustLanScanner.kt)
- Replace the entire content with the provided "RobustLanScanner.kt" code.
- Ensure package name `com.spyboy.camxploit` is correct.

## Verification Plan

### Automated Tests
- N/A (Networking hardware dependent)

### Manual Verification
- Deploy the app to a physical device on a network with multiple devices (ideally including some IP cameras or servers).
- Navigate to the "LAN" tab.
- Start a scan.
- Verify:
    - Discovery results appear quickly (Phase 1).
    - Detailed information (more ports) appears for relevant devices (Phase 2).
    - Progress bar behaves correctly.
    - Scan completes successfully.
