# Task List - Layered Discovery Strategy

- [x] Create `PassiveDiscovery.kt` (SSDP + NsdScanner)
- [x] Create `OnvifProber.kt` (WS-Discovery)
- [x] Create `DiscoveryCoordinator.kt` (Manager for all layers)
- [x] Update `LanScanner.kt` (Optimized port knocking & concurrency)
- [x] Update `NetworkDiscoveryHelper.kt` (Refined mDNS targeting)
- [x] Update `MainActivity.kt` (UI Integration)
- [x] Active SSDP / UPnP Discovery
    - [x] Create `SsdpProber.kt` (Active M-SEARCH + XML Parsing)
    - [x] Integrate into `DiscoveryCoordinator.kt`
    - [x] Update UI in `MainActivity.kt` to show friendly names
- [x] Verification
    - [x] Build project (Semantic check passed)
    - [x] Manual test plan review
