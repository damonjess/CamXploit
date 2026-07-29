# Walkthrough - Layered Discovery Strategy

I have successfully implemented the multi-layered network discovery strategy to enhance camera detection speed and accuracy.

## Changes Made

### 📡 Passive Layer
- **SSDP Listening**: Implemented a raw UDP socket listener in [PassiveDiscovery.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/PassiveDiscovery.kt) that waits for `NOTIFY` packets from UPnP devices.
- **mDNS Scanning**: Refined [NetworkDiscoveryHelper.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/NetworkDiscoveryHelper.kt) to target specific camera service types like `_onvif._tcp` and `_axis-video._tcp`.

### ⚡ Active Fast Layer
- **Optimized Port Knocking**: Updated [LanScanner.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/LanScanner.kt) with a tactical list of camera ports (`81`, `554`, `8080`, `8554`, `8899`) and increased concurrency to 32 hosts.
- **Speed Improvements**: Reduced ping timeouts to 200ms for faster subnet traversal on healthy networks.

### 🔍 Deep Layer
- **ONVIF Prober**: Added [OnvifProber.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/OnvifProber.kt) to perform active WS-Discovery probes, retrieving detailed device info (XAddrs, Types) even if the device doesn't broadcast.

### 🎮 Orchestration & UI
- **Discovery Coordinator**: Created [DiscoveryCoordinator.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/DiscoveryCoordinator.kt) to manage all discovery flows and provide a unified `progressFlow` and `discoveryFlow`.
- **UI Integration**: Integrated the coordinator into [MainActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/MainActivity.kt). The LanScanTab now benefits from real-time updates from all discovery layers.

## Verification Results

### Semantic Analysis
Verified all new files and modifications for syntax and basic logic. Resolved initial reference errors in the coordinator and UI hooks.

### Performance Notes
> [!TIP]
> The SSDP listener is passive and extremely battery-efficient. It will catch many modern cameras (like Hikvision and Axis) almost instantly without sending a single packet.

> [!IMPORTANT]
> Ensure the app has **Location Permissions** granted, as Android requires them for mDNS and ARP-related network visibility.
