# Fix LAN Discovery to Detect All Devices

The current LAN discovery is only finding the router because of a critical bug in the subnet detection logic and reliance on unreliable checks (like port 7 or ICMP ping). This plan will implement a robust, multi-layered scanning approach that works on non-rooted Android devices.

## User Review Required

> [!IMPORTANT]
> The primary cause of the "only router" issue is a bug in `RobustLanScanner.kt` that was scanning the wrong IP range (e.g., `192.168.1`, `192.168.2` instead of `192.168.1.x`).

> [!WARNING]
> Scanning an entire /24 subnet (254 hosts) with multiple ports can be battery-intensive and may trigger network security alerts on some enterprise routers. We will optimize this by using smart timeouts and parallelizing effectively.

## Proposed Changes

### [Component] LAN Scanning Logic

#### [MODIFY] [RobustLanScanner.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/RobustLanScanner.kt)
- **Fix Subnet Detection**: Use `ConnectivityManager` to get the actual local IP and calculate the `/24` subnet correctly.
- **Remove Port 7 Filter**: Remove the `isHostReachableByTcp(ip, 7)` check as it blocks discovery of most consumer devices.
- **Enhance TCP Sweep**: Directly probe common camera/web ports (80, 554, 8080, 443, etc.) for all hosts in the subnet.
- **Improve SSDP**: Refine the SSDP discovery to be more robust.

#### [MODIFY] [LanScanner.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/LanScanner.kt)
- Update `getLocalIpAndSubnet` to be the "source of truth" for IP detection, used by both scanners.

#### [MODIFY] [DiscoveryCoordinator.kt](file:///C:/Users/Damon/StudioProjects/CamXploit/app/src/main/java/com/spyboy/camxploit/DiscoveryCoordinator.kt)
- Ensure it uses the corrected IP/Subnet information.
- Adjust progress reporting to reflect the status of the deep scan layers.

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors.
- Monitor Logcat for "LAN_SCAN" tags to verify subnet detection.

### Manual Verification
1.  **Run LAN Scan**: Verify that multiple devices (phones, computers, smart TVs, cameras) appear in the list.
2.  **Verify IP Range**: Check Logcat to ensure the scanner is targeting the correct subnet (e.g., `192.168.1.1` - `192.168.1.254`).
3.  **Check Device Details**: Ensure MAC addresses and vendors are correctly resolved for the discovered devices.
