# Walkthrough - Layered Discovery Strategy

I have successfully implemented the multi-layered network discovery strategy to enhance camera detection speed and accuracy.

## Changes Made

### 📡 Passive Layer
- **SSDP Listening**: Implemented a raw UDP socket listener in [PassiveDiscovery.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/PassiveDiscovery.kt) that waits for `NOTIFY` packets from UPnP devices.
- **mDNS Scanning**: Refined [NetworkDiscoveryHelper.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/NetworkDiscoveryHelper.kt) to target specific camera service types like `_http._tcp`, `_rtsp._tcp`, and `_onvif._tcp` using Android's native `NsdManager`.

### ⚡ Active Fast Layer
- **Optimized Port Knocking**: Updated [LanScanner.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/LanScanner.kt) with a tactical list of camera ports (`80`, `81`, `88`, `443`, `554`, `8080`, `8443`, `8554`, `8899`, `10554`).
- **High-Parallelism Scanning**: Switched to `Dispatchers.IO.limitedParallelism(50)` for both ping sweeps and port scanning, significantly reducing the overall scan time.
- **Speed Improvements**: Reduced ping timeouts to 200ms and socket connection timeouts to 250ms for aggressive local network traversal.

### 🔍 Deep Layer
- **ONVIF Prober**: Added [OnvifProber.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/OnvifProber.kt) to perform active WS-Discovery probes.
- **Active SSDP Prober**: Added [SsdpProber.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/SsdpProber.kt) to send `M-SEARCH` probes. It retrieves and parses UPnP device descriptors to extract friendly names, models, and manufacturers.

### 🎮 Orchestration & UI
- **Discovery Coordinator**: Updated [DiscoveryCoordinator.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/DiscoveryCoordinator.kt) to integrate active SSDP searches targeting `ssdp:all` and specific camera schemas.
- **UI Integration**: Enhanced the host list in [MainActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/MainActivity.kt) to display friendly names (e.g., "Front Door") and models (e.g., "DS-2CD2143G0-I") retrieved via SSDP.

## Verification Results

### Semantic Analysis
Verified all new files and modifications for syntax and basic logic. Resolved initial reference errors in the coordinator and UI hooks.

### Performance Notes
> [!TIP]
> The SSDP listener is passive and extremely battery-efficient. It will catch many modern cameras (like Hikvision and Axis) almost instantly without sending a single packet.

> [!IMPORTANT]
> Ensure the app has **Location Permissions** granted, as Android requires them for mDNS and ARP-related network visibility.
