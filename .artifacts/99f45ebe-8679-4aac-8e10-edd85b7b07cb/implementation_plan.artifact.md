# Active SSDP / UPnP Discovery Implementation Plan

Implement active SSDP discovery to retrieve rich device metadata (friendly name, model, manufacturer) from IP cameras.

## User Review Required

> [!NOTE]
> This active probe will send multicast packets on the network. Some firewalls or isolated Wi-Fi networks might block these.

## Proposed Changes

### [Network Discovery]

#### [NEW] [SsdpProber.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/SsdpProber.kt)
- **search()**: Sends `M-SEARCH` multicast packets.
- **fetchDeviceDescriptor()**: Downloads the XML from the `LOCATION` header.
- **parseDescriptor()**: Extracts `friendlyName`, `modelName`, and `manufacturer`.

#### [MODIFY] [DiscoveryCoordinator.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/DiscoveryCoordinator.kt)
- Integrate `SsdpProber` into the `startDiscovery()` flow.
- Add `SsdpDeviceInfo` to `DiscoveryResult`.

### [UI Integration]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/MainActivity.kt)
- Update `DiscoveryResult` handling to display friendly names and models in the LAN scan results.
- Prefer friendly names over raw IP addresses or "Unknown" vendors when available.

## Verification Plan

### Automated Tests
- Unit test for XML parsing with sample UPnP descriptors.

### Manual Verification
- Run discovery on a network with UPnP-enabled cameras (e.g., Hikvision, Axis, Sony).
- Verify that the "Friendly Name" (e.g., "Front Door Camera") appears in the list.
- Verify that the "Model" (e.g., "DS-2CD2143G0-I") is correctly extracted.
