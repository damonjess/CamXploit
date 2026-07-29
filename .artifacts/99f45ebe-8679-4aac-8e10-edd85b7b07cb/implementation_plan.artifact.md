# Layered Discovery Strategy Implementation Plan

Implement a multi-layered network discovery strategy to identify IP cameras efficiently. This combines passive listening, fast active scanning, and deep inspection.

## User Review Required

> [!IMPORTANT]
> The strategy uses raw UDP sockets for SSDP discovery, which may be affected by Android's battery optimization or background restrictions if not run in the foreground.

## Proposed Changes

### [Network Discovery]

#### [NEW] [DiscoveryCoordinator.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/DiscoveryCoordinator.kt)
A coordinator to manage the three layers of discovery: Passive, Active Fast, and Deep.

#### [NEW] [PassiveDiscovery.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/PassiveDiscovery.kt)
- **SSDPLister**: Raw UDP socket listener for SSDP broadcasts (port 1900).
- **NsdScanner**: Native Android `NsdManager` for mDNS/Bonjour services.

#### [MODIFY] [LanScanner.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/LanScanner.kt)
- Update port knocking to include: `81, 554, 8080, 8554, 8899`.
- Optimize ping sweep and port check concurrency.

#### [MODIFY] [NetworkDiscoveryHelper.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/NetworkDiscoveryHelper.kt)
- Refine mDNS discovery to target camera-specific services (`_onvif._tcp`, `_axis-video._tcp`, etc.).

#### [NEW] [OnvifProber.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/OnvifProber.kt)
A dedicated prober for ONVIF devices using WS-Discovery (UDP 3702).

### [UI Integration]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/MainActivity.kt)
- Update `LanScanTab` to use the new `DiscoveryCoordinator`.
- Add UI feedback for each discovery layer.

## Verification Plan

### Automated Tests
- Unit tests for `LanScanner` port logic.
- Mock network tests for SSDP/mDNS parsing.

### Manual Verification
- Deploy to a physical device on a network with IP cameras.
- Verify that cameras are detected via SSDP within seconds.
- Verify that the port knocking correctly identifies cameras on non-standard ports (e.g., 81, 8899).
- Check the "Deep" scan results for ONVIF details.
